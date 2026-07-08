# `events/`

**Español**

Listeners de JDA: bienvenida y auto-roles (select menu de objetivo), XP por mensaje con
cooldown anti-spam (60 s), interacciones de botones/select menus, auto-mod (flood, filtro de
insultos). Los listeners **no implementan la lógica**: delegan en `services/` para poder
testearla con JUnit + Mockito.

_Ejemplo:_ `MensajeXpListener` recibe el mensaje, comprueba cooldown y llama a `XpService.gana(...)`.

---

**English**

JDA listeners: welcome and auto-roles (goal select menu), per-message XP with anti-spam
cooldown (60 s), button/select-menu interactions, auto-mod (flood, profanity filter). Listeners
**don't implement logic**: they delegate to `services/` so it can be unit-tested with JUnit +
Mockito.

_Example:_ `MensajeXpListener` receives the message, checks the cooldown and calls
`XpService.gana(...)`.
