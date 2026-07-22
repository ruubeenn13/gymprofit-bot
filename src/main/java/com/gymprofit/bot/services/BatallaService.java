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
import java.util.Map;
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
    /** Energía que cuesta entrar en una mazmorra (varias oleadas). */
    public static final int ENERGIA_POR_MAZMORRA = 30;
    /** Cooldown tras perder una batalla. */
    public static final Duration COOLDOWN_DERROTA = Duration.ofMinutes(5);
    /** Salud (bienestar) que se pierde al ser derrotado. */
    public static final int SALUD_PERDIDA_DERROTA = 20;

    private static final double VARIANZA_MIN = 0.85;
    private static final double VARIANZA_RANGO = 0.30; // factor final en [0.85, 1.15]
    private static final double DEFENSA_MULT = 0.5;    // el monstruo pega la mitad si defiendes
    private static final int CRIT_MULT = 2;            // un crítico dobla el daño
    private static final double CRIT_MONSTRUO = 0.08;  // prob. de crítico del monstruo
    private static final double ESQUIVA_MONSTRUO = 0.05; // prob. de que el monstruo esquive

    /** Fuente de azar en [0,1). Inyectable para tests deterministas. */
    @FunctionalInterface
    public interface Aleatorio {
        double next();
    }

    /** Estado del intento de iniciar una pelea. */
    public enum InicioEstado {
        OK, MONSTRUO_NO_EXISTE, MUNDO_BLOQUEADO, NIVEL_INSUFICIENTE, SIN_ENERGIA, EN_COOLDOWN,
        DORMIDO
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
     * Resultado de un turno: desenlace + daños de ese turno + marcas de crítico/esquiva (para el
     * embed).
     *
     * @param desenlace        cómo queda la pelea tras el turno
     * @param danoAlMonstruo   daño que ha hecho el jugador (0 si defendió, esquivado o usó energía)
     * @param danoAlJugador    daño del contraataque del monstruo (0 si no hubo o lo esquivaste)
     * @param curado           HP curado con un objeto (0 si no aplica)
     * @param sinContraataque  {@code true} si el monstruo no contraatacó (p. ej. objeto de energía)
     * @param critJugador      el golpe del jugador fue crítico
     * @param esquivaMonstruo  el monstruo esquivó el golpe del jugador
     * @param critMonstruo     el contraataque del monstruo fue crítico
     * @param esquivaJugador   el jugador esquivó el contraataque
     */
    public record Turno(Desenlace desenlace, int danoAlMonstruo, int danoAlJugador,
                        int curado, boolean sinContraataque, boolean critJugador,
                        boolean esquivaMonstruo, boolean critMonstruo, boolean esquivaJugador) {
    }

    /** Resultado de un golpe individual (con azar de esquiva/crítico ya resuelto). */
    private record Golpe(int dano, boolean critico, boolean esquivado) {
    }

    /**
     * Ataque, defensa y crítico tras aplicar los efectos pasivos. Existe para que la aplicación de
     * los bonos sea una función <b>pura y pública</b> —y por tanto testeable sin base de datos—
     * aunque {@link #nuevaSesion} siga siendo privado, que es donde debe estar el snapshot.
     */
    public record BonosCombate(int ataque, int defensa, double critico) {
    }

    /**
     * Aplica los bonos pasivos de combate al ataque, la defensa y el crítico base. <b>Pura.</b>
     *
     * <p>Los planos se redondean con {@code Math.round} y el crítico se suma <b>antes</b> del tope
     * existente de {@code CombateService.probCritico}, con el mismo techo duro de 0,9 que usa el
     * encantamiento {@code CRITICO}: un jugador ya saturado no gana nada, y eso está bien — el
     * crítico de pasivos es para quien aún no ha entrenado fuerza.
     */
    public static BonosCombate conPasivos(int ataque, int defensa, double crit,
                                          Map<Pasivos.Tipo, Double> bonos) {
        return new BonosCombate(
                ataque + (int) Math.round(bonos.getOrDefault(Pasivos.Tipo.COMBATE_ATAQUE, 0.0)),
                defensa + (int) Math.round(bonos.getOrDefault(Pasivos.Tipo.COMBATE_DEFENSA, 0.0)),
                Math.min(0.9, crit + bonos.getOrDefault(Pasivos.Tipo.CRITICO, 0.0)));
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
    private final DescansoService descanso;
    private final PasivoService pasivos;
    private final Aleatorio azar;

    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos, DescansoService descanso,
                          PasivoService pasivos, Aleatorio azar) {
        this.personajes = personajes;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.economia = economia;
        this.xp = xp;
        this.mundos = mundos;
        this.descanso = descanso;
        this.pasivos = pasivos;
        this.azar = azar;
    }

    /** Constructor de producción: azar real ({@link ThreadLocalRandom}). */
    public BatallaService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios, EconomiaRepositorio economia,
                          XpService xp, MundoRepositorio mundos, DescansoService descanso,
                          PasivoService pasivos) {
        this(personajes, inventario, usuarios, economia, xp, mundos, descanso, pasivos,
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
        // Dormido no se pelea: se comprueba antes de gastar energía o mirar cooldowns, para que el
        // intento salga gratis y el listener pueda ofrecer despertar.
        if (descanso.estaDormido(discordId)) {
            return new ResultadoInicio(InicioEstado.DORMIDO, null, 0);
        }
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
        return new ResultadoInicio(InicioEstado.OK, nuevaSesion(p, monstruo, monstruo.mundo()), 0);
    }

    /**
     * Valida y arranca una <b>mazmorra</b> (oleadas de monstruos seguidas). Mismo gate que
     * {@link #iniciar} (mundo, nivel, cooldown) pero cuesta más energía; la sesión queda con la
     * primera oleada activa y el resto en cola.
     */
    public ResultadoInicio iniciarMazmorra(long discordId, String mazmorraId) {
        Mazmorras mz = Mazmorras.porId(mazmorraId).orElse(null);
        if (mz == null || mz.oleadas().isEmpty()) {
            return new ResultadoInicio(InicioEstado.MONSTRUO_NO_EXISTE, null, 0);
        }
        usuarios.obtenerOCrear(discordId);
        // Misma guarda que en iniciar: dormido no se entra a la mazmorra.
        if (descanso.estaDormido(discordId)) {
            return new ResultadoInicio(InicioEstado.DORMIDO, null, 0);
        }
        Personaje p = personajes.obtenerOCrear(discordId);
        Mundos mundo = Mundos.porId(mz.mundo()).orElseThrow();
        if (!MundoService.estaDesbloqueado(mundo, mundos.completados(discordId))) {
            return new ResultadoInicio(InicioEstado.MUNDO_BLOQUEADO, null, 0);
        }
        int nivel = usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
        if (nivel < mundo.nivelRequerido()) {
            return new ResultadoInicio(InicioEstado.NIVEL_INSUFICIENTE, null, mundo.nivelRequerido());
        }
        if (p.ultimoCombate() != null) {
            long restante = COOLDOWN_DERROTA.getSeconds()
                    - Duration.between(p.ultimoCombate(), Instant.now()).getSeconds();
            if (restante > 0) {
                return new ResultadoInicio(InicioEstado.EN_COOLDOWN, null, (int) restante);
            }
        }
        if (!personajes.gastarEnergia(discordId, ENERGIA_POR_MAZMORRA)) {
            return new ResultadoInicio(InicioEstado.SIN_ENERGIA, null, ENERGIA_POR_MAZMORRA);
        }

        List<Monstruos> oleadas = mz.oleadas().stream()
                .map(id -> Monstruos.porId(id).orElseThrow()).toList();
        CombateSesion sesion = nuevaSesion(p, oleadas.get(0), mz.mundo());
        sesion.configurarMazmorra(mazmorraId, oleadas.subList(1, oleadas.size()), oleadas.size());
        return new ResultadoInicio(InicioEstado.OK, sesion, 0);
    }

    /** Otorga el bonus de completar una mazmorra (coins + XP). */
    public BonusMazmorra completarMazmorra(long discordId, String mazmorraId) {
        Mazmorras mz = Mazmorras.porId(mazmorraId).orElseThrow();
        economia.ingresar(discordId, mz.bonusCoins(), "mazmorra:" + mazmorraId);
        xp.ganarXp(discordId, mz.bonusXp());
        return new BonusMazmorra(mz.bonusCoins(), mz.bonusXp());
    }

    /** Bonus de finalización de una mazmorra. */
    public record BonusMazmorra(long coins, int xp) {
    }

    /** Construye la sesión aplicando ataque/crit/esquiva/robo del personaje (arma, nivel, encanto). */
    private CombateSesion nuevaSesion(Personaje p, Monstruos monstruo, String mundoId) {
        int ataque = CombateService.ataqueDe(p) + p.armaNivel() * CombateService.NIVEL_DANO;
        double crit = CombateService.probCritico(p);
        double esquiva = CombateService.probEsquiva(p);
        double roboVida = 0;

        // Los pasivos entran ANTES que el encantamiento a propósito: DANO_PCT multiplica y debe
        // multiplicar sobre el ataque completo, que es la lectura intuitiva de «+X % de daño».
        // Y entran AQUÍ, en el snapshot, para que valgan toda la pelea y toda la mazmorra: sin
        // consultas por turno y sin el exploit de equipar el cohete antes del golpe final del jefe.
        BonosCombate bp = conPasivos(ataque, CombateService.defensaDe(p), crit,
                pasivos == null ? Map.of() : pasivos.bonosDe(p.discordId()));
        ataque = bp.ataque();
        int defensa = bp.defensa();
        crit = bp.critico();

        Encantamiento e = p.armaEncanto() == null ? null
                : Encantamiento.porId(p.armaEncanto()).orElse(null);
        if (e != null) {
            switch (e.tipo()) {
                case DANO_PLANO -> ataque += (int) e.magnitud();
                case DANO_PCT -> ataque = (int) Math.round(ataque * (1 + e.magnitud()));
                case CRITICO -> crit = Math.min(0.9, crit + e.magnitud());
                case ESQUIVA -> esquiva = Math.min(0.6, esquiva + e.magnitud());
                case ROBO_VIDA -> roboVida = e.magnitud();
            }
        }
        return new CombateSesion(p.discordId(), mundoId, monstruo, ataque, defensa, crit, esquiva,
                roboVida, CombateService.hpCombate(p));
    }

    /** Acción <b>Atacar</b>: el jugador golpea (con opción a crítico/esquiva); si sobrevive, contraataca. */
    public Turno atacar(CombateSesion s) {
        Golpe pg = golpe(s.ataqueJugador(), defensaMonstruo(s.monstruo()),
                s.critJugador(), ESQUIVA_MONSTRUO);
        s.danarMonstruo(pg.dano());
        robarVida(s, pg.dano());
        if (s.monstruoMuerto()) {
            return new Turno(Desenlace.VICTORIA, pg.dano(), 0, 0, false,
                    pg.critico(), pg.esquivado(), false, false);
        }
        Golpe cg = contraataque(s);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), pg.dano(), cg.dano(), 0, false,
                pg.critico(), pg.esquivado(), cg.critico(), cg.esquivado());
    }

    /** Acción <b>Defender</b>: el jugador no golpea, pero el contraataque llega a la mitad. */
    public Turno defender(CombateSesion s) {
        s.ponerDefendiendo(true);
        Golpe cg = contraataque(s);
        s.ponerDefendiendo(false);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), 0, cg.dano(), 0, false,
                false, false, cg.critico(), cg.esquivado());
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
            return new Turno(Desenlace.CONTINUA, 0, 0, 0, true, false, false, false, false);
        }
        // SALUD (o cualquier otro consumible): cura HP de combate y el monstruo contraataca.
        int curado = Math.min(i.valor(), s.hpMaxJugador() - s.hpJugador());
        s.curarJugador(i.valor());
        Golpe cg = contraataque(s);
        s.avanzarTurno();
        return new Turno(desenlaceTrasContra(s), 0, cg.dano(), curado, false,
                false, false, cg.critico(), cg.esquivado());
    }

    private static final int POTENTE_MULT = 2;   // el golpe potente dobla el ataque
    private static final double CURA_PCT = 0.30;  // la curación recupera 30% del HP máximo

    /**
     * Acción <b>Habilidad</b>: usa una habilidad si está fuera de cooldown (la pone en cooldown al
     * terminar). Devuelve {@code null} si la habilidad no existe o aún está en cooldown.
     */
    public Turno usarHabilidad(CombateSesion s, String habilidadId) {
        Optional<Habilidad> hab = Habilidad.porId(habilidadId);
        if (hab.isEmpty() || s.cooldown(habilidadId) > 0) {
            return null;
        }
        Habilidad h = hab.get();
        int defMon = defensaMonstruo(s.monstruo());
        Turno t = switch (h) {
            case POTENTE -> {
                // Golpe imposible de esquivar que dobla el ataque.
                Golpe pg = golpe(s.ataqueJugador() * POTENTE_MULT, defMon, s.critJugador(), 0.0);
                s.danarMonstruo(pg.dano());
                robarVida(s, pg.dano());
                if (s.monstruoMuerto()) {
                    yield new Turno(Desenlace.VICTORIA, pg.dano(), 0, 0, false,
                            pg.critico(), false, false, false);
                }
                Golpe cg = contraataque(s);
                yield new Turno(desenlaceTrasContra(s), pg.dano(), cg.dano(), 0, false,
                        pg.critico(), false, cg.critico(), cg.esquivado());
            }
            case CURAR -> {
                int cura = (int) Math.round(s.hpMaxJugador() * CURA_PCT);
                int curado = Math.min(cura, s.hpMaxJugador() - s.hpJugador());
                s.curarJugador(cura);
                Golpe cg = contraataque(s);
                yield new Turno(desenlaceTrasContra(s), 0, cg.dano(), curado, false,
                        false, false, cg.critico(), cg.esquivado());
            }
            case ATURDIR -> {
                Golpe pg = golpe(s.ataqueJugador(), defMon, s.critJugador(), ESQUIVA_MONSTRUO);
                s.danarMonstruo(pg.dano());
                robarVida(s, pg.dano());
                if (s.monstruoMuerto()) {
                    yield new Turno(Desenlace.VICTORIA, pg.dano(), 0, 0, false,
                            pg.critico(), pg.esquivado(), false, false);
                }
                // Aturdido: el monstruo pierde su contraataque este turno (sinContraataque + daño > 0).
                yield new Turno(Desenlace.CONTINUA, pg.dano(), 0, 0, true,
                        pg.critico(), pg.esquivado(), false, false);
            }
        };
        s.avanzarTurno();
        s.ponerCooldown(h.id(), h.cooldown());
        return t;
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

    /**
     * Resuelve un golpe: primero tira esquiva (anula el golpe), luego el daño con varianza y por
     * último crítico (×2). Cada tirada consume una muestra de {@link #azar}.
     */
    private Golpe golpe(int ofensiva, int defensa, double probCritico, double probEsquiva) {
        if (azar.next() < probEsquiva) {
            return new Golpe(0, false, true);
        }
        int dano = CombateService.dano(ofensiva, defensa, factor());
        boolean critico = azar.next() < probCritico;
        if (critico) {
            dano *= CRIT_MULT;
        }
        return new Golpe(dano, critico, false);
    }

    /** Robo de vida del encantamiento: cura al jugador una fracción del daño que ha infligido. */
    private void robarVida(CombateSesion s, int dano) {
        if (s.roboVida() > 0 && dano > 0) {
            s.curarJugador((int) Math.round(dano * s.roboVida()));
        }
    }

    /** Contraataque del monstruo (mitad si el jugador defiende; el jugador puede esquivarlo). */
    private Golpe contraataque(CombateSesion s) {
        Golpe g = golpe(s.monstruo().poder(), s.defensaJugador(), CRIT_MONSTRUO, s.esquivaJugador());
        int dano = g.dano();
        if (dano > 0 && s.defendiendo()) {
            dano = Math.max(1, (int) Math.round(dano * DEFENSA_MULT));
        }
        s.danarJugador(dano);
        return new Golpe(dano, g.critico(), g.esquivado());
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
