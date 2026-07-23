# Registro de cambios de `/setup` — diseño

**Fecha:** 2026-07-23
**Estado:** aprobado (dirección delegada por el usuario: «haz lo que veas mejor»)

## Problema

`/setup` y `/setup desde_cero` configuran el servidor pero al terminar solo muestran un embed
con **contadores** (`setup.resumen`: nº de roles del plan, canales creados, limpiados, reglas
AutoMod). No dice **qué** cambió: qué rol/canal/intro es nuevo, cuál se actualizó, cuál se eliminó.
El usuario quiere un **registro** por ejecución, con nombres, para llevar la cuenta de todo lo que
se va añadiendo, actualizando, eliminando y limpiando —incluyendo intros de canal y la descripción
del servidor.

## Decisiones (del brainstorming)

1. **Destino:** el informe va en la **respuesta** del comando **y**, además, como entrada
   persistente y fechada en el canal **`bot-logs`** (`TipoCanal.BOT_LOGS`).
2. **Detalle:** solo los **cambios reales**, con **nombres**, agrupados por tipo (🆕 nuevos /
   ✏️ actualizados / 🗑️ eliminados). Lo que ya estaba igual no aparece. Si no cambió nada:
   «sin cambios».
3. **Qué cuenta como cambio:** para lo que `/setup` reaplica cada vez (intros, descripción del
   servidor, welcome), se marca como actualizado **solo si el contenido difiere** del actual
   (comparación de contenido, no «reaplicado»).

## Alcance

Cubre todo lo que toca `/setup`: roles, categorías, canales, intros fijadas, reglas AutoMod,
pantalla de bienvenida (welcome screen), canal AFK, anclas de Comunidad, paneles (roles/ticket) y
—**nuevo**— la **descripción del servidor** (hoy `/setup` no la gestiona; se añade su gestión con
diff de contenido, porque el usuario la incluyó explícitamente en el registro).

## Arquitectura

### 1. `RegistroCambios` (clase nueva, colector puro y testeable)

Paquete `com.gymprofit.bot.commands.admin`. No conoce JDA ni i18n de render: solo acumula.

```java
public final class RegistroCambios {
    public enum Categoria { ROL, CATEGORIA, CANAL, INTRO, DESCRIPCION_SERVIDOR,
                            WELCOME, AFK, AUTOMOD, ANCLA, PANEL }
    public enum Tipo { CREADO, ACTUALIZADO, ELIMINADO }

    public void creado(Categoria cat, String nombre)      { … }
    public void actualizado(Categoria cat, String nombre) { … }
    public void eliminado(Categoria cat, String nombre)   { … }

    public boolean huboCambios();
    public int cuenta(Tipo tipo);                 // para la línea de contadores
    /** Entradas (tipo, categoría, nombre) en orden de registro, para render externo. */
    public List<Entrada> entradas();
    public record Entrada(Tipo tipo, Categoria categoria, String nombre) {}
}
```

Se instancia al inicio de `ejecutar` y se pasa por referencia a cada helper.

### 2. Instrumentación (dónde se detecta el cambio)

Cada helper de `SetupComando`/`SetupServidorPlan` recibe el `RegistroCambios` y registra:

| Helper | Regla |
|---|---|
| `crearRoles` | `creado(ROL)` si no existía; si existe, comparar color → `actualizado(ROL)` si difiere. |
| `vaciarServidor` (desde_cero) | `eliminado(CANAL/CATEGORIA, nombre)` por cada borrado real. |
| `crearCategoriasYCanales` / `crearCanal` | `creado(CATEGORIA/CANAL)` si nuevo; si reutilizado, comparar topic/slowmode/config → `actualizado(CANAL)` si difiere. |
| `fijarIntro` (canal nuevo) | `creado(INTRO, canal)`. |
| `actualizarIntro` (canal reutilizado) | leer el embed fijado por el bot y comparar su descripción con la nueva → `actualizado(INTRO)` **solo si difiere**; si es idéntica, no editar (ahorra llamada) y no registrar. |
| `configurarBienvenida` | leer welcome screen actual, comparar descripción + lista de canales → `creado`/`actualizado(WELCOME)` si difiere. |
| `configurarDescripcionServidor` (**nuevo**) | fijar `guild` description desde i18n `setup.descripcion_servidor`; comparar con la actual → `creado`/`actualizado(DESCRIPCION_SERVIDOR)`. |
| `configurarAfk` | comparar canal AFK + timeout → `actualizado(AFK)` si difiere. |
| `crearReglasAutoMod` | `creado(AUTOMOD, nombre)` por regla nueva. |
| anclas de Comunidad | `creado`/`actualizado(ANCLA)`. |
| paneles (roles/ticket) | `creado(PANEL)` si se publica uno nuevo; si ya hay panel fijado, no re-registrar. |

Comparaciones **null-safe**: si no hay valor actual (intro/descripción inexistente), cuenta como
`creado`.

### 3. Render y entrega

- **Informe** (texto localizado):
  - Cabecera: servidor, ejecutado por (mención), modo (`normal` / `desde_cero`), fecha
    (`EmbedFactory.fechaLarga`).
  - Línea de contadores: `🆕 {n} · ✏️ {m} · 🗑️ {k} · 🧹 {limpiados}`.
  - Bloques 🆕 Nuevos / ✏️ Actualizados / 🗑️ Eliminados; dentro, agrupado por categoría con el
    nombre de cada ítem. Si `!huboCambios()` → línea única «sin cambios».
- **Respuesta al invocador:** `EmbedFactory.base(STATS, …)`, **troceada** a ≤4096 con el util
  compartido (ver §4) por si el informe es largo (un `desde_cero` crea decenas de ítems). Enviada
  por `getHook()` (pública, como el resumen actual). Sustituye al `setup.resumen` de hoy.
- **`bot-logs`:** localizar el canal `BOT_LOGS`; publicar el mismo informe como entrada persistente
  (también troceada). Si no se encuentra el canal, `log.warn` y seguir (no romper el setup).

### 4. Util compartido de troceo

Extraer `partirEnBloques(List<String>, int)` —hoy en `TrabajoComando` (fix reciente de
`/trabajo lista`)— a `com.gymprofit.bot.util.Embeds`. `TrabajoComando` y `SetupComando` lo usan.
Se mueve también su test.

## i18n (ES + EN, mismas claves)

`setup.registro.titulo`, `setup.registro.sincambios`, `setup.registro.cabecera` (servidor/por/modo/
fecha), `setup.registro.modo.normal`, `setup.registro.modo.desde_cero`, `setup.registro.contadores`,
`setup.registro.nuevos`, `setup.registro.actualizados`, `setup.registro.eliminados`,
`setup.registro.categoria.{rol,categoria,canal,intro,descripcion_servidor,welcome,afk,automod,ancla,panel}`,
y `setup.descripcion_servidor` (texto de la descripción del servidor, incluye el correo de soporte
`gymprofit.soporte@gmail.com`). Se retira/reutiliza `setup.resumen`.

## Manejo de errores

- `bot-logs` ausente → `log.warn`, no falla el setup.
- Interacción expirada (setup largo) → el `catch`/callback de error actual ya lo cubre.
- Comparaciones de contenido null-safe.

## Testing

- **`RegistroCambios`** (JUnit 5): registrar entradas, `huboCambios()`, `cuenta(tipo)`, orden de
  `entradas()`, y el caso «sin cambios».
- **`Embeds.partirEnBloques`**: tests movidos desde `TrabajoComando` (límite, no parte líneas,
  bloque único si cabe).
- **Render del informe** (función pura de `entradas()`+locale → líneas): agrupación por tipo y
  categoría, «sin cambios», contadores.
- Diff de contenido y wiring de `/setup`: **Discord en vivo** (smoke test), no unit-testeable.

## Despliegue

Toca la salida de `/setup` y añade gestión de la descripción del servidor → **reiniciar bot** +
**`/setup`**. Sin migración Flyway.

## Fuera de alcance (YAGNI)

- Historial acumulado más allá de las entradas en `bot-logs` (el canal ya es el histórico).
- Diff campo a campo de permisos de canal (se compara config visible: topic/slowmode; permisos no).
- Panel de diferencias interactivo o exportación a fichero.
```
