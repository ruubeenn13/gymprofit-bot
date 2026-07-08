# `.claude/skills/`

**Español**

Skills de Claude Code para tareas repetitivas del repo, con sus reglas ya incorporadas:
`nuevo-comando` (scaffold de slash command: clase, registro, i18n ES+EN, permisos, cooldown,
test), `nueva-migracion` (migración Flyway con numeración correcta y seeds) y `nuevo-embed`
(método en `EmbedFactory` respetando paleta y reglas §7). Cada skill es un `SKILL.md` con
frontmatter `name`/`description`.

_Ejemplo:_ al crear un comando, seguir `nuevo-comando/SKILL.md` para no saltarse i18n ni el test.

---

**English**

Claude Code skills for repetitive repo tasks, with the rules baked in: `nuevo-comando`
(slash command scaffold: class, registration, ES+EN i18n, permissions, cooldown, test),
`nueva-migracion` (Flyway migration with correct numbering and seeds) and `nuevo-embed`
(`EmbedFactory` method following the palette and §7 rules). Each skill is a `SKILL.md` with
`name`/`description` frontmatter.

_Example:_ when adding a command, follow `nuevo-comando/SKILL.md` so i18n and the test aren't missed.
