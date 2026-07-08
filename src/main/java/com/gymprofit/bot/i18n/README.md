# `i18n/`

**Español**

Internacionalización ES/EN sobre `ResourceBundle`. `Messages.get(locale, clave, args...)` es el
único acceso al texto visible; **prohibido hardcodear**. Toda clave nueva se añade **a la vez**
en `messages_es.properties` y `messages_en.properties` (los archivos viven en
`src/main/resources`). El test `MessagesTest` falla si los dos idiomas se desincronizan.

_Ejemplo:_ `Messages.get(Messages.ES, "nivel.subida", nivel)`.

---

**English**

ES/EN internationalization over `ResourceBundle`. `Messages.get(locale, key, args...)` is the
only access to user-visible text; **no hardcoding**. Every new key is added **at the same time**
in `messages_es.properties` and `messages_en.properties` (files live in `src/main/resources`).
The `MessagesTest` fails if the two languages drift apart.

_Example:_ `Messages.get(Messages.EN, "nivel.subida", level)`.
