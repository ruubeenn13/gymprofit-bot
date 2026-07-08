package com.gymprofit.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que el health server responde 200 "OK" en {@code /health}. Es la garantía que
 * usan Render y {@code keep-alive.yml} (SPEC §14).
 */
class HealthServerTest {

    private HealthServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void healthEndpointDevuelve200Ok() throws Exception {
        server = new HealthServer(0); // puerto efímero
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.boundPort() + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }
}
