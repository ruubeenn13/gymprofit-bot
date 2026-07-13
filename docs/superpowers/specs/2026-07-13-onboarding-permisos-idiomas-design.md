# Onboarding, matriz de permisos declarativa e integración bilingüe — Diseño

**Fecha:** 2026-07-13
**Estado:** aprobado (pendiente plan de implementación)
**Ámbito:** `SetupComando`, `SetupServidorPlan`, i18n. Fase 1 (Núcleo).

## Objetivo

Cerrar el último hueco manual del montaje del servidor: el **onboarding de Discord**
(canales predeterminados + preguntas de personalización + roles/canales por respuesta), y de
paso profesionalizar el **modelo de permisos por canal**, hoy demasiado grueso
(`@everyone` / Staff / Silenciado). Se añade además **integración bilingüe** ES/EN sin
fragmentar la comunidad.

Todo queda automatizado por `/setup` e **idempotente** (reejecutable sin romper nada).

## Contexto y problema

- `SetupServidorPlan` describe roles, categorías y canales; `SetupComando` los crea/reutiliza.
- Memoria previa daba el onboarding por **no automatizable**: es **incorrecto**. Existe el
  endpoint REST `PUT /guilds/{guild.id}/onboarding`; JDA 5.6.1 no lo envuelve, pero se puede
  invocar por REST cruda (`RestActionImpl` + `Route.custom`). Requiere `MANAGE_GUILD` +
  `MANAGE_ROLES` (el bot ya es Administrador).
- **Bug de permisos detectado:** la factoría `anuncios()` crea los canales News con
  `soloLectura=false`, y las categorías normales no deniegan `MESSAGE_SEND` a `@everyone`.
  Resultado: `📣・anuncios`, `📲・novedades-app` (y los nuevos `📅・eventos` / `🎁・sorteos`)
  quedan **escribibles por cualquiera**. Un canal de anuncios debe publicarlo solo el staff.
- El modelo de permisos ignora roles funcionales (Coach, Nutricionista, Ponente, Creador,
  Vinculado…): ningún canal les da permisos específicos.

## Restricciones de la plataforma

- **Onboarding con `enabled=true`:** mínimo **7 canales predeterminados**, y al menos **5**
  deben permitir enviar mensajes a `@everyone`.
- **El texto del onboarding es un único set**, no se localiza por idioma del usuario. Por eso
  los títulos/descripciones de las preguntas van **bilingües** (ES + EN en el mismo texto).
- Al crear/actualizar una opción de prompt hay que usar `emoji_id`/`emoji_name`/`emoji_animated`,
  no un objeto emoji.

## Diseño

### 1. Modelo de permisos declarativo (núcleo del cambio)

Extender los records del plan para que **cada canal/categoría declare sus overrides por rol**:

- Nuevo record `PermisoRol(String rolNombre, long allow, long deny)`. El rol se referencia
  **por nombre** y se resuelve a ID en `/setup` desde el mapa `roles` ya existente.
- `CanalPlan` y `CategoriaPlan` ganan `List<PermisoRol> permisos`.
- Builders fluidos sobre `CanalPlan`:
  - `.permite(String rol, Permission... p)` → añade allow.
  - `.niega(String rol, Permission... p)` → añade deny.
  - `.soloLectura()` → azúcar de `.niega("@everyone", MESSAGE_SEND).permite("🧹 Staff", MESSAGE_SEND)`.
    (Sustituye al flag booleano `soloLectura` actual.)
- `SetupComando` aplica los overrides **tras crear y sincronizar** el canal, dentro de
  `aplicarConfig` (o método análogo), de modo que corre **igual en la ruta de creación y en la
  de reutilización** → idempotente. Los roles inexistentes en el mapa se ignoran con `log.warn`.

`@everyone` se referencia con la constante del rol público; Staff/Admin/Fundador por su nombre
del plan.

### 2. Matriz de permisos por categoría (baseline; los canales afinan)

| Categoría        | `@everyone`             | Roles con permiso extra                                   |
|------------------|-------------------------|----------------------------------------------------------|
| INFORMACIÓN      | ver, **no** SEND        | Staff: SEND                                               |
| COMUNIDAD        | ver + SEND              | —                                                        |
| FITNESS          | ver + SEND              | 🧑‍🏫 Coach / 🍎 Nutricionista: SEND + gestionar hilos    |
| GAMIFICACIÓN     | ver; SEND solo en `🤖・comandos-bot` | 🤖 Bots: SEND en todos                       |
| EVENTOS          | ver, **no** SEND        | Staff: SEND; 🎙️ Ponente: hablar en `🎤 Escenario`        |
| AYUDA            | ver + SEND              | Staff: gestionar                                         |
| STAFF / TICKETS  | **oculto** (sin VIEW)   | Staff+ : ver + SEND                                       |
| VOZ              | ver + conectar          | 🎙️ Ponente: hablar en Escenario                          |
| SERVER STATS     | ver, sin conectar (voz) | —                                                        |

Silenciado mantiene su `deny` global (ya implementado). Categorías ocultas: sin cambios.

### 3. Anuncios en solo-lectura (arreglo del bug)

`📣・anuncios`, `📲・novedades-app`, `📅・eventos`, `🎁・sorteos` pasan a **solo-lectura**
(`@everyone` no envía; Staff sí). Publica el staff y hace `@mención` al rol opt-in
correspondiente.

### 4. Canales y roles nuevos

- **Canales** (categoría EVENTOS, tipo News, solo-lectura, con intro i18n):
  `📅・eventos` (ping `@📅 Eventos`), `🎁・sorteos` (ping `@🎁 Sorteos`). *(Ya añadidos al plan.)*
- **Roles nuevos (7):**
  - Experiencia: `🌱 Principiante`, `💪 Intermedio`, `🔥 Avanzado`.
  - Idioma: `🇪🇸 Español`, `🇬🇧 English`.
  - Notificaciones: `📅 Eventos`, `🎁 Sorteos`.
  *(Ya añadidos a `SetupServidorPlan.ROLES` en sus secciones.)*

### 5. Integración bilingüe (sin fragmentar)

Decisión: **rol de idioma con bandera + canales compartidos**. Un solo juego de canales; se
escribe en cualquier idioma; el bot ya responde en el idioma de cada usuario (i18n). El rol de
idioma es cosmético y sirve para **pings segmentados** (p. ej. anunciar en inglés mencionando
`@🇬🇧 English`). **No** se duplican canales por idioma (mataría la actividad de una comunidad
que arranca); si en el futuro crece la comunidad EN, se puede añadir *un* `🌍・english-chat`.

### 6. Onboarding — preguntas

Se aplica con `PUT /guilds/{id}/onboarding`, `enabled=true`, `mode` por defecto. Cada opción
lleva `role_ids` y/o `channel_ids`. IDs de rol y canal se toman de los mapas que `/setup` ya
construye (`roles` e `idsPorNombre`).

**P1 · 🌍 Idioma / Language** — `single_select`, **required**, `in_onboarding`
| Opción | Descripción | Rol | Canal |
|---|---|---|---|
| 🇪🇸 Español | Hablo español / I speak Spanish | 🇪🇸 Español | — |
| 🇬🇧 English | I speak English / Hablo inglés | 🇬🇧 English | — |

**P2 · 🎯 ¿Cuál es tu objetivo? / Your goal?** — `single_select`, **required**, `in_onboarding`
| Opción | Descripción | Rol | + Canal |
|---|---|---|---|
| 💪 Fuerza | Levantar más, ganar músculo y potencia | 💪 Fuerza | 📚・rutinas |
| 🏃 Cardio | Resistencia, correr y salud cardiovascular | 🏃 Cardio | 📚・rutinas |
| ⚖️ Pérdida de peso | Definir, quemar grasa y sentirte mejor | ⚖️ Pérdida de peso | 🍎・nutrición |
| 🌟 General | Fitness completo sin un foco único | 🌟 General | — |

**P3 · 🏋️ ¿Tu nivel en el gym? / Your level?** — `single_select`, opcional, `in_onboarding`
| Opción | Descripción | Rol | + Canal |
|---|---|---|---|
| 🌱 Principiante | Empezando o menos de 1 año entrenando | 🌱 Principiante | ❓・dudas |
| 💪 Intermedio | 1–3 años, técnica ya asentada | 💪 Intermedio | — |
| 🔥 Avanzado | +3 años, entreno serio y constante | 🔥 Avanzado | — |

**P4 · 📣 ¿Qué avisos quieres? / Notifications?** — `multi_select`, opcional, `in_onboarding`
| Opción | Descripción | Rol |
|---|---|---|
| 📣 Anuncios | Novedades importantes del servidor | 📣 Avisos |
| 🎯 Retos | Aviso cuando empieza un reto | 🎯 Retos |
| 📅 Eventos | Quedadas, directos y eventos en vivo | 📅 Eventos |
| 🎁 Sorteos | Te avisamos de cada sorteo | 🎁 Sorteos |

**P5 · 📌 ¿Qué te interesa? / Interests?** — `multi_select`, opcional, `in_onboarding` — solo canales
| Opción | Descripción | Canal |
|---|---|---|
| 🏋️ Rutinas | Planes de entreno y divisiones | 📚・rutinas |
| 🍎 Nutrición | Dietas, recetas y suplementación | 🍎・nutrición |
| 📈 Progresos | Comparte tu antes/después y PRs | 📈・progresos |
| 🎯 Retos | Únete a los retos de la comunidad | 🎯・retos |

Los títulos y descripciones van **bilingües** (ES + EN en el mismo string).

### 7. Canales predeterminados (`default_channel_ids`)

Nueve canales (≥7, con ≥5 escribibles):

- **Escribibles (5):** `💬・general`, `👋・presentaciones`, `🎧・off-topic`, `🤖・comandos-bot`,
  `🗓️・ejercicio-del-día`.
- **Solo lectura (4):** `📣・anuncios`, `📜・reglas`, `🚀・empieza-aquí`, `🏆・logros`.

Los canales de intereses (`rutinas`, `nutrición`, `progresos`, `retos`) **no** son
predeterminados: entran por la respuesta de la P5.

## Enfoque técnico del onboarding (REST cruda)

JDA no expone onboarding. Se implementa un pequeño helper que construye el JSON del body
(`prompts`, `default_channel_ids`, `enabled`, `mode`) y lo envía con
`Route.custom(Method.PUT, "guilds/{guild_id}/onboarding")` sobre un `RestActionImpl`, lo que
reutiliza autenticación y control de rate limit de JDA. Se dispara al **final** de `/setup`,
tras crear roles y canales (ya se dispone de sus IDs). El PUT reemplaza toda la config →
idempotente. Si falla, se registra `log.warn` y `/setup` no se rompe (patrón ya usado).

## Orden de construcción de los IDs de emoji

Las opciones usan emojis Unicode (banderas, iconos) → `emoji_name` con el carácter y
`emoji_id=null`.

## Testing

- **`SetupServidorPlanTest`:** aserciones nuevas para los overrides declarativos (que
  `soloLectura()` genera deny SEND a `@everyone` y allow a Staff; que los canales de anuncios
  son solo-lectura; que existen los 7 roles nuevos).
- **Test del builder de onboarding:** construir el JSON body a partir del plan y verificar
  estructura (nº de prompts, flags single/required, que cada opción referencia roles/canales
  esperados, ≥7 default channels con ≥5 escribibles). Sin llamar a Discord.
- Lo que exige Discord en vivo (aplicar el PUT, ver las preguntas) queda como **smoke test
  manual** en el servidor de pruebas.
- `./mvnw verify` en verde con evidencia real.

## Fuera de alcance

- Duplicar canales por idioma (descartado por fragmentación).
- Canal exclusivo de `📲 Vinculado` (depende de la API, Fase 3).
- Lógica Join-To-Create de voz (pendiente aparte).

## Cambios ya aplicados en esta sesión (sin commit)

- `SetupServidorPlan.ROLES`: +7 roles (secciones EXPERIENCIA, IDIOMA, y Eventos/Sorteos en
  NOTIFICACIONES).
- `SetupServidorPlan` categoría EVENTOS: +`📅・eventos` y `🎁・sorteos` (News, con intro).
- `messages_es.properties` / `messages_en.properties`: claves `intro.eventos` e `intro.sorteos`.
- `SetupComando` / `Main`: `/setup` ya no borra mensajes (fix previo, commit `6d851d3`).
