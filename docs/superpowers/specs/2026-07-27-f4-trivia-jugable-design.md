# F4 — Trivia jugable (diseño)

**Fecha:** 2026-07-27
**Fase:** F4 Competición (primer subsistema). Las preguntas ya están sembradas desde F1 pero nunca se
leyeron: este módulo las hace jugables y amplía el banco.
**Precondición:** núcleo del bot desplegado; tabla `trivia_preguntas` + 50 semillas (V1/V2) presentes.

## Objetivo

Convertir las preguntas de trivia (hoy datos huérfanos) en un juego real: `/trivia` plantea una pregunta
con botones A-D; acertar da coins + XP. Añadir competición vía un ranking de aciertos y ampliar el banco a
~200 preguntas en 6 categorías para que sea rejugable y no memorizable de un vistazo.

## Bucle de juego

- **`/trivia [categoria]`** (categoría opcional): saca **una pregunta al azar que el jugador aún no ha
  respondido** (filtrada por categoría si se indica). Se muestra **efímera** al invocador con 4 botones
  (A-D), en el idioma del invocador (las preguntas son bilingües ES/EN).
  - *Efímera a propósito:* una trivia pública spoilearía la respuesta al resto y permitiría copiar. Es una
    excepción justificada al «público por defecto» (mecánica del juego), documentada en
    [[gymprobot-visibilidad-comandos]].
- **Un intento por pregunta.** Al pulsar un botón (`TriviaListener`): se registra la respuesta (one-shot,
  no reintentable), se revela la correcta, y:
  - **Acierto** → coins + XP por dificultad; cuenta como acierto.
  - **Fallo** → sin premio; la pregunta queda **consumida** (no se vuelve a ofrecer).
  - El mensaje se edita con el resultado + el marcador del jugador (aciertos X / total).
- Cuando el jugador ha respondido **todas** las de su filtro → «ya has respondido todas»; puede seguir sin
  premio (o el comando avisa de que no quedan).
- **Cooldown** corto por usuario (anti-spam; la recompensa ya está acotada por el nº de preguntas).
- **`/trivia ranking`**: top por número de aciertos (parte «rankings» de F4). Muestra tu posición.

## Antiinflación

La recompensa total que un jugador puede sacar de la trivia está **acotada**: cada pregunta premia como
mucho una vez, así que el máximo histórico es `Σ premio` sobre ~200 preguntas. Es un faucet **finito**, no
recurrente. Los premios se pueden calibrar sin miedo a inflación estructural.

## Esquema y componentes

- **Migración V37 — `trivia_respuestas`** (estado del juego): tabla
  `trivia_respuestas(discord_id BIGINT, pregunta_id BIGINT, acierto BOOLEAN NOT NULL, respondida_en
  TIMESTAMP, PRIMARY KEY(discord_id, pregunta_id))` con FK a `usuarios_discord(discord_id)` y a
  `trivia_preguntas(id)`, ambas `ON DELETE CASCADE` (RGPD / consistencia).
- **Migración V38 — ampliación del banco:** `ALTER` del CHECK de `trivia_preguntas.categoria` para admitir
  las **6 categorías** (`FITNESS`, `NUTRICION`, `ENTRENAMIENTO`, `ANATOMIA`, `SUPLEMENTACION`,
  `CULTURA_FITNESS`) + `INSERT` de **~150 preguntas nuevas** (ES+EN, 4 opciones, respuesta correcta,
  dificultad), repartidas entre las 6 categorías (~33 cada una contando las 50 existentes). Idempotente en
  la medida de lo posible (seeds con `INSERT`; si hace falta re-ejecutar, cuidar duplicados).
- **`TriviaPregunta`** (record en `db/`): id, categoria, dificultad, textos y opciones del idioma resuelto,
  letra correcta. El repo devuelve ya el idioma pedido (o ambos y el service elige).
- **`TriviaRepositorio`** (JDBC): `aleatoriaNoRespondida(discordId, categoriaOpt) -> Optional<TriviaPregunta>`
  (SQL con `NOT EXISTS` sobre `trivia_respuestas` + `ORDER BY RAND() LIMIT 1`), `porId(id)`,
  `registrarRespuesta(discordId, preguntaId, acierto)`, `yaRespondida(discordId, preguntaId)`,
  `aciertosDe(discordId) -> int`, `total(categoriaOpt) -> int`, `ranking(limite) -> List<FilaTrivia>`.
- **`TriviaService`**: `siguiente(actorId, categoria)`; `responder(actorId, preguntaId, letra) -> Resultado`
  (gate one-shot: si `yaRespondida` → estado YA_RESPONDIDA; compara letra con la correcta; en acierto aplica
  premio con `EconomiaRepositorio.ingresar` + XP; registra la respuesta SIEMPRE, acierto o fallo). Premios
  por dificultad como constantes puras. Azar inyectable si hace falta para tests deterministas del reparto.
- **`TriviaComando`** (`commands/comunidad/` o `gamificacion/`, `implements ComandoAutocompletable` no hace
  falta; usa `addChoice` para `categoria`): subcomandos implícitos — `/trivia [categoria]` (jugar) y
  `/trivia ranking`. Respuesta de juego **efímera**; el ranking público.
- **`TriviaListener`** (`events/`): botón `trivia:resp:<preguntaId>:<letra>`; solo el autor (la interacción
  es efímera, solo él la ve) responde; llama a `service.responder`, edita el embed con el resultado.
- **XP:** aplicar XP con el servicio de XP existente (comprobar la API de `XpService`/`XpRepositorio` para
  conceder XP directo; si no hay método, añadir uno mínimo).
- **i18n ES+EN:** claves del comando, botones, resultado (acierto/fallo con la correcta), premio, ranking,
  «no quedan preguntas», nombres de las 6 categorías (`trivia.categoria.<minúsculas>`).
- **Canal `🧠・trivia`:** ya existe con intro «próximamente» (`SetupServidorPlan`); actualizar su intro para
  explicar `/trivia` y `/trivia ranking`.
- **Embed:** `EmbedFactory.Tipo.TRIVIA` (🧠) ya reservado.

## Números (constantes, tunables)

| Dificultad | Coins | XP |
|---|---|---|
| fácil | 40 | 20 |
| media | 80 | 35 |
| difícil | 120 | 50 |

Cooldown de `/trivia`: ~30-60 s por usuario. Banco objetivo: ~200 preguntas, 6 categorías.

## Tests

- **`TriviaService`** (Mockito): responder con letra correcta → estado ACIERTO + `economia.ingresar` con el
  premio de esa dificultad + XP + `registrarRespuesta(...,true)`; letra incorrecta → FALLO, sin premio,
  `registrarRespuesta(...,false)`; pregunta ya respondida → YA_RESPONDIDA sin premio ni doble registro;
  premios por dificultad correctos.
- **`TriviaRepositorio`** (Testcontainers): `aleatoriaNoRespondida` excluye las ya respondidas y filtra por
  categoría; `registrarRespuesta` es one-shot (PK); `aciertosDe`/`ranking` cuentan bien; el CHECK ampliado
  admite las 6 categorías; las semillas nuevas cargan.
- **Banco de preguntas:** un test de sanidad (Testcontainers o de recurso) que verifique que todas las
  preguntas tienen las 4 opciones no vacías en ES y EN y una `correcta` válida (A-D) — evita seeds rotas.
- Baseline actual: 648 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código)

- **ADR-028** — trivia jugable (comprobar que el último ADR es 027).
- `docs/architecture.md`: viñeta F4 trivia; migraciones a V38; `TriviaListener` en `events/`.
- `CHANGELOG.md`: entrada F4 trivia.
- `README.md` / `README.en.md`: nuevos comandos `/trivia` (jugar · ranking).
- Actualizar `docs/architecture-map.json` + `docs/architecture-map.html` (nuevo comando/listener/migraciones;
  regla de mantener el mapa al día).

## Despliegue

Reiniciar bot (V37 + V38 + comando `/trivia` + `TriviaListener`) y re-ejecutar `/setup` (refrescar la intro
del canal `🧠・trivia`). Añade un slash command → reiniciar para re-registrarlo. Smoke: `/trivia` de cada
categoría (efímera, 4 botones), acierto cobra coins+XP y sube el marcador, fallo consume la pregunta sin
premio, una pregunta ya respondida no reaparece, `/trivia ranking` lista el top, y al responderlas todas
avisa de que no quedan.

## Fuera de alcance

- Quiz de varias preguntas encadenadas; trivia del día común a todos; apuestas sobre trivia.
- Alta de preguntas por comando (staff): el banco vive en seeds; ampliar es una migración nueva.
- Reinicio/temporadas del ranking de trivia.

## Orden de implementación (subagent-driven)

- **T1**: migración **V37** (`trivia_respuestas`) + `TriviaPregunta` + `TriviaRepositorio` + tests
  (Testcontainers). (El banco ampliado va en T4, en paralelo conceptual, pero como migración V38 separada.)
- **T2**: `TriviaService` (siguiente/responder, one-shot, premio) + tests. **Review** (dinero: premio una
  sola vez, one-shot correcto).
- **T3**: `TriviaComando` + `TriviaListener` + i18n ES+EN + intro del canal + wiring en `main()`.
- **T4**: migración **V38** (ampliar CHECK a 6 categorías + sembrar ~150 preguntas ES+EN) + test de sanidad
  del banco. (Contenido; puede trocearse por categorías.)
- **T5**: docs (ADR-028, architecture, CHANGELOG, READMEs, **mapa de arquitectura**) + `clean verify` final.
