# `embeds/`

**Español**

`EmbedFactory` central: **única** vía para crear embeds (prohibido `new EmbedBuilder()` suelto en
comandos o listeners). Garantiza las reglas visuales de la SPEC §7: color por categoría, un solo
emoji en el título, footer `GymProBot • GymProFit` + timestamp, fields inline, imágenes grandes
solo en hitos y paginación con botones cuando no cabe. Textos por i18n. Ver skill `nuevo-embed`.

_Ejemplo:_ `EmbedFactory.logro(...)` ⇒ embed dorado (`#E8B84B`) con 🏆.

---

**English**

Central `EmbedFactory`: the **only** way to build embeds (no stray `new EmbedBuilder()` in
commands or listeners). It enforces the §7 visual rules: per-category color, a single title
emoji, footer `GymProBot • GymProFit` + timestamp, inline fields, large images only on
milestones and button pagination when it doesn't fit. Text via i18n. See the `nuevo-embed` skill.

_Example:_ `EmbedFactory.logro(...)` ⇒ gold embed (`#E8B84B`) with 🏆.
