package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Motor de la batalla por turnos (COMBAT-3). Valida y arranca una pelea (mundo desbloqueado, nivel,
 * energía y cooldown), resuelve cada acción del jugador (Atacar / Defender / Objeto) con el
 * contraataque del monstruo, y aplica el desenlace: al ganar reparte coins + XP + botín (y, si es
 * jefe, desbloquea el mundo siguiente); al perder resta salud y activa el cooldown. El azar se
 * inyecta ({@link Aleatorio}) para poder testear el balance de forma determinista. El HP de combate
 * y el estado del turno viven en {@link CombateSesion} (en memoria); aquí solo va la lógica.
 */
public final class BatallaService {

    /** Energía que cuesta entrar en una pelea. */
    public static final int ENERGIA_POR_PELEA = 12;
    /** Cooldown tras perder una batalla. */
    public static final Duration COOLDOWN_DERROTA = Duration.ofMinutes(5);
    /** Salud (bienestar) que se pierde al ser derrotado. */
    public static final int SALUD_PERDIDA_DERROTA = 20;

    private static final double VARIANZA_MIN = 0.85;
    private static final double VARIANZA_RANGO = 0.30; // factor final en [0.85, 1.15]
    private static final double DEFENSA_MULT = 0.5;    // el monstruo pega la mitad si defiendes

    /** Fuente de azar en [0,1). Inyectable para tests deterministas. */
    @FunctionalInterface
    public interface Aleatorio {
        double next();
    }

    /** Estado del intento de iniciar una pelea. */
    public enum InicioEstado {
        OK, MONSTRUO_NO_EXISTE, MUNDO_BLOQUEADO, NIVEL_INSUFICIENTE, SIN_ENERGIA, EN_COOLDOWN
    }

    /**
     * Resultado de iniciar: estado + sesión (si {@code OK}) + un detalle según el fallo
     * (nivel requerido, o segundos de cooldown restantes).
     */
    public record ResultadoInicio(InicioEstado estado, CombateSesion sesion, int detalle) {
    }

    /** Desenlace de una acción de combate. */
    public enum Desenlace { CONTINUA, VICTORIA, DERROTA }

    /**
     * Resultado de un turno: desenlace + daños de ese turno (para el embed).
     *
     * @param desenlace        cómo queda la pelea tras el turno
     * @param danoAlMonstruo   daño que ha hecho el jugador (0 si defendió o usó objeto de energía)
     * @param danoAlJugador    daño del contraataque del monstruo (0 si no hubo contraataque)
     * @param curado           HP curado con un objeto (0 si no aplica)
     * @param sinContraataque  {@code true} si el monstruo no contraatacó (p. ej. objeto de energía)
     */
    public record Turno(Desenlace desenlace, int danoAlMonstruo, int danoAlJugador,
                        int curado, boolean sinContraataque) {
    }

    /**
     * Botín de la victoria.
     *
     * @param coins             coins ganados
     * @param xp                XP ganada
     * @param subioNivel        si la XP hizo subir de nivel
     * @param items             ids de ítems que han caído (loot)
     * @param jefeDerrotado     si el monstruo era el jefe del mundo
     * @param siguienteMundoId  id del mundo recién desbloqueado, o {@code null}
     */
    public record Botin(long coins, int xp, boolean subioNivel, List<String> items,
                        boolean jefeDerrotado, String siguienteMundoId) {
    }

    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;
    private final EconomiaRepositorio economia;
    private final XpService xp;
    private final MundoRepositorio mundos;
    private final Aleatorio azar;

    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos, Aleatorio azar) {
        this.personajes = personajes;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.economia = economia;
        this.xp = xp;
        this.mundos = mundos;
        this.azar = azar;
    }

    /** Constructor de producción: azar real ({@link ThreadLocalRandom}). */
    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos) {
        this(personajes, inventario, usuarios, economia, xp, mundos,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * Valida y arranca una pelea contra {@code monstruoId}. Si todo está en orden, <b>consume la
     * energía</b> y devuelve la sesión lista para el primer turno.
     */
    public ResultadoInicio iniciar(long discordId, String monstruoId) {
        Optional<Monstruos> mo = Monstruos.porId(monstruoId);
        if (mo.isEmpty()) {
            return new ResultadoInicio(InicioEstado.MONSTRUO_NO_EXISTE, null, 0);
        }
        Monstruos monstruo = mo.get();
        usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);

        // Mundo desbloqueado (jefe del anterior derrotado).
        Mundos mundo = Mundos.porId(monstruo.mundo()).orElseThrow();
        Set<String> completados = mundos.completados(discordId);
        if (!MundoService.estaDesbloqueado(mundo, completados)) {
            return new ResultadoInicio(InicioEstado.MUNDO_BLOQUEADO, null, 0);
        }
        // Nivel recomendado del mundo.
        int nivel = usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
        if (nivel < mundo.nivelRequerido()) {
            return new ResultadoInicio(InicioEstado.NIVEL_INSUFICIENTE, null, mundo.nivelRequerido());
        }
        // Cooldown tras derrota.
        if (p.ultimoCombate() != null) {
            long restante = COOLDOWN_DERROTA.getSeconds()
                    - Duration.between(p.ultimoCombate(), Instant.now()).getSeconds();
            if (restante > 0) {
                return new ResultadoInicio(InicioEstado.EN_COOLDOWN, null, (int) restante);
            }
        }
        // Energía (atómico): si no hay, no se arranca.
        if (!personajes.gastarEnergia(discordId, ENERGIA_POR_PELEA)) {
            return new ResultadoInicio(InicioEstado.SIN_ENERGIA, null, ENERGIA_POR_PELEA);
        }

        CombateSesion sesion = new CombateSesion(discordId, monstruo.mundo(), monstruo,
                CombateService.ataqueDe(p), CombateService.defensaDe(p),
                CombateService.hpCombate(p));
        return new ResultadoInicio(InicioEstado.OK, sesion, 0);
    }

    /** Acción <b>Atacar</b>: el jugador golpea; si el monstruo sobrevive, contraataca. */
    public Turno atacar(CombateSesion s) {
        int dano = CombateService.dano(s.ataqueJugador(), defensaMonstruo(s.monstruo()), factor());
        s.danarMonstruo(dano);
        if (s.monstruoMuerto()) {
            return new Turno(Desenlace.VICTORIA, dano, 0, 0, false);
        }
        int contra = contraataque(s);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), dano, contra, 0, false);
    }

    /** Acción <b>Defender</b>: el jugador no golpea, pero el contraataque llega a la mitad. */
    public Turno defender(CombateSesion s) {
        s.ponerDefendiendo(true);
        int contra = contraataque(s);
        s.ponerDefendiendo(false);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), 0, contra, 0, false);
    }

    /**
     * Acción <b>Objeto</b>: usa un consumible del inventario (lo descuenta). Los de SALUD curan HP de
     * combate y gastan el turno (el monstruo contraataca); los de ENERGIA dan un <b>turno extra</b>
     * (el monstruo no contraataca). Devuelve {@code null} si el ítem no es un consumible válido o el
     * jugador no lo tiene.
     */
    public Turno usarObjeto(CombateSesion s, String itemId) {
        Optional<Items> item = Items.porId(itemId);
        if (item.isEmpty() || item.get().categoria() != Items.Categoria.CONSUMIBLE) {
            return null;
        }
        Items i = item.get();
        if (!inventario.quitar(s.jugadorId(), itemId, 1)) {
            return null;
        }
        if (i.efecto() == Items.Efecto.ENERGIA) {
            // Turno extra: recuperas tempo, el monstruo no contraataca este turno.
            s.avanzarTurno();
            return new Turno(Desenlace.CONTINUA, 0, 0, 0, true);
        }
        // SALUD (o cualquier otro consumible): cura HP de combate y el monstruo contraataca.
        int curado = Math.min(i.valor(), s.hpMaxJugador() - s.hpJugador());
        s.curarJugador(i.valor());
        int contra = contraataque(s);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), 0, contra, curado, false);
    }

    /** Reparte el botín de una victoria (coins + XP + loot) y desbloquea el mundo si era el jefe. */
    public Botin recompensar(CombateSesion s) {
        Monstruos m = s.monstruo();
        economia.ingresar(s.jugadorId(), m.coins(), "combate:" + m.id());
        XpResultado xpRes = xp.ganarXp(s.jugadorId(), m.xp());

        List<String> caidos = new ArrayList<>();
        for (Monstruos.Drop drop : m.loot()) {
            if (azar.next() < drop.prob()) {
                inventario.anadir(s.jugadorId(), drop.itemId(), 1);
                caidos.add(drop.itemId());
            }
        }

        boolean jefe = m.esJefe();
        String siguiente = null;
        if (jefe) {
            mundos.marcarJefeDerrotado(s.jugadorId(), s.mundoId());
            // El mundo desbloqueado es el que tiene a este como anterior.
            siguiente = Mundos.CATALOGO.stream()
                    .filter(w -> w.orden() == Mundos.porId(s.mundoId()).map(Mundos::orden).orElse(-1) + 1)
                    .map(Mundos::id).findFirst().orElse(null);
        }
        return new Botin(m.coins(), m.xp(), xpRes.subioNivel(), caidos, jefe, siguiente);
    }

    /** Aplica la penalización de una derrota: resta salud y activa el cooldown. */
    public void penalizar(long discordId) {
        personajes.registrarDerrota(discordId, SALUD_PERDIDA_DERROTA);
    }

    // ---------------------- internos ----------------------

    /** Contraataque del monstruo (mitad si el jugador defiende). Devuelve el daño aplicado. */
    private int contraataque(CombateSesion s) {
        int dano = CombateService.dano(s.monstruo().poder(), s.defensaJugador(), factor());
        if (s.defendiendo()) {
            dano = Math.max(1, (int) Math.round(dano * DEFENSA_MULT));
        }
        s.danarJugador(dano);
        return dano;
    }

    private static Desenlace desenlaceTrasContra(CombateSesion s) {
        return s.jugadorMuerto() ? Desenlace.DERROTA : Desenlace.CONTINUA;
    }

    /** Defensa del monstruo: pequeña, proporcional a su poder (el HP es la esponja principal). */
    private static int defensaMonstruo(Monstruos m) {
        return m.poder() / 8;
    }

    private double factor() {
        return VARIANZA_MIN + azar.next() * VARIANZA_RANGO;
    }
}
