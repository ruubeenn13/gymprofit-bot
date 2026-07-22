# Operación — arrancar, reiniciar y desplegar GymProBot

Guía práctica para el día a día: cómo tener el bot funcionando, qué hacer **cada vez que hay
cambios de código**, y cómo dejarlo **24/7**. Léela cuando no recuerdes el flujo.

> **TL;DR**
> - Ahora mismo el bot corre **en tu PC** (una terminal). **No es 24/7**: se apaga al cerrar la
>   terminal o apagar el equipo.
> - **No hay recarga en caliente.** Cada cambio de código = **recompilar + rearrancar**.
> - Para **24/7 real** hay que **desplegar en Render** (git push → build + deploy automático).

---

## 1. ¿Dónde corre y cómo me conecto?

El bot es un proceso Java (`java -jar`). No te "conectas" a él como a un servidor: **se conecta él
solo a Discord** por la gateway (WebSocket) con el `DISCORD_TOKEN`. Tú solo lo **arrancas** y ves sus
**logs** en la terminal.

- **Estado / salud:** expone `http://localhost:8080/health` (útil para comprobar que vive; en Render
  lo usa el keep-alive).
- **Está "activo" mientras el proceso esté vivo.** Si ves los logs corriendo, está online en Discord.

---

## 2. Arrancar / reiniciar en LOCAL (desarrollo)

### Opción más fácil (doble clic)

**Doble clic en `scripts\arrancar-bot.bat`.** Hace todo (JDK 21, compilar, `.env`, arrancar)
delegando en `run-local.ps1`; la ventana se queda abierta al terminar para poder leer errores.
**Parar:** `Ctrl+C` en esa ventana.

### Opción fácil (script)
```powershell
.\scripts\run-local.ps1
```
Compila, carga el `.env` y arranca. Para no recompilar: `.\scripts\run-local.ps1 -SkipBuild`.
**Parar:** `Ctrl+C` en la terminal.

### Opción manual (si el script falla)
```powershell
cd C:\Users\ruben\Desktop\gymprofit-bot
$env:JAVA_HOME = "$HOME\.jdks\ms-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -DskipTests package
```
Luego exporta las variables del `.env` (o las pones a mano) y:
```powershell
java -jar target/gymprofit-bot.jar
```

**Requisitos:** JDK 21 en `~/.jdks/ms-21.0.11` y un `.env` relleno (copia de `.env.example`; DB de
Aiven, token del bot de test). El `.env` está gitignoreado: nunca se commitea.

---

## 3. Cada vez que hay CAMBIOS de código

No hay hot-reload. El flujo es siempre:

1. **Para** el bot (`Ctrl+C`).
2. **Recompila y arranca** de nuevo:
   ```powershell
   .\scripts\run-local.ps1
   ```
3. Si cambiaste **slash commands** (nombre, opciones, permisos), Discord tarda un poco en refrescar
   la definición; reinicia el cliente de Discord si no los ves.
4. Si cambiaste el **esquema de BD**, se añade una **migración Flyway** nueva (`V4__…`) — Flyway la
   aplica sola al arrancar. Nunca se edita una migración ya aplicada.

> Cambios que **solo** tocan la estructura del server (canales, roles, AutoMod) se prueban con
> `/setup desde_cero:true` en el servidor de test tras rearrancar.

---

## 4. Dejarlo 24/7 (producción → Render)

El proyecto trae `Dockerfile` (multi-stage) y `render.yaml` (blueprint). En Render el bot corre en
segundo plano y **se redepliega en cada push**.

1. **Variables de entorno en Render** (dashboard del servicio → *Environment*), NO en el `.env`:
   `DISCORD_TOKEN` (bot de **producción**, distinto al de test), `DB_URL`, `DB_USER`, `DB_PASSWORD`,
   `GYMPROFIT_API_URL`, `PORT`, `TZ`. (Bloque PROD comentado en `.env` como referencia.)
2. **Desplegar:** haz *merge* a la rama que Render observa (normalmente `main`) o pulsa *Manual
   Deploy*. Render construye la imagen Docker y arranca el contenedor.
3. **Logs:** en el dashboard de Render (pestaña *Logs*). Deben salir Flyway `V1…Vn` + `Conectado a
   Discord como …`.
4. **Keep-alive:** en plan free, Render duerme los servicios inactivos; el health server + el cron de
   `keep-alive.yml` pingan `/health` para mantenerlo despierto. Ver ADR sobre hosting en
   [`decisions.md`](decisions.md).

> **Importante:** producción y test deben usar **bots/tokens distintos** e, idealmente, **bases de
> datos separadas** (p. ej. `gymprofit_bot_prod`), para no mezclar datos.

---

## 5. Checklist rápido

| Quiero… | Hago… |
|---|---|
| Arrancar en local | `.\scripts\run-local.ps1` |
| Rearrancar tras un cambio | `Ctrl+C` → `.\scripts\run-local.ps1` |
| Arrancar sin recompilar | `.\scripts\run-local.ps1 -SkipBuild` |
| Parar el bot | `Ctrl+C` en la terminal |
| Ver si está vivo | logs corriendo, o `http://localhost:8080/health` |
| Ponerlo 24/7 | push a `main` → deploy en Render |
| Cambiar esquema de BD | nueva migración Flyway `V4__…` (se aplica sola al arrancar) |

---

## 6. Problemas típicos

- **`'mysql' no se reconoce`** → usa `mysqlsh` (MySQL Shell) con `--sql --host=… --port=… --user=… --password --ssl-mode=REQUIRED`.
- **`Access denied for user`** → el usuario de BD no tiene permiso sobre `gymprofit_bot` (revisa el GRANT en Aiven).
- **`Cannot delete a channel required for community servers`** → normal con Comunidad activada; `/setup desde_cero` ya lo maneja (canal temporal). Si quedaran huérfanos, reasigna a mano los canales de comunidad y bórralos.
- **El bot arranca pero no responde comandos** → mira que tenga rol **Administrador** en el server y que la conexión ponga `Finished Loading!`.
- **`java` es la versión 8** → no exportaste `JAVA_HOME` al JDK 21 (el script lo hace por ti).
