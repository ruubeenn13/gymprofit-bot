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
 */
public final class ApiClient {

    private final TokenManager tokens;
    private final EjerciciosApi ejercicios;
    // Hilos propios y daemon: si el bot se apaga a mitad de llamada, no retienen la JVM.
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "gymprobot-api");
        t.setDaemon(true);
        return t;
    });

    public ApiClient(String baseUrl, String usuario, String password) {
        this(baseUrl, usuario, password, Duration.ofSeconds(60));
    }

    /** El timeout es inyectable para que los tests no esperen 60 s. */
    public ApiClient(String baseUrl, String usuario, String password, Duration timeout) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/"; // Retrofit lo exige

        // Retrofit "pelado" solo para auth: sin interceptor (un login no lleva Bearer) y sin
        // authenticator (un 401 del login es un error real, no un token caducado).
        AuthApi authApi = new Retrofit.Builder()
                .baseUrl(base)
                .client(new OkHttpClient.Builder()
                        .connectTimeout(timeout)
                        .readTimeout(timeout)
                        .build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi.class);
        this.tokens = new TokenManager(authApi, usuario, password);

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
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
                    // Un solo reintento: si la respuesta ya viene de un reintento (tiene
                    // priorResponse), el token nuevo tampoco vale y no insistimos.
                    if (respuesta.priorResponse() != null) {
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

    private static String extraerBearer(Response respuesta) {
        String cabecera = respuesta.request().header("Authorization");
        return (cabecera != null && cabecera.startsWith("Bearer "))
                ? cabecera.substring("Bearer ".length()) : null;
    }
}
