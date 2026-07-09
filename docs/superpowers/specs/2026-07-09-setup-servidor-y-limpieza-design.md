# Diseño — `/setup` (montaje del servidor) y `/limpiar` (limpieza)

Fecha: 2026-07-09 · Fase: F1 (utilidades de administración) · Estado: aprobado para plan

## Objetivo

Dar al staff dos herramientas de administración del servidor de Discord de GymProFit:

1. **`/setup`**: monta de una vez la estructura completa del servidor (roles, categorías,
   canales y permisos), deja los canales limpios y **autorrellena `config_servidor`** para que
   el resto de módulos (bienvenida, ejercicio del día, tickets…) funcionen sin configurar a mano.
2. **`/limpiar`**: purga los mensajes recientes del canal actual, re-ejecutable cuando el staff
   quiera.

Ambos comparten un `LimpiezaService`. El blueprint del servidor vive en un `SetupServidorPlan`
(datos), separado de las llamadas a JDA, para poder testear la definición sin conexión.

## Decisiones (acordadas con el usuario)

- Creación del servidor: **comando `/setup`** (lo ejecuta el staff; el bot crea todo).
- Alcance: **todos los canales F1–F4** desde el principio (algunos "dormidos" hasta su fase).
- Idioma del servidor: **español**.
- Modo **Comunidad** de Discord: **sí** (activación manual por el owner).
- Limpieza: **solo purga reciente** (mensajes < 14 días; límite de Discord). Sin vaciado por
  clonación.
- `/setup` **purga los mensajes recientes** de los canales existentes al montar.
- Servidor **público** (perfil no privado); onboarding vía modo Comunidad.

## Límite de Discord (clave)

El borrado en bloque (`purgeMessages`) solo elimina mensajes de **< 14 días**. Los más antiguos
no se tocan. No implementamos vaciado total por clonación (cambiaría el ID del canal y obligaría
a re-mapear `config_servidor`); si algún día se quiere, sería una ampliación aparte.

## Blueprint del servidor

### Roles (creados por `/setup` si no existen)

El bot solo crea roles **por debajo del suyo** y solo puede otorgar permisos que **él mismo
posee** (no tiene Administrador ni Expulsar). Por eso los roles de jerarquía staff se crean como
**identidad/color** y el owner les asigna los permisos reales y los sube de posición (pasos
manuales). Los de objetivo quedan por debajo del bot, asignables por la bienvenida.

Jerarquía objetivo (de arriba a abajo): 👑 Fundador › 🛡️ Admin › 🧹 Staff › 🤖 GymProBot ›
💎 Booster › 🏅 veteranía › objetivos › 📣/🎯 › 🤝 Miembro › 🔇 Silenciado › `@everyone`.

| Rol | Permisos que aplica `/setup` | Notas |
|---|---|---|
| 👑 Fundador | — (color) | tuyo; el owner lo sube arriba (manual) |
| 🛡️ Admin | — (color) | el owner le da **Administrador** y lo sube (manual) |
| 🧹 Staff / Mod | Ver categoría STAFF (overwrite) | el owner añade Gestionar mensajes/Expulsar/Moderar y lo sube por encima del bot (manual) |
| 💎 Booster | *(no se crea)* | rol nativo de Discord; se estiliza a mano |
| 🏅 Leyenda / Veterano / Habitual / Novato | — | veteranía cosmética (por ahora manual/futuro) |
| 💪 Fuerza · 🏃 Cardio · ⚖️ Pérdida de peso · 🌟 General | — | objetivo (auto-rol bienvenida) |
| 📣 Avisos · 🎯 Retos | — (mencionables) | opt-in de notificación (Onboarding) |
| 🤝 Miembro | — | se da en onboarding; **no** oculta canales (server público) |
| 🔇 Silenciado | overwrites en todos los canales: deny Enviar mensajes, Añadir reacciones, Hablar | moderación |

### Categorías y canales (separador `・`)

| Categoría | Canales | Permisos |
|---|---|---|
| 📢 INFORMACIÓN | `👋・bienvenidas` · `📜・reglas` · `📣・anuncios` · `🗺️・cómo-funciona` | `@everyone`: ver sí, escribir no (reacciones sí); staff escribe |
| 💬 COMUNIDAD | `💬・general` · `👋・presentaciones` · `🏋️・progresos` · `📸・fotos` · `🍎・nutrición` · `🎧・off-topic` | `@everyone`: escribir sí |
| 🎮 GAMIFICACIÓN | `🏆・logros` · `📊・ranking` · `🪙・economía` · `🧠・trivia` · `⚔️・duelos` · `🤖・comandos-bot` | escribir sí; `comandos-bot` slowmode 5 s |
| 🏋️ ENTRENO | `🗓️・ejercicio-del-día` · `💡・sugerencias` · `🎯・retos` · `🎫・soporte` | escribir sí |
| 🔊 VOZ | `🔊 General` · `🏋️ Sala de entreno` · `🎶 Chill` (canales de voz) | por defecto |
| 🔒 STAFF | `🛠️・staff-chat` · `🤖・bot-logs` · `📋・moderación` | `@everyone`: sin acceso; Staff: ver; bot: ver |
| 🎫 TICKETS | (vacía; el bot crea aquí los canales de ticket) | `@everyone`: sin acceso |

### Autoconfiguración de `config_servidor`

Al terminar, `/setup` guarda en `config_servidor` (vía `ConfigServidorService`):

| Campo | Canal / rol |
|---|---|
| `canal_bienvenida` | `👋・bienvenidas` |
| `canal_ejercicio_dia` | `🗓️・ejercicio-del-día` |
| `canal_logros` | `🏆・logros` |
| `canal_sugerencias` | `💡・sugerencias` |
| `canal_soporte` | `🎫・soporte` |
| `canal_bot_logs` | `🤖・bot-logs` |
| `rol_objetivo_fuerza/cardio/perdida_peso/general` | roles de objetivo creados |

## Comando `/setup`

- **Permisos**: staff — `DefaultMemberPermissions.enabledFor(ADMINISTRATOR)`; `guildOnly`.
- **Requiere** que el bot tenga *Gestionar roles* y *Gestionar canales* (ya los tiene).
- **Idempotente**: busca por nombre; si un rol/categoría/canal ya existe, lo reutiliza (no
  duplica). Re-ejecutable sin romper nada.
- **Flujo** (con `deferReply`, es largo):
  1. **Limpieza**: purga mensajes recientes de los canales de texto existentes.
  2. Crea/reutiliza **roles**.
  3. Crea/reutiliza **categorías y canales**, aplicando permisos y slowmode.
  4. **Autoconfig**: resuelve IDs y guarda en `config_servidor`.
  5. Responde un **embed resumen** (creado / reutilizado / limpiado) y registra en `bot-logs`.
- **Errores**: si falta un permiso o falla una llamada, se captura, se registra y el resumen lo
  refleja (no aborta a medias sin avisar).

## Comando `/limpiar`

- **Permisos**: staff — `DefaultMemberPermissions.enabledFor(MANAGE_MESSAGES)`; `guildOnly`.
- **Opción**: `cantidad` (entero, 1–1000, requerido).
- **Comportamiento**: purga los últimos `cantidad` mensajes del canal actual (bulk, < 14 días).
  `deferReply` efímero; al terminar responde cuántos borró (embed rojo, categoría moderación) y
  registra la acción en `bot-logs`.
- **Límite**: los mensajes de > 14 días no se borran; el resumen lo indica si aplica.

## Componentes nuevos

| Componente | Responsabilidad |
|---|---|
| `services/LimpiezaService` | Purga de mensajes recientes de un canal; devuelve cuántos borró. Testeable. |
| `services/SetupServidorPlan` | Blueprint (datos) del servidor: roles, categorías, canales, permisos, mapeo a `config`. Sin JDA. Testeable. |
| `commands/admin/SetupComando` | Ejecuta el plan contra JDA (crea/reutiliza) y autoconfigura. |
| `commands/moderacion/LimpiarComando` | `/limpiar` sobre el canal actual. |

Reutiliza `ConfigServidorService` (autoconfig) y `EmbedFactory` (resúmenes). Textos por i18n
ES/EN.

## Pasos manuales (no los puede hacer el bot / requieren owner)

1. Renombrar el servidor a **GymProFit** y subir el **icono** (logo).
2. **Activar modo Comunidad** (Ajustes → Habilitar Comunidad): elegir canal de reglas
   (`📜・reglas`) y de novedades (`📣・anuncios`); verificación **Media**; filtro de contenido
   explícito **On**.
3. Configurar **Onboarding**: preguntas que asignen los roles opt-in (📣 Avisos, 🎯 Retos) y
   guíen a los canales clave.
4. Activar **AutoMod nativo** (spam, spam de menciones, palabras) como primera barrera.
5. Subir la jerarquía y dar permisos reales: **👑 Fundador** y **🛡️ Admin** (con
   *Administrador*) por encima del bot; **🧹 Staff** por encima del bot con
   *Gestionar mensajes / Expulsar / Moderar miembros*. Estilizar el rol nativo **💎 Booster**.
6. Pegar los **textos de `📜・reglas` y `🗺️・cómo-funciona`** (los entrega el bot/documentación).
7. Perfil del servidor **público**; descripción y 5 rasgos con emoji (ya definidos).

## Testing

- `LimpiezaService`: test con Testcontainers no aplica (es JDA); se extrae la lógica pura
  testeable posible (p. ej. cálculo de "cuántos se pueden borrar") o se cubre con mocks de JDA
  donde tenga sentido; lo demás queda como **smoke test manual**.
- `SetupServidorPlan`: test unitario del blueprint (número de canales por categoría, mapeo a
  `config`, unicidad de nombres).
- Ejecución en vivo de `/setup` y `/limpiar`: **smoke test manual** en el servidor de pruebas.

## Fuera de alcance

- Vaciado total por clonación (cambio de ID + re-mapeo).
- Server Discovery (requisitos de tamaño), banner y vanity URL (requieren boosts).
- Roles-recompensa por nivel (se abordan en F2 con la economía).
- Contenido dinámico de onboarding vía API (se hace manual en la UI de Discord).
