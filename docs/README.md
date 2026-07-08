# `docs/`

**Español**

Documentación técnica viva del bot. `architecture.md` amplía la arquitectura (paquetes, flujo
de datos, hosting); `decisions.md` recoge las decisiones (ADR): auth bot→API, BD compartida en
Aiven, validación mixta de retos, hosting. Regla: quien cambie estructura o tome una decisión,
actualiza estos archivos **en el mismo commit** (ver [`../CLAUDE.md`](../CLAUDE.md)).

_Ejemplo:_ añadir una dependencia nueva ⇒ ampliar `decisions.md` (ADR-005) con su justificación.

---

**English**

Living technical documentation. `architecture.md` expands the architecture (packages, data
flow, hosting); `decisions.md` holds the ADRs: bot→API auth, shared Aiven DB, mixed challenge
validation, hosting. Rule: whoever changes structure or makes a decision updates these files
**in the same commit** (see [`../CLAUDE.md`](../CLAUDE.md)).

_Example:_ adding a new dependency ⇒ extend `decisions.md` (ADR-005) with its rationale.
