# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
[Versionado Semántico](https://semver.org/lang/es/). Cada fase/feature deja su entrada aquí
(alimenta el comando `/anunciar`).

## [Sin publicar]

### Cambiado
- **Comandos de economía agrupados en subcomandos**: para no acercarnos al límite de 100 slash
  commands de Discord, se consolidan cinco familias en un comando cada una con subcomandos —
  `/gremio` (crear/ver/add/kick/salir/disolver), `/banco` (ver/depositar/retirar/prestamo/pagar),
  `/mercado` (ver/publicar/comprar/retirar), `/bolsa` (ver/invertir/vender/cartera) y `/casino`
  (coinflip/dado/ruleta/duelo). Baja el total de 94 a 76 comandos (24 de margen) y agrupa mejor cada
  sistema. La funcionalidad no cambia; solo la forma de invocarla (p. ej. `/banco depositar`).

### Añadido
- **RPG — robar** (extra, con riesgo): `/robar <usuario>` intenta robarle coins a otro jugador. Con
  suerte te llevas un 15 % de su monedero (con tope); si te pillan, pagas una multa a la víctima (así
  el robo es de suma cero, no infla la economía). Cooldown de 30 min para evitar el spam.
  `RoboService` (azar inyectable) + tests.
- **RPG — bolsa ficticia** (extra): migración **V22** (tablas `acciones`, `cartera`). 8 acciones
  inventadas cuyo **precio se mueve solo** (job cada 12 min: random walk por volatilidad + eventos de
  crash/boom). `/bolsa` muestra precios y tendencia (↑↓); `/invertir <acción> <cantidad>` compra,
  `/vender-acciones` vende (comisión 1 %, sumidero) y `/cartera` muestra tus posiciones, su valor
  actual y el P/L. Nuevo canal `📈・bolsa` en `/setup`. Todo es ficción (moneda no real ni convertible).
  `Acciones`/`BolsaService` (movimiento de precio puro y testeado) + `BolsaJob` + tests.
- **RPG — casino de ficción** (F-ECO-6): juegos de azar con **moneda ficticia** (nunca dinero real).
  `/coinflip <apuesta> <cara>` (paga el doble), `/dado <número> <apuesta>` (acierta 1-6, paga 5×) y
  `/ruleta <apuesta> <color>` (rojo/negro 2×, verde 30×), con límites de apuesta, cooldown anti-spam y
  una pequeña **ventaja de la casa** (valor esperado < 1 = sumidero). `/duelo <usuario> <apuesta>`
  reta a otro jugador: ambos apuestan y, al azar, el ganador se lleva todo (confirmación por botón).
  `ApuestaService`/`DueloService` (azar inyectable) + tests. Con esto **F-ECO-6 hecho** y la economía
  queda muy completa (quedan alianzas entre gremios y ascensos de carrera como extras).
- **RPG — gremios** (F-ECO-5a): migración **V21** (tablas `gremios`, `gremio_miembros`). `/crear-gremio
  <nombre>` funda un gremio (coste 5000, sumidero) y le crea un **canal privado** visible solo para
  sus miembros (por permisos de miembro, sin rol, para no gastar el cupo de roles). `/gremio` lo
  muestra; `/gremio-add` y `/gremio-kick` gestionan miembros (solo dueño); `/salir-gremio` abandona;
  `/disolver-gremio` lo elimina y borra el canal. Un jugador pertenece a un solo gremio; máximo 10
  miembros. `GremioService` (lógica de datos, testeada) + `GremioCanal` (gestión del canal por JDA).
- **RPG — trueque entre jugadores** (F-ECO-4d): `/trueque <usuario> [doy_item] [doy_coins] [pido_item]
  [pido_coins] …` propone un intercambio de ítems y/o coins en ambos sentidos; el otro jugador lo
  **confirma con botones** (Aceptar/Rechazar). El intercambio es atómico: reserva lo que aporta cada
  parte y, si alguna no cumple, deshace todo (nadie pierde nada). Cierra la economía entre jugadores
  (F-ECO-4 completo: regalar · mercado · banco · trueque). `TruequeService`/`TruequeListener` + tests.
- **RPG — banco: ahorro y préstamos** (F-ECO-4c): migración **V20** (tabla `banco`). `/banco` muestra
  ahorro, deuda y monedero; `/depositar` y `/retirar-banco` mueven coins entre monedero y ahorro; el
  ahorro genera un **interés diario pequeño** (2 %/día, tope 500/día) que se aplica de forma perezosa
  (por días transcurridos, sin job 24/7). `/prestamo` adelanta coins hasta un límite que crece con el
  nivel y se devuelve con una **comisión del 10 %** (sumidero); `/pagar-prestamo` salda la deuda.
  `BancoService` (interés puro y testeado) + tests.
- **RPG — mercado entre jugadores** (F-ECO-4b): migración **V19** (tabla `mercado`). `/publicar
  <item> <cantidad> <precio>` pone ítems a la venta (se retiran del inventario como escrow),
  `/mercado` lista los anuncios activos, `/comprar-mercado <nº> [cantidad]` compra (reserva atómica de
  stock y reembolso si el cobro falla) y `/retirar <nº>` cancela un anuncio propio devolviendo los
  ítems. La venta cobra una **comisión del 5 %** al vendedor (sumidero anti-inflación). Con esto ya se
  pueden vender loot y minerales entre jugadores. `Mercado*` (repo/servicio) + tests.
- **RPG — regalar entre jugadores** (F-ECO-4a): `/regalar <usuario> <coins>` y
  `/regalar-item <usuario> <item> [cantidad]` transfieren coins e ítems a otro jugador (sin comisión,
  atómico; no a bots ni a ti mismo). Primer paso de la economía entre jugadores. `RegaloService` +
  tests; sin cambios de esquema. Intro de 💰・economía actualizada.
- **RPG — progresión: estudios e insignias** (F-ECO-3b): migraciones **V17** (`personajes.estudios`) y
  **V18** (`insignias_ganadas`). Nuevo `/estudiar` (gasta energía, sube estudios; cada punto da **+1 %
  al sueldo** de `/work`, hasta +25 %). Nuevo `/insignias`: 13 logros que se **desbloquean solos** al
  cumplir su condición sobre tu estado actual (nivel, coins, poder, minería, mundos, estudios, tener
  trabajo). Estudios visibles en `/perfil` y `/rank`. `Insignias`/`InsigniaService` (condición pura,
  testeada) + `EstudiarComando`/`InsigniasComando`. Las intros de `/setup` (💰・economía, ⚔️・combate)
  ahora listan todos los comandos nuevos (minería, herrería, cofres, misiones, mazmorras, progresión).
- **RPG — progresión: rangos automáticos y /rank** (F-ECO-3a): al subir de nivel, el bot asigna solo
  el **rol de rango** que toca (🏅 Novato → Habitual → Veterano → Leyenda) y quita el anterior. Nuevo
  `/rank [usuario]`: tarjeta con rango, nivel, **barra de XP** al siguiente nivel, saldo, poder de
  combate y atributos (además auto-corrige el rango). `Rango` + `RangoService`; sin cambios de esquema.
- **RPG — sistema de cofres**: cofres comprables que sueltan **botín al azar** con rarezas
  (⬜🟦🟪🟨). `/abrir <cofre> [cantidad]` reparte un premio por cofre según su tabla (a menos peso,
  más raro): ítems, coins, minerales, un encantamiento para el arma o un nivel de arma (si sales sin
  arma, el encanto/nivel se convierte en coins). `/cofres` muestra cada cofre con sus **probabilidades
  reales**. 4 cofres (común, raro, épico, legendario); a mejor cofre, más opción de lo top. Balance de
  **sumidero** garantizado por test (valor esperado en coins < precio, para no inflar la economía).
  Cofres comprables en `/tienda`; catálogos `Cofres`/`Rareza`, `CofreService` + tests.
- **Combate / RPG — mazmorras** (COMBAT-6b): `/mazmorra <mazmorra>` encadena **varias oleadas de
  monstruos** que terminan en el jefe del mundo, **sin curarte entre oleadas** (ese es el riesgo).
  Reutiliza el combate por turnos: cada monstruo da su botín normal (y avanza misiones) y, al superar
  la última oleada, se cobra un **bonus de finalización** (coins + XP). Cuesta más energía que una
  pelea normal. 4 mazmorras (bosque, cueva, pantano, desierto). Catálogo `Mazmorras`;
  `BatallaService.iniciarMazmorra`/`completarMazmorra`, oleadas en `CombateSesion`; `MazmorraComando`.
  Con esto **COMBAT-6 queda hecho** (6a misiones · 6b mazmorras); el «loot al mercado» espera a F-ECO-4.
- **Combate / RPG — misiones de caza** (COMBAT-6a): migración **V16** (tabla `mision_progreso`).
  Catálogo de 8 misiones repetibles del tipo «mata N de X» (por monstruo, por mundo o jefes). Se
  **completan solas** al ganar combates: la recompensa (coins + XP) aparece en el propio embed de
  victoria, sin comando de reclamar. `/misiones` muestra tu progreso con barras. `Misiones` +
  `MisionService` (enganchado a la victoria en `CombateListener`); tests. Siguiente: 6b mazmorras.
- **Combate / RPG — herrería y crafteo**: cierra el bucle **minar → forjar → combatir**. `/craftear
  <receta>` combina minerales (de `/minar`) para fabricar armas, armaduras y picos, sin gastar coins
  (el coste es la minería). `/recetas` lista las 15 recetas con sus materiales. Incluye recetas de
  picos (para subir de tier sin pasar por la tienda) y de equipo hasta la espada legendaria y la
  armadura dracónica. Catálogo `Recetas` + `CrafteoService`; `CrafteoComando`/`RecetasComando` y tests.
  Sin cambios de esquema (todo va por el inventario).
- **Combate / RPG — durabilidad de picos y reparación** (COMBAT-5b): migración **V15** (tabla
  `durabilidad_picos`). Los picos ahora se **desgastan** al minar (durabilidad por tier: madera 30 …
  mithril 150); al llegar a 0 el pico se rompe y no se puede usar. `/reparar <pico>` lo restaura a tope
  cobrando coins según el desgaste y el tier (sumidero contra la inflación). `/minar` usa el mejor pico
  **con durabilidad** y muestra la restante. Con esto **COMBAT-5 queda completo** (5a minar/vender ·
  5b durabilidad/reparar). `MineriaService` amplía durabilidad + `reparar`; `RepararComando` + tests.
- **Combate / RPG — minería y venta** (COMBAT-5a): migración **V14** (tabla `mineria`: nivel y
  cooldown por jugador). `/minar` es una actividad universal: con un pico en el inventario (se usa el
  de mayor tier) extraes minerales gastando energía y con cooldown; el **nivel de minería** sube con
  el uso y aumenta la cantidad. Picos por tier (madera→hierro→diamante→mithril) que desbloquean qué
  minerales salen; catálogo de **11 minerales** (piedra→mithril, con valor creciente). `/vender <item>
  [cantidad]` convierte minerales/loot en coins (minerales a valor completo; el resto a la mitad, para
  evitar arbitraje). Picos comprables en `/tienda` (sección Picos); los minerales no se compran.
  Catálogos `Picos`/`Minerales`, `MineriaService` (azar inyectable) y `VentaService`; tests de ambos.
  Siguiente: 5b durabilidad de picos + `/reparar`.
- **Combate / RPG — encantamientos de arma** (COMBAT-4c): migración **V13** (`personajes.arma_nivel`,
  `arma_encanto`). `/encantar` mejora el arma equipada: sin opción **sube su nivel** (+daño en combate,
  coste creciente, tope nivel 10); con la opción `efecto` aplica un **encantamiento** del catálogo
  (`Encantamiento`, 9: afilado/veneno = +daño plano, llama/tormenta = +% daño, vampírico/sagrado =
  robo de vida, preciso/rúnico = +crítico, escarcha = +esquiva). Los bonos se resuelven al iniciar la
  pelea (`BatallaService`); el robo de vida cura por golpe. `/perfil` muestra el arma con su
  **+nivel** y el emoji del encantamiento. `EncantarService` (sumidero de coins) + `EncantarServiceTest`.
  Con esto **COMBAT-4 queda completo** (4a críticos/esquivas/rareza · 4b habilidades · 4c encantar).
- **Combate / RPG — habilidades de combate** (COMBAT-4b): botón **✨ Habilidad** en la batalla con un
  set fijo para todos y **cooldown en turnos** (sin schema): **💥 Golpe potente** (dobla tu ataque, no
  esquivable), **💚 Curación** (recupera 30 % de tu HP de combate) y **💫 Aturdir** (golpeas y el
  monstruo pierde su contraataque ese turno). Los cooldowns se llevan en la sesión y bajan cada turno.
  `Habilidad` (enum) + `BatallaService.usarHabilidad`; menú de habilidades con su estado en
  `CombateListener`. Tests de habilidades en `BatallaServiceTest`. Siguiente: 4c `/encantar`.
- **Combate / RPG — críticos, esquivas y rareza de loot** (COMBAT-4a): en la batalla por turnos, cada
  golpe puede ser **crítico** (×2 daño; probabilidad escala con la fuerza) y ser **esquivado** (anula
  el golpe; escala con el carisma); también el monstruo puede criticar/esquivar. El botín se pinta con
  su **rareza** (⬜ común · 🟦 raro · 🟪 épico · 🟨 legendario), derivada del ítem (stat de combate en
  armas/armaduras, precio en el resto) sin tocar el catálogo. Sin cambios de esquema. `Rareza` +
  `CombateService.probCritico/probEsquiva`; azar inyectable en `BatallaService` para tests
  deterministas (`RarezaTest`, tests de crítico/esquiva). Siguiente: 4b habilidades, 4c `/encantar`.
- **Combate / RPG — batalla por turnos** (COMBAT-3): migración **V12** (`personajes.ultimo_combate`,
  cooldown tras derrota). `/pelear <mundo>` abre un menú de rivales y arranca una **batalla por
  turnos con botones** (Atacar / Defender / Objeto / Huir) conducida por `CombateListener` (sesión en
  memoria, una por jugador). HP de combate propio = `base + resistencia·k` (distinto de la salud);
  daño = `f(ataque+arma, defensa rival, azar)`, con contraataque del monstruo. **Objeto** usa
  consumibles: los de salud curan HP de combate, los de energía dan **turno extra**. Al **ganar**:
  coins + XP + tirada de **loot** (al inventario) y, si es jefe, se marca `progreso_mundos` y se
  **desbloquea el mundo siguiente**; al **perder**: −salud + cooldown. Pelear cuesta energía. Todo en
  **embeds con barras de HP**. `BatallaService` (motor, azar inyectable) + `CombateSesion`;
  `BatallaServiceTest` (15) y math de combate en `CombateServiceTest`.
- **Combate / RPG — mundos y bestiario** (COMBAT-2): migración **V11** (`progreso_mundos`: qué
  mundos ha completado cada jugador). Catálogos en código: **`Mundos`** (8 mundos desbloqueables en
  orden, del Bosque Susurrante al Reino Sombrío, con nivel recomendado) y **`Monstruos`** (bestiario
  **amplio, 64 monstruos**: 7 normales por dificultad + 1 jefe por mundo, con poder, HP, coins, XP y
  tabla de botín que apunta a `Items`). `MundoService` calcula el **desbloqueo** (un mundo se abre al
  derrotar al jefe del anterior) y `MundoRepositorio` persiste el progreso. Comandos `/mundos`
  (lista con 🔓/🔒/✅ y tu nivel) y `/monstruos <mundo>` (bestiario, un embed por dificultad para
  respetar el límite de 4096). Canales nuevos en `/setup` (categoría 🎮 VIDA): **⚔️・combate**,
  **🗺️・mundos** y **📖・bestiario**. Aún **sin pelea** (solo datos + navegación); la batalla por
  turnos es COMBAT-3. `MundoServiceTest` (10) cubre desbloqueo e integridad de catálogos.
- **Combate / RPG — armas, armaduras y poder de combate** (COMBAT-1): migración **V10** (columnas
  `personajes.arma`/`armadura`); catálogo ampliado (`Items`) con **16 armas** (puños→espada
  legendaria, +ataque) y **12 armaduras** (ropa→armadura divina, +defensa), temática aventura
  (no-gym), precios anclados a la escala lenta. `CombateService` con `/equipar <item>` (exige
  poseerlo, no lo consume) y `/desequipar <ranura>`; `/perfil` muestra el **poder de combate**
  (fuerza + resistencia + ataque + defensa) y el equipo. `/tienda` añade las secciones ⚔️ Armas y
  🛡️ Armaduras con su stat. Base para los mundos/monstruos/batalla (COMBAT-2+). Spec
  `docs/superpowers/specs/2026-07-13-combate-mundos-monstruos.md`.

### Corregido
- **Embeds — test de la línea de autor**: `EmbedFactoryTest` seguía exigiendo la línea de autor que
  se retiró en `693f268` (solo footer); actualizado para verificar que **ya no** hay autor. (CI en
  verde de nuevo.)

- **Economía / RPG — árbol de mejoras**: migración **V9** (`mejoras`); `/mejoras` dibuja un **árbol
  visual** (ASCII/emoji) con ramas de fuerza/resistencia/carisma, nodos ✅ comprado / 🔓 disponible /
  🔒 bloqueado y su precio; `/mejorar <nodo>` compra (valida prerrequisito y saldo, sube el atributo
  permanentemente). Progresión lenta (coste creciente). (F-ECO-2b.)
- **Economía / RPG — tienda e inventario**: migración **V8** (`inventario`); catálogo amplio de ítems
  (`Items`, ~24: consumibles, equipo, bienes). `/tienda` (por categoría), `/comprar` (pago atómico
  con el monedero), `/inventario` (agrupado) y `/usar` (los consumibles aplican energía/salud). Rol
  cosmético de trabajo al `/elegir-trabajo` (lazy, reutiliza, sin agotar cupo). (F-ECO-2a.)
- **Economía / RPG — trabajo y energía**: migración **V7** (personajes con trabajo + cooldown);
  catálogo **amplio de ~32 trabajos** de muchos sectores (transporte, hostelería, tecnología,
  sanidad, oficios, derecho, ciencia…) con tiers y requisito de nivel. `/trabajos` (lista),
  `/elegir-trabajo`, `/work` (gana coins, gasta energía, cooldown 1 h), `/entrenar` (sube atributos)
  y `EnergiaJob` (regenera energía). (F-ECO-1.)
- **Economía / RPG — cimientos**: migración **V6** (`personajes` con atributos/energía/salud +
  `transacciones` como ledger de coins); monedero **seguro** (operaciones atómicas, nunca saldo
  negativo, todo registrado); `/balance`, `/perfil` (personaje + saldo) y `/daily` (recompensa
  diaria con racha, **progresión lenta a propósito**). Primer paso del simulador de vida de ficción
  (spec `docs/superpowers/specs/2026-07-13-economia-rpg-vision.md`). (F-ECO-0.)
- **Sugerencias**: `/sugerencia` crea un post en el foro `💡・sugerencias` con votación 👍/👎 y
  etiqueta «En estudio», y lo guarda; `/sugerencia-resolver <id> aceptada|rechazada` (staff) cambia
  el estado en BD y la etiqueta del foro (Aprobada/Rechazada). (Bloque D2.)
- **Tickets de soporte**: panel por botón (`/panel tipo:ticket` en `🎫・soporte`) → abre un **canal
  privado** (autor + staff) en la categoría TICKETS; botón **Cerrar** → guarda la **transcripción**
  en la BD y la publica en `🗄️・logs-tickets`, y borra el canal. Uno abierto por usuario.
  (`TicketService`, `TicketListener`. Bloque D1.)
- **Contenido — panel de roles**: `/panel` republica el panel de auto-roles (menús de objetivo y
  notificaciones) en `🎭・roles` (o el canal indicado) y lo fija. Panel extraído a `PanelRolesFactory`
  reutilizada por `/setup`. (Bloque A3.)
- **Contenido — sorteos**: `/sorteo` (premio + duración + nº ganadores) publica un sorteo en
  `🎁・sorteos` con reacción 🎉 y ping a `@🎁 Sorteos`; migración **V5** (`sorteos`) + `SorteoJob` que
  al vencer elige ganadores al azar entre los participantes y los anuncia. (Bloque A2.)
- **Contenido (staff)**: `/anuncio` (publica un anuncio con marca en `📣・anuncios`, imagen y ping
  opcional a `@📣 Avisos`) y `/redes` (publica las redes oficiales en `📱・redes-sociales`). Se añade
  `Comando.Categoria` (PUBLICO/MODERACION) como base del futuro directorio de comandos autogenerado.
  (Bloque A1.)
- **Privacidad (RGPD) — derechos del usuario**: `/privacidad` (qué se guarda y para qué),
  `/mis-datos` (export JSON efímero, acceso/portabilidad) y `/borrar-mis-datos` (olvido, con
  confirmación por botón). Nuevo `PrivacidadService` y **job de retención** (`RetencionJob`): purga
  avisos revocados > 6 meses y sanciones > 12 meses. Sección de privacidad en el README. (Fase E.)
- **Moderación — canales**: `/lock` `/unlock` (bloquear/reabrir un canal), `/lockdown` `/unlockdown`
  (todos los canales de texto, anti-raid) y `/slowmode` (modo lento). Solo altos cargos; log en
  `🤖・bot-logs`. (Fase D.)
- **Moderación — historial**: `/modlogs` (historial completo de un miembro, **paginado con botones**
  ◀ ▶) y `/motivo` (edita el motivo de un caso; se re-cifra). (Fase C2.)
- **Moderación — sanciones directas**: `/mute` `/unmute` (rol 🔇 Silenciado), `/timeout` `/untimeout`
  (aislamiento nativo, duraciones tipo `30m`/`2h`/`1d`), `/kick`, `/ban` (con borrado de mensajes
  0-7 días), `/unban` (por id), `/nick` (guarda el apodo anterior cifrado). Solo altos cargos; todo
  se registra en `sanciones` y se publica en `🤖・bot-logs`. Nuevo `util/Duraciones`. (Fase C1.)
- **Moderación — avisos con escalado**: `/warn`, `/warns`, `/unwarn`, `/clearwarns` (solo altos
  cargos: 🧹 Staff / 🛡️ Admin / 👑 Fundador). Los avisos se guardan con el **motivo cifrado** y toda
  acción queda en el historial `sanciones` y se publica en `🤖・bot-logs`. **Escalado automático**:
  3 avisos → timeout 1 h, 5 → timeout 24 h, 7 → ban (`ModeracionService`). (Fase B.)
- **Protección de datos (RGPD) — base**: cifrado de campo **AES-256-GCM** (`util/Cifrador`) para el
  texto libre con posible dato personal (motivos de sanción, apodos previos); clave por env var
  `BOT_CRYPTO_KEY`. Migración **V4** con la tabla `sanciones` (auditoría de moderación) y
  `warns.motivo` ampliado a TEXT para el cifrado. Ver ADR-009. (Fase A del módulo de moderación.)
- **Onboarding de Discord (configuración manual)**: diseñadas las 5 preguntas de personalización
  (idioma, objetivo, experiencia, notificaciones, intereses) con roles/canales por opción y los
  canales predeterminados. Se configura **a mano** en el editor de Discord (su API/editor impone
  límites de nº de preguntas y de cobertura de canales que hacían inviable automatizarlo). El diseño
  vive en el spec `docs/superpowers/specs/2026-07-13-onboarding-permisos-idiomas-design.md`. Ver ADR-007.
- **Integración bilingüe (ES/EN) sin fragmentar**: pregunta de idioma → rol con bandera
  (`🇪🇸 Español` / `🇬🇧 English`), cosmético y para pings segmentados; canales compartidos (el bot ya
  responde en el idioma de cada usuario).
- **Matriz de permisos por rol declarativa**: `CanalPlan` admite overrides por rol
  (`.permite(...)`, `.niega(...)`, `.conSoloLectura()`) que `/setup` aplica al crear y al reutilizar.
  Coach/Nutricionista gestionan los foros de fitness; Ponente habla en el Escenario. Ver ADR-008.
- **Roles y canales nuevos**: roles `🌱 Principiante`, `💪 Intermedio`, `🔥 Avanzado`,
  `🇪🇸 Español`, `🇬🇧 English`, `📅 Eventos`, `🎁 Sorteos`; canales News (solo-lectura)
  `📅・eventos` y `🎁・sorteos` en la categoría EVENTOS, con su intro ES/EN.

### Corregido
- **Canales de anuncios en solo-lectura**: `📣・anuncios`, `📲・novedades-app`, `📅・eventos` y
  `🎁・sorteos` eran escribibles por `@everyone` (la factoría los creaba sin restricción). Ahora
  publica solo el staff y `@everyone` lee.
- **`/setup` ya no borra mensajes (sobrescribe, no elimina)**: la ruta normal purgaba los mensajes
  recientes de **todos** los canales de texto en cada ejecución. Eliminado: `/setup` ahora solo
  crea/reutiliza estructura y no toca los mensajes. El borrado total (canales incluidos) queda
  **solo** en `/setup desde_cero:true`. Se elimina la dependencia de `LimpiezaService` en el comando.
- **`/setup` ya no borra roles (evita agotar el cupo diario de Discord)**: borrar y recrear los 25
  roles en cada `desde_cero` agotaba el límite diario de creación de roles del servidor y lo
  bloqueaba ~2 días (429 con retry-after enorme, que además colgaba el bot). Ahora los roles se
  **reutilizan por nombre** (solo se crean los que falten) y un `RestAction.setDefaultTimeout(30s)`
  hace que cualquier acción atrapada en un rate limit brutal falle en 30 s en vez de colgar.
- **`/setup desde_cero` borra todo mediante canales-ancla de comunidad**: Discord no deja borrar los
  canales de reglas/actualizaciones/seguridad de una comunidad (error 50074). Ahora `/setup` usa dos
  **canales-ancla permanentes y ocultos** (`📜・reglas-comunidad` y `🛡️・avisos-comunidad`, en STAFF,
  solo visibles para el bot y el Fundador): antes de vaciar, ancla a ellos los ajustes de comunidad
  (reglas → uno; updates+seguridad → otro), lo que libera los canales de contenido para poder
  borrarlos. Las anclas se reutilizan y **nunca se borran**. Antes quedaban 2 canales huérfanos.
- **Canales de media más robustos**: se crean "desnudos" (el topic en la creación disparaba el error
  50024) y se les fija topic/permisos después; si aun así media no está disponible en la guild, se
  cae a un **foro** (galería por publicaciones). Afecta a `📈・progresos` y `📸・fotos`.
- **AutoMod idempotente por tipo de trigger**: las reglas de mención/spam/preset están limitadas a
  1 por servidor; ahora `/setup` omite crearlas si ya existe una **de ese tipo** (aunque tenga otro
  nombre o la haya creado el staff a mano), evitando el error 50035 (máximo de reglas superado).

### Añadido
- **Todos los foros pulidos + Staff publica en solo-lectura**: cada foro (faq, rutinas, nutrición,
  dudas, sugerencias, reportes, progresos, fotos) se crea con **etiquetas**, **reacción por defecto
  temática** (faq 💡, rutinas 💪, nutrición 🍎, dudas 🙋, sugerencias 👍, reportes 🚨, progresos 🔥,
  fotos ❤️) y una **primera publicación** (guía + ejemplo) para que no arranque vacío. Los canales de
  solo-lectura (anuncios, reglas, info…) ahora **permiten publicar al Staff** (no solo al bot/admin):
  niegan escribir a `@everyone` pero conceden `MESSAGE_SEND` al rol Staff.
- **Coherencia de tipos de canal + AFK**: `❓・faq` y `📥・reportes` pasan a **foro** (con etiquetas);
  `🏆・logros` y `📊・ranking` a **solo-lectura** (los publica el bot, sin chatter); y `/setup` fija
  `💤 AFK` como **canal AFK** del servidor (timeout 5 min).
- **Pantalla de bienvenida (Welcome Screen) en `/setup`**: al montar, si el servidor es Comunidad,
  `/setup` configura la pantalla de bienvenida (`modifyWelcomeScreen`) con descripción de marca y 5
  canales sugeridos con emoji (empieza-aquí, reglas, roles, general, soporte). Es lo único de la
  «incorporación» que expone la API; el onboarding con preguntas de personalización sigue siendo
  manual (Discord no da API de escritura). Pendiente de smoke test manual.
- **Eventos del servidor (`/reto` y `/evento`)**: dos contadores más en SERVER STATS —`🎯 Reto`
  (reto de la semana) y `⏳ Evento` (próximo evento con cuenta atrás «en 3d 4h»)— alimentados por
  la nueva tabla `eventos_servidor` (migración **V3**) vía `EventoServidorRepositorio` y
  `EventoService`. Comandos solo-staff `/reto <texto>` y `/evento <nombre> <fecha>` (formato
  `2026-07-20 18:30`, hora peninsular; la confirmación usa timestamps dinámicos de Discord). El job
  de estadísticas refresca ambos contadores. Tests `EventoServiceTest` (parseo de fecha) y
  ampliación de `EstadisticasServiceTest` (cuenta atrás). Ejecución en vivo pendiente de smoke test.
- **Tipos de canal coherentes en `/setup` (foros, media, anuncios)**: `TipoCanalDiscord` amplía a
  `FORO`, `MEDIA` y `ANUNCIOS` y `/setup` los crea con su rama propia:
  - **Foros** (publicaciones título+imagen+descripción con etiquetas): `💡 sugerencias`
    (En estudio/Aprobada/Rechazada/Implementada), `📚 rutinas` (Push/Pull/Pierna/Full-body/Cardio/
    Movilidad), `🍎 nutrición` (Receta/Plan/Duda/Suplementación), `❓ dudas` (Técnica/Material/
    Lesión/Resuelto).
  - **Media** (galería): `📈 progresos` y `📸 fotos` (separados).
  - **Anuncios** (News, seguibles y solo-staff): `📣 anuncios` y `📲 novedades-app`.
  - Cada canal lleva su descripción (topic) con las instrucciones de uso; los foros, sus etiquetas.
    Tests `SetupServidorPlanTest` (topics de todos los canales de mensajes, etiquetas solo en foros).
  - **Para aplicar los tipos nuevos a un server ya montado hay que reejecutar con
    `/setup desde_cero:true`**: reutilizar por nombre no cambia el tipo de un canal existente.
    Creación en vivo pendiente de smoke test manual.
- **Estadísticas en vivo + AutoMod + más canales en `/setup`**:
  - Categoría **`📊 SERVER STATS`** (arriba del todo, de solo lectura) con 4 contadores en canales
    de voz bloqueados: **XP repartido**, **Nº1** (líder de XP), **Boosts** y **En voz**. Los
    mantiene al día el nuevo `EstadisticasService` (job cada 6 min; renombra solo si el valor
    cambió, para no gastar rate limit; localiza los canales por prefijo de nombre → sin persistir
    IDs). XP repartido y Nº1 salen de la BD (`sumaXp` / `listarTopPorXp`); boosts y gente en voz, de
    la caché estándar. **No requiere intents privilegiados adicionales.**
  - **AutoMod por código**: `/setup` crea (idempotente) 3 reglas con alerta a `📋・moderación`:
    anti-menciones masivas (>8), anti-spam y lenguaje inapropiado (presets PROFANITY/SLURS/
    SEXUAL_CONTENT, que cubren español). Se omite sin romper si falta `MANAGE_SERVER`.
  - **Descripciones (topic)** en todos los canales de texto; se aplican al crear y al reejecutar.
  - Nuevos canales: **`🎤 Escenario`** (Stage, mano levantada) + rol **Ponente**, y lobby
    **`➕ Crear sala`** (base del futuro Join-To-Create) en sustitución de la `Privada` fija.
  - Idempotencia reforzada: los canales de stats se casan por prefijo, así reejecutar `/setup` no
    los duplica tras el renombrado. Tests `EstadisticasServiceTest`, ampliación de
    `SetupServidorPlanTest` (topics) y `DiscordBotTest` (3 intents). Ejecución en vivo (creación de
    canales/reglas y renombrado) **pendiente de smoke test manual**.

### Cambiado
- **Estilo de embeds mejorado**: todos los embeds llevan ahora **línea de autor** (cabecera de
  marca con el avatar del bot arriba), además del footer y el timestamp. `EmbedFactory` expone
  **helpers de timestamp dinámico** de Discord (`tiempoRelativo` → `<t:…:R>`, `fechaLarga` →
  `<t:…:F>`), que se renderizan y actualizan solos en el cliente; `/evento` los usa en su
  confirmación. Tests ampliados en `EmbedFactoryTest`. (Los mensajes fijados de los canales heredan
  el nuevo estilo automáticamente al pasar por la factoría.)
- **Servidor pulido (visual)**: `/setup` publica mensajes fijados con **contenido rico**
  (reglas largas y estructuradas por secciones, guías con divisores), **thumbnail** del bot en los
  embeds, **categorías decoradas** (`▬▬ 📢 INFORMACIÓN ▬▬`) y un **panel de navegación con
  botones-enlace** en `🚀・empieza-aquí` que salta a los canales clave. `/setup` corre en hilo
  propio, es resiliente por ítem y tolera la expiración de la interacción.
- **Pase visual (2)**: `/top` con medallas 🥇🥈🥉 en el podio; el embed de subida de nivel
  añade thumbnail del avatar. `/ping` y las confirmaciones de `/config` se dejan como están
  (línea corta, sin muro).
- **Pase visual de embeds**: footer con el avatar del bot en todos los embeds
  (`EmbedFactory.configurarIconoFooter`); `/config ver` pasa de 11 campos sueltos a una
  descripción agrupada (Canales / Roles) con indicadores ✅/⚪, emoji por línea y thumbnail;
  `/nivel` añade barra de progreso y thumbnail del avatar. Nueva utilidad `util/Barras`
  (barra de progreso reutilizable) con test.

### Añadido
- **Panel de auto-roles + contenido del servidor (F1)**: `/setup` publica y fija un panel en
  `🎭・roles` con menús (objetivo + notificaciones) gestionados por `PanelRolesListener` (además
  del onboarding). Mensajes fijados de ayuda en los canales clave (empieza-aquí, reglas,
  cómo-funciona, faq, redes, soporte, general, presentaciones, comandos-bot, fitness) y aviso de
  «próximamente» en los de fases futuras. Permisos por rol (Silenciado no habla en ningún canal),
  categorías decoradas. `/setup` corre fuera del hilo del gateway, comprueba permisos antes de
  tocar nada y ya no borra roles del propio bot ni se autobloquea en categorías ocultas.
- **Administración del servidor (F1)**: `/setup` (solo admin) monta roles, categorías y canales
  (F1–F4) con permisos según `SetupServidorPlan` (blueprint testeable), purga los mensajes
  recientes existentes y autorrellena `config_servidor`; opción `desde_cero` que borra todos los
  canales y roles borrables antes de montar (irreversible). `/limpiar <cantidad>` purga los
  últimos N mensajes del canal (solo staff), vía `LimpiezaService`. Tests `SetupServidorPlanTest`,
  `LimpiezaServiceTest`, `LimpiarComandoTest`. Ejecución en vivo pendiente de smoke test manual.
- **Bienvenida + auto-roles (F1)**: `BienvenidaListener` publica un embed de bienvenida (con
  thumbnail del avatar) en el canal configurado al entrar un miembro, con un menú de selección de
  objetivo (Fuerza/Cardio/Pérdida de peso/General); al elegir, asigna el rol configurado en
  `/config`. Helper `ConfigServidorService.rolDe` (con test). Pendiente de smoke test manual
  (requiere canal y roles configurados en el servidor).
- **Configuración de servidor (F1)**: entidad `ConfigServidor` + `ConfigServidorRepositorio`
  (upsert de `config_servidor`), `ConfigServidorService` (fijar canal/rol/idioma conservando el
  resto) y comando `/config` (solo staff, `MANAGE_SERVER`, guild-only) con subcomandos `ver`,
  `canal`, `rol` e `idioma`. Base para bienvenida/auto-roles y el resto de F1. Tests
  `ConfigServidorServiceTest` (Mockito) y `ConfigServidorRepositorioTest` (Testcontainers).
  Registro verificado en vivo (4 comandos); uso en vivo pendiente de smoke test manual.
- **XP y niveles (F1)**: XP por mensaje con cooldown anti-spam de 60 s (`XpMensajeListener` +
  `util/Cooldown`), `XpService` con curva de nivel documentada (`NivelCalculadora`,
  `50·n²+50·n`), anuncio de subida de nivel (embed dorado), y comandos `/nivel [usuario]` y
  `/top` (leaderboard) en `commands/gamificacion`. Nueva consulta `listarTopPorXp` en el
  repositorio. Tests unitarios (`NivelCalculadoraTest`, `CooldownTest`, `XpServiceTest` con
  Mockito) y de repositorio (Testcontainers). XP por mensaje, `/nivel` y `/top` en vivo quedan
  pendientes de smoke test manual (registro de los 3 comandos verificado en vivo).
- **Infraestructura de slash commands + `/ping` (F1)**: interfaz `Comando`, `RouterComandos`
  (registra los comandos por servidor en `onGuildReady` y enruta las interacciones, aislando
  errores) y primer comando `/ping` (`commands/general`) con descripción localizada ES/EN y
  respuesta por `EmbedFactory`. `DiscordBot.start` acepta listeners; `Main` monta el router.
  Helper `Messages.desdeTag` para resolver idioma desde el locale de Discord. Tests
  `PingComandoTest` y ampliación de `MessagesTest`. Registro verificado en vivo; invocación de
  `/ping` pendiente de smoke test manual.
- **EmbedFactory central (F1, SPEC §7)**: única vía para crear embeds. `Categoria` fija la
  paleta (naranja/dorado/verde/azul/rojo) y `Tipo` asocia cada emoji identificador (uno por
  título) con su color; footer de marca `GymProBot • GymProFit` + timestamp e i18n del footer
  (`embed.footer`). Los tipos sin color en la §7 (duelos, trivia, sugerencias, tickets) usan
  azul como color de info. Test `EmbedFactoryTest`.
- **Capa de BD y arranque de Flyway (F1)**: `db/Database` monta el pool **HikariCP** y aplica
  las migraciones Flyway al arrancar; `Main` sigue el orden health → Flyway → JDA con arranque
  degradado si falta `DB_URL` o `DISCORD_TOKEN`. Primer repositorio JDBC
  `UsuarioDiscordRepositorio` (`buscar`, `obtenerOCrear`, `guardar`) sobre `usuarios_discord`,
  con `UsuarioDiscord` (entidad) y `DatabaseException`. Test end-to-end con Testcontainers
  (`UsuarioDiscordRepositorioTest`). Verificado en vivo: arranque real contra MySQL 8 aplicando
  las 2 migraciones (esquema v2) y conectando a Discord.
- **Esquema inicial de la BD + seeds (F1)**: migraciones Flyway `V1__esquema_inicial_f1.sql`
  (tablas de la Fase 1: `usuarios_discord`, `config_servidor`, `warns`, `tickets`,
  `sugerencias`, `trivia_preguntas`, `trivia_scores`, `frases`) y `V2__seed_frases_trivia.sql`
  (seeds obligatorios SPEC §10: 50 preguntas de trivia y 32 frases, bilingües ES/EN). Test de
  migración con **Testcontainers** (`mysql:8.0`) que valida `flyway migrate` y los mínimos de
  seeds; se salta si el cliente Docker no es alcanzable en local (corre en CI). Ver ADR-006.
- **Conexión con Discord (F1, bootstrap JDA)**: `DiscordBot` centraliza la construcción de
  JDA con los intents privilegiados `GUILD_MEMBERS` + `MESSAGE_CONTENT`, cache de miembros,
  presencia (`bot.actividad` en ES/EN) y estado *online*. `Main` conecta al arrancar si hay
  `DISCORD_TOKEN` (si no, solo el health server) y cierra JDA + health de forma ordenada en
  `SIGTERM`. Sin comandos ni listeners todavía. Test `DiscordBotTest` (contrato de intents).
  Verificado en vivo: *Login Successful!* contra el servidor de test.
- **Andamiaje del repositorio** (sin lógica de comandos todavía):
  - Esqueleto Maven Java 21 + JDA 5: `pom.xml`, estructura de paquetes de la SPEC §4
    (con `package-info.java`), `Main` con health server en `/health`, `BotConfig`, `Messages` (i18n).
  - `messages_es/en.properties` (vacíos), `logback.xml` (solo consola), `.env.example`.
  - Tests iniciales: health endpoint (`HealthServerTest`) y sincronización ES/EN (`MessagesTest`).
  - Documentación de repo nativo de IA: `README.md` + `README.en.md`, `CLAUDE.md`, `docs/`
    (architecture, decisions/ADR), `rules/` (coding-rules, review-checklist), READMEs por carpeta,
    y skills `nuevo-comando` / `nueva-migracion` / `nuevo-embed`.
  - CI (`ci.yml`, JDK 21 Temurin + `mvn verify`), `keep-alive.yml`, `dependabot.yml` y plantillas
    de issue/PR.
  - `Dockerfile` (multi-stage JDK 21) y `render.yaml` (blueprint con secretos `sync:false` y
    `TZ=Europe/Madrid`).
  - `LICENSE` propietario (copiado de GymProFit) y `.gitignore` (Maven + IDE + `.env`/logs).
