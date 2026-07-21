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
 *
 * <p>Los <b>efectos pasivos</b> ({@link PasivoService}) entran en dos sitios: {@code MINERIA_CANTIDAD}
 * suma minerales <b>y sube el tope</b> {@link #CANTIDAD_MAX}, y {@code MINERIA_DURABILIDAD} da una
 * probabilidad de que el pico no se desgaste. Esa tirada usa el <b>mismo</b> {@code Aleatorio}
 * inyectado, así que sigue siendo determinista en tests sin añadir infraestructura nueva.
 */
public final class MineriaService {

    /** Energía que cuesta un minado. */
    public static final int ENERGIA_POR_MINAR = 10;
    /** Cooldown entre minados. */
    public static final Duration COOLDOWN = Duration.ofMinutes(2);
    /**
     * Tope <b>base</b> de unidades por minado. Los pasivos de {@code MINERIA_CANTIDAD} lo suben
     * (ver {@link #tirar}); si no lo hicieran, el pasivo sería invisible para quien ya topa.
     */
    public static final int CANTIDAD_MAX = 5;
    /** Coste de reparación por punto de durabilidad y tier del pico. */
    public static final long COSTE_REPARAR_POR_PUNTO = 8;

    /** Estado del intento de minar. */
    public enum Estado { OK, SIN_PICO, PICO_ROTO, SIN_ENERGIA, EN_COOLDOWN, DORMIDO }

    /** Estado del intento de reparar. */
    public enum EstadoReparar { OK, NO_ES_PICO, NO_TIENE, YA_REPARADO, SIN_SALDO }

    /**
     * Resultado de minar.
     *
     * @param estado              resultado
     * @param minerales           minerales obtenidos (id → cantidad), vacío si no {@code OK}
     * @param nivelNuevo          nivel de minería tras el minado
     * @param detalle             segundos de cooldown restantes o energía necesaria, según el fallo
     * @param picoId              pico usado (o {@code null})
     * @param durabilidad         durabilidad restante del pico usado
     * @param durabilidadMax      durabilidad máxima del pico usado
     * @param durabilidadAhorrada el pasivo de durabilidad ha evitado el desgaste en este minado
     */
    public record Resultado(Estado estado, Map<String, Integer> minerales, int nivelNuevo,
                            int detalle, String picoId, int durabilidad, int durabilidadMax,
                            boolean durabilidadAhorrada) {
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
    private final DescansoService descanso;
    /** Efectos pasivos del jugador. {@code null} equivale a no tener nada equipado. */
    private final PasivoService pasivos;
    private final BatallaService.Aleatorio azar;

    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          PasivoService pasivos, BatallaService.Aleatorio azar) {
        this.mineria = mineria;
        this.personajes = personajes;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
        this.descanso = descanso;
        this.pasivos = pasivos;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          PasivoService pasivos) {
        this(mineria, personajes, inventario, economia, usuarios, descanso, pasivos,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Coste de reparar {@code faltante} puntos en un pico de tier {@code tier}. */
    public static long costeReparar(int tier, int faltante) {
        return (long) faltante * COSTE_REPARAR_POR_PUNTO * tier;
    }

    /**
     * Ejecuta un minado: valida sueño/pico/durabilidad/cooldown/energía, tira minerales y desgasta el
     * pico.
     */
    public Resultado minar(long discordId) {
        usuarios.obtenerOCrear(discordId);
        // Primera guarda: dormido no se pica. Va antes que todo lo demás para no gastar energía ni
        // durabilidad al intentarlo, y para que el comando pueda ofrecer despertar.
        if (descanso.estaDormido(discordId)) {
            return fallo(Estado.DORMIDO, 0);
        }
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

        // Un único viaje a los pasivos por minado: hacen falta dos tipos de bono (cantidad y
        // durabilidad) y cada llamada a bonosDe son tres consultas, así que se leen juntos.
        Map<Pasivos.Tipo, Double> bonos = bonosDe(discordId);
        Map<String, Integer> obtenidos = tirar(pico.tier(), estado.nivelMineria(),
                bonoCantidad(bono(bonos, Pasivos.Tipo.MINERIA_CANTIDAD)));
        obtenidos.forEach((id, cant) -> inventario.anadir(discordId, id, cant));
        mineria.registrarMinado(discordId);
        // Pasivo de durabilidad: con suerte el pico no se gasta este minado. Se tira con el mismo
        // Aleatorio inyectado, así que el test es determinista sin añadir infraestructura.
        boolean ahorrada = ahorraDurabilidad(azar.next(),
                bono(bonos, Pasivos.Tipo.MINERIA_DURABILIDAD));
        if (!ahorrada) {
            mineria.gastarDurabilidad(discordId, pico.itemId(), pico.durabilidadMax());
        }
        return new Resultado(Estado.OK, obtenidos, estado.nivelMineria() + 1, 0,
                pico.itemId(), ahorrada ? durActual : Math.max(0, durActual - 1),
                pico.durabilidadMax(), ahorrada);
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

    /**
     * Unidades extra por minado que aporta el bono de {@code MINERIA_CANTIDAD}. <b>Pura.</b>
     *
     * <p>Es un bono <b>plano</b> (no una fracción): se redondea a unidades y se topa en
     * {@code Pasivos.TOPES.get(MINERIA_CANTIDAD)} (+3). Un bono negativo se ignora: los pasivos
     * nunca pueden restar minerales.
     *
     * @param bono bono bruto de los pasivos
     * @return unidades extra, siempre entre 0 y el tope
     */
    public static int bonoCantidad(double bono) {
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.MINERIA_CANTIDAD),
                Math.max(0, bono));
        return (int) Math.round(aplicado);
    }

    /**
     * Decide si el pasivo de {@code MINERIA_DURABILIDAD} evita el desgaste del pico. <b>Pura.</b>
     *
     * <p>La comparación es {@code tirada < probabilidad}, así que con bono 0 nunca ahorra (ni
     * siquiera con la tirada 0,0, que {@code nextDouble()} sí puede devolver). La probabilidad se
     * topa en {@code Pasivos.TOPES.get(MINERIA_DURABILIDAD)} (40 %): el pico siempre se acaba
     * gastando, que es lo que sostiene el sumidero de coins de {@code /reparar}.
     *
     * @param tirada tirada uniforme en [0,1)
     * @param bono   bono bruto de los pasivos
     * @return {@code true} si este minado no desgasta el pico
     */
    public static boolean ahorraDurabilidad(double tirada, double bono) {
        return tirada < Math.min(Pasivos.TOPES.get(Pasivos.Tipo.MINERIA_DURABILIDAD),
                Math.max(0, bono));
    }

    private static Resultado fallo(Estado estado, int detalle) {
        return new Resultado(estado, Map.of(), 0, detalle, null, 0, 0, false);
    }

    /** Bonos pasivos del jugador, o el mapa vacío si el service no está inyectado. */
    private Map<Pasivos.Tipo, Double> bonosDe(long discordId) {
        return pasivos == null ? Map.of() : pasivos.bonosDe(discordId);
    }

    /** Lectura defensiva de un tipo concreto del mapa de bonos. */
    private static double bono(Map<Pasivos.Tipo, Double> bonos, Pasivos.Tipo tipo) {
        return bonos.getOrDefault(tipo, 0.0);
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
     *
     * <p>{@code bonoCantidad} suma unidades <b>y sube el tope</b> {@link #CANTIDAD_MAX}. Lo segundo
     * es imprescindible: sin ello, un minero de nivel alto (que ya toca el tope) no notaría el
     * pasivo, y es justo el que se lo ha comprado.
     */
    private Map<String, Integer> tirar(int tierPico, int nivel, int bonoCantidad) {
        List<Minerales> elegibles = Minerales.extraiblesCon(tierPico);
        int cantidad = Math.min(CANTIDAD_MAX + bonoCantidad,
                1 + nivel / 25 + (azar.next() < 0.5 ? 1 : 0) + bonoCantidad);
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
