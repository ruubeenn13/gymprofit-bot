package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.MineriaEstado;
import com.gymprofit.bot.db.MineriaRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lógica de {@code /minar} y {@code /reparar} (COMBAT-5). Actividad universal: quien tenga un pico en
 * el inventario puede minar (se usa el de mayor tier <b>con durabilidad</b>), gastando energía y con
 * cooldown. Cada minado suelta minerales (según tier del pico y nivel de minería, que sube con el
 * uso) y desgasta el pico; al agotarse hay que {@code /reparar} (sumidero de coins). El azar se
 * inyecta ({@link BatallaService.Aleatorio}) para tests deterministas.
 */
public final class MineriaService {

    /** Energía que cuesta un minado. */
    public static final int ENERGIA_POR_MINAR = 10;
    /** Cooldown entre minados. */
    public static final Duration COOLDOWN = Duration.ofMinutes(2);
    /** Tope de unidades por minado. */
    public static final int CANTIDAD_MAX = 5;
    /** Coste de reparación por punto de durabilidad y tier del pico. */
    public static final long COSTE_REPARAR_POR_PUNTO = 8;

    /** Estado del intento de minar. */
    public enum Estado { OK, SIN_PICO, PICO_ROTO, SIN_ENERGIA, EN_COOLDOWN }

    /** Estado del intento de reparar. */
    public enum EstadoReparar { OK, NO_ES_PICO, NO_TIENE, YA_REPARADO, SIN_SALDO }

    /**
     * Resultado de minar.
     *
     * @param estado         resultado
     * @param minerales      minerales obtenidos (id → cantidad), vacío si no {@code OK}
     * @param nivelNuevo     nivel de minería tras el minado
     * @param detalle        segundos de cooldown restantes o energía necesaria, según el fallo
     * @param picoId         pico usado (o {@code null})
     * @param durabilidad    durabilidad restante del pico usado
     * @param durabilidadMax durabilidad máxima del pico usado
     */
    public record Resultado(Estado estado, Map<String, Integer> minerales, int nivelNuevo,
                            int detalle, String picoId, int durabilidad, int durabilidadMax) {
    }

    /**
     * Resultado de reparar.
     *
     * @param estado resultado
     * @param coste  coins cobrados
     * @param picoId pico reparado (o {@code null})
     */
    public record ResultadoReparar(EstadoReparar estado, long coste, String picoId) {
    }

    private final MineriaRepositorio mineria;
    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, BatallaService.Aleatorio azar) {
        this.mineria = mineria;
        this.personajes = personajes;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios) {
        this(mineria, personajes, inventario, economia, usuarios,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Coste de reparar {@code faltante} puntos en un pico de tier {@code tier}. */
    public static long costeReparar(int tier, int faltante) {
        return (long) faltante * COSTE_REPARAR_POR_PUNTO * tier;
    }

    /** Ejecuta un minado: valida pico/durabilidad/cooldown/energía, tira minerales y desgasta el pico. */
    public Resultado minar(long discordId) {
        usuarios.obtenerOCrear(discordId);
        List<Picos> propios = picosPropios(discordId);
        if (propios.isEmpty()) {
            return fallo(Estado.SIN_PICO, 0);
        }
        // Mejor pico (por tier) que aún tenga durabilidad.
        Picos pico = null;
        int durActual = 0;
        for (Picos p : propios) {
            int dur = mineria.durabilidad(discordId, p.itemId(), p.durabilidadMax());
            if (dur > 0 && (pico == null || p.tier() > pico.tier())) {
                pico = p;
                durActual = dur;
            }
        }
        if (pico == null) {
            return fallo(Estado.PICO_ROTO, 0);
        }

        MineriaEstado estado = mineria.obtenerOCrear(discordId);
        if (estado.ultimoMinado() != null) {
            long restante = COOLDOWN.getSeconds()
                    - Duration.between(estado.ultimoMinado(), Instant.now()).getSeconds();
            if (restante > 0) {
                return fallo(Estado.EN_COOLDOWN, (int) restante);
            }
        }
        if (!personajes.gastarEnergia(discordId, ENERGIA_POR_MINAR)) {
            return fallo(Estado.SIN_ENERGIA, ENERGIA_POR_MINAR);
        }

        Map<String, Integer> obtenidos = tirar(pico.tier(), estado.nivelMineria());
        obtenidos.forEach((id, cant) -> inventario.anadir(discordId, id, cant));
        mineria.registrarMinado(discordId);
        mineria.gastarDurabilidad(discordId, pico.itemId(), pico.durabilidadMax());
        return new Resultado(Estado.OK, obtenidos, estado.nivelMineria() + 1, 0,
                pico.itemId(), Math.max(0, durActual - 1), pico.durabilidadMax());
    }

    /** Repara un pico del inventario dejándolo a tope (cobra un coste proporcional al desgaste). */
    public ResultadoReparar reparar(long discordId, String picoId) {
        Picos pico = Picos.porId(picoId).orElse(null);
        if (pico == null) {
            return new ResultadoReparar(EstadoReparar.NO_ES_PICO, 0, null);
        }
        usuarios.obtenerOCrear(discordId);
        if (inventario.cantidad(discordId, picoId) <= 0) {
            return new ResultadoReparar(EstadoReparar.NO_TIENE, 0, picoId);
        }
        int dur = mineria.durabilidad(discordId, picoId, pico.durabilidadMax());
        int faltante = pico.durabilidadMax() - dur;
        if (faltante <= 0) {
            return new ResultadoReparar(EstadoReparar.YA_REPARADO, 0, picoId);
        }
        long coste = costeReparar(pico.tier(), faltante);
        if (!economia.gastar(discordId, coste, "reparar:" + picoId)) {
            return new ResultadoReparar(EstadoReparar.SIN_SALDO, coste, picoId);
        }
        mineria.repararPico(discordId, picoId, pico.durabilidadMax());
        return new ResultadoReparar(EstadoReparar.OK, coste, picoId);
    }

    private static Resultado fallo(Estado estado, int detalle) {
        return new Resultado(estado, Map.of(), 0, detalle, null, 0, 0);
    }

    /** Picos que posee el jugador (según el inventario). */
    private List<Picos> picosPropios(long discordId) {
        Map<String, Integer> inv = inventario.listar(discordId);
        return inv.keySet().stream()
                .map(id -> Picos.porId(id).orElse(null))
                .filter(p -> p != null)
                .toList();
    }

    /**
     * Tira los minerales de un minado: la cantidad crece con el nivel; cada unidad se elige entre los
     * extraíbles por el pico, con peso inverso al tier (los raros salen menos).
     */
    private Map<String, Integer> tirar(int tierPico, int nivel) {
        List<Minerales> elegibles = Minerales.extraiblesCon(tierPico);
        int cantidad = Math.min(CANTIDAD_MAX, 1 + nivel / 25 + (azar.next() < 0.5 ? 1 : 0));
        int tierMax = elegibles.stream().mapToInt(Minerales::tier).max().orElse(1);

        Map<String, Integer> res = new LinkedHashMap<>();
        for (int i = 0; i < cantidad; i++) {
            Minerales m = elegir(elegibles, tierMax);
            res.merge(m.itemId(), 1, Integer::sum);
        }
        return res;
    }

    /** Elige un mineral con peso inverso al tier (tier bajo = más probable). */
    private Minerales elegir(List<Minerales> elegibles, int tierMax) {
        int pesoTotal = elegibles.stream().mapToInt(m -> tierMax - m.tier() + 1).sum();
        int objetivo = (int) (azar.next() * pesoTotal);
        int acum = 0;
        for (Minerales m : elegibles) {
            acum += tierMax - m.tier() + 1;
            if (objetivo < acum) {
                return m;
            }
        }
        return elegibles.get(elegibles.size() - 1);
    }
}
