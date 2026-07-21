package com.gymprofit.bot.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Punto único de acceso HTTP a la API GymProFit (SPEC §4.1): monta Retrofit+OkHttp con la
 * autenticación de la cuenta de servicio y los timeouts largos que exige Render free (el
 * servicio duerme y el primer request puede tardar ~50 s, de ahí los 60 s por defecto).
 *
 * <p>Autenticación transparente: un interceptor añade {@code Authorization: Bearer} a todo lo
 * que no sea {@code /auth/}, y un {@link okhttp3.Authenticator} reacciona a los 401 renovando
 * vía {@link TokenManager} y reintentando la petición original una sola vez (al segundo 401
 * seguido se rinde para no entrar en bucle).</p>
 *
 * <p>Expone también un executor propio: las llamadas pueden tardar 60 s y JAMÁS deben ejecutarse
 * en el hilo del gateway de JDA.</p>
 *
 * <p>Es {@link AutoCloseable}: {@link #cerrar()} libera el executor y los pools de conexiones de
 * ambos clientes OkHttp. Se cierra al apagar el bot (o al final de cada test).</p>
 */
public final class ApiClient implements AutoCloseable {

    private final TokenManager tokens;
    private final EjerciciosApi ejercicios;
    private final OkHttpClient httpAuth;
    private final OkHttpClient http;
    // Hilos propios y daemon: si el bot se apaga a mitad de llamada, no retienen la JVM.
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "gymprobot-api");
        t.setDaemon(true);
        return t;
    });

    /**
     * Cliente de producción contra la API real, con el presupuesto de 60 s por conexión y
     * lectura que exige el free tier de Render.
     *
     * @param baseUrl  raíz de la API (con o sin barra final)
     * @param usuario  usuario de la cuenta de servicio (nunca se loguea)
     * @param password contraseña de la cuenta de servicio (nunca se loguea)
     */
    public ApiClient(String baseUrl, String usuario, String password) {
        this(baseUrl, usuario, password, Duration.ofSeconds(60));
    }

    /** El timeout es inyectable para que los tests no esperen 60 s. */
    public ApiClient(String baseUrl, String usuario, String password, Duration timeout) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/"; // Retrofit lo exige

        // Retrofit "pelado" solo para auth: sin interceptor (un login no lleva Bearer) y sin
        // authenticator (un 401 del login es un error real, no un token caducado).
        this.httpAuth = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout.multipliedBy(2)) // conectar + leer, con margen
                .build();
        AuthApi authApi = new Retrofit.Builder()
                .baseUrl(base)
                .client(httpAuth)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi.class);
        this.tokens = new TokenManager(authApi, usuario, password);

        this.http = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                // Los cuerpos que enviamos son diminutos (JSON de credenciales o filtros): el
                // write nunca debería tardar, pero sin tope una red medio caída deja el hilo
                // colgado, y solo hay 4 para todo el bot.
                .writeTimeout(timeout)
                // Tope de la llamada lógica COMPLETA: login perezoso + petición + refresh ante
                // 401 + reintento son hasta cuatro viajes, así que se da 4× el presupuesto. Sin
                // esto, un encadenado desafortunado bloquearía un hilo del pool durante minutos.
                .callTimeout(timeout.multipliedBy(4))
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    if (original.url().encodedPath().contains("/auth/")) {
                        return chain.proceed(original);
                    }
                    return chain.proceed(original.newBuilder()
                            .header("Authorization", "Bearer " + tokens.obtenerToken())
                            .build());
                })
                .authenticator((route, respuesta) -> {
                    // Un solo reintento. Ojo: OkHttp pone priorResponse en CUALQUIER encadenado
                    // (también en un redirect), así que mirar solo si es null nos dejaría sin
                    // renovar en cuanto la API colase un 301/302. Se cuentan solo los 401.
                    if (cuenta401(respuesta) > 1) {
                        return null;
                    }
                    String caducado = extraerBearer(respuesta);
                    String nuevo = tokens.renovar(caducado);
                    return respuesta.request().newBuilder()
                            .header("Authorization", "Bearer " + nuevo)
                            .build();
                })
                .build();

        this.ejercicios = new Retrofit.Builder()
                .baseUrl(base)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EjerciciosApi.class);
    }

    public EjerciciosApi ejercicios() {
        return ejercicios;
    }

    /** Executor para llamadas a la API fuera del hilo de eventos de JDA. */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Libera los recursos del cliente: el pool de 4 hilos y los dispatchers y pools de
     * conexiones de ambos clientes OkHttp. Sin esto cada instancia deja hilos vivos hasta el
     * final del proceso (se nota sobre todo en la suite de tests, que crea una por método).
     */
    public void cerrar() {
        executor.shutdownNow();
        liberar(httpAuth);
        liberar(http);
    }

    @Override
    public void close() {
        cerrar();
    }

    private static void liberar(OkHttpClient cliente) {
        cliente.dispatcher().executorService().shutdown();
        cliente.connectionPool().evictAll();
    }

    /** Nº de 401 en la cadena de respuestas (la actual más sus {@code priorResponse}). */
    private static int cuenta401(Response respuesta) {
        int total = 0;
        for (Response r = respuesta; r != null; r = r.priorResponse()) {
            if (r.code() == 401) {
                total++;
            }
        }
        return total;
    }

    private static String extraerBearer(Response respuesta) {
        String cabecera = respuesta.request().header("Authorization");
        return (cabecera != null && cabecera.startsWith("Bearer "))
                ? cabecera.substring("Bearer ".length()) : null;
    }
}
