package com.gymprofit.bot.services;

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
 * Lógica de {@code /minar} (COMBAT-5a). Actividad universal: quien tenga un pico en el inventario
 * puede minar (se usa el de mayor tier), gastando energía y con cooldown. Suelta minerales según el
 * tier del pico y el nivel de minería (que sube con el uso). El azar se inyecta ({@link
 * BatallaService.Aleatorio}) para tests deterministas.
 */
public final class MineriaService {

    /** Energía que cuesta un minado. */
    public static final int ENERGIA_POR_MINAR = 10;
    /** Cooldown entre minados. */
    public static final Duration COOLDOWN = Duration.ofMinutes(2);
    /** Tope de unidades por minado. */
    public static final int CANTIDAD_MAX = 5;

    /** Estado del intento de minar. */
    public enum Estado { OK, SIN_PICO, SIN_ENERGIA, EN_COOLDOWN }

    /**
     * Resultado de minar.
     *
     * @param estado     resultado
     * @param minerales  minerales obtenidos (id → cantidad), vacío si no {@code OK}
     * @param nivelNuevo nivel de minería tras el minado
     * @param detalle    segundos de cooldown restantes o energía necesaria, según el fallo
     */
    public record Resultado(Estado estado, Map<String, Integer> minerales, int nivelNuevo,
                            int detalle) {
    }

    private final MineriaRepositorio mineria;
    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, UsuarioDiscordRepositorio usuarios,
                          BatallaService.Aleatorio azar) {
        this.mineria = mineria;
        this.personajes = personajes;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public MineriaService(MineriaRepositorio mineria, PersonajeRepositorio personajes,
                          InventarioRepositorio inventario, UsuarioDiscordRepositorio usuarios) {
        this(mineria, personajes, inventario, usuarios,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Ejecuta un minado: valida pico/cooldown/energía, tira minerales y sube el nivel. */
    public Resultado minar(long discordId) {
        usuarios.obtenerOCrear(discordId);
        Picos pico = mejorPico(discordId);
        if (pico == null) {
            return new Resultado(Estado.SIN_PICO, Map.of(), 0, 0);
        }
        MineriaEstado estado = mineria.obtenerOCrear(discordId);
        if (estado.ultimoMinado() != null) {
            long restante = COOLDOWN.getSeconds()
                    - Duration.between(estado.ultimoMinado(), Instant.now()).getSeconds();
            if (restante > 0) {
                return new Resultado(Estado.EN_COOLDOWN, Map.of(), estado.nivelMineria(),
                        (int) restante);
            }
        }
        if (!personajes.gastarEnergia(discordId, ENERGIA_POR_MINAR)) {
            return new Resultado(Estado.SIN_ENERGIA, Map.of(), estado.nivelMineria(),
                    ENERGIA_POR_MINAR);
        }

        Map<String, Integer> obtenidos = tirar(pico.tier(), estado.nivelMineria());
        obtenidos.forEach((id, cant) -> inventario.anadir(discordId, id, cant));
        mineria.registrarMinado(discordId);
        return new Resultado(Estado.OK, obtenidos, estado.nivelMineria() + 1, 0);
    }

    /** Pico de mayor tier que posee el jugador, o {@code null} si no tiene ninguno. */
    private Picos mejorPico(long discordId) {
        Map<String, Integer> inv = inventario.listar(discordId);
        return inv.keySet().stream()
                .map(id -> Picos.porId(id).orElse(null))
                .filter(p -> p != null)
                .max((a, b) -> Integer.compare(a.tier(), b.tier()))
                .orElse(null);
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
