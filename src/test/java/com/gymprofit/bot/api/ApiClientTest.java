package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el montaje HTTP central: cabecera Bearer en cada petición, renovación transparente
 * ante 401 (refresh + reintento de la petición original) y rendición al segundo 401 seguido
 * (sin bucle infinito de authenticator).
 */
class ApiClientTest {

    private MockWebServer servidor;
    private ApiClient cliente;

    private static final String LOGIN_OK =
            "{\"token\":\"acc1\",\"refreshToken\":\"ref1\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}";
    private static final String REFRESH_OK =
            "{\"token\":\"acc2\",\"refreshToken\":\"ref2\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}";
    private static final String EJERCICIO =
            "{\"id\":1,\"nombre\":\"Sentadilla\",\"grupoMuscular\":\"PIERNAS\",\"dificultad\":\"PRINCIPIANTE\"}";

    @BeforeEach
    void arrancar() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
        cliente = new ApiClient(servidor.url("/api/").toString(), "gymprobot", "secreta",
                Duration.ofSeconds(5));
    }

    @AfterEach
    void parar() throws Exception {
        cliente.cerrar(); // libera executor y pools: si no, cada test los deja vivos
        servidor.shutdown();
    }

    @Test
    void anadeBearerYNoLoMandaAlLogin() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        servidor.enqueue(new MockResponse().setBody(EJERCICIO));

        EjercicioDTO e = cliente.ejercicios().porId(1, "es").execute().body();
        assertEquals("Sentadilla", e.nombre());

        RecordedRequest login = servidor.takeRequest(5, TimeUnit.SECONDS);
        assertTrue(login.getPath().endsWith("/auth/login"));
        assertNull(login.getHeader("Authorization")); // el login va limpio
        RecordedRequest ficha = servidor.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("Bearer acc1", ficha.getHeader("Authorization"));
        assertEquals("es", ficha.getHeader("Accept-Language"));
    }

    @Test
    void ante401RenuevaYReintentaLaMismaPeticion() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        servidor.enqueue(new MockResponse().setResponseCode(401)); // token caducado
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));  // authenticator renueva
        servidor.enqueue(new MockResponse().setBody(EJERCICIO));   // reintento con acc2

        EjercicioDTO e = cliente.ejercicios().porId(1, "es").execute().body();
        assertEquals("Sentadilla", e.nombre());

        servidor.takeRequest(5, TimeUnit.SECONDS); // login
        servidor.takeRequest(5, TimeUnit.SECONDS); // porId con acc1 → 401
        assertTrue(servidor.takeRequest(5, TimeUnit.SECONDS).getPath().endsWith("/auth/refresh"));
        assertEquals("Bearer acc2", servidor.takeRequest(5, TimeUnit.SECONDS).getHeader("Authorization"));
    }

    @Test
    void renuevaAunqueHayaHabidoUnRedirectAntesDel401() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        // Render (o un http→https, o una barra final) puede colar un redirect antes del 401:
        // ese 401 ya llega con priorResponse != null y el token debe renovarse igual.
        servidor.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", "/api/ejercicios/1?idioma=es"));
        servidor.enqueue(new MockResponse().setResponseCode(401));
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));
        servidor.enqueue(new MockResponse().setBody(EJERCICIO));

        EjercicioDTO e = cliente.ejercicios().porId(1, "es").execute().body();
        assertEquals("Sentadilla", e.nombre());
        assertEquals(5, servidor.getRequestCount());
    }

    @Test
    void seRindeTrasSegundo401() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        servidor.enqueue(new MockResponse().setResponseCode(401));
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));
        servidor.enqueue(new MockResponse().setResponseCode(401)); // también 401 tras renovar

        int codigo = cliente.ejercicios().porId(1, "es").execute().code();
        assertEquals(401, codigo); // devuelve el 401 en vez de reintentar para siempre
        assertEquals(4, servidor.getRequestCount());
    }
}
