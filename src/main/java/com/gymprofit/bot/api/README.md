# `api/`

**Español**

Cliente Retrofit2 + OkHttp3 + Gson hacia la API GymProFit, con una interfaz por dominio
(ejercicios, rutinas, logros, sesiones, mediciones, admin, discord). El bot **nunca** toca la BD
de la app: todo pasa por aquí. El access token se **cachea** y solo se renueva ante 401; ante
429 se respeta `Retry-After` con backoff (SPEC §4.1). Toda llamada envía `Accept-Language`.
Se testea con OkHttp `MockWebServer`.

_Ejemplo:_ `EjerciciosApi.listar(grupo, dificultad)` con header `Accept-Language: es`.

---

**English**

Retrofit2 + OkHttp3 + Gson client for the GymProFit API, one interface per domain (exercises,
routines, achievements, sessions, measurements, admin, discord). The bot **never** touches the
app DB: everything goes through here. The access token is **cached** and only refreshed on 401;
on 429 it honors `Retry-After` with backoff (SPEC §4.1). Every call sends `Accept-Language`.
Tested with OkHttp `MockWebServer`.

_Example:_ `EjerciciosApi.listar(group, difficulty)` with header `Accept-Language: es`.
