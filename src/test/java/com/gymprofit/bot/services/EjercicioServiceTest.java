package com.gymprofit.bot.services;

import com.gymprofit.bot.api.ApiClient;
import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la resiliencia del catálogo (SPEC §7 del diseño): mapeo correcto, reintento ante
 * 5xx, respeto del Retry-After de un 429 (con sus ms exactos, recorte incluido), error amable
 * tras agotar reintentos y que la caché evita la segunda llamada dentro del TTL (y la repite al
 * caducar). También los caminos de fallo: un login caído por red se reintenta, unas credenciales
 * rechazadas no, las claves adyacentes no colisionan y dos consultas iguales a la vez viajan una.
 */
class EjercicioServiceTest {

    private MockWebServer servidor;
    private ApiClient cliente;
    private EjercicioService service;
    private final AtomicLong reloj = new AtomicLong(0); // reloj inyectado (controla el TTL)
    /** Ms que el service ha pedido dormir, sin dormirlos: así se asserta el backoff exacto. */
    private final List<Long> esperas = new ArrayList<>();

    private static final String LOGIN_OK =
            "{\"token\":\"acc1\",\"refreshToken\":\"ref1\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}";
    private static final String PAGINA = """
            {"content":[{"id":1,"nombre":"Sentadilla","grupoMuscular":"PIERNAS",
            "dificultad":"PRINCIPIANTE"}],"page":0,"size":8,"totalElements":873,
            "totalPages":110,"last":false}""";

    @BeforeEach
    void arrancar() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK)); // login perezoso de la 1ª llamada
        cliente = new ApiClient(servidor.url("/api/").toString(),
                "gymprobot", "secreta", Duration.ofSeconds(5));
        // esperaBase 1000 con la espera anotada en vez de dormida; TTL 5 min sobre el reloj
        // inyectado. Así los tests miden el backoff sin tardar lo que tardaría en producción.
        service = new EjercicioService(cliente.ejercicios(), Duration.ofMinutes(5), 1_000,
                reloj::get, esperas::add);
    }

    @AfterEach
    void parar() throws Exception {
        cliente.cerrar(); // sin esto cada test deja 4 hilos y dos pools vivos hasta el final
        servidor.shutdown();
    }

    @Test
    void buscaYMapeaLaPagina() {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        PaginaDTO<EjercicioDTO> p = service.buscar(null, "PIERNAS", null, 0, "es");
        assertEquals(873, p.totalElements());
        assertEquals("Sentadilla", p.content().get(0).nombre());
    }

    @Test
    void laCacheEvitaLaSegundaLlamadaYCaducaConElTtl() {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("press", null, null, 0, "es");
        service.buscar("press", null, null, 0, "es"); // misma clave: de caché
        assertEquals(2, servidor.getRequestCount()); // login + 1 búsqueda

        reloj.addAndGet(Duration.ofMinutes(6).toMillis()); // pasa el TTL
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("press", null, null, 0, "es");
        assertEquals(3, servidor.getRequestCount()); // caducó: vuelve a la red
    }

    @Test
    void distintoIdiomaEsOtraEntradaDeCache() {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("press", null, null, 0, "es");
        service.buscar("press", null, null, 0, "en");
        assertEquals(3, servidor.getRequestCount()); // login + una por idioma
    }

    @Test
    void reintentaAnte5xx() {
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        PaginaDTO<EjercicioDTO> p = service.buscar(null, null, null, 0, "es");
        assertEquals(873, p.totalElements());
    }

    @Test
    void respetaElRetryAfterDeUn429() {
        servidor.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "2"));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        PaginaDTO<EjercicioDTO> p = service.buscar(null, null, null, 0, "es");
        assertEquals(873, p.totalElements());
        assertEquals(List.of(2_000L), esperas); // los segundos de la cabecera, en ms
    }

    @Test
    void un429SinRetryAfterEsperaElMinimo() {
        servidor.enqueue(new MockResponse().setResponseCode(429));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar(null, null, null, 0, "es");
        assertEquals(List.of(1_000L), esperas);
    }

    @Test
    void unRetryAfterConFechaHttpEsperaElMinimo() {
        servidor.enqueue(new MockResponse().setResponseCode(429)
                .setHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar(null, null, null, 0, "es");
        assertEquals(List.of(1_000L), esperas); // no se sabe parsear: espera mínima, no 0 ni ∞
    }

    @Test
    void unRetryAfterEnormeSeRecortaA10Segundos() {
        servidor.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "300"));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar(null, null, null, 0, "es");
        assertEquals(List.of(10_000L), esperas); // una interacción de Discord no aguanta 5 min
    }

    @Test
    void noSeEsperaDespuesDelUltimoIntento() {
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        assertThrows(ApiException.class, () -> service.buscar(null, null, null, 0, "es"));
        // Backoff creciente entre los tres intentos, pero nada tras el tercero (fallo seguro).
        assertEquals(List.of(1_000L, 2_000L), esperas);
    }

    @Test
    void agotaReintentosYLanzaApiException() {
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        assertThrows(ApiException.class, () -> service.buscar(null, null, null, 0, "es"));
    }

    @Test
    void listarTodosNoSeCachea() {
        String catalogo = """
                [{"id":1,"nombre":"Sentadilla","grupoMuscular":"PIERNAS",
                "dificultad":"PRINCIPIANTE"}]""";
        servidor.enqueue(new MockResponse().setBody(catalogo));
        servidor.enqueue(new MockResponse().setBody(catalogo));
        assertEquals(1, service.listarTodos("es").size());
        assertEquals(1, service.listarTodos("es").size());
        assertEquals(3, servidor.getRequestCount()); // login + dos listados: sin caché a propósito
    }

    @Test
    void un400FallaRapidoSinGastarReintentos() {
        servidor.enqueue(new MockResponse().setResponseCode(400));
        assertThrows(ApiException.class, () -> service.buscar("?", null, null, 0, "es"));
        assertEquals(2, servidor.getRequestCount()); // login + 1 intento, no 3
    }

    @Test
    void clavesAdyacentesNoColisionan() {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("a|b", "c", null, 0, "es");
        service.buscar("a", "b|c", null, 0, "es"); // con clave de texto plano colisionaba
        assertEquals(3, servidor.getRequestCount()); // login + una por consulta
    }

    @Test
    void elTextoSeNormalizaAntesDeCachear() {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("press", null, null, 0, "es");
        service.buscar("  press  ", null, null, 0, "es"); // trim: mismo hueco de caché
        service.buscar(null, null, null, 0, "es");
        service.buscar("   ", null, null, 0, "es"); // blank == null: reusa la entrada anterior
        assertEquals(3, servidor.getRequestCount()); // login + "press" + sin filtro
    }

    @Test
    void dosConsultasIdenticasALaVezHacenUnaSolaPeticion() throws Exception {
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        service.buscar("previa", null, null, 0, "es"); // fuerza el login perezoso
        assertEquals(2, servidor.getRequestCount());

        // Dispatcher propio: cuenta peticiones y avisa en cuanto la primera está en vuelo, de
        // modo que el segundo hilo entra con la carga a medias (sin sleeps ni carreras).
        CountDownLatch enVuelo = new CountDownLatch(1);
        AtomicInteger peticiones = new AtomicInteger();
        servidor.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest peticion) {
                peticiones.incrementAndGet();
                enVuelo.countDown();
                return new MockResponse().setBody(PAGINA)
                        .setBodyDelay(500, TimeUnit.MILLISECONDS);
            }
        });

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<PaginaDTO<EjercicioDTO>> primera =
                    hilos.submit(() -> service.buscar("simultanea", null, null, 0, "es"));
            assertTrue(enVuelo.await(5, TimeUnit.SECONDS), "la primera petición nunca llegó");
            Future<PaginaDTO<EjercicioDTO>> segunda =
                    hilos.submit(() -> service.buscar("simultanea", null, null, 0, "es"));

            assertEquals(873, primera.get(10, TimeUnit.SECONDS).totalElements());
            assertEquals(873, segunda.get(10, TimeUnit.SECONDS).totalElements());
            assertEquals(1, peticiones.get()); // single-flight: la segunda se enganchó a la primera
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void unFalloDeRedEnElLoginSeReintenta() throws Exception {
        try (MockWebServer propio = new MockWebServer()) {
            propio.start();
            // El login se cae a nivel de socket (Render despertando); el interceptor propaga una
            // ApiException con causa IOException que debe reintentarse igual que un 5xx.
            propio.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
            propio.enqueue(new MockResponse().setBody(LOGIN_OK));
            propio.enqueue(new MockResponse().setBody(PAGINA));

            try (ApiClient suyo = new ApiClient(propio.url("/api/").toString(),
                    "gymprobot", "secreta", Duration.ofSeconds(5))) {
                EjercicioService service = new EjercicioService(suyo.ejercicios(),
                        Duration.ofMinutes(5), 1_000, reloj::get, esperas::add);
                assertEquals(873, service.buscar(null, null, null, 0, "es").totalElements());
            }
        }
    }

    @Test
    void unasCredencialesRechazadasFallanSinGastarReintentos() throws Exception {
        try (MockWebServer propio = new MockWebServer()) {
            propio.start();
            propio.enqueue(new MockResponse().setResponseCode(401)); // login rechazado de verdad
            try (ApiClient suyo = new ApiClient(propio.url("/api/").toString(),
                    "gymprobot", "mala", Duration.ofSeconds(5))) {
                EjercicioService service = new EjercicioService(suyo.ejercicios(),
                        Duration.ofMinutes(5), 1_000, reloj::get, esperas::add);
                assertThrows(ApiException.class, () -> service.buscar(null, null, null, 0, "es"));
                assertEquals(1, propio.getRequestCount()); // no se reintenta una credencial mala
                assertEquals(List.of(), esperas);
            }
        }
    }

    @Test
    void fichaPorIdTambienSeCachea() {
        servidor.enqueue(new MockResponse().setBody(
                "{\"id\":1,\"nombre\":\"Sentadilla\",\"grupoMuscular\":\"PIERNAS\",\"dificultad\":\"PRINCIPIANTE\"}"));
        assertEquals("Sentadilla", service.porId(1, "es").nombre());
        assertEquals("Sentadilla", service.porId(1, "es").nombre());
        assertEquals(2, servidor.getRequestCount()); // login + 1 ficha
    }
}
