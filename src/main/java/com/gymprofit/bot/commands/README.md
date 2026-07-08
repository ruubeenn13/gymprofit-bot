# `commands/`

**Español**

Un archivo por slash command, en subpaquetes por categoría (fitness, economía, moderación…).
Cada comando: localiza nombre/descripción (ES/EN), saca texto de `i18n`, crea embeds solo por
`EmbedFactory`, aplica permisos (`setDefaultPermissions` en staff) y cooldown si escribe BD/API,
y **delega la lógica en `services/`**. Ver la skill `nuevo-comando`.

_Ejemplo:_ `/imc` → clase en `commands/fitness/`, cálculo en `NutricionService`, respuesta con
`EmbedFactory` (categoría stats, azul).

---

**English**

One file per slash command, in per-category subpackages (fitness, economy, moderation…). Each
command localizes name/description (ES/EN), pulls text from `i18n`, builds embeds only via
`EmbedFactory`, applies permissions (`setDefaultPermissions` for staff) and a cooldown when it
writes to DB/API, and **delegates logic to `services/`**. See the `nuevo-comando` skill.

_Example:_ `/imc` → class in `commands/fitness/`, calculation in `NutricionService`, reply via
`EmbedFactory` (stats category, blue).
