# `embeds/`

**Español**

`EmbedFactory` central: **única** vía para crear embeds (prohibido `new EmbedBuilder()` suelto en
comandos o listeners). Garantiza las reglas visuales de la SPEC §7: color por categoría, un solo
emoji en el título, footer `GymProBot • GymProFit` + timestamp, fields inline, imágenes grandes
solo en hitos y paginación con botones cuando no cabe. Textos por i18n. Ver skill `nuevo-embed`.

Los tipos cuyo color no fija la §7 (duelos, trivia, sugerencias, tickets) usan **azul** como
color de información general. El footer lleva el avatar del bot
(`configurarIconoFooter`, fijado al arrancar). Para datos con muchos ítems, preferir una
descripción agrupada con indicadores (✅/⚪) antes que decenas de fields; barras de progreso
con `util/Barras`.

_Ejemplo:_ `EmbedFactory.base(Tipo.LOGRO, locale, titulo)` ⇒ embed dorado (`#E8B84B`) con 🏆.

---

**English**

Central `EmbedFactory`: the **only** way to build embeds (no stray `new EmbedBuilder()` in
commands or listeners). It enforces the §7 visual rules: per-category color, a single title
emoji, footer `GymProBot • GymProFit` + timestamp, inline fields, large images only on
milestones and button pagination when it doesn't fit. Text via i18n. See the `nuevo-embed` skill.

Types whose color §7 does not define (duels, trivia, suggestions, tickets) use **blue** as the
general-info color.

_Example:_ `EmbedFactory.base(Tipo.LOGRO, locale, titulo)` ⇒ gold embed (`#E8B84B`) with 🏆.
