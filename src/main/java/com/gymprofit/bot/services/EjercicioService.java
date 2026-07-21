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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Consultas al catálogo de ejercicios con la resiliencia que exige Render free (SPEC §3.2 del
 * diseño): caché en memoria con TTL (la clave es la consulta completa, incluido el idioma, y el
 * valor la respuesta ya mapeada: la segunda persona que consulta no espera el despertar de la
 * API) y reintento con espera creciente ante fallo de red o 5xx, respetando el
 * {@code Retry-After} de un 429. No conoce JDA: los comandos lo llaman y pintan.
 *
 * <p>La caché es <b>acotada</b> (las claves llevan texto que escribe el usuario en un slash
 * command: sin tope crecerían durante toda la vida del proceso) y de <b>vuelo único</b>: si N
 * personas lanzan la misma consulta con la API dormida, solo sale una petición y las demás se
 * enganchan a ella en vez de abrir N esperas de 60 s.</p>
 */
public final class EjercicioService {

    private static final Logger log = LoggerFactory.getLogger(EjercicioService.class);

    /** Ejercicios por página del embed de {@code /ejercicios} (spec §4). */
    public static final int TAMANO_PAGINA = 8;
    private static final int MAX_INTENTOS = 3;
    /**
     * Tope de la espera entre reintentos. Detrás de esto hay una interacción de Discord esperando
     * respuesta: por mucho que un {@code Retry-After} pida 300 s, dormirlos solo garantiza que
     * nadie reciba nada. Mejor rendirse con un aviso amable que bloquear un hilo del pool.
     */
    private static final long ESPERA_MAXIMA_MILLIS = 10_000;
    /** Tope de entradas vivas en caché (la clave la escribe el usuario: no puede ser infinita). */
    private static final int MAX_ENTRADAS_CACHE = 500;

    private final EjerciciosApi api;
    private final long ttlMillis;
    private final long esperaBaseMillis;
    private final LongSupplier reloj;
    private final LongConsumer espera;
    private final Map<Object, Entrada> cache = new ConcurrentHashMap<>();

    /**
     * Valor cacheado con su instante de caducidad (según el reloj inyectado). El valor es un
     * {@link CompletableFuture} y no el objeto ya cargado: así la entrada se publica <i>antes</i>
     * de lanzar la petición y quien llegue después espera a esa misma carga (vuelo único).
     */
    private record Entrada(CompletableFuture<Object> valor, long caducaEn) { }

    /** Clave tipada de una búsqueda: evita las colisiones de concatenar campos con separador. */
    private record ClaveBusqueda(String q, String grupo, String dificultad, int pagina,
                                 String idioma) { }

    /** Clave tipada de una ficha por id. */
    private record ClaveFicha(int id, String idioma) { }

    /** Producción: TTL de 5 min y esperas reales de 2 s de base. */
    public EjercicioService(EjerciciosApi api) {
        this(api, Duration.ofMinutes(5), 2_000, System::currentTimeMillis);
    }

    /** TTL, espera base y reloj inyectables para testear sin dormir ni depender del reloj real. */
    public EjercicioService(EjerciciosApi api, Duration ttl, long esperaBaseMillis,
                            LongSupplier reloj) {
        this(api, ttl, esperaBaseMillis, reloj, EjercicioService::dormir);
    }

    /**
     * Variante con la espera inyectable: permite a los tests comprobar <i>cuántos</i> ms se piden
     * (el {@code Retry-After}, el backoff, el recorte) sin dormirlos de verdad.
     *
     * @param espera receptor de los ms a esperar; en producción es {@link Thread#sleep(long)}
     */
    public EjercicioService(EjerciciosApi api, Duration ttl, long esperaBaseMillis,
                            LongSupplier reloj, LongConsumer espera) {
        this.api = api;
        this.ttlMillis = ttl.toMillis();
        this.esperaBaseMillis = esperaBaseMillis;
        this.reloj = reloj;
        this.espera = espera;
    }

    /** Búsqueda paginada del catálogo; filtros a {@code null} = sin filtro. Cacheada. */
    public PaginaDTO<EjercicioDTO> buscar(String q, String grupo, String dificultad,
                                          int pagina, String idioma) {
        // Se normaliza UNA vez y se usa lo mismo para la clave y para la petición: así "press",
        // " press " y (null, "", "   ") no abren entradas de caché distintas.
        String texto = normalizar(q);
        String grupoN = normalizar(grupo);
        String dificultadN = normalizar(dificultad);
        ClaveBusqueda clave = new ClaveBusqueda(texto, grupoN, dificultadN, pagina, idioma);
        return cacheado(clave, () -> ejecutar(
                () -> api.buscar(texto, grupoN, dificultadN, pagina, TAMANO_PAGINA, idioma)));
    }

    /** Ficha completa de un ejercicio. Cacheada (la ficha del día se pide muchas veces). */
    public EjercicioDTO porId(int id, String idioma) {
        return cacheado(new ClaveFicha(id, idioma), () -> ejecutar(() -> api.porId(id, idioma)));
    }

    /** Catálogo completo (solo lo usa el sorteo diario, una vez al día: sin caché a propósito). */
    public List<EjercicioDTO> listarTodos(String idioma) {
        return ejecutar(() -> api.listarTodos(idioma));
    }

    /**
     * Devuelve el valor cacheado de la clave o lo carga. La entrada (un future aún sin completar)
     * se publica antes de cargar, de modo que las llamadas simultáneas con la misma clave se
     * enganchan a la primera en vez de repetir la petición.
     */
    private <T> T cacheado(Object clave, Supplier<T> carga) {
        long ahora = reloj.getAsLong();
        CompletableFuture<Object> propio = new CompletableFuture<>();
        Entrada nuestra = new Entrada(propio, ahora + ttlMillis);
        // compute (no computeIfAbsent) porque además de "no estaba" hay que sustituir lo caducado.
        // Dentro NO se hace E/S: solo se decide quién carga.
        Entrada elegida = cache.compute(clave,
                (k, previa) -> (previa != null && previa.caducaEn() > ahora) ? previa : nuestra);
        if (elegida != nuestra) {
            // Único cast sin comprobar de la clase: el tipo del valor lo fija el tipo de la clave
            // (ClaveBusqueda → PaginaDTO, ClaveFicha → EjercicioDTO), que solo construimos aquí.
            @SuppressWarnings("unchecked")
            T compartido = (T) esperarA(elegida.valor());
            return compartido;
        }
        try {
            T valor = carga.get();
            propio.complete(valor);
            podar(ahora);
            return valor;
        } catch (RuntimeException e) {
            // Se quita la entrada para no envenenar la clave: el siguiente en pedirla reintenta.
            cache.remove(clave, nuestra);
            propio.completeExceptionally(e);
            throw e;
        }
    }

    /** Espera a la carga que lanzó otro hilo, propagando su error tal cual. */
    private static Object esperarA(CompletableFuture<Object> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw new ApiException("Fallo esperando la consulta al catálogo", e.getCause());
        }
    }

    /**
     * Mantiene la caché acotada. Solo se mira al superar el tope (el caso normal no paga nada):
     * primero se tiran las entradas caducadas y, si aun así no cabe, se vacía entera — perder
     * caché es barato; crecer sin techo con claves que teclea el usuario, no.
     */
    private void podar(long ahora) {
        if (cache.size() <= MAX_ENTRADAS_CACHE) {
            return;
        }
        cache.values().removeIf(entrada -> entrada.caducaEn() <= ahora);
        if (cache.size() > MAX_ENTRADAS_CACHE) {
            log.info("Caché de ejercicios llena con entradas vigentes; se vacía entera");
            cache.clear();
        }
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
                if (respuesta.isSuccessful()) {
                    T cuerpo = respuesta.body();
                    if (cuerpo != null) {
                        return cuerpo;
                    }
                    // 2xx sin cuerpo: no es un fallo de red ni un 5xx, es un contrato roto.
                    throw new ApiException("La API respondió HTTP " + respuesta.code()
                            + " sin cuerpo");
                }
                int codigo = respuesta.code();
                if (codigo == 429) {
                    ultimo = new ApiException("API con rate limit (429)");
                    esperarSiQuedanIntentos(intento, millisRetryAfter(respuesta));
                } else if (codigo >= 500) {
                    ultimo = new ApiException("Error de la API: HTTP " + codigo);
                    esperarSiQuedanIntentos(intento, esperaBaseMillis * intento);
                } else {
                    throw new ApiException("Respuesta inesperada de la API: HTTP " + codigo);
                }
            } catch (IOException e) {
                ultimo = new ApiException("Fallo de red hablando con la API", e);
                esperarSiQuedanIntentos(intento, esperaBaseMillis * intento);
            } catch (ApiException e) {
                // El login perezoso ocurre DENTRO del interceptor de OkHttp, que no envuelve las
                // unchecked: una ApiException con causa IOException es exactamente el mismo fallo
                // de red que arriba (la API despertando) y se reintenta igual. Sin causa —«login
                // rechazado: HTTP 401», 400, 2xx sin cuerpo— es un error real: falla ya.
                if (!(e.getCause() instanceof IOException)) {
                    throw e;
                }
                ultimo = e;
                esperarSiQuedanIntentos(intento, esperaBaseMillis * intento);
            }
        }
        log.warn("Llamada a la API agotó los {} intentos", MAX_INTENTOS, ultimo);
        throw ultimo;
    }

    /** Milisegundos que pide el {@code Retry-After}; 1 s si falta o viene con fecha HTTP. */
    private static long millisRetryAfter(Response<?> respuesta) {
        try {
            String cabecera = respuesta.headers().get("Retry-After");
            return (cabecera == null ? 1 : Long.parseLong(cabecera.trim())) * 1_000L;
        } catch (NumberFormatException e) {
            return 1_000L; // Retry-After con fecha HTTP en vez de segundos: espera mínima
        }
    }

    /**
     * Espera antes del siguiente intento, recortada a {@link #ESPERA_MAXIMA_MILLIS}. Tras el
     * último intento no se espera: solo retrasaría la excepción que ya es inevitable.
     */
    private void esperarSiQuedanIntentos(int intento, long millis) {
        if (intento >= MAX_INTENTOS) {
            return;
        }
        espera.accept(Math.min(millis, ESPERA_MAXIMA_MILLIS));
    }

    private static void dormir(long millis) {
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

    /** {@code null}, cadena vacía y solo espacios son lo mismo: sin filtro. */
    private static String normalizar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
