# `api/`

**Español**

Cliente Retrofit2 + OkHttp3 + Gson hacia la API GymProFit. El bot **nunca** toca la BD de la
app: todo pasa por aquí. Contenido real de la capa:

- **`ApiClient`** — punto único de acceso: monta Retrofit+OkHttp con timeouts de **60 s**
  (Render free duerme y el primer request puede tardar ~50 s), un interceptor que añade el
  `Authorization: Bearer` de la cuenta de servicio a todo lo que no sea `/auth/`, y un
  `Authenticator` que ante un **401** renueva el token y reintenta la petición original una
  sola vez. Expone un **executor propio de 4 hilos daemon**: las llamadas pueden tardar 60 s
  y jamás deben ejecutarse en el hilo del gateway de JDA.
- **`TokenManager`** — cachea el access token y lo renueva **serializado** (si dos hilos ven
  el 401 a la vez, solo uno renueva); primero intenta el **refresh** y, si también falla, cae
  a un **login** completo con las credenciales.
- **`AuthApi`** / **`EjerciciosApi`** — interfaces Retrofit por dominio (login/refresh y
  catálogo de ejercicios: búsqueda paginada, ficha por id, listado completo). Toda llamada
  de ejercicios envía `Accept-Language` (`es`/`en`): la API devuelve los textos ya localizados.
- **`dtos/`** — records espejo del JSON de la API (`TokenDTO`, `CredencialesDTO`,
  `RefreshDTO`, `EjercicioDTO`, `PaginaDTO<T>`). Nunca se exponen entidades.
- **`ApiException`** — error único de la capa; los comandos lo capturan y muestran el
  mensaje amable de indisponibilidad.

Los **reintentos con backoff** (5xx, red) y el respeto del `Retry-After` de un **429** no
viven aquí sino en `services/EjercicioService`, junto con la **caché TTL** por
consulta+idioma. Se testea con OkHttp `MockWebServer`.

_Ejemplo:_

```java
ApiClient cliente = new ApiClient(baseUrl, usuario, password);
EjercicioService ejercicios = new EjercicioService(cliente.ejercicios());
// Siempre desde cliente.executor(), nunca en el hilo de eventos de JDA:
PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar("press", "PECHO", null, 0, "es");
```

---

**English**

Retrofit2 + OkHttp3 + Gson client for the GymProFit API. The bot **never** touches the app
DB: everything goes through here. Actual layer contents:

- **`ApiClient`** — single access point: builds Retrofit+OkHttp with **60 s** timeouts
  (Render free sleeps and the first request can take ~50 s), an interceptor adding the
  service account's `Authorization: Bearer` to everything but `/auth/`, and an
  `Authenticator` that on a **401** refreshes the token and retries the original request
  exactly once. Exposes its **own 4-thread daemon executor**: calls can take 60 s and must
  never run on the JDA gateway thread.
- **`TokenManager`** — caches the access token and refreshes it **serialized** (if two
  threads hit the 401 at once, only one refreshes); tries the **refresh** first and falls
  back to a full **login** with the credentials if that also fails.
- **`AuthApi`** / **`EjerciciosApi`** — Retrofit interfaces per domain (login/refresh and
  the exercise catalog: paged search, detail by id, full listing). Every exercise call sends
  `Accept-Language` (`es`/`en`): the API returns already-localized texts.
- **`dtos/`** — records mirroring the API JSON (`TokenDTO`, `CredencialesDTO`, `RefreshDTO`,
  `EjercicioDTO`, `PaginaDTO<T>`). Entities are never exposed.
- **`ApiException`** — the layer's single error; commands catch it and show the friendly
  unavailability message.

**Retries with backoff** (5xx, network) and honoring a **429**'s `Retry-After` live not here
but in `services/EjercicioService`, together with the per-query+language **TTL cache**.
Tested with OkHttp `MockWebServer`.

_Example:_

```java
ApiClient client = new ApiClient(baseUrl, user, password);
EjercicioService exercises = new EjercicioService(client.ejercicios());
// Always from client.executor(), never on the JDA event thread:
PaginaDTO<EjercicioDTO> page = exercises.buscar("press", "PECHO", null, 0, "en");
```
