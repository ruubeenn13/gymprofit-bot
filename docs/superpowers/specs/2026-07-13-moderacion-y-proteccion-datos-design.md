# Moderación, auditoría y protección de datos (RGPD) — Diseño

**Fecha:** 2026-07-13
**Estado:** aprobado (implementación por fases)
**Ámbito:** nuevo módulo de moderación + cifrado de campo + herramientas de privacidad. Fase 1.

## Objetivo

Dotar al bot de **moderación completa** (solo Staff/Admin/Fundador), con **registro de auditoría**
de todas las sanciones, **cifrado** del texto libre con posible dato personal y **herramientas de
privacidad RGPD** (acceso, portabilidad, olvido, retención). Todo persistido en `gymprofit_bot`,
paginado y con la mínima superficie legal.

## Principios de protección de datos (RGPD)

El bot **no toca datos de la app** (ADR-002); su BD solo guarda IDs de Discord (snowflakes,
pseudónimos) + números + poco texto libre. Medidas:

- **Cifrado en reposo:** ya cubierto por Aiven (disco AES) + TLS en tránsito (`sslMode=REQUIRED`).
- **Minimización:** solo IDs y números; nunca nombres reales, emails ni avatares.
- **Cifrado de campo (AES-GCM):** solo el texto libre que puede contener dato personal:
  `warns.motivo`, `sanciones.motivo`, `sanciones.nick_anterior` y (futuro) transcripts de tickets.
  Los IDs y numéricos van en claro para poder consultar, unir y paginar.
- **Base legal:** interés legítimo (moderar y gamificar la comunidad). Documentado en ADR-009.
- **Derechos del interesado:** `/mis-datos` (acceso/portabilidad), `/borrar-mis-datos` (olvido),
  `/privacidad` + nota en README (transparencia).
- **Retención:** job diario que purga datos viejos por ventanas.

## Componentes

### Cifrado — `util/Cifrador`

AES-256-GCM. Clave de 32 bytes en base64 desde env var **`BOT_CRYPTO_KEY`** (vía `BotConfig`). Cada
valor: IV aleatorio de 12 bytes ‖ ciphertext ‖ tag, todo en base64. API: `String cifrar(String)` /
`String descifrar(String)`; `null` → `null`. Si falta la clave, el bot arranca pero los comandos que
cifran avisan y no persisten texto (degradado seguro). **Perder la clave = no poder descifrar los
motivos** (se documenta en `.env.example` y operación).

Las columnas de texto cifrado pasan a `TEXT` (el base64 del cifrado excede `VARCHAR(500)`).

### Esquema — migración `V4__moderacion_sanciones.sql`

```sql
-- Log unificado de auditoría de toda acción de moderación.
CREATE TABLE sanciones (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    guild_id      BIGINT      NOT NULL,
    discord_id    BIGINT      NOT NULL,     -- sancionado
    moderador_id  BIGINT      NOT NULL,
    tipo          VARCHAR(16) NOT NULL,     -- WARN, MUTE, UNMUTE, TIMEOUT, KICK, BAN, UNBAN, NICK
    motivo        TEXT        NULL,         -- CIFRADO (AES-GCM)
    nick_anterior TEXT        NULL,         -- CIFRADO, solo en NICK
    duracion_seg  BIGINT      NULL,         -- para TIMEOUT/MUTE temporal
    creado_en     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sanciones_usuario (guild_id, discord_id, creado_en)
);
-- warns.motivo pasa a TEXT para alojar el texto cifrado.
ALTER TABLE warns MODIFY motivo TEXT NULL;
```

`warns` (ya existe en V1) sigue siendo la fuente para el **escalado** (cuenta `activo=TRUE`);
`sanciones` es el **historial completo** para `/modlogs` y accountability.

### Capa de datos

- `SancionRepositorio` — insertar, listar por usuario (paginado con `LIMIT/OFFSET`), contar,
  borrar por usuario (olvido).
- `WarnRepositorio` — insertar, contar activos, listar por usuario (paginado), revocar (`activo=
  false`), borrar por usuario.

### Servicio — `ModeracionService`

- `avisar(guild, usuario, moderador, motivo)` → inserta warn (motivo cifrado) + registra en
  `sanciones`, cuenta warns activos y **devuelve la acción de escalado** a aplicar.
- `registrar(...)` para cada tipo de sanción (log en `sanciones`).
- Consultas paginadas para `/warns` y `/modlogs`.
- **Escalado:** 3 warns → timeout 1 h; 5 → timeout 24 h; 7 → ban. Umbrales en constantes.
- Las **acciones JDA** (ban/kick/timeout/rol/nick) las ejecuta cada comando (necesitan el `Guild`);
  el servicio solo persiste y decide el escalado.

### Comandos — `commands/moderacion/`

Gate: helper `esAltoCargo(Member)` (roles 🧹 Staff / 🛡️ Admin / 👑 Fundador por nombre) + además
`setDefaultPermissions` a un permiso de moderación para ocultarlos en la UI. El bot valida que
**tiene el permiso Discord** antes de actuar y que **no** modera a alguien de rango ≥ (jerarquía).
Toda acción se registra en `sanciones` y se **loguea en `🤖・bot-logs`** (embed tono serio).

| Grupo | Comandos |
|---|---|
| Avisos | `/warn` · `/warns` (paginado) · `/unwarn <id>` · `/clearwarns <user>` |
| Silencio | `/mute <user> [razón]` (rol 🔇 Silenciado) · `/unmute` |
| Timeout | `/timeout <user> <duración> [razón]` (aislar nativo) · `/untimeout` |
| Expulsión | `/kick` · `/ban [borrar_días]` · `/unban <id>` |
| Canal | `/slowmode <seg> [canal]` · `/lock [canal]` · `/unlock [canal]` · `/lockdown` · `/unlockdown` |
| Nombre | `/nick <user> <apodo>` (guarda `nick_anterior` cifrado) |
| Historial | `/modlogs <user>` (paginado) · `/motivo <caso_id> <texto>` |

### Privacidad — `commands/privacidad/` + job

- `/borrar-mis-datos` — confirmación por botón; borra todas las filas del usuario en `gymprofit_bot`
  (FK CASCADE en `usuarios_discord`; borrado explícito en `sanciones`) y revoca vinculación vía API
  si existe (F3). Ephemeral.
- `/mis-datos` — export JSON (ephemeral/DM) de todo lo que el bot guarda del usuario.
- `/privacidad` — embed con qué se guarda, para qué, cuánto y cómo borrarlo. Sección equivalente en
  `README`.
- **Job de retención** (`jobs/`): diario, purga warns revocados > 6 meses, tickets cerrados > 3
  meses y usuarios inactivos > 12 meses (sin XP reciente). Ventanas en constantes.

### Paginación

`/warns` y `/modlogs` consultan con `LIMIT/OFFSET` y pintan el embed con **botones de página**
(patrón reutilizable `PaginadorEmbed`). `/top` migrará a este paginador cuando toque.

## Fases de implementación

1. **F-A · Cifrado + esquema:** `Cifrador` + `BOT_CRYPTO_KEY` en `BotConfig`/`.env.example`,
   migración V4, `MigracionesTest` actualizado. Tests del cifrador.
2. **F-B · Moderación núcleo:** repos + `ModeracionService` (escalado) + `/warn` `/warns` `/unwarn`
   `/clearwarns` + log en `bot-logs`. Tests de servicio (umbrales) y repos.
3. **F-C · Sanciones directas:** `/mute` `/unmute` `/timeout` `/untimeout` `/kick` `/ban` `/unban`
   `/nick` + registro en `sanciones` + `/modlogs` `/motivo`.
4. **F-D · Canales:** `/slowmode` `/lock` `/unlock` `/lockdown` `/unlockdown`.
5. **F-E · Privacidad:** `/borrar-mis-datos` `/mis-datos` `/privacidad` + README + job de retención.

Cada fase: `./mvnw verify` en verde con evidencia, tests de lo nuevo, i18n ES+EN, docs vivas
(CHANGELOG, decisions, READMEs). Lo que exija Discord en vivo se marca como smoke test manual.

## Fuera de alcance

- Anti-spam/flood automático (ya lo cubre AutoMod de `/setup`).
- Sistema de apelaciones por DM.
- Migrar datos existentes al cifrado (la BD aún no tiene warns en producción).

## Decisiones nuevas (ADR)

- **ADR-009** — Protección de datos del bot: minimización + cifrado en reposo (Aiven) + cifrado de
  campo AES-GCM solo en texto libre; base legal interés legítimo; derechos vía comandos; retención
  por job. Clave `BOT_CRYPTO_KEY` por env var.
