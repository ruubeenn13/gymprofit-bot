# Consultas a la API: `/ejercicios` y ejercicio del día — diseño

- **Fecha:** 2026-07-20
- **Fase:** F1 Núcleo (SPEC §5), módulos «Consultas a la app» y «Ejercicio del día»
- **Estado:** aprobado por el usuario, pendiente de plan de implementación

## 1. Problema

Los paquetes `api/` y `jobs/` están vacíos desde la Fase 0 porque los dos módulos que faltan de F1
dependen de la API de GymProFit, y hasta hoy no había ni URL ni credenciales. Ya las hay:

- **URL:** `https://gymprofit-api.onrender.com/api` (Render, plan free).
- **Cuenta de servicio:** `gymprobot` (rol ADMIN), credenciales en el `.env` del bot
  (`BOT_SERVICE_USER` / `BOT_SERVICE_PASSWORD`).
- **Catálogo:** **873 ejercicios** verificados en producción.

Con eso se pueden cerrar los dos módulos. `/rutinas` **queda fuera** (ver §8).

## 2. Alcance

Entra todo el módulo, construido por fases dentro de un mismo plan:

1. Capa `api/`: cliente REST con login, refresh y reintentos.
2. `/ejercicios`: consulta paginada del catálogo con ficha de detalle.
3. `/ejercicio-dia` + `EjercicioDiaJob`: publicación diaria a las 8:00.
4. `/frase`: el banco de frases, que ya usa el post diario, expuesto como comando.

## 3. Arquitectura

```
api/       ApiClient · TokenManager · AuthApi · EjerciciosApi · dtos/
services/  EjercicioService (caché + reintento) · EjercicioDiaService (elección diaria)
commands/consultas/  EjerciciosComando · EjercicioDiaComando · FraseComando
events/    EjerciciosPaginadorListener
jobs/      EjercicioDiaJob
db/        EjercicioDiaRepositorio · FraseRepositorio
```

Reglas de dependencia, para que cada pieza se pueda probar sola:

- **`api/` es la única puerta a la API.** Nadie más construye peticiones HTTP.
- **`services/` no conoce JDA.** Recibe y devuelve datos; se testea con `MockWebServer`.
- **`commands/` y `events/` no conocen HTTP.** Piden al service y pintan embeds.

Retrofit2 + OkHttp3 + Gson y `MockWebServer` **ya están en el `pom.xml`** (ADR-006 y ADR de
`docs/decisions.md`); no hace falta ninguna dependencia nueva.

### 3.1 Autenticación (SPEC §4.1)

`TokenManager` guarda el access token en memoria y lo renueva **solo ante un 401**, no por reloj:

- Login: `POST /auth/login {username,password}` → `{token, refreshToken, username, roles}`.
- Cabecera `Authorization: Bearer <token>` vía interceptor de OkHttp.
- Access token de **30 min**, refresh de 30 días. Ante 401: `POST /auth/refresh`; si también falla,
  login completo.
- El refresh está **serializado** (un solo hilo lo hace): si tres comandos chocan con un 401 a la
  vez, se renueva una vez y los tres reutilizan el token nuevo.
- Las credenciales salen de `BotConfig` (env vars). **Nunca** se loguean.

### 3.2 Resiliencia (Render free duerme)

El servicio se duerme por inactividad y el primer request tarda hasta ~50 s. Por eso:

- **Timeouts de 60 s** (conexión y lectura), muy por encima de lo normal a propósito.
- Todos los comandos hacen `deferReply()` antes de llamar: Discord da 15 min en vez de 3 s.
- **Reintento con espera creciente** ante error de red o 5xx; ante **429** se respeta `Retry-After`.
- **Caché en memoria con caducidad** en `EjercicioService`: la clave es la consulta
  (filtros + página + idioma) y el valor, la respuesta ya mapeada. TTL corto (minutos): el catálogo
  cambia poco y así la segunda persona que consulta no espera.
- El job de las 8:00 hace de **despertador diario** de la API.

## 4. `/ejercicios`

```
/ejercicios [grupo] [dificultad] [buscar]
```

Responde con un embed de **8 por página** y una fila de botones `◀ ▶`, más un **menú desplegable**
con los ejercicios de esa página. Al elegir uno, el mismo mensaje pasa a la **ficha completa**
(imagen, grupo, músculo primario, dificultad, calorías, equipo, instrucciones) con un botón
**Volver a la lista**.

Endpoint: `GET /ejercicios/buscar?q=&grupoMuscular=&dificultad=&page=&size=` → `PageDTO`
(`content`, `page`, `size`, `totalElements`, `totalPages`, `last`). El idioma va en
`Accept-Language` según el locale de quien pregunta.

**Estado en el `customId`**, siguiendo el patrón de `/modlogs`
(`ejercicios:<ownerId>:<pag>:<grupo>:<dif>:<q>`), en vez de un registro en memoria: los botones
siguen funcionando aunque el bot se reinicie. Como el `customId` de Discord admite 100 caracteres,
**el texto de búsqueda se trunca a 40** al construirlo. Solo el dueño puede pulsar sus botones
(`ejercicios.noestuyo`), como en el resto del bot.

Respuesta **pública** (regla 13 de `rules/coding-rules.md`): consultar el catálogo no tiene nada de
sensible y así lo aprovecha quien pase por el canal.

Con `/ejercicio-dia` y `/frase` (§5.3) son **tres comandos nuevos de nivel superior**: el total pasa
de 56 a **59**, con 41 huecos libres del límite de 100 de Discord (ADR-011). No se agrupan en una
familia porque no comparten flujo: buscar en el catálogo, ver el post del día y soltar una frase son
tres cosas distintas.

## 5. Ejercicio del día

### 5.1 Elección

Migración **V24**, tabla `ejercicio_dia`:

| Columna | Tipo | Papel |
|---|---|---|
| `fecha` | `DATE` PK | Día natural (Europe/Madrid) |
| `ejercicio_id` | `INT` | Id del ejercicio en la API |
| `ronda` | `INT` | Vuelta al catálogo (empieza en 1) |
| `publicado_en` | `TIMESTAMP` | Cuándo se publicó |

Cada día se sortea entre los ejercicios que **no han salido en la ronda actual**; cuando se agotan
los 873, empieza la ronda siguiente. Así no se repite hasta haberlos visto todos.

`fecha` como clave primaria da idempotencia gratis: si el job se ejecuta dos veces (reinicio,
despliegue), el segundo intento encuentra la fila y no vuelve a publicar.

`/ejercicio-dia` lee la fila de hoy y, **si el job aún no ha corrido, la crea**: el comando y la
publicación siempre coinciden.

### 5.2 Publicación

`EjercicioDiaJob` arranca a las **8:00 Europe/Madrid** y se repite cada 24 h. Publica en el
`canal_ejercicio_dia` de cada servidor configurado (lo guarda `/setup` en `config_servidor`), en
**el idioma del servidor** — no el de un usuario concreto, porque el post es para todos.

El embed lleva la ficha del ejercicio y, debajo, una **frase motivadora** del banco propio: la tabla
`frases` ya está sembrada en ES/EN desde la V2 y solo falta un `FraseRepositorio` mínimo
(`aleatoria()`). Va en naranja de marca (SPEC §7).

**Sin mención de rol.** Es un canal al que la gente entra queriendo; un ping diario a las 8:00 quema
más que aporta.

Si la API falla, el job **no publica un post roto**: lo registra y reintenta más tarde.

### 5.3 `/frase`

El banco de frases deja de usarse solo por dentro: `/frase` devuelve una al azar en el idioma de
quien la pide, con su autor si lo tiene (32 frases ES/EN sembradas en la V2, todas de categoría
`MOTIVACION`, así que el comando **no lleva opciones**).

Comparte el `FraseRepositorio` con el post diario, de modo que el coste es un comando y su embed.
Respuesta **pública** y **cooldown de 30 s por usuario** (`util/Cooldown`, el mismo que ya frena el
XP por mensaje): es un comando barato de repetir y sin freno acabaría empapelando el canal.

## 6. Errores e i18n

Ante API caída o timeout, embed de aviso amable («el catálogo no responde ahora mismo, inténtalo en
un momento»), nunca una traza. Los avisos de error van efímeros (regla 13); las consultas correctas,
públicas.

Todo texto sale de `messages_es.properties` / `messages_en.properties`, y todo embed pasa por
`EmbedFactory`: azul de consulta para la lista y la ficha, naranja de marca para el ejercicio del
día.

## 7. Tests

- `EjercicioServiceTest` (**MockWebServer**): respuesta correcta y mapeo del `PageDTO`; **401 →
  refresh → reintento**; 429 respetando `Retry-After`; timeout; y que la **caché evita la segunda
  llamada**.
- `EjercicioDiaServiceTest`: no repite dentro de una ronda, cambia de ronda al agotar el catálogo, y
  el mismo día devuelve siempre el mismo ejercicio.
- `EjerciciosComandoTest`: construcción de la lista y de la ficha (páginas, botones deshabilitados
  en los extremos, truncado de la búsqueda), sin JDA real.
- `FraseRepositorio`: cubierto por el test de migraciones (Testcontainers) y por el test del post
  diario, que necesita una frase para montar el embed.
- `MigracionesTest` se actualiza al añadir la V24.

## 8. Fuera de alcance

- **`/rutinas`**: necesita la **vinculación de cuentas (F3)** para saber qué usuario de la app es
  cada miembro de Discord, y además **hoy no hay ninguna rutina en la BD de producción** (0 filas, 0
  predefinidas). Cuando se haga: `/rutinas/predefinidas` + `/rutinas/usuario/{id}`, mostrando las de
  otro usuario **jamás** sin vinculación.
- **Calculadoras** (`/imc`, `/calorias`, `/macros`, `/rm`): módulo aparte de F1, sin dependencia de
  la API.

## 9. Despliegue

Al terminar: **reiniciar el bot** (registra los tres comandos nuevos y aplica la V24) y **`/setup`
normal** (refresca las intros de canal con los comandos nuevos). No hace falta `desde_cero`.
