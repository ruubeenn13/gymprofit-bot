package com.gymprofit.bot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Servidor HTTP mínimo (JDK {@code com.sun.net.httpserver}, sin dependencias externas)
 * que expone {@code /health}. Lo consumen el health check de Render y el workflow
 * {@code keep-alive.yml} (ver {@code GYMPROBOT_SPEC.md} §14).
 */
public final class HealthServer {

    private static final Logger log = LoggerFactory.getLogger(HealthServer.class);
    private static final String BODY = "OK";

    private final int port;
    private HttpServer server;

    public HealthServer(int port) {
        this.port = port;
    }

    /** Arranca el servidor en un pool de hilos por defecto. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", HealthServer::handle);
        server.setExecutor(null);
        server.start();
    }

    /**
     * Puerto realmente enlazado. Útil cuando se arranca con puerto 0 (efímero), p. ej.
     * en tests.
     *
     * @return el puerto en uso, o -1 si aún no se ha arrancado
     */
    public int boundPort() {
        return (server == null) ? -1 : server.getAddress().getPort();
    }

    /** Detiene el servidor sin esperar a peticiones en curso. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Health server detenido.");
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        byte[] payload = BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
