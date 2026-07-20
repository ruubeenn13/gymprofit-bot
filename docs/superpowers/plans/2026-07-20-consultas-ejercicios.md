# Consultas a la API (`/ejercicios`, `/ejercicio-dia` + job, `/frase`) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar F1 construyendo la capa `api/` (Retrofit+OkHttp contra `https://gymprofit-api.onrender.com/api`), `/ejercicios` (catálogo paginado con ficha), `/ejercicio-dia` + `EjercicioDiaJob` (publicación 8:00 Europe/Madrid con migración V24) y `/frase` (banco V2, cooldown 30 s). 56 → 59 comandos.

**Architecture:** `api/` es la única puerta HTTP (TokenManager renueva solo ante 401, serializado; timeouts 60 s por Render free). `services/` no conoce JDA (caché TTL + reintentos, test con MockWebServer). `commands/`+`events/` no conocen HTTP: piden al service y pintan embeds vía `EmbedFactory`. Estado de paginación en el `customId` (patrón `/modlogs`), sin memoria.

**Tech Stack:** Java 21, JDA 5.6.1, Retrofit 2.11 + OkHttp 4.12 + Gson 2.11 (ya en `pom.xml`), Flyway (V24), JUnit 5 + Mockito + MockWebServer + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-20-consultas-ejercicios-design.md`

---

## Notas para el ejecutor (leer antes de cada tarea)

- **Build:** `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd <goal>` (el `java` por defecto de la máquina es 8). Los tests Testcontainers se **saltan en local** (npipe de Docker Desktop) y corren en CI: un verify verde local NO los valida.
- **Convenciones obligatorias:** cabecera Javadoc en cada archivo (también SQL), comentarios inline con el *porqué*, i18n en `messages_es.properties` **y** `messages_en.properties`, ningún embed fuera de `EmbedFactory`, secretos solo por `BotConfig`.
- **Commits:** sin `Co-Authored-By` ni "Generated with". Mensajes largos con sección `Novedades:` (alimentan el feed de Noticias). Cada commit incluye la doc afectada.
- **Contratos verificados contra el código real de la API** (monorepo `~/Desktop/TFG/TFG-GymProFit`): `TokenDTO{token,refreshToken,username,roles}`, `EjercicioDTO{id,nombre,descripcion,grupoMuscular,musculoPrimario,dificultad,imagenUrl,imagenUrl2,instrucciones,caloriasQuemadas,equipoNecesario,activo}`, `PageDTO{content,page,size,totalElements,totalPages,last}`, `POST /auth/refresh {refreshToken}`. Grupos: `PECHO,ESPALDA,PIERNAS,HOMBROS,BRAZOS,ABDOMEN,CARDIO,FULLBODY`. Dificultades: `PRINCIPIANTE,INTERMEDIO,AVANZADO`.
- **Desviación consciente del spec §4:** la búsqueda en el `customId` se trunca a **30** caracteres, no 40. Con el prefijo más largo (`ejercicios-volver:`, 18) + snowflake (20) + página (4) + grupo (8) + dificultad (12) + 4 separadores, 40 de búsqueda superaría los 100 chars que admite Discord (105); con 30 el peor caso queda en 96.
- **No bloquear el hilo del gateway:** toda llamada a la API (hasta 60 s) se hace en el executor propio de `ApiClient`, nunca en el hilo del evento. Siempre `deferReply()`/`deferEdit()` antes.

## Estructura de archivos

```
api/            ApiClient · TokenManager · ApiException · AuthApi · EjerciciosApi
api/dtos/       TokenDTO · CredencialesDTO · RefreshDTO · EjercicioDTO · PaginaDTO
services/       EjercicioService · EjercicioDiaService
commands/consultas/  EjerciciosComando · EjercicioDiaComando · FraseComando (+ package-info)
events/         EjerciciosPaginadorListener
jobs/           EjercicioDiaJob
db/             EjercicioDia · EjercicioDiaRepositorio · Frase · FraseRepositorio
                (+ ConfigServidorRepositorio.listarConEjercicioDia)
db/migration/   V24__ejercicio_dia.sql
```

---

### Task 1: Migración V24 `ejercicio_dia`

**Files:**
- Create: `src/main/resources/db/migration/V24__ejercicio_dia.sql`
- Modify: `src/test/java/com/gymprofit/bot/db/MigracionesTest.java`
- Modify (si lista tablas): `src/main/resources/db/migration/` README / `db/README.md`

- [ ] **Step 1: Escribir la migración**

```sql
-- ----------------------------------------------------------------------------
-- Ejercicio del día (F1, consultas a la API) — histórico de publicaciones diarias.
--
-- Cada día natural (Europe/Madrid) se sortea un ejercicio del catálogo de la API entre los que
-- aún no han salido en la ronda actual; al agotar el catálogo (873 hoy) empieza la ronda
-- siguiente, de modo que nada se repite hasta haberlo visto todo. `fecha` como PK da la
-- idempotencia: si el job corre dos veces (reinicio, deploy) el segundo intento choca con la
-- fila y no vuelve a publicar. El id es de la API (no hay FK: el catálogo vive fuera del bot).
-- ----------------------------------------------------------------------------
CREATE TABLE ejercicio_dia (
    fecha        DATE      NOT NULL COMMENT 'Día natural (Europe/Madrid)',
    ejercicio_id INT       NOT NULL COMMENT 'Id del ejercicio en la API GymProFit',
    ronda        INT       NOT NULL DEFAULT 1 COMMENT 'Vuelta al catálogo (empieza en 1)',
    publicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuándo se eligió/publicó',
    PRIMARY KEY (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 2: Ampliar `MigracionesTest`** — añadir dentro del bloque de asserts existente:

```java
                // V24 aplicada: el histórico del ejercicio del día existe y arranca vacío.
                assertEquals(0, contar(st, "SELECT COUNT(*) FROM ejercicio_dia"),
                        "ejercicio_dia debe existir y arrancar vacía");
```

- [ ] **Step 3: Verificar**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd -Dtest=MigracionesTest test`
Expected: en local, `Tests run: 1, Skipped: 1` (Docker npipe). El SQL lo valida CI; revisar sintaxis a mano.

- [ ] **Step 4: Commit**

```
feat(api): migración V24 — histórico del ejercicio del día

Tabla ejercicio_dia (fecha PK, ejercicio_id, ronda, publicado_en): un ejercicio por día
natural, sin repetir dentro de una ronda hasta agotar el catálogo. La PK por fecha hace
idempotente el job de publicación.

Novedades:
- Preparado el terreno del ejercicio del día: cada día saldrá uno distinto y no se repetirá
  ninguno hasta que hayan salido los 873 del catálogo.
```

---

### Task 2: DTOs de `api/` + interfaces Retrofit

**Files:**
- Create: `src/main/java/com/gymprofit/bot/api/dtos/TokenDTO.java`, `CredencialesDTO.java`, `RefreshDTO.java`, `EjercicioDTO.java`, `PaginaDTO.java`, `package-info.java`
- Create: `src/main/java/com/gymprofit/bot/api/AuthApi.java`, `EjerciciosApi.java`, `ApiException.java`
- Test: `src/test/java/com/gymprofit/bot/api/DtosMapeoTest.java`

- [ ] **Step 1: Test de mapeo Gson (falla: no compilan los DTOs)**

```java
package com.gymprofit.bot.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que los records de {@code api/dtos} mapean el JSON real de la API GymProFit
 * (mismos nombres de campo que sus DTOs de Spring; Gson soporta records desde 2.10).
 */
class DtosMapeoTest {

    private final Gson gson = new Gson();

    @Test
    void mapeaTokenDTO() {
        TokenDTO t = gson.fromJson(
                "{\"token\":\"jwt\",\"refreshToken\":\"opaco\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}",
                TokenDTO.class);
        assertEquals("jwt", t.token());
        assertEquals("opaco", t.refreshToken());
        assertEquals("ADMIN", t.roles().get(0));
    }

    @Test
    void mapeaPaginaDeEjercicios() {
        String json = """
                {"content":[{"id":7,"nombre":"Press banca","descripcion":"Empuje horizontal",
                "grupoMuscular":"PECHO","musculoPrimario":"Pectoral mayor","dificultad":"INTERMEDIO",
                "imagenUrl":"http://img/1.png","imagenUrl2":null,"instrucciones":"Baja la barra...",
                "caloriasQuemadas":8,"equipoNecesario":"Barra","activo":true}],
                "page":0,"size":8,"totalElements":873,"totalPages":110,"last":false}""";
        PaginaDTO<EjercicioDTO> p = gson.fromJson(json,
                new TypeToken<PaginaDTO<EjercicioDTO>>() { }.getType());
        assertEquals(873, p.totalElements());
        assertEquals(110, p.totalPages());
        EjercicioDTO e = p.content().get(0);
        assertEquals(7, e.id());
        assertEquals("PECHO", e.grupoMuscular());
        assertNull(e.imagenUrl2());
        assertTrue(e.activo());
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd -Dtest=DtosMapeoTest test`
Expected: FAIL de compilación (los DTOs no existen).

- [ ] **Step 3: Crear los records** (todos con cabecera Javadoc; se muestra la esencia)

`dtos/package-info.java`:
```java
/**
 * DTOs del cliente REST de GymProFit: espejo mínimo de los DTOs de la API (mismos nombres de
 * campo para que Gson mapee sin anotaciones). Solo los campos que el bot consume.
 */
package com.gymprofit.bot.api.dtos;
```

```java
/** Respuesta de {@code POST /auth/login} y {@code /auth/refresh}: tokens y roles emitidos. */
public record TokenDTO(String token, String refreshToken, String username, List<String> roles) { }

/** Cuerpo de {@code POST /auth/login}: credenciales de la cuenta de servicio (nunca loguearlas). */
public record CredencialesDTO(String username, String password) { }

/** Cuerpo de {@code POST /auth/refresh}: el refresh token opaco guardado en memoria. */
public record RefreshDTO(String refreshToken) { }

/** Ficha completa de un ejercicio del catálogo (localizada por la API vía Accept-Language). */
public record EjercicioDTO(Integer id, String nombre, String descripcion, String grupoMuscular,
                           String musculoPrimario, String dificultad, String imagenUrl,
                           String imagenUrl2, String instrucciones, Integer caloriasQuemadas,
                           String equipoNecesario, Boolean activo) { }

/** Página de resultados de la API ({@code PageDTO} de Spring): contenido + metadatos. */
public record PaginaDTO<T>(List<T> content, int page, int size, long totalElements,
                           int totalPages, boolean last) { }
```

`ApiException.java`:
```java
package com.gymprofit.bot.api;

/**
 * Error hablando con la API GymProFit (red caída, 5xx persistente, respuesta inválida). Es
 * unchecked para que los comandos la capturen en un único punto y respondan con un aviso amable
 * (nunca una traza al usuario).
 */
public class ApiException extends RuntimeException {
    public ApiException(String mensaje) { super(mensaje); }
    public ApiException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
```

`AuthApi.java`:
```java
package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.CredencialesDTO;
import com.gymprofit.bot.api.dtos.RefreshDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Endpoints de autenticación de la API. Se consumen con un Retrofit SIN interceptor de
 * Authorization (romperíamos el login con un token caducado) — ver {@link ApiClient}.
 */
public interface AuthApi {

    @POST("auth/login")
    Call<TokenDTO> login(@Body CredencialesDTO credenciales);

    @POST("auth/refresh")
    Call<TokenDTO> refresh(@Body RefreshDTO refresh);
}
```

`EjerciciosApi.java`:
```java
package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

/**
 * Catálogo de ejercicios de la API. El idioma va por petición en {@code Accept-Language}
 * ({@code es}/{@code en}); los mappers de la API devuelven los textos ya localizados.
 */
public interface EjerciciosApi {

    /** Búsqueda paginada del catálogo (q, grupo y dificultad opcionales: null = sin filtro). */
    @GET("ejercicios/buscar")
    Call<PaginaDTO<EjercicioDTO>> buscar(@Query("q") String q,
                                         @Query("grupoMuscular") String grupoMuscular,
                                         @Query("dificultad") String dificultad,
                                         @Query("page") int page,
                                         @Query("size") int size,
                                         @Header("Accept-Language") String idioma);

    /** Ficha completa de un ejercicio. */
    @GET("ejercicios/{id}")
    Call<EjercicioDTO> porId(@Path("id") int id, @Header("Accept-Language") String idioma);

    /** Catálogo completo (lo usa el sorteo diario para conocer todos los ids). */
    @GET("ejercicios")
    Call<List<EjercicioDTO>> listarTodos(@Header("Accept-Language") String idioma);
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd -Dtest=DtosMapeoTest test`
Expected: PASS (2 tests).

*(Sin commit aún: la capa `api/` se commitea entera al final de la Task 5.)*

---

### Task 3: `TokenManager`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/api/TokenManager.java`
- Test: `src/test/java/com/gymprofit/bot/api/TokenManagerTest.java`

- [ ] **Step 1: Test con MockWebServer (falla)**

```java
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
```

- [ ] **Step 2: Verificar que falla** — `.\mvnw.cmd -Dtest=TokenManagerTest test` → FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.CredencialesDTO;
import com.gymprofit.bot.api.dtos.RefreshDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.IOException;

/**
 * Ciclo de vida del access token de la cuenta de servicio (SPEC §4.1): login perezoso en la
 * primera petición, renovación <b>solo ante un 401</b> (nunca por reloj) y todo en memoria (el
 * free tier de Render reinicia el proceso; re-loguear al arrancar es barato).
 *
 * <p>Los métodos son {@code synchronized} a propósito: si tres comandos chocan con un 401 a la
 * vez, el primero renueva y los otros dos reutilizan el token nuevo comparándolo con el que les
 * caducó ({@link #renovar}). Las credenciales jamás se loguean.</p>
 */
public final class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);

    private final AuthApi authApi;
    private final String usuario;
    private final String password;

    private String token;
    private String refreshToken;

    public TokenManager(AuthApi authApi, String usuario, String password) {
        this.authApi = authApi;
        this.usuario = usuario;
        this.password = password;
    }

    /** Access token vigente, haciendo login la primera vez. */
    public synchronized String obtenerToken() {
        if (token == null) {
            login();
        }
        return token;
    }

    /**
     * Renueva el token que acaba de recibir un 401. Si el vigente ya no es ese, otro hilo se
     * adelantó y se devuelve sin tocar la red. Intenta refresh; si el refresh también está
     * caducado, login completo.
     *
     * @param tokenCaducado el token con el que la petición recibió el 401
     */
    public synchronized String renovar(String tokenCaducado) {
        if (token != null && !token.equals(tokenCaducado)) {
            return token;
        }
        try {
            Response<TokenDTO> respuesta =
                    authApi.refresh(new RefreshDTO(refreshToken)).execute();
            if (respuesta.isSuccessful() && respuesta.body() != null) {
                guardar(respuesta.body());
                return token;
            }
            log.info("Refresh token rechazado ({}); reintentando con login completo.",
                    respuesta.code());
        } catch (IOException e) {
            log.warn("Fallo de red renovando el token; reintentando con login completo.", e);
        }
        login();
        return token;
    }

    private void login() {
        try {
            Response<TokenDTO> respuesta =
                    authApi.login(new CredencialesDTO(usuario, password)).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                throw new ApiException("Login de la cuenta de servicio rechazado: HTTP "
                        + respuesta.code());
            }
            guardar(respuesta.body());
        } catch (IOException e) {
            throw new ApiException("Fallo de red en el login de la cuenta de servicio", e);
        }
    }

    private void guardar(TokenDTO dto) {
        this.token = dto.token();
        this.refreshToken = dto.refreshToken();
    }
}
```

- [ ] **Step 4: Verificar que pasa** — `.\mvnw.cmd -Dtest=TokenManagerTest test` → PASS (5 tests).

---

### Task 4: `ApiClient`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/api/ApiClient.java`
- Test: `src/test/java/com/gymprofit/bot/api/ApiClientTest.java`

- [ ] **Step 1: Test (falla)**

```java
package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
        servidor.shutdown();
    }

    @Test
    void anadeBearerYNoLoMandaAlLogin() throws Exception {
        servidor.enqueue(new MockResponse().setBody(LOGIN_OK));
        servidor.enqueue(new MockResponse().setBody(EJERCICIO));

        EjercicioDTO e = cliente.ejercicios().porId(1, "es").execute().body();
        assertEquals("Sentadilla", e.nombre());

        RecordedRequest login = servidor.takeRequest();
        assertTrue(login.getPath().endsWith("/auth/login"));
        assertNull(login.getHeader("Authorization")); // el login va limpio
        RecordedRequest ficha = servidor.takeRequest();
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

        servidor.takeRequest(); // login
        servidor.takeRequest(); // porId con acc1 → 401
        assertTrue(servidor.takeRequest().getPath().endsWith("/auth/refresh"));
        assertEquals("Bearer acc2", servidor.takeRequest().getHeader("Authorization"));
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
```

- [ ] **Step 2: Verificar que falla** — `.\mvnw.cmd -Dtest=ApiClientTest test` → FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
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
```

- [ ] **Step 4: Verificar que pasa** — `.\mvnw.cmd -Dtest=ApiClientTest test` → PASS (3 tests).

---

### Task 5: `EjercicioService` (caché + reintentos) y commit de la capa `api/`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/EjercicioService.java`
- Test: `src/test/java/com/gymprofit/bot/services/EjercicioServiceTest.java`
- Modify: `src/main/java/com/gymprofit/bot/api/README.md` (estado real de la capa), `docs/architecture.md` (sección api/), `CHANGELOG.md`

- [ ] **Step 1: Test con MockWebServer a través del ApiClient real (falla)**

```java
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
```

Nota: el caso «401 → refresh → reintento» ya queda cubierto en `ApiClientTest` (es responsabilidad del authenticator, no del service); el spec lo lista dentro de los tests de la capa API y ahí vive.

- [ ] **Step 2: Verificar que falla** — `.\mvnw.cmd -Dtest=EjercicioServiceTest test` → FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
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
```

- [ ] **Step 4: Verificar que pasa** — `.\mvnw.cmd -Dtest=EjercicioServiceTest test` → PASS (7 tests).

- [ ] **Step 5: `verify` completo** — `.\mvnw.cmd verify` → verde (305 + nuevos).

- [ ] **Step 6: Docs + commit de la capa `api/`** — actualizar `api/README.md` (qué hay de verdad: ApiClient/TokenManager/EjerciciosApi/dtos, auth por 401, timeouts, ejemplo), `docs/architecture.md` (api/ deja de estar vacío) y `CHANGELOG.md` (Sin publicar → Añadido).

```
feat(api): capa de acceso a la API GymProFit (Retrofit + OkHttp)

ApiClient monta el HTTP central: Bearer de la cuenta de servicio por interceptor,
renovación transparente ante 401 (refresh serializado en TokenManager, con caída a login),
timeouts de 60 s porque Render free duerme, y executor propio para no tocar el hilo del
gateway. EjercicioService añade caché con TTL de 5 min por consulta+idioma y reintentos
con espera creciente (429 respeta Retry-After). DTOs como records espejo de la API.

Novedades:
- El bot ya sabe hablar con la app de GymProFit: primer paso para ver el catálogo de
  ejercicios (873) desde Discord.
```

---

### Task 6: `Frase` + `FraseRepositorio`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/db/Frase.java`, `src/main/java/com/gymprofit/bot/db/FraseRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/FraseRepositorioTest.java`

- [ ] **Step 1: Test Testcontainers (falla)** — mismo patrón de salto que `ConfigServidorRepositorioTest`:

```java
package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba {@link FraseRepositorio} contra MySQL real con los seeds de la V2 (32 frases): la
 * aleatoria siempre devuelve algo y el texto sale en el idioma pedido. Se salta sin Docker
 * (npipe en local); corre en CI.
 */
class FraseRepositorioTest {

    @Test
    void devuelveFraseAleatoriaConAmbosIdiomas() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                FraseRepositorio repo = new FraseRepositorio(db.dataSource());

                Frase frase = repo.aleatoria().orElseThrow();
                assertFalse(frase.textoEs().isBlank());
                assertFalse(frase.textoEn().isBlank());
                assertTrue(frase.texto(Locale.forLanguageTag("en")).equals(frase.textoEn()));
                assertTrue(frase.texto(Locale.forLanguageTag("es")).equals(frase.textoEs()));
            }
        }
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

`Frase.java`:
```java
package com.gymprofit.bot.db;

import java.util.Locale;

/**
 * Frase motivadora del banco bilingüe (tabla {@code frases}, sembrada en la V2). La usan
 * {@code /frase} y el post del ejercicio del día.
 *
 * @param id      identificador
 * @param textoEs texto en español
 * @param textoEn texto en inglés
 * @param autor   autor, o {@code null} si es anónima/propia
 */
public record Frase(long id, String textoEs, String textoEn, String autor) {

    /** Texto en el idioma pedido (cualquier cosa que no sea inglés cae a español). */
    public String texto(Locale locale) {
        return (locale != null && "en".equalsIgnoreCase(locale.getLanguage()))
                ? textoEn : textoEs;
    }
}
```

`FraseRepositorio.java`:
```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/**
 * Repositorio JDBC del banco de frases (tabla {@code frases}). Solo lectura: las frases se
 * siembran por migración (V2), nunca desde el bot.
 */
public final class FraseRepositorio {

    private final DataSource dataSource;

    public FraseRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Una frase al azar del banco. {@code ORDER BY RAND()} es O(n) pero el banco tiene ~32
     * filas: más simple que un offset aleatorio y de sobra para un comando con cooldown.
     */
    public Optional<Frase> aleatoria() {
        String sql = "SELECT id, texto_es, texto_en, autor FROM frases ORDER BY RAND() LIMIT 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new Frase(rs.getLong("id"), rs.getString("texto_es"),
                    rs.getString("texto_en"), rs.getString("autor")));
        } catch (java.sql.SQLException e) {
            throw new DatabaseException("Error leyendo una frase aleatoria", e);
        }
    }
}
```

- [ ] **Step 4: Verificar** — `.\mvnw.cmd -Dtest=FraseRepositorioTest test` → skipped en local (compila); CI lo ejecuta.

---

### Task 7: `/frase` (comando + Tipo FRASE + wiring)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/consultas/FraseComando.java`, `src/main/java/com/gymprofit/bot/commands/consultas/package-info.java`
- Modify: `src/main/java/com/gymprofit/bot/embeds/EmbedFactory.java` (Tipo `FRASE`), `src/main/java/com/gymprofit/bot/Main.java`, `src/main/resources/messages_es.properties`, `src/main/resources/messages_en.properties`
- Modify: `README.md` (tabla comandos), `CHANGELOG.md`
- Test: `src/test/java/com/gymprofit/bot/commands/consultas/FraseComandoTest.java`

- [ ] **Step 1: Test del builder estático (falla)**

```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el embed de {@code /frase}: idioma correcto y autor solo cuando lo hay. */
class FraseComandoTest {

    private static final Frase CON_AUTOR =
            new Frase(1, "No cuentes los días.", "Do not count the days.", "Muhammad Ali");
    private static final Frase ANONIMA =
            new Frase(2, "Progreso, no perfección.", "Progress, not perfection.", null);

    @Test
    void muestraElTextoDelIdiomaPedido() {
        MessageEmbed es = FraseComando.construirEmbed(Messages.ES, CON_AUTOR);
        assertTrue(es.getDescription().contains("No cuentes los días."));
        MessageEmbed en = FraseComando.construirEmbed(Messages.EN, CON_AUTOR);
        assertTrue(en.getDescription().contains("Do not count the days."));
    }

    @Test
    void incluyeElAutorSoloSiExiste() {
        assertTrue(FraseComando.construirEmbed(Messages.ES, CON_AUTOR)
                .getDescription().contains("Muhammad Ali"));
        assertFalse(FraseComando.construirEmbed(Messages.ES, ANONIMA)
                .getDescription().contains("—"));
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

En `EmbedFactory.Tipo`, añadir (naranja de marca: motivación = identidad GymProFit):
```java
        FRASE("💬", Categoria.MARCA),
```

`package-info.java` de `commands/consultas`:
```java
/**
 * Comandos de consulta a contenidos de GymProFit: el catálogo de ejercicios de la API
 * ({@code /ejercicios}), el ejercicio del día ({@code /ejercicio-dia}) y el banco de frases
 * motivadoras ({@code /frase}). No hablan HTTP: delegan en {@code services/} y pintan embeds.
 */
package com.gymprofit.bot.commands.consultas;
```

`FraseComando.java`:
```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /frase}: una frase motivadora al azar del banco bilingüe (32 sembradas en la V2, todas
 * de categoría MOTIVACION: sin opciones). Respuesta pública (regla 13) con cooldown de 30 s por
 * usuario: es barato de repetir y sin freno empapelaría el canal.
 */
public final class FraseComando implements Comando {

    private static final String NOMBRE = "frase";

    private final FraseRepositorio frases;
    private final Cooldown cooldown;

    public FraseComando(FraseRepositorio frases, Cooldown cooldown) {
        this.frases = frases;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.frase.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.frase.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.frase.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // El cooldown se comprueba antes de tocar la BD; el aviso va efímero (regla 13: ruido).
        if (!cooldown.intentar(evento.getUser().getIdLong(), System.currentTimeMillis())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.FRASE, locale,
                    Messages.get(locale, "frase.cooldown"))).setEphemeral(true).queue();
            return;
        }
        Optional<Frase> frase = frases.aleatoria();
        if (frase.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.FRASE, locale,
                    Messages.get(locale, "frase.vacia"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(construirEmbed(locale, frase.get())).queue();
    }

    /** Embed de la frase: cita en el idioma del usuario y autor si lo tiene. Estático para test. */
    static MessageEmbed construirEmbed(Locale locale, Frase frase) {
        String cita = "*«" + frase.texto(locale) + "»*";
        if (frase.autor() != null) {
            cita += "\n— **" + frase.autor() + "**";
        }
        return EmbedFactory.base(EmbedFactory.Tipo.FRASE, locale,
                Messages.get(locale, "frase.titulo"), cita).build();
    }
}
```

i18n — `messages_es.properties`:
```properties
# --- /frase ---------------------------------------------------------------
comando.frase.descripcion=Suelta una frase motivadora del banco de GymProFit
frase.titulo=Dosis de motivación
frase.cooldown=🧘 Respira… en unos segundos puedes pedir otra frase.
frase.vacia=El banco de frases está vacío. Avisa al staff.
```
`messages_en.properties`:
```properties
# --- /frase ---------------------------------------------------------------
comando.frase.descripcion=Drop a motivational quote from the GymProFit bank
frase.titulo=Motivation boost
frase.cooldown=🧘 Breathe… you can ask for another quote in a few seconds.
frase.vacia=The quote bank is empty. Let the staff know.
```

Wiring en `Main.iniciarDiscord` (dentro del bloque `db != null`, junto al resto de comandos; import de `FraseComando`, `FraseRepositorio` y `java.time.Duration` ya está importado con nombre completo en usos existentes — seguir el estilo del archivo):
```java
            // Consultas (F1): el banco de frases solo necesita BD (los seeds de la V2).
            FraseRepositorio fraseRepo = new FraseRepositorio(db.dataSource());
            comandos.add(new FraseComando(fraseRepo,
                    new Cooldown(java.time.Duration.ofSeconds(30))));
```

- [ ] **Step 4: Verificar** — `.\mvnw.cmd -Dtest=FraseComandoTest test` → PASS. Después `.\mvnw.cmd verify` → verde.

- [ ] **Step 5: Docs + commit** — añadir `/frase` a la tabla de comandos del `README.md` (y `README.en.md` si existe tabla equivalente) y entrada en `CHANGELOG.md`.

```
feat(consultas): /frase — el banco de frases sale a la luz

El banco bilingüe sembrado en la V2 (32 frases, ES/EN) deja de usarse solo por dentro:
/frase devuelve una al azar en el idioma de quien la pide, con autor si lo tiene. Pública
con cooldown de 30 s por usuario (el mismo util Cooldown del XP). Nuevo Tipo FRASE (💬,
naranja de marca) en EmbedFactory.

Novedades:
- Nuevo comando /frase: tu dosis de motivación al momento, en tu idioma. 💬
```

---

### Task 8: `EjercicioDia` + `EjercicioDiaRepositorio`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/db/EjercicioDia.java`, `src/main/java/com/gymprofit/bot/db/EjercicioDiaRepositorio.java`
- Test: `src/test/java/com/gymprofit/bot/db/EjercicioDiaRepositorioTest.java`

- [ ] **Step 1: Test Testcontainers (falla)**

```java
package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba {@link EjercicioDiaRepositorio} contra MySQL real (V24): inserción idempotente por
 * fecha (PK), lectura por fecha, ronda actual e ids usados por ronda. Se salta sin Docker.
 */
class EjercicioDiaRepositorioTest {

    @Test
    void insercionIdempotenteYConsultasDeRonda() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                EjercicioDiaRepositorio repo = new EjercicioDiaRepositorio(db.dataSource());
                LocalDate hoy = LocalDate.of(2026, 7, 20);

                assertEquals(1, repo.rondaActual()); // sin filas: primera ronda

                assertTrue(repo.insertar(new EjercicioDia(hoy, 7, 1)));
                assertFalse(repo.insertar(new EjercicioDia(hoy, 99, 1))); // PK: no pisa
                assertEquals(7, repo.buscarPorFecha(hoy).orElseThrow().ejercicioId());

                repo.insertar(new EjercicioDia(hoy.plusDays(1), 8, 1));
                repo.insertar(new EjercicioDia(hoy.plusDays(2), 7, 2)); // ya en ronda 2
                assertEquals(Set.of(7, 8), repo.idsDeRonda(1));
                assertEquals(Set.of(7), repo.idsDeRonda(2));
                assertEquals(2, repo.rondaActual());
            }
        }
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

`EjercicioDia.java`:
```java
package com.gymprofit.bot.db;

import java.time.LocalDate;

/**
 * Elección del ejercicio del día (tabla {@code ejercicio_dia}, V24). Una fila por día natural
 * de Europe/Madrid; {@code ronda} cuenta las vueltas completas al catálogo.
 *
 * @param fecha       día natural
 * @param ejercicioId id del ejercicio en la API GymProFit
 * @param ronda       vuelta al catálogo (empieza en 1)
 */
public record EjercicioDia(LocalDate fecha, int ejercicioId, int ronda) { }
```

`EjercicioDiaRepositorio.java`:
```java
package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Repositorio JDBC del histórico del ejercicio del día (tabla {@code ejercicio_dia}, V24).
 * La PK por fecha da la idempotencia del job: {@link #insertar} con {@code INSERT IGNORE}
 * devuelve si esta ejecución ganó la fila o ya existía (reinicio, carrera comando/job).
 */
public final class EjercicioDiaRepositorio {

    private final DataSource dataSource;

    public EjercicioDiaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** La elección de una fecha, si ya se hizo. */
    public Optional<EjercicioDia> buscarPorFecha(LocalDate fecha) {
        String sql = "SELECT fecha, ejercicio_id, ronda FROM ejercicio_dia WHERE fecha = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EjercicioDia(rs.getDate("fecha").toLocalDate(),
                        rs.getInt("ejercicio_id"), rs.getInt("ronda")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el ejercicio del día " + fecha, e);
        }
    }

    /** Ronda en curso: la mayor registrada, o 1 si aún no hay ninguna fila. */
    public int rondaActual() {
        String sql = "SELECT COALESCE(MAX(ronda), 1) FROM ejercicio_dia";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la ronda actual", e);
        }
    }

    /** Ids del catálogo que ya han salido en una ronda (para no repetir hasta agotarla). */
    public Set<Integer> idsDeRonda(int ronda) {
        String sql = "SELECT ejercicio_id FROM ejercicio_dia WHERE ronda = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ronda);
            try (ResultSet rs = ps.executeQuery()) {
                Set<Integer> ids = new HashSet<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo los ids de la ronda " + ronda, e);
        }
    }

    /**
     * Inserta la elección del día si nadie la hizo antes ({@code INSERT IGNORE} sobre la PK).
     *
     * @return {@code true} si esta llamada insertó la fila; {@code false} si ya existía
     */
    public boolean insertar(EjercicioDia dia) {
        String sql = "INSERT IGNORE INTO ejercicio_dia (fecha, ejercicio_id, ronda) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dia.fecha()));
            ps.setInt(2, dia.ejercicioId());
            ps.setInt(3, dia.ronda());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el ejercicio del día " + dia.fecha(), e);
        }
    }
}
```

- [ ] **Step 4: Verificar** — `.\mvnw.cmd -Dtest=EjercicioDiaRepositorioTest test` → skipped en local (compila); CI lo ejecuta.

---

### Task 9: `EjercicioDiaService`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/services/EjercicioDiaService.java`
- Test: `src/test/java/com/gymprofit/bot/services/EjercicioDiaServiceTest.java`

- [ ] **Step 1: Test con Mockito (falla)**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.EjercicioDiaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica la elección diaria: mismo día → mismo ejercicio (sin tocar la API), no repite
 * dentro de una ronda, cambia de ronda al agotar el catálogo y resuelve la carrera si otro
 * proceso insertó primero.
 */
class EjercicioDiaServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // 2026-07-20 a las 08:00 de Madrid (verano, UTC+2).
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), MADRID);
    private static final LocalDate HOY = LocalDate.of(2026, 7, 20);

    private EjercicioDiaRepositorio repo;
    private EjercicioService ejercicios;
    private EjercicioDiaService service;

    private static EjercicioDTO dto(int id) {
        return new EjercicioDTO(id, "E" + id, null, "PECHO", null, "INTERMEDIO",
                null, null, null, null, null, true);
    }

    @BeforeEach
    void preparar() {
        repo = mock(EjercicioDiaRepositorio.class);
        ejercicios = mock(EjercicioService.class);
        // Random fijo: con new Random(1), nextInt acotado es determinista dentro del test.
        service = new EjercicioDiaService(repo, ejercicios, new Random(1), RELOJ);
    }

    @Test
    void mismoDiaDevuelveLoYaElegidoSinTocarLaApi() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.of(new EjercicioDia(HOY, 7, 1)));
        assertEquals(7, service.deHoy().ejercicioId());
        verify(ejercicios, never()).listarTodos(anyString());
    }

    @Test
    void eligeSoloEntreLosNoUsadosDeLaRonda() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.empty());
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1), dto(2), dto(3)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of(1, 3));
        when(repo.insertar(any())).thenReturn(true);

        EjercicioDia dia = service.deHoy();
        assertEquals(2, dia.ejercicioId()); // único candidato libre
        assertEquals(1, dia.ronda());
    }

    @Test
    void catalogoAgotadoEmpiezaRondaNueva() {
        when(repo.buscarPorFecha(HOY)).thenReturn(Optional.empty());
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1), dto(2)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of(1, 2)); // todos usados
        when(repo.insertar(any())).thenReturn(true);

        EjercicioDia dia = service.deHoy();
        assertEquals(2, dia.ronda());
        assertTrue(dia.ejercicioId() == 1 || dia.ejercicioId() == 2); // vuelve a valer todo
    }

    @Test
    void siOtroProcesoInsertoPrimeroDevuelveLoSuyo() {
        when(repo.buscarPorFecha(HOY))
                .thenReturn(Optional.empty())                              // 1ª lectura: nada
                .thenReturn(Optional.of(new EjercicioDia(HOY, 9, 1)));     // tras perder la carrera
        when(ejercicios.listarTodos("es")).thenReturn(List.of(dto(1)));
        when(repo.rondaActual()).thenReturn(1);
        when(repo.idsDeRonda(1)).thenReturn(Set.of());
        when(repo.insertar(any())).thenReturn(false); // el INSERT IGNORE no ganó

        assertEquals(9, service.deHoy().ejercicioId());
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
package com.gymprofit.bot.services;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.EjercicioDiaRepositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Elección del ejercicio del día (spec §5.1): cada día natural de Europe/Madrid se sortea un
 * ejercicio entre los que <b>no</b> han salido en la ronda actual; al agotar el catálogo
 * empieza la siguiente. La fila del día la crea quien llegue primero (job de las 8:00 o
 * {@code /ejercicio-dia}): el {@code INSERT IGNORE} sobre la PK por fecha resuelve la carrera
 * y ambos ven siempre el mismo ejercicio.
 */
public final class EjercicioDiaService {

    /** El «día» del bot es el de la comunidad, no UTC. */
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final EjercicioDiaRepositorio repo;
    private final EjercicioService ejercicios;
    private final Random azar;
    private final Clock reloj;

    public EjercicioDiaService(EjercicioDiaRepositorio repo, EjercicioService ejercicios) {
        this(repo, ejercicios, new Random(), Clock.system(ZONA));
    }

    /** Azar y reloj inyectables para testear el sorteo sin aleatoriedad ni reloj real. */
    public EjercicioDiaService(EjercicioDiaRepositorio repo, EjercicioService ejercicios,
                               Random azar, Clock reloj) {
        this.repo = repo;
        this.ejercicios = ejercicios;
        this.azar = azar;
        this.reloj = reloj;
    }

    /** La elección de hoy, creándola si nadie la hizo aún (idéntica para job y comando). */
    public EjercicioDia deHoy() {
        LocalDate hoy = LocalDate.now(reloj);
        return repo.buscarPorFecha(hoy).orElseGet(() -> elegir(hoy));
    }

    private EjercicioDia elegir(LocalDate hoy) {
        // El catálogo se pide en ES: aquí solo importan los ids (la ficha localizada la pide
        // luego quien pinta, con el idioma que toque).
        List<Integer> catalogo = ejercicios.listarTodos("es").stream()
                .map(EjercicioDTO::id)
                .toList();
        int ronda = repo.rondaActual();
        Set<Integer> usados = repo.idsDeRonda(ronda);
        List<Integer> candidatos = catalogo.stream()
                .filter(id -> !usados.contains(id))
                .toList();
        if (candidatos.isEmpty()) {
            // Ronda completada: vuelve a valer el catálogo entero en la ronda siguiente.
            ronda++;
            candidatos = catalogo;
        }
        int elegido = candidatos.get(azar.nextInt(candidatos.size()));
        EjercicioDia dia = new EjercicioDia(hoy, elegido, ronda);
        if (!repo.insertar(dia)) {
            // Otro proceso (job vs comando) ganó la carrera: lo suyo es lo que vale.
            return repo.buscarPorFecha(hoy).orElseThrow(
                    () -> new IllegalStateException("ejercicio_dia sin fila tras perder la carrera"));
        }
        return dia;
    }
}
```

- [ ] **Step 4: Verificar** — `.\mvnw.cmd -Dtest=EjercicioDiaServiceTest test` → PASS (4 tests).

---

### Task 10: `/ejercicios` — estado en customId, builders y comando

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/consultas/EjerciciosComando.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`
- Test: `src/test/java/com/gymprofit/bot/commands/consultas/EjerciciosComandoTest.java`

- [ ] **Step 1: Test de builders y codec (falla)**

```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la lista y la ficha de {@code /ejercicios} sin JDA real: codec del estado en el
 * customId (ida y vuelta, truncado a 30, búsquedas con ':'), botones deshabilitados en los
 * extremos y contenido de los embeds.
 */
class EjerciciosComandoTest {

    private static EjercicioDTO dto(int id) {
        return new EjercicioDTO(id, "Ejercicio " + id, "Descripción " + id, "PECHO",
                "Pectoral mayor", "INTERMEDIO", "http://img/" + id + ".png", null,
                "Instrucciones " + id, 8, "Barra", true);
    }

    private static PaginaDTO<EjercicioDTO> pagina(int numero, int totalPaginas, boolean ultima) {
        return new PaginaDTO<>(IntStream.rangeClosed(1, 8).mapToObj(EjerciciosComandoTest::dto).toList(),
                numero, 8, 873, totalPaginas, ultima);
    }

    @Test
    void elCodecHaceIdaYVueltaConBusquedaConDosPuntos() {
        var filtros = new EjerciciosComando.Filtros(42L, 3, "PECHO", "INTERMEDIO", "press: banca");
        String id = filtros.codificar(EjerciciosComando.PREFIJO_NAV);
        assertTrue(id.startsWith("ejercicios:"));
        var leidos = EjerciciosComando.Filtros.parsear(id, EjerciciosComando.PREFIJO_NAV);
        assertEquals(filtros, leidos); // los ':' de la búsqueda sobreviven (va en último campo)
    }

    @Test
    void laBusquedaSeTruncaA30AlCodificar() {
        String larga = "a".repeat(80);
        var filtros = new EjerciciosComando.Filtros(42L, 0, "", "", larga);
        var leidos = EjerciciosComando.Filtros.parsear(
                filtros.codificar(EjerciciosComando.PREFIJO_VOLVER), EjerciciosComando.PREFIJO_VOLVER);
        assertEquals(30, leidos.busqueda().length());
        // Peor caso real bajo el límite de 100 de Discord.
        var peor = new EjerciciosComando.Filtros(Long.MAX_VALUE, 999, "FULLBODY",
                "PRINCIPIANTE", larga);
        assertTrue(peor.codificar(EjerciciosComando.PREFIJO_VOLVER).length() <= 100);
    }

    @Test
    void primeraYUltimaPaginaDeshabilitanSusFlechas() {
        var filtros = new EjerciciosComando.Filtros(1L, 0, "", "", "");
        List<ActionRow> filas = EjerciciosComando.construirComponentes(pagina(0, 110, false), filtros);
        List<Button> botones = filas.get(0).getButtons();
        assertTrue(botones.get(0).isDisabled());  // ◀ en la primera
        assertFalse(botones.get(1).isDisabled());

        var alFinal = new EjerciciosComando.Filtros(1L, 109, "", "", "");
        List<Button> ultimos = EjerciciosComando
                .construirComponentes(pagina(109, 110, true), alFinal).get(0).getButtons();
        assertTrue(ultimos.get(1).isDisabled());  // ▶ en la última
    }

    @Test
    void laListaLlevaMenuConLosOchoDeLaPagina() {
        var filtros = new EjerciciosComando.Filtros(1L, 0, "", "", "");
        List<ActionRow> filas = EjerciciosComando.construirComponentes(pagina(0, 110, false), filtros);
        assertEquals(2, filas.size()); // botones + menú
        assertEquals(8, filas.get(1).getActionComponents().get(0)
                .asStringSelectMenu().getOptions().size());
    }

    @Test
    void elEmbedDeListaResumeYNumera() {
        MessageEmbed embed = EjerciciosComando.construirLista(Messages.ES,
                pagina(0, 110, false), new EjerciciosComando.Filtros(1L, 0, "", "", ""));
        assertTrue(embed.getDescription().contains("Ejercicio 1"));
        assertTrue(embed.getDescription().contains("873"));
    }

    @Test
    void laFichaMuestraTodosLosCampos() {
        MessageEmbed ficha = EjerciciosComando.construirFicha(Messages.ES, dto(7));
        assertTrue(ficha.getTitle().contains("Ejercicio 7"));
        assertEquals("http://img/7.png", ficha.getImage().getUrl());
        assertTrue(ficha.getFields().stream()
                .anyMatch(f -> "Pectoral mayor".equals(f.getValue())));
    }

    @Test
    void listaVaciaAvisaSinComponentes() {
        PaginaDTO<EjercicioDTO> vacia = new PaginaDTO<>(List.of(), 0, 8, 0, 0, true);
        MessageEmbed embed = EjerciciosComando.construirLista(Messages.ES, vacia,
                new EjerciciosComando.Filtros(1L, 0, "", "", "zzz"));
        assertTrue(embed.getDescription().contains(Messages.get(Messages.ES, "ejercicios.vacio")));
        assertTrue(EjerciciosComando.construirComponentes(vacia,
                new EjerciciosComando.Filtros(1L, 0, "", "", "zzz")).isEmpty());
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * {@code /ejercicios [grupo] [dificultad] [buscar]}: catálogo paginado de la API (8 por página)
 * con botones ◀ ▶ y menú desplegable que abre la ficha completa. El estado viaja en el
 * {@code customId} (patrón {@code /modlogs}): los botones sobreviven a un reinicio del bot.
 * Respuesta pública (regla 13); los errores de API, efímeros y amables.
 */
public final class EjerciciosComando implements Comando {

    private static final String NOMBRE = "ejercicios";
    /** Navegación ◀ ▶: {@code ejercicios:<owner>:<pag>:<grupo>:<dif>:<q>}. */
    public static final String PREFIJO_NAV = "ejercicios:";
    /** Menú de ficha: mismos campos de estado; el value de la opción es el id. */
    public static final String PREFIJO_SEL = "ejercicios-sel:";
    /** Botón de volver de la ficha a la lista. */
    public static final String PREFIJO_VOLVER = "ejercicios-volver:";
    /**
     * Tope de la búsqueda dentro del customId. El spec pedía 40, pero con el prefijo más largo
     * (18) + snowflake (20) + página + filtros + separadores, 40 rebasa los 100 chars que admite
     * Discord; con 30 el peor caso queda en 96.
     */
    public static final int MAX_BUSQUEDA = 30;

    private static final List<String> GRUPOS = List.of("PECHO", "ESPALDA", "PIERNAS", "HOMBROS",
            "BRAZOS", "ABDOMEN", "CARDIO", "FULLBODY");
    private static final List<String> DIFICULTADES =
            List.of("PRINCIPIANTE", "INTERMEDIO", "AVANZADO");

    private final EjercicioService ejercicios;
    private final ExecutorService executor;

    public EjerciciosComando(EjercicioService ejercicios, ExecutorService executor) {
        this.ejercicios = ejercicios;
        this.executor = executor;
    }

    /**
     * Estado de una consulta, codificado en el customId. {@code grupo}/{@code dificultad}/
     * {@code busqueda} usan "" como «sin filtro» (un customId no transporta nulls).
     */
    record Filtros(long ownerId, int pagina, String grupo, String dificultad, String busqueda) {

        /** Codifica con el prefijo dado, truncando la búsqueda a {@link #MAX_BUSQUEDA}. */
        String codificar(String prefijo) {
            String q = busqueda.length() > MAX_BUSQUEDA
                    ? busqueda.substring(0, MAX_BUSQUEDA) : busqueda;
            return prefijo + ownerId + ":" + pagina + ":" + grupo + ":" + dificultad + ":" + q;
        }

        /** Cambia solo la página (para las flechas). */
        Filtros conPagina(int nueva) {
            return new Filtros(ownerId, nueva, grupo, dificultad, busqueda);
        }

        /**
         * Parsea el estado. La búsqueda va en el último campo y el split se limita a 5 trozos:
         * los ':' que el usuario escriba en ella sobreviven.
         */
        static Filtros parsear(String customId, String prefijo) {
            String[] p = customId.substring(prefijo.length()).split(":", 5);
            return new Filtros(Long.parseUnsignedLong(p[0]), Integer.parseInt(p[1]),
                    p[2], p[3], p[4]);
        }
    }

    @Override
    public SlashCommandData definicion() {
        OptionData grupo = new OptionData(OptionType.STRING, "grupo",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.grupo"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.grupo"));
        GRUPOS.forEach(g -> grupo.addChoice(capitalizar(g), g));
        OptionData dificultad = new OptionData(OptionType.STRING, "dificultad",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.dificultad"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.dificultad"));
        DIFICULTADES.forEach(d -> dificultad.addChoice(capitalizar(d), d));
        OptionData buscar = new OptionData(OptionType.STRING, "buscar",
                Messages.get(Messages.ES, "comando.ejercicios.opcion.buscar"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.opcion.buscar"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ejercicios.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ejercicios.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejercicios.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(grupo, dificultad, buscar);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Filtros filtros = new Filtros(evento.getUser().getIdLong(), 0,
                opcion(evento, "grupo"), opcion(evento, "dificultad"), opcion(evento, "buscar"));
        // deferReply antes de tocar la API: Render puede tardar ~50 s en despertar y Discord
        // solo da 3 s sin defer. La llamada va al executor propio, nunca al hilo del gateway.
        evento.deferReply().queue();
        CompletableFuture.runAsync(() -> {
            try {
                PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar(filtros.busqueda(),
                        filtros.grupo(), filtros.dificultad(), 0, locale.getLanguage());
                evento.getHook().editOriginalEmbeds(construirLista(locale, pagina, filtros))
                        .setComponents(construirComponentes(pagina, filtros))
                        .queue();
            } catch (ApiException e) {
                // El «pensando…» público se borra y el aviso va efímero (regla 13).
                evento.getHook().deleteOriginal().queue();
                evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS,
                                locale, Messages.get(locale, "ejercicios.error")))
                        .setEphemeral(true).queue();
            }
        }, executor);
    }

    private static String opcion(SlashCommandInteractionEvent evento, String nombre) {
        var opcion = evento.getOption(nombre);
        return opcion == null ? "" : opcion.getAsString();
    }

    /** Embed de una página de la lista (azul de consulta). Estático para test y listener. */
    public static MessageEmbed construirLista(Locale locale, PaginaDTO<EjercicioDTO> pagina,
                                              Filtros filtros) {
        EmbedBuilder builder = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.titulo"));
        if (pagina.content().isEmpty()) {
            return builder.setDescription(Messages.get(locale, "ejercicios.vacio")).build();
        }
        StringBuilder desc = new StringBuilder(Messages.get(locale, "ejercicios.resumen",
                pagina.totalElements(), pagina.page() + 1, pagina.totalPages()));
        desc.append("\n");
        int n = pagina.page() * EjercicioService.TAMANO_PAGINA + 1;
        for (EjercicioDTO e : pagina.content()) {
            desc.append("\n**").append(n++).append(". ").append(e.nombre()).append("**")
                    .append(" — ").append(capitalizar(e.grupoMuscular()))
                    .append(" · ").append(capitalizar(e.dificultad()));
        }
        return builder.setDescription(desc.toString()).build();
    }

    /** Fila de flechas + menú de ficha; vacío si no hay resultados. Estático para el listener. */
    public static List<ActionRow> construirComponentes(PaginaDTO<EjercicioDTO> pagina,
                                                       Filtros filtros) {
        if (pagina.content().isEmpty()) {
            return List.of();
        }
        Button anterior = Button.secondary(
                        filtros.conPagina(filtros.pagina() - 1).codificar(PREFIJO_NAV), "◀")
                .withDisabled(filtros.pagina() == 0);
        Button siguiente = Button.secondary(
                        filtros.conPagina(filtros.pagina() + 1).codificar(PREFIJO_NAV), "▶")
                .withDisabled(pagina.last());
        StringSelectMenu.Builder menu = StringSelectMenu.create(filtros.codificar(PREFIJO_SEL))
                .setPlaceholder(Messages.get(Messages.ES, "ejercicios.menu"));
        for (EjercicioDTO e : pagina.content()) {
            // El label admite 100 chars; los nombres del catálogo caben, pero por si acaso.
            String nombre = e.nombre().length() > 100 ? e.nombre().substring(0, 100) : e.nombre();
            menu.addOption(nombre, String.valueOf(e.id()));
        }
        return List.of(ActionRow.of(anterior, siguiente), ActionRow.of(menu.build()));
    }

    /** Ficha completa de un ejercicio (imagen grande + campos). Estática para el listener. */
    public static MessageEmbed construirFicha(Locale locale, EjercicioDTO e) {
        EmbedBuilder builder = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale, e.nombre());
        if (e.descripcion() != null && !e.descripcion().isBlank()) {
            builder.setDescription(e.descripcion());
        }
        if (e.imagenUrl() != null && !e.imagenUrl().isBlank()) {
            builder.setImage(e.imagenUrl());
        }
        builder.addField(Messages.get(locale, "ejercicios.campo.grupo"),
                capitalizar(e.grupoMuscular()), true);
        if (e.musculoPrimario() != null) {
            builder.addField(Messages.get(locale, "ejercicios.campo.musculo"),
                    e.musculoPrimario(), true);
        }
        builder.addField(Messages.get(locale, "ejercicios.campo.dificultad"),
                capitalizar(e.dificultad()), true);
        if (e.caloriasQuemadas() != null) {
            builder.addField(Messages.get(locale, "ejercicios.campo.calorias"),
                    String.valueOf(e.caloriasQuemadas()), true);
        }
        if (e.equipoNecesario() != null && !e.equipoNecesario().isBlank()) {
            builder.addField(Messages.get(locale, "ejercicios.campo.equipo"),
                    e.equipoNecesario(), true);
        }
        if (e.instrucciones() != null && !e.instrucciones().isBlank()) {
            builder.addField(Messages.get(locale, "ejercicios.campo.instrucciones"),
                    truncar(e.instrucciones(), 1024), false); // límite de field de Discord
        }
        return builder.build();
    }

    /** Botón para volver de la ficha a la página de lista de la que se vino. */
    public static List<ActionRow> construirBotonVolver(Locale locale, Filtros filtros) {
        return List.of(ActionRow.of(Button.secondary(filtros.codificar(PREFIJO_VOLVER),
                Messages.get(locale, "ejercicios.volver"))));
    }

    /** {@code "PECHO"} → {@code "Pecho"} (los enums de la API gritan; el embed no). */
    private static String capitalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "—";
        }
        return valor.charAt(0) + valor.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
```

i18n — `messages_es.properties`:
```properties
# --- /ejercicios ----------------------------------------------------------
comando.ejercicios.descripcion=Explora el catálogo de ejercicios de GymProFit
comando.ejercicios.opcion.grupo=Filtra por grupo muscular
comando.ejercicios.opcion.dificultad=Filtra por dificultad
comando.ejercicios.opcion.buscar=Busca por nombre
ejercicios.titulo=Catálogo de ejercicios
ejercicios.resumen={0} ejercicios · página {1} de {2}
ejercicios.vacio=No hay ejercicios con esos filtros. Prueba a quitar alguno.
ejercicios.menu=Elige un ejercicio para ver su ficha
ejercicios.volver=Volver a la lista
ejercicios.noestuyo=✋ Esa búsqueda no es tuya: lanza tu propio /ejercicios.
ejercicios.campo.grupo=Grupo muscular
ejercicios.campo.musculo=Músculo primario
ejercicios.campo.dificultad=Dificultad
ejercicios.campo.calorias=Calorías aprox.
ejercicios.campo.equipo=Equipo
ejercicios.campo.instrucciones=Instrucciones
ejercicios.error=😴 El catálogo no responde ahora mismo (la API se está despertando). Inténtalo en un momento.
```
`messages_en.properties`:
```properties
# --- /ejercicios ----------------------------------------------------------
comando.ejercicios.descripcion=Browse the GymProFit exercise catalog
comando.ejercicios.opcion.grupo=Filter by muscle group
comando.ejercicios.opcion.dificultad=Filter by difficulty
comando.ejercicios.opcion.buscar=Search by name
ejercicios.titulo=Exercise catalog
ejercicios.resumen={0} exercises · page {1} of {2}
ejercicios.vacio=No exercises match those filters. Try removing one.
ejercicios.menu=Pick an exercise to see its card
ejercicios.volver=Back to the list
ejercicios.noestuyo=✋ That search is not yours: run your own /ejercicios.
ejercicios.campo.grupo=Muscle group
ejercicios.campo.musculo=Primary muscle
ejercicios.campo.dificultad=Difficulty
ejercicios.campo.calorias=Approx. calories
ejercicios.campo.equipo=Equipment
ejercicios.campo.instrucciones=Instructions
ejercicios.error=😴 The catalog is not responding right now (the API is waking up). Try again in a moment.
```

- [ ] **Step 4: Verificar** — `.\mvnw.cmd -Dtest=EjerciciosComandoTest test` → PASS (7 tests).

*(El wiring en Main llega en la Task 12 junto al resto de piezas de API; sin commit aún.)*

---

### Task 11: `EjerciciosPaginadorListener`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/events/EjerciciosPaginadorListener.java`

- [ ] **Step 1: Implementar** (la lógica testeable —codec, builders, extremos— ya quedó cubierta en la Task 10; el listener es pegamento JDA, como `ModlogsPaginadorListener`, que tampoco tiene test propio)

```java
package com.gymprofit.bot.events;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.commands.consultas.EjerciciosComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Navegación de {@code /ejercicios}: flechas ◀ ▶ (recalcula la página), menú desplegable
 * (pasa el mismo mensaje a la ficha) y botón «Volver a la lista». Todo el estado viene en el
 * {@code customId} (ver {@link EjerciciosComando.Filtros}): sobrevive a reinicios del bot.
 * Solo el dueño de la búsqueda puede usar sus controles; las llamadas a la API van al executor
 * propio con {@code deferEdit()} previo (la API puede tardar en despertar).
 */
public final class EjerciciosPaginadorListener extends ListenerAdapter {

    private final EjercicioService ejercicios;
    private final ExecutorService executor;

    public EjerciciosPaginadorListener(EjercicioService ejercicios, ExecutorService executor) {
        this.ejercicios = ejercicios;
        this.executor = executor;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        // NAV y VOLVER comparten manejo: ambos re-pintan la lista en la página del estado.
        String prefijo = id.startsWith(EjerciciosComando.PREFIJO_VOLVER)
                ? EjerciciosComando.PREFIJO_VOLVER
                : id.startsWith(EjerciciosComando.PREFIJO_NAV) ? EjerciciosComando.PREFIJO_NAV : null;
        // Ojo: "ejercicios-sel:"/"ejercicios-volver:" también empiezan por "ejercicios"; el
        // prefijo NAV exacto es "ejercicios:" y el startsWith de arriba comprueba VOLVER antes.
        if (prefijo == null || id.startsWith(EjerciciosComando.PREFIJO_SEL)) {
            return;
        }
        var filtros = EjerciciosComando.Filtros.parsear(id, prefijo);
        if (!esDueno(evento, filtros.ownerId())) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferEdit().queue();
        CompletableFuture.runAsync(() -> {
            try {
                PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar(filtros.busqueda(),
                        filtros.grupo(), filtros.dificultad(), filtros.pagina(),
                        locale.getLanguage());
                evento.getHook().editOriginalEmbeds(
                                EjerciciosComando.construirLista(locale, pagina, filtros))
                        .setComponents(EjerciciosComando.construirComponentes(pagina, filtros))
                        .queue();
            } catch (ApiException e) {
                avisarError(evento, locale);
            }
        }, executor);
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(EjerciciosComando.PREFIJO_SEL)) {
            return;
        }
        var filtros = EjerciciosComando.Filtros.parsear(id, EjerciciosComando.PREFIJO_SEL);
        if (!esDueno(evento, filtros.ownerId())) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        int ejercicioId = Integer.parseInt(evento.getValues().get(0));
        evento.deferEdit().queue();
        CompletableFuture.runAsync(() -> {
            try {
                EjercicioDTO ficha = ejercicios.porId(ejercicioId, locale.getLanguage());
                evento.getHook().editOriginalEmbeds(
                                EjerciciosComando.construirFicha(locale, ficha))
                        .setComponents(EjerciciosComando.construirBotonVolver(locale, filtros))
                        .queue();
            } catch (ApiException e) {
                avisarError(evento, locale);
            }
        }, executor);
    }

    /** Solo el dueño usa sus controles (como en el resto del bot); el aviso va efímero. */
    private static boolean esDueno(GenericComponentInteractionCreateEvent evento, long ownerId) {
        if (evento.getUser().getIdLong() == ownerId) {
            return true;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.noestuyo"))).setEphemeral(true).queue();
        return false;
    }

    /** El mensaje original se queda como está; el error va en followup efímero (regla 13). */
    private static void avisarError(GenericComponentInteractionCreateEvent evento, Locale locale) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.error"))).setEphemeral(true).queue();
    }
}
```

- [ ] **Step 2: Verificar que compila** — `.\mvnw.cmd -DskipTests compile` → BUILD SUCCESS.

---

### Task 12: `/ejercicio-dia` + wiring de Main (comandos + listener)

**Files:**
- Create: `src/main/java/com/gymprofit/bot/commands/consultas/EjercicioDiaComando.java`
- Modify: `src/main/java/com/gymprofit/bot/Main.java`
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties`
- Test: `src/test/java/com/gymprofit/bot/commands/consultas/EjercicioDiaComandoTest.java`

- [ ] **Step 1: Test del builder (falla)**

```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el embed del ejercicio del día: naranja de marca, ficha completa y frase al pie. */
class EjercicioDiaComandoTest {

    private static final EjercicioDTO EJERCICIO = new EjercicioDTO(7, "Sentadilla",
            "El básico de pierna", "PIERNAS", "Cuádriceps", "PRINCIPIANTE",
            "http://img/7.png", null, "Baja controlado…", 9, "Peso corporal", true);
    private static final Frase FRASE =
            new Frase(1, "Progreso, no perfección.", "Progress, not perfection.", null);

    @Test
    void llevaFichaFraseYColorDeMarca() {
        MessageEmbed embed = EjercicioDiaComando.construirEmbed(Messages.ES, EJERCICIO, FRASE);
        assertEquals(0xFF6A00, embed.getColorRaw()); // naranja MARCA (SPEC §7)
        assertTrue(embed.getTitle().contains(Messages.get(Messages.ES, "ejerciciodia.titulo")));
        assertTrue(embed.getDescription().contains("Sentadilla"));
        assertEquals("http://img/7.png", embed.getImage().getUrl());
        assertTrue(embed.getFields().stream()
                .anyMatch(f -> f.getValue() != null && f.getValue().contains("Progreso")));
    }

    @Test
    void sinFraseElEmbedSigueEntero() {
        MessageEmbed embed = EjercicioDiaComando.construirEmbed(Messages.EN, EJERCICIO, null);
        assertTrue(embed.getDescription().contains("Sentadilla"));
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar**

```java
package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * {@code /ejercicio-dia}: el ejercicio de hoy. Si el job de las 8:00 aún no corrió, la
 * elección se crea aquí mismo ({@link EjercicioDiaService#deHoy()}): comando y publicación
 * siempre coinciden. Respuesta pública, naranja de marca, con frase motivadora al pie.
 */
public final class EjercicioDiaComando implements Comando {

    private static final String NOMBRE = "ejercicio-dia";

    private final EjercicioDiaService eleccion;
    private final EjercicioService ejercicios;
    private final FraseRepositorio frases;
    private final ExecutorService executor;

    public EjercicioDiaComando(EjercicioDiaService eleccion, EjercicioService ejercicios,
                               FraseRepositorio frases, ExecutorService executor) {
        this.eleccion = eleccion;
        this.ejercicios = ejercicios;
        this.frases = frases;
        this.executor = executor;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ejerciciodia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ejerciciodia.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ejerciciodia.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply().queue(); // la API puede estar despertando (~50 s)
        CompletableFuture.runAsync(() -> {
            try {
                EjercicioDia dia = eleccion.deHoy();
                EjercicioDTO ficha = ejercicios.porId(dia.ejercicioId(), locale.getLanguage());
                Frase frase = frases.aleatoria().orElse(null);
                evento.getHook().editOriginalEmbeds(construirEmbed(locale, ficha, frase)).queue();
            } catch (ApiException e) {
                evento.getHook().deleteOriginal().queue();
                evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.EJERCICIO,
                                locale, Messages.get(locale, "ejercicios.error")))
                        .setEphemeral(true).queue();
            }
        }, executor);
    }

    /**
     * Embed del ejercicio del día (lo comparten el comando y el job): título de marca, la ficha
     * completa reutilizando los campos de {@code /ejercicios} y la frase motivadora al pie.
     *
     * @param frase puede ser {@code null} (banco vacío): el post sale igual, sin cita
     */
    public static MessageEmbed construirEmbed(Locale locale, EjercicioDTO ejercicio, Frase frase) {
        // Se parte de la ficha estándar y se re-etiqueta como post del día: mismo contenido,
        // color/emoji de marca (SPEC §7: el ejercicio del día es naranja).
        MessageEmbed ficha = EjerciciosComando.construirFicha(locale, ejercicio);
        EmbedBuilder builder = new EmbedBuilder(ficha);
        builder.setColor(EmbedFactory.Categoria.MARCA.color());
        builder.setTitle(EmbedFactory.Tipo.EJERCICIO.emoji() + "  "
                + Messages.get(locale, "ejerciciodia.titulo"));
        builder.setDescription("## " + ejercicio.nombre()
                + (ficha.getDescription() == null ? "" : "\n" + ficha.getDescription()));
        if (frase != null) {
            String cita = "*«" + frase.texto(locale) + "»*";
            if (frase.autor() != null) {
                cita += " — " + frase.autor();
            }
            builder.addField(Messages.get(locale, "ejerciciodia.campo.motivacion"), cita, false);
        }
        return builder.build();
    }
}
```

Nota para el ejecutor: `new EmbedBuilder(ficha)` copia el embed existente (constructor de copia de JDA). Verificar que existe en JDA 5.6.1 (`EmbedBuilder(MessageEmbed)`); si no, construir el embed desde cero con `EmbedFactory.base(Tipo.EJERCICIO, ...)` repitiendo los `addField` de `construirFicha` extraídos a un helper compartido `agregarCamposFicha(EmbedBuilder, Locale, EjercicioDTO)`.

i18n — `messages_es.properties`:
```properties
# --- /ejercicio-dia -------------------------------------------------------
comando.ejerciciodia.descripcion=El ejercicio del día de GymProFit
ejerciciodia.titulo=Ejercicio del día
ejerciciodia.campo.motivacion=💬 Motivación
```
`messages_en.properties`:
```properties
# --- /ejercicio-dia -------------------------------------------------------
comando.ejerciciodia.descripcion=GymProFit's exercise of the day
ejerciciodia.titulo=Exercise of the day
ejerciciodia.campo.motivacion=💬 Motivation
```

- [ ] **Step 4: Wiring en `Main.iniciarDiscord`** — tras el bloque de `/frase` de la Task 7 (con sus imports):

```java
            // Consultas a la API (F1): catálogo y ejercicio del día. Solo si hay URL y
            // credenciales de la cuenta de servicio; sin ellas el bot arranca sin estos
            // comandos (arranque degradado, mismo patrón que BD/JDA).
            if (!BotConfig.apiUrl().isBlank() && !BotConfig.botServiceUser().isBlank()
                    && !BotConfig.botServicePassword().isBlank()) {
                ApiClient apiClient = new ApiClient(BotConfig.apiUrl(),
                        BotConfig.botServiceUser(), BotConfig.botServicePassword());
                EjercicioService ejercicioService = new EjercicioService(apiClient.ejercicios());
                EjercicioDiaService ejercicioDiaService = new EjercicioDiaService(
                        new EjercicioDiaRepositorio(db.dataSource()), ejercicioService);
                comandos.add(new EjerciciosComando(ejercicioService, apiClient.executor()));
                comandos.add(new EjercicioDiaComando(ejercicioDiaService, ejercicioService,
                        fraseRepo, apiClient.executor()));
                listeners.add(new EjerciciosPaginadorListener(ejercicioService,
                        apiClient.executor()));
            } else {
                log.warn("GYMPROFIT_API_URL o BOT_SERVICE_USER/PASSWORD sin configurar: "
                        + "/ejercicios y /ejercicio-dia deshabilitados.");
            }
```

El job (Task 13) necesita JDA ya construido: para no montar dos `ApiClient`, extraer la construcción anterior a un campo/holder que `main()` pueda reutilizar. Patrón concreto: crear en `Main` un record interno

```java
    /** Piezas de la capa API construidas una sola vez y compartidas por comandos y job. */
    private record CapaApi(EjercicioService ejercicios, EjercicioDiaService eleccion) { }
```

construir `CapaApi` en `main()` (antes de `iniciarDiscord`, si hay BD y config de API), pasarla a `iniciarDiscord(db, capaApi)` (nullable) y usarla también para arrancar el job en `main()` cuando `jda != null && capaApi != null` (ver Task 13). El ejecutor decide los detalles mínimos manteniendo este reparto.

- [ ] **Step 5: Verificar** — `.\mvnw.cmd -Dtest=EjercicioDiaComandoTest test` → PASS; `.\mvnw.cmd verify` → verde.

- [ ] **Step 6: Docs + commit de `/ejercicios` + `/ejercicio-dia` (comandos)** — README tabla de comandos + CHANGELOG.

```
feat(consultas): /ejercicios y /ejercicio-dia — el catálogo de la app, en Discord

/ejercicios explora los 873 ejercicios reales de GymProFit: lista paginada de 8 con
flechas, menú para abrir la ficha completa (imagen, músculo, dificultad, calorías, equipo,
instrucciones) y botón de volver. El estado viaja en el customId (patrón /modlogs): los
botones sobreviven a reinicios. /ejercicio-dia muestra el elegido de hoy y lo crea si el
job aún no corrió (misma elección siempre, INSERT IGNORE sobre la PK por fecha). Arranque
degradado sin credenciales de la API.

Novedades:
- Nuevo /ejercicios: busca en el catálogo real de la app por grupo muscular, dificultad o
  nombre, y abre la ficha de cada ejercicio sin salir de Discord. 🏋️
- Nuevo /ejercicio-dia: el ejercicio elegido de hoy, con su frase motivadora.
```

---

### Task 13: `EjercicioDiaJob` + `listarConEjercicioDia`

**Files:**
- Create: `src/main/java/com/gymprofit/bot/jobs/EjercicioDiaJob.java`
- Modify: `src/main/java/com/gymprofit/bot/db/ConfigServidorRepositorio.java`, `src/test/java/com/gymprofit/bot/db/ConfigServidorRepositorioTest.java`, `src/main/java/com/gymprofit/bot/Main.java`
- Test: `src/test/java/com/gymprofit/bot/jobs/EjercicioDiaJobTest.java`

- [ ] **Step 1: Test del cálculo de espera (falla)**

```java
package com.gymprofit.bot.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica el cálculo de la espera hasta las 8:00 de Europe/Madrid, incluido el salto de día
 * y el cambio de hora (el cálculo va en hora local: tras un cambio DST sigue siendo a las 8:00
 * de reloj de pared, no 24 h exactas después).
 */
class EjercicioDiaJobTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void antesDeLasOchoEsperaHastaHoy() {
        ZonedDateTime seisAm = ZonedDateTime.of(2026, 7, 20, 6, 30, 0, 0, MADRID);
        assertEquals(Duration.ofMinutes(90), EjercicioDiaJob.esperaHastaLasOcho(seisAm));
    }

    @Test
    void despuesDeLasOchoEsperaHastaManana() {
        ZonedDateTime nueveAm = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(23), EjercicioDiaJob.esperaHastaLasOcho(nueveAm));
    }

    @Test
    void justoALasOchoProgramaParaManana() {
        ZonedDateTime lasOcho = ZonedDateTime.of(2026, 7, 20, 8, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(24), EjercicioDiaJob.esperaHastaLasOcho(lasOcho));
    }

    @Test
    void elCambioDeHoraMantieneLasOchoLocales() {
        // Noche del 24 al 25 de octubre de 2026: a las 03:00 se retrasa a las 02:00 (25 h de día).
        ZonedDateTime vispera = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, MADRID);
        assertEquals(Duration.ofHours(25), EjercicioDiaJob.esperaHastaLasOcho(vispera));
    }
}
```

- [ ] **Step 2: Verificar que falla** — FAIL de compilación.

- [ ] **Step 3: Implementar el job**

```java
package com.gymprofit.bot.jobs;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.commands.consultas.EjercicioDiaComando;
import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.Frase;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publica el ejercicio del día a las <b>8:00 Europe/Madrid</b> en el canal configurado de cada
 * servidor ({@code config_servidor.canal_ejercicio_dia}, lo fija {@code /setup}), en el idioma
 * del servidor (el post es para todos, no para un usuario). Sin mención de rol (spec §5.2: un
 * ping diario quema). De paso hace de despertador de la API de Render.
 *
 * <p>Se reprograma a sí mismo tras cada ejecución calculando las próximas 8:00 en hora local
 * (aguanta los cambios de horario). Si la API falla, no publica un post roto: lo registra y
 * reintenta a los 30 min; los servidores ya publicados hoy no se repiten (idempotencia por
 * guild en memoria + fila única por fecha en BD).</p>
 */
public final class EjercicioDiaJob {

    private static final Logger log = LoggerFactory.getLogger(EjercicioDiaJob.class);
    private static final LocalTime HORA_PUBLICACION = LocalTime.of(8, 0);
    private static final long REINTENTO_MIN = 30;

    private final JDA jda;
    private final EjercicioDiaService eleccion;
    private final EjercicioService ejercicios;
    private final FraseRepositorio frases;
    private final ConfigServidorRepositorio configs;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gymprobot-ejercicio-dia");
                t.setDaemon(true);
                return t;
            });

    /** Guilds ya publicados en el día en curso (evita repetir el post en los reintentos). */
    private final Set<Long> publicadosHoy = ConcurrentHashMap.newKeySet();
    private volatile LocalDate diaDePublicados;

    public EjercicioDiaJob(JDA jda, EjercicioDiaService eleccion, EjercicioService ejercicios,
                           FraseRepositorio frases, ConfigServidorRepositorio configs) {
        this.jda = jda;
        this.eleccion = eleccion;
        this.ejercicios = ejercicios;
        this.frases = frases;
        this.configs = configs;
    }

    public void iniciar() {
        programarSiguiente();
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    /**
     * Espera desde {@code ahora} hasta las próximas 8:00 <b>locales</b> de Madrid. Si ya son
     * las 8:00 en punto o más tarde, apunta a mañana. En hora local a propósito: tras un cambio
     * de horario el post sigue saliendo a las 8:00 de reloj, no 24 h después.
     */
    static Duration esperaHastaLasOcho(ZonedDateTime ahora) {
        ZonedDateTime proxima = ahora.toLocalDate().atTime(HORA_PUBLICACION)
                .atZone(ahora.getZone());
        if (!proxima.isAfter(ahora)) {
            proxima = ahora.toLocalDate().plusDays(1).atTime(HORA_PUBLICACION)
                    .atZone(ahora.getZone());
        }
        return Duration.between(ahora, proxima);
    }

    private void programarSiguiente() {
        Duration espera = esperaHastaLasOcho(ZonedDateTime.now(EjercicioDiaService.ZONA));
        scheduler.schedule(this::tick, espera.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Ejercicio del día programado para dentro de {} min", espera.toMinutes());
    }

    private void tick() {
        // Lo primero es reprogramar mañana: un fallo publicando jamás mata el ciclo diario.
        programarSiguiente();
        publicar();
    }

    /** Publica en todos los servidores configurados. Público para invocarlo en manual/tests. */
    public void publicar() {
        LocalDate hoy = LocalDate.now(EjercicioDiaService.ZONA);
        if (!hoy.equals(diaDePublicados)) {
            publicadosHoy.clear();
            diaDePublicados = hoy;
        }
        try {
            EjercicioDia dia = eleccion.deHoy();
            for (ConfigServidor config : configs.listarConEjercicioDia()) {
                if (publicadosHoy.contains(config.guildId())) {
                    continue; // reintento: este servidor ya tiene su post de hoy
                }
                TextChannel canal = jda.getTextChannelById(config.canalEjercicioDia());
                if (canal == null) {
                    log.warn("Canal de ejercicio del día no encontrado en el guild {}",
                            config.guildId());
                    continue;
                }
                Locale locale = Messages.desdeTag(config.idioma());
                EjercicioDTO ficha = ejercicios.porId(dia.ejercicioId(), locale.getLanguage());
                Frase frase = frases.aleatoria().orElse(null);
                canal.sendMessageEmbeds(
                        EjercicioDiaComando.construirEmbed(locale, ficha, frase)).queue();
                publicadosHoy.add(config.guildId());
            }
        } catch (ApiException e) {
            // API caída/despertando: nada de posts rotos; reintento con la misma idempotencia.
            log.warn("La API no respondió para el ejercicio del día; reintento en {} min",
                    REINTENTO_MIN, e);
            scheduler.schedule(this::publicar, REINTENTO_MIN, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.error("Fallo publicando el ejercicio del día", e);
        }
    }
}
```

- [ ] **Step 4: `listarConEjercicioDia` en `ConfigServidorRepositorio`** — añadir método (reutiliza `mapear`):

```java
    /** Servidores con canal de ejercicio del día configurado (los destinos del job diario). */
    public java.util.List<ConfigServidor> listarConEjercicioDia() {
        String sql = "SELECT guild_id, idioma, canal_bienvenida, canal_ejercicio_dia, "
                + "canal_logros, canal_sugerencias, canal_soporte, canal_bot_logs, "
                + "rol_objetivo_fuerza, rol_objetivo_cardio, rol_objetivo_perdida_peso, "
                + "rol_objetivo_general FROM config_servidor WHERE canal_ejercicio_dia IS NOT NULL";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            java.util.List<ConfigServidor> lista = new java.util.ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new DatabaseException("Error listando servidores con ejercicio del día", e);
        }
    }
```

(Con imports normales `java.util.List`/`ArrayList` arriba, siguiendo el estilo del archivo.)

Y ampliar `ConfigServidorRepositorioTest` dentro del bloque existente (aprovecha el contenedor ya arrancado), al final del try de `Database`:

```java
                // listarConEjercicioDia: solo salen los servidores con el canal fijado.
                assertEquals(0, repo.listarConEjercicioDia().size());
                repo.guardar(new ConfigServidor(42L, "en", 100L, 555L, null, null, null, null,
                        200L, null, null, null));
                assertEquals(1, repo.listarConEjercicioDia().size());
                assertEquals(555L, repo.listarConEjercicioDia().get(0).canalEjercicioDia());
```

- [ ] **Step 5: Arrancar el job en `Main.main()`** — junto a `SorteoJob`, usando la `CapaApi` de la Task 12:

```java
        // Publicación diaria del ejercicio (8:00 Europe/Madrid). Requiere BD + JDA + API.
        if (db != null && jda != null && capaApi != null) {
            new EjercicioDiaJob(jda, capaApi.eleccion(), capaApi.ejercicios(),
                    new FraseRepositorio(db.dataSource()),
                    new ConfigServidorRepositorio(db.dataSource())).iniciar();
        }
```

- [ ] **Step 6: Verificar** — `.\mvnw.cmd -Dtest=EjercicioDiaJobTest test` → PASS (4 tests); `.\mvnw.cmd verify` → verde.

- [ ] **Step 7: Docs + commit** — `jobs/README.md` (el ejemplo `EjercicioDelDiaJob` pasa a ser real con su nombre definitivo `EjercicioDiaJob`), README, CHANGELOG.

```
feat(consultas): el ejercicio del día se publica solo a las 8:00

EjercicioDiaJob se autoreprograma a las 8:00 de Europe/Madrid en hora local (aguanta los
cambios de horario), publica en el canal_ejercicio_dia de cada servidor configurado en el
idioma del servidor, sin mención de rol, con frase motivadora al pie. Si la API duerme,
registra y reintenta a los 30 min sin repetir los servidores ya publicados. De regalo,
despierta la API de Render cada mañana.

Novedades:
- Cada mañana a las 8:00, ejercicio nuevo en 🗓️・ejercicio-del-día: no se repite ninguno
  hasta agotar los 873 del catálogo. 🌅
```

---

### Task 14: Intros de `/setup`, docs finales y verificación completa

**Files:**
- Modify: `src/main/java/com/gymprofit/bot/services/SetupServidorPlan.java` (línea ~249: el canal `🗓️・ejercicio-del-día` pasa a tener intro)
- Modify: `src/main/resources/messages_es.properties`, `messages_en.properties` (intro nueva + `intro.comandos.desc` + `intro.como.desc`)
- Modify: `README.md` (+ `README.en.md` si tiene tabla), `docs/architecture.md`, `CHANGELOG.md`, `GYMPROBOT_SPEC.md` si lista contadores de comandos

- [ ] **Step 1: Intro del canal del ejercicio del día** — en `SetupServidorPlan`:

```java
                    texto("🗓️・ejercicio-del-día", TipoCanal.EJERCICIO_DIA, "intro.ejerciciodia")
                            .conTopic("El ejercicio o reto del día. ¡A moverse! 🗓️"),
```

`messages_es.properties`:
```properties
intro.ejerciciodia.titulo=Ejercicio del día
intro.ejerciciodia.desc=🗓️ Cada mañana a las **8:00** el bot publica aquí un ejercicio del catálogo real de GymProFit, con su ficha completa y una frase para arrancar el día.\n\n¿Llegas antes que el bot o quieres verlo otra vez? Usa `/ejercicio-dia`. Para explorar el catálogo entero: `/ejercicios` en 🤖 comandos-bot.
```
`messages_en.properties`:
```properties
intro.ejerciciodia.titulo=Exercise of the day
intro.ejerciciodia.desc=🗓️ Every morning at **8:00** the bot posts an exercise from the real GymProFit catalog here, with its full card and a quote to start the day.\n\nBeat the bot to it or want it again? Use `/ejercicio-dia`. To browse the whole catalog: `/ejercicios` in 🤖 comandos-bot.
```

- [ ] **Step 2: Actualizar intros que mencionan comandos** (regla [[gymprobot-setup-intros]]):
  - `intro.comandos.desc` (ES): añadir los nuevos → `…Prueba /nivel, /top, /ejercicios o /frase, y escribe / para ver todo…` (y equivalente EN).
  - `intro.como.desc` (ES): la línea «🔜 Muy pronto: frase y ejercicio del día, …» deja de ser futura → mover frase/ejercicio a la parte real (p. ej. añadir viñeta «**🏋️ Catálogo real** > `/ejercicios` para explorar los ejercicios de la app y `/ejercicio-dia`/`/frase` para tu dosis diaria.») y dejar el "muy pronto" con trivia/retos. Equivalente EN.

- [ ] **Step 3: Docs finales**
  - `README.md`: tabla de comandos con `/ejercicios`, `/ejercicio-dia`, `/frase` (total 59); sección de arquitectura si menciona api/ vacío.
  - `docs/architecture.md`: capa api/ + job diario (flujo 8:00) + diagrama/lista de paquetes al día.
  - `CHANGELOG.md`: ya alimentado por los commits anteriores; revisar coherencia.
  - `api/README.md`, `jobs/README.md`: ya tocados en Tasks 5 y 13; revisar que no queden como "vacío/futuro".

- [ ] **Step 4: Verificación final completa**

Run: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\ms-21.0.11"; .\mvnw.cmd verify`
Expected: BUILD SUCCESS, ~325+ tests, 0 fallos (los Testcontainers skipped en local — CI los valida).

- [ ] **Step 5: Commit final**

```
docs(consultas): intros de /setup y documentación del módulo de consultas

El canal 🗓️・ejercicio-del-día estrena intro fijada (explica el post de las 8:00 y
/ejercicio-dia), y las intros de comandos-bot y cómo-funciona mencionan /ejercicios y
/frase (dejan de estar en el «muy pronto»). README con la tabla al día (59 comandos),
architecture con la capa api/ y el job diario.

Novedades:
- El servidor explica solo las novedades: pásate por 🗓️・ejercicio-del-día y 🤖・comandos-bot.
```

---

## Self-review (hecho al escribir el plan)

1. **Cobertura del spec:** §3 capa api (Tasks 2–5) ✔ · §3.1 auth 401 serializado (T3–4) ✔ · §3.2 timeouts/defer/reintentos/429/caché/despertador (T4, T5, T13) ✔ · §4 /ejercicios con customId, truncado, dueño, 8/página, menú+ficha+volver, público (T10–11) ✔ · §5.1 V24+rondas+idempotencia+comando-crea (T1, T8, T9, T12) ✔ · §5.2 job 8:00 idioma servidor, sin ping, sin post roto (T13) ✔ · §5.3 /frase cooldown 30 s (T6–7) ✔ · §6 errores efímeros amables + i18n + EmbedFactory (transversal) ✔ · §7 tests listados (el caso 401→refresh vive en ApiClientTest; justificado en T5) ✔ · §9 despliegue (abajo) ✔.
2. **Placeholders:** ninguno; todo paso con código lo trae completo.
3. **Consistencia de tipos:** `Filtros` (T10) es lo que parsea el listener (T11); `construirEmbed(Locale, EjercicioDTO, Frase)` (T12) es lo que llama el job (T13); `EjercicioService.TAMANO_PAGINA` usado en T10; constructores de test (`EjercicioService(api, ttl, esperaBase, reloj)`, `EjercicioDiaService(repo, service, azar, reloj)`) definidos en sus tareas.

## Al terminar (avisar al usuario — no preguntar, informar)

**Despliegue necesario:** reiniciar el bot (registra `/ejercicios`, `/ejercicio-dia`, `/frase` y aplica la V24; arranca el job) **y `/setup` normal** (refresca las intros de canal). **No hace falta `desde_cero`.** Queda **pendiente de smoke test manual** en el servidor de pruebas: los 3 comandos, botones/menú del paginador, y el post de las 8:00 (o forzarlo llamando a `publicar()` en local / esperando al día siguiente). Verificar también que `.env` real tiene `BOT_SERVICE_USER`/`BOT_SERVICE_PASSWORD` (el `.env.example` ya los lista).
