package com.gymprofit.bot.services;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.EjerciciosApi;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Consultas al catálogo de ejercicios con la resiliencia que exige Render free (SPEC §3.2 del
 * diseño): caché en memoria con TTL (la clave es la consulta completa, incluido el idioma, y el
 * valor la respuesta ya mapeada: la segunda persona que consulta no espera el despertar de la
 * API) y reintento con espera creciente ante fallo de red o 5xx, respetando el
 * {@code Retry-After} de un 429. No conoce JDA: los comandos lo llaman y pintan.
 */
public final class EjercicioService {

    private static final Logger log = LoggerFactory.getLogger(EjercicioService.class);

    /** Ejercicios por página del embed de {@code /ejercicios} (spec §4). */
    public static final int TAMANO_PAGINA = 8;
    private static final int MAX_INTENTOS = 3;

    private final EjerciciosApi api;
    private final long ttlMillis;
    private final long esperaBaseMillis;
    private final LongSupplier reloj;
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    /** Valor cacheado con su instante de caducidad (según el reloj inyectado). */
    private record Entrada(Object valor, long caducaEn) { }

    /** Producción: TTL de 5 min y esperas reales de 2 s de base. */
    public EjercicioService(EjerciciosApi api) {
        this(api, Duration.ofMinutes(5), 2_000, System::currentTimeMillis);
    }

    /** TTL, espera base y reloj inyectables para testear sin dormir ni depender del reloj real. */
    public EjercicioService(EjerciciosApi api, Duration ttl, long esperaBaseMillis,
                            LongSupplier reloj) {
        this.api = api;
        this.ttlMillis = ttl.toMillis();
        this.esperaBaseMillis = esperaBaseMillis;
        this.reloj = reloj;
    }

    /** Búsqueda paginada del catálogo; filtros a {@code null} = sin filtro. Cacheada. */
    @SuppressWarnings("unchecked")
    public PaginaDTO<EjercicioDTO> buscar(String q, String grupo, String dificultad,
                                          int pagina, String idioma) {
        String clave = String.join("|", "buscar", vacia(q), vacia(grupo), vacia(dificultad),
                String.valueOf(pagina), idioma);
        return (PaginaDTO<EjercicioDTO>) cacheado(clave,
                () -> ejecutar(() -> api.buscar(vaciaANull(q), vaciaANull(grupo),
                        vaciaANull(dificultad), pagina, TAMANO_PAGINA, idioma)));
    }

    /** Ficha completa de un ejercicio. Cacheada (la ficha del día se pide muchas veces). */
    public EjercicioDTO porId(int id, String idioma) {
        String clave = String.join("|", "id", String.valueOf(id), idioma);
        return (EjercicioDTO) cacheado(clave, () -> ejecutar(() -> api.porId(id, idioma)));
    }

    /** Catálogo completo (solo lo usa el sorteo diario, una vez al día: sin caché). */
    public List<EjercicioDTO> listarTodos(String idioma) {
        return ejecutar(() -> api.listarTodos(idioma));
    }

    private Object cacheado(String clave, Supplier<Object> carga) {
        long ahora = reloj.getAsLong();
        Entrada entrada = cache.get(clave);
        if (entrada != null && entrada.caducaEn() > ahora) {
            return entrada.valor();
        }
        Object valor = carga.get();
        cache.put(clave, new Entrada(valor, ahora + ttlMillis));
        return valor;
    }

    /**
     * Ejecuta la llamada con hasta {@link #MAX_INTENTOS} intentos: fallo de red y 5xx esperan
     * esperaBase × nº de intento (creciente); un 429 espera lo que diga {@code Retry-After}.
     * Cualquier otro código es un error definitivo (no tiene sentido reintentar un 400).
     */
    private <T> T ejecutar(Supplier<Call<T>> llamada) {
        ApiException ultimo = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                Response<T> respuesta = llamada.get().execute();
                if (respuesta.isSuccessful() && respuesta.body() != null) {
                    return respuesta.body();
                }
                int codigo = respuesta.code();
                if (codigo == 429) {
                    ultimo = new ApiException("API con rate limit (429)");
                    esperar(segundosRetryAfter(respuesta) * 1_000L);
                } else if (codigo >= 500) {
                    ultimo = new ApiException("Error de la API: HTTP " + codigo);
                    esperar(esperaBaseMillis * intento);
                } else {
                    throw new ApiException("Respuesta inesperada de la API: HTTP " + codigo);
                }
            } catch (IOException e) {
                ultimo = new ApiException("Fallo de red hablando con la API", e);
                esperar(esperaBaseMillis * intento);
            }
        }
        log.warn("Llamada a la API agotó los {} intentos", MAX_INTENTOS, ultimo);
        throw ultimo;
    }

    private static long segundosRetryAfter(Response<?> respuesta) {
        try {
            String cabecera = respuesta.headers().get("Retry-After");
            return cabecera == null ? 1 : Long.parseLong(cabecera.trim());
        } catch (NumberFormatException e) {
            return 1; // Retry-After con fecha HTTP en vez de segundos: espera mínima
        }
    }

    private void esperar(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Interrumpido esperando el reintento", e);
        }
    }

    private static String vacia(String s) {
        return s == null ? "" : s;
    }

    private static String vaciaANull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
