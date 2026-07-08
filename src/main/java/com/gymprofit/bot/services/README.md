# `services/`

**Español**

Lógica de negocio del bot: `XpService`, `EconomyService`, `ChallengeService`, etc. Sin estado
estático oculto; BD y cliente API **inyectados**, de modo que cada service es testeable de forma
aislada. Regla: **todo service nuevo llega con su test** (JUnit 5 + Mockito). La curva de nivel,
multiplicadores de racha y fórmulas quedan documentadas en el propio código.

_Ejemplo:_ `XpService.gana(discordId, cantidad)` devuelve si hubo subida de nivel; su test cubre
el umbral de nivel.

---

**English**

Bot business logic: `XpService`, `EconomyService`, `ChallengeService`, etc. No hidden static
state; DB and API client are **injected**, so each service is testable in isolation. Rule:
**every new service ships with its test** (JUnit 5 + Mockito). The level curve, streak
multipliers and formulas are documented in the code itself.

_Example:_ `XpService.gana(discordId, amount)` returns whether a level-up happened; its test
covers the level threshold.
