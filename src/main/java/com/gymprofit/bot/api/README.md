# `api/`

**Español**

Cliente Retrofit2 + OkHttp3 + Gson hacia la API GymProFit. El bot **nunca** toca la BD de la
app: todo pasa por aquí. Contenido real de la capa:

- **`ApiClient`** — punto único de acceso: monta Retrofit+OkHttp con timeouts de **60 s**
  (Render free duerme y el primer request puede tardar ~50 s), un interceptor que añade el
  `Authorization: Bearer` de la cuenta de servicio a todo lo que no sea `/auth/`, y un
  `Authenticator` que ante un **401** renueva el token y reintenta la petición original una
  sola vez (cuenta los **401 de la cadena**, no cualquier `priorResponse`: un redirect por
  medio no debe dejar el token sin renovar). Un `callTimeout` acota la llamada lógica
  completa (login + petición + refresh + reintento). Expone un **executor propio de 4 hilos
  daemon**: las llamadas pueden tardar 60 s y jamás deben ejecutarse en el hilo del gateway
  de JDA. Es `AutoCloseable`: **`cerrar()`** apaga el executor y libera dispatchers y pools
  de conexiones (al apagar el bot y al final de cada test).
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
consulta+idioma. Detalles que conviene saber al usarlo:

- La espera entre reintentos se **recorta a 10 s** y no se duerme tras el último intento:
  detrás hay una interacción de Discord que no puede esperar más.
- Un fallo de red **durante el login perezoso** llega como `ApiException` con causa
  `IOException` (OkHttp no envuelve las unchecked de un interceptor) y se reintenta igual que
  un 5xx; unas credenciales rechazadas fallan al primer intento, sin gastar reintentos.
- La caché está **acotada** (500 entradas, purga de caducadas al desbordar: la clave lleva
  texto que teclea el usuario) y es de **vuelo único** — si diez personas lanzan la misma
  consulta con la API dormida, sale **una** petición y las demás se enganchan a ella.
- `listarTodos` **no se cachea a propósito**: solo lo usa el sorteo diario, una vez al día.

Se testea con OkHttp `MockWebServer`.

_Ejemplo:_

```java
ApiClient cliente = new ApiClient(baseUrl, usuario, password); // uno por bot; cerrar al apagar
EjercicioService ejercicios = new EjercicioService(cliente.ejercicios());

// La llamada BLOQUEA hasta 60 s: siempre en cliente.executor(), nunca en el hilo de eventos.
evento.deferReply().queue();
cliente.executor().submit(() -> {
    try {
        PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar("press", "PECHO", null, 0, "es");
        evento.getHook().editOriginalEmbeds(embeds.catalogo(pagina)).queue();
    } catch (ApiException e) {
        evento.getHook().editOriginal(mensajes.get("api.no.disponible")).queue();
    }
});
```

---

**English**

Retrofit2 + OkHttp3 + Gson client for the GymProFit API. The bot **never** touches the app
DB: everything goes through here. Actual layer contents:

- **`ApiClient`** — single access point: builds Retrofit+OkHttp with **60 s** timeouts
  (Render free sleeps and the first request can take ~50 s), an interceptor adding the
  service account's `Authorization: Bearer` to everything but `/auth/`, and an
  `Authenticator` that on a **401** refreshes the token and retries the original request
  exactly once (it counts the **401s in the chain**, not any `priorResponse`: a redirect in
  between must not leave the token unrefreshed). A `callTimeout` bounds the whole logical
  call (login + request + refresh + retry). Exposes its **own 4-thread daemon executor**:
  calls can take 60 s and must never run on the JDA gateway thread. It is `AutoCloseable`:
  **`cerrar()`** shuts the executor down and releases dispatchers and connection pools.
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
but in `services/EjercicioService`, together with the per-query+language **TTL cache**. Worth
knowing:

- The wait between retries is **capped at 10 s** and is skipped after the last attempt: a
  Discord interaction is waiting behind it.
- A network failure **during the lazy login** surfaces as an `ApiException` caused by an
  `IOException` (OkHttp does not wrap unchecked exceptions thrown by an interceptor) and is
  retried like a 5xx; rejected credentials fail on the first attempt.
- The cache is **bounded** (500 entries, expired ones purged on overflow: the key contains
  user-typed text) and **single-flight** — ten people running the same query against a
  sleeping API produce **one** request.
- `listarTodos` is **deliberately uncached**: only the daily draw uses it, once a day.

Tested with OkHttp `MockWebServer`.

_Example:_

```java
ApiClient client = new ApiClient(baseUrl, user, password); // one per bot; close on shutdown
EjercicioService exercises = new EjercicioService(client.ejercicios());

// The call BLOCKS for up to 60 s: always on client.executor(), never on the event thread.
event.deferReply().queue();
client.executor().submit(() -> {
    try {
        PaginaDTO<EjercicioDTO> page = exercises.buscar("press", "PECHO", null, 0, "en");
        event.getHook().editOriginalEmbeds(embeds.catalogo(page)).queue();
    } catch (ApiException e) {
        event.getHook().editOriginal(messages.get("api.no.disponible")).queue();
    }
});
```
