---
name: nuevo-embed
description: Añadir un método a EmbedFactory respetando la paleta y las reglas visuales de la SPEC §7. Usar al crear cualquier tipo de embed nuevo.
---

# Nuevo embed en EmbedFactory

Todos los embeds se crean en `embeds/EmbedFactory` (única vía; prohibido `new EmbedBuilder()`
suelto). Reglas de [`GYMPROBOT_SPEC.md`](../../../GYMPROBOT_SPEC.md) §7:

1. **Color = categoría** (no inventar colores):

   | Categoría | Hex |
   |---|---|
   | Marca, bienvenida, ejercicio del día, anuncios | `#FF6A00` (naranja) |
   | Logros, rachas, insignias | `#E8B84B` (dorado) |
   | Economía, tienda, retos completados | `#1E8E4A` (verde) |
   | Stats y perfil | `#378ADD` (azul) |
   | Moderación y warns | `#C62828` (rojo) |

2. **Un solo emoji** identificador en el título (máximo uno):
   📣 anuncios · 🏋️ ejercicio · 🏆 logros · 🔥 racha · 🪙 economía · 🎯 retos · 📊 stats ·
   ⚔️ duelos · 🧠 trivia · 🛡️ moderación · 🎫 tickets · 💡 sugerencias.
3. **Footer siempre** `GymProBot • GymProFit` + timestamp.
4. **Fields inline** para datos; descripción corta con el tono de la §6.
5. **GIF/imagen grande solo en hitos:** subida de nivel, logro desbloqueado, podio de reto.
6. **Nada de walls of text:** si no cabe, paginar con botones.
7. **Textos por i18n** (ES/EN), nunca hardcodeados.

Añadir un método por tipo de embed (p. ej. `logro(...)`, `warn(...)`) y su test si lleva
lógica. Actualizar el README de `embeds/` si cambian las reglas.
