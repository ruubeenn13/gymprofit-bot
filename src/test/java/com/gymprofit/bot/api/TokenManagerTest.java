package com.gymprofit.bot.api;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el ciclo de vida del token: login perezoso, renovación ante 401 con refresh,
 * caída a login completo si el refresh falla y serialización (un hilo renueva, el resto reusa).
 */
class TokenManagerTest {

    private MockWebServer servidor;
    private TokenManager tokens;

    private static final String LOGIN_OK =
            "{\"token\":\"acc1\",\"refreshToken\":\"ref1\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}";
    private static final String REFRESH_OK =
            "{\"token\":\"acc2\",\"refreshToken\":\"ref2\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}";

    @BeforeEach
    void arrancar() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
        AuthApi authApi = new Retrofit.Builder()
                .baseUrl(servidor.url("/api/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi.class);
        tokens = new TokenManager(authApi, "gymprobot", "secreta");
    }

    @AfterEach
    void parar() throws Exception {
        servidor.shutdown();
    }

    @Test
    void loginPerezosoYCacheado() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        assertEquals("acc1", tokens.obtenerToken());
        assertEquals("acc1", tokens.obtenerToken()); // segunda llamada: sin red
        assertEquals(1, servidor.getRequestCount());
        assertTrue(servidor.takeRequest().getPath().endsWith("/auth/login"));
    }

    @Test
    void renovarUsaRefreshYGuardaElNuevo() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        tokens.obtenerToken();
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));
        assertEquals("acc2", tokens.renovar("acc1"));
        servidor.takeRequest(); // login
        assertTrue(servidor.takeRequest().getPath().endsWith("/auth/refresh"));
        assertEquals("acc2", tokens.obtenerToken());
    }

    @Test
    void renovarNoRepiteSiOtroHiloYaRenovo() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        tokens.obtenerToken();
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));
        tokens.renovar("acc1");
        // Segundo hilo llega con el token viejo: recibe el nuevo sin más peticiones.
        assertEquals("acc2", tokens.renovar("acc1"));
        assertEquals(2, servidor.getRequestCount()); // login + un solo refresh
    }

    @Test
    void siElRefreshFallaCaeALoginCompleto() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        tokens.obtenerToken();
        servidor.enqueue(new MockResponse().setResponseCode(401)); // refresh caducado
        servidor.enqueue(new MockResponse().setBody(REFRESH_OK));  // re-login
        assertEquals("acc2", tokens.renovar("acc1"));
    }

    @Test
    void loginImposibleLanzaApiException() {
        servidor.enqueue(new MockResponse().setResponseCode(401));
        assertThrows(ApiException.class, () -> tokens.obtenerToken());
    }
}
