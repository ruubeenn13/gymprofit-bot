# Empresas Fase 4 — Estatus (diseño)

**Fecha:** 2026-07-24
**Fase:** F4 del roadmap de empresas (SPEC §5; sobre F1 fundar/pertenecer, F2 gobernanza, F3 economía).
**Precondición:** F3 desplegada y funcionando (regla de fases del `CLAUDE.md`).

## Objetivo

Dar a las empresas **estatus visible** y un espacio propio, cerrando la escalera de empresas antes de
saltar a las piezas grandes de F5 (producción, impuestos, acciones…). Dos piezas:

1. `/empresa ranking` — tabla pública de prestigio.
2. **Canal privado por empresa** (permisos de miembro, sin gastar cupo de roles), para **todas** las
   empresas, con creación perezosa.

Los **cosméticos comprables** quedan **fuera de F4** (mini-fase posterior, cuando el ranking ya dé
sentido a competir por estatus).

## Pieza 1 — `/empresa ranking`

### Prestigio (función pura)

`Prestigio.calcular(int nivel, int numMiembros, long bote) -> long`:

```
prestigio = nivel * 10_000 + numMiembros * 1_000 + bote / 1_000
```

- **Nivel domina**: es 0–10, se gana quemando bote en `/empresa mejorar`; cada nivel es un salto grande.
- **Miembros** premia el tamaño real de la plantilla.
- **Bote** pesa poco **a propósito**: divide entre 1.000, para no premiar acaparar dinero líquido por
  encima de una empresa bien nivelada y poblada. Pesos *tunables* si el balance en vivo pide ajuste.

Función pura, sin estado → test unitario directo (orden correcto, nivel manda sobre bote a igualdad,
empates estables).

### Repositorio

`EmpresaRepositorio.ranking(int limite) -> List<EmpresaRanking>`:
- `SELECT` de las empresas con el `COUNT` de miembros por empresa
  (`LEFT JOIN empresa_miembros ... GROUP BY e.id`).
- Devuelve un record ligero **`EmpresaRanking(String nombre, String rama, int nivel, int miembros,
  long bote, long prestigio)`**. Como hay pocas empresas, el service trae la lista, calcula el
  prestigio con `Prestigio.calcular`, ordena descendente y recorta al `limite`. (El `COUNT` en SQL; el
  orden por prestigio en memoria, para no duplicar la fórmula en SQL.)

### Comando

- **`/empresa ranking`** — sin argumentos, ranking **global** (la competición por rama es F5, cuota de
  mercado). Nuevo `case "ranking"` en el `switch` de `EmpresaComando`.
- Top **10**. Embed vía `EmbedFactory` (tipo `STATS`), con 🥇🥈🥉 para los tres primeros y el número de
  puesto para el resto, igual que `TopComando`.
- Cada fila: `<emblema puesto> **<nombre>** — <rama i18n> · Nv.<nivel> · <miembros> miembros · 💰<bote>`.
- Respuesta **pública** (visibilidad por defecto del bot).
- Si no hay ninguna empresa: mensaje `empresa.ranking.vacio`.

## Pieza 2 — Canal privado por empresa

Para **todas** las empresas (no es un privilegio por nivel). Reutiliza el patrón ya probado de
`GremioCanal` (F-ECO-5a): permisos **de miembro**, sin rol, para no gastar el cupo de roles; **todo
best-effort** (si el bot no puede gestionar canales, se registra y se sigue: la empresa existe igual).

### Migración V30

```sql
ALTER TABLE empresas ADD COLUMN canal_id BIGINT NULL;
```

- `Empresa` record gana `Long canalId`; se actualizan `SELECT_EMPRESA`, `mapearEmpresa` y todos los
  `SELECT e.id, e.rama, ...` explícitos (p. ej. `deMiembro`) para incluir `canal_id`.
- Repo: `void fijarCanal(long empresaId, long canalId)`.

### Helper `EmpresaCanal`

Clase `final` en `commands/economia` (espejo de `GremioCanal`), métodos estáticos:
- `crear(Guild, nombre, duenoId, LongConsumer onCreado)` — crea `🏢・<nombre>`, deniega
  `VIEW_CHANNEL` a `@everyone`, concede los permisos de miembro al dueño; devuelve el id por callback
  solo si se creó.
- `anadir(Guild, canalId, miembroId)` / `quitar(Guild, canalId, miembroId)` — sincroniza el override
  de un miembro.
- `eliminar(Guild, canalId)` — borra el canal.
- Permisos de miembro: los mismos que `GremioCanal.PERMISOS` (ver + escribir en su canal).

Vive en la capa comando/listener porque necesita el `Guild` de JDA; **`EmpresaGestionService` y
`EmpresaService` siguen guild-agnostic** (no conocen JDA).

### Creación perezosa y sincronización

`ensureCanal(Guild, Empresa)` (coordinador en la capa comando): si `empresa.canalId()` es `null`, crea
el canal, persiste el id con `fijarCanal`, y **resincroniza a TODOS los miembros actuales**
(`repo.miembros(empresaId)` → `anadir` cada uno). Esto materializa el canal de las empresas fundadas en
F1–F3 sin tener que hacer backfill al arrancar.

Enganches en los puntos que F1/F2 ya tienen (todos best-effort):
- **fundar** → `crear` + persistir `canal_id` + añadir al dueño (creación **inmediata**, ya hay Guild).
- **ingreso** (invitación aceptada / solicitud aprobada) → `ensureCanal` + `anadir(miembro)`.
- **dimitir / sacar / despedir** → `quitar(miembro)`.
- **disolver** → `eliminar`.
- **`/empresa info`** (cualquier miembro lo corre) → `ensureCanal`, para que las empresas viejas
  materialicen su canal la primera vez que alguien las mira. `/empresa ranking` **no** dispara ensure
  (es una vista global de muchas empresas, no el contexto de una).
- **cambiar rango** → sin efecto en el canal.

## i18n (ES + EN, obligatorio en ambos)

- `comando.empresa.ranking.desc` (descripción del subcomando).
- `empresa.ranking.titulo`, `empresa.ranking.fila`, `empresa.ranking.vacio`.
- Reusa las claves `rama.<rama>` ya existentes para el nombre bonito de la rama.

## Tests

- **`Prestigio.calcular`**: orden correcto, nivel manda sobre bote a igualdad de nivel/miembros,
  empates estables, valores 0.
- **`EmpresaRepositorio.ranking`**: Testcontainers (como el resto de tests de repos con BD) — cuenta de
  miembros correcta, empresas sin miembros con `miembros=0`.
- **Lógica de `ensureCanal`**: la parte pura/decisoria (¿crear?, ¿a quién añadir/quitar?) se prueba;
  la interacción real con JDA es best-effort y queda **pendiente de smoke test en vivo**.
- Baseline actual: 525 tests. `./mvnw clean verify` verde con lo nuevo.

## Documentación (mismo commit que el código que la afecta)

- **ADR-019** — estatus de empresas (ranking de prestigio + canal privado perezoso). Comprobar que el
  último ADR es el 018.
- `docs/architecture.md`: viñeta F4 en el bloque de empresas; `canal_id`/V30 en migraciones (V6–V30).
- `CHANGELOG.md`: entrada de F4 bajo `## [Sin publicar]` / `### Añadido`.
- `README.md` / `README.en.md`: `/empresa` suma `ranking`.

## Despliegue

Al cerrar F4: **reiniciar bot** (aplica V30 + subcomando `ranking` + hooks de canal). **No** requiere
`/setup`. Smoke test en vivo: `/empresa ranking`, materialización del canal de una empresa vieja al
correr `/empresa info`, y sincronización al entrar/salir/echar/disolver.

## Fuera de alcance (F4)

- Cosméticos comprables (emblema/color) → mini-fase posterior.
- Ranking por rama / cuota de mercado → F5 (reputación y competencia).
- Cualquier canal de voz o hilos: F4 es un único canal de texto por empresa.

## Orden de implementación (subagent-driven con review de la lógica de riesgo)

- **T1**: V30 + `Empresa.canalId` + repo (`fijarCanal`, `ranking`, `EmpresaRanking`) + `Prestigio` +
  tests.
- **T2**: `/empresa ranking` (comando + embed) + i18n.
- **T3**: `EmpresaCanal` + `ensureCanal` + enganches en fundar/ingreso/dimitir/sacar/despedir/disolver/
  info. **Lleva review** (toca permisos de canal y sincronización de estado).
- **T4**: docs (ADR-019, architecture, CHANGELOG, READMEs) + `clean verify` final.
