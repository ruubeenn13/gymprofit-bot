package com.gymprofit.bot.services;

import com.gymprofit.bot.api.ApiClient;
import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica la resiliencia del catálogo (SPEC §7 del diseño): mapeo correcto, reintento ante
 * 5xx, respeto del Retry-After de un 429, error amable tras agotar reintentos y que la caché
 * evita la segunda llamada dentro del TTL (y la repite al caducar).
 */
class EjercicioServiceTest {

    private MockWebServer servidor;
    private EjercicioService service;
    private final AtomicLong reloj = new AtomicLong(0); // reloj inyectado (controla el TTL)

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
        ApiClient cliente = new ApiClient(servidor.url("/api/").toString(),
                "gymprobot", "secreta", Duration.ofSeconds(5));
        // esperaBase 0 para no dormir en tests; TTL 5 min sobre el reloj inyectado.
        service = new EjercicioService(cliente.ejercicios(), Duration.ofMinutes(5), 0, reloj::get);
    }

    @AfterEach
    void parar() throws Exception {
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
        servidor.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
        servidor.enqueue(new MockResponse().setBody(PAGINA));
        PaginaDTO<EjercicioDTO> p = service.buscar(null, null, null, 0, "es");
        assertEquals(873, p.totalElements());
    }

    @Test
    void agotaReintentosYLanzaApiException() {
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        servidor.enqueue(new MockResponse().setResponseCode(503));
        assertThrows(ApiException.class, () -> service.buscar(null, null, null, 0, "es"));
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
