# `jobs/`

**Español**

Tareas programadas: ejercicio del día (8:00), retos semanales (lunes 9:00), sync de logros de
usuarios vinculados, resets de ranking. **Zona horaria:** cada job fija `Europe/Madrid`
explícitamente; los contenedores de Render van en UTC, así que no se confía en la TZ del sistema
(SPEC §3). Los fallos de job se registran **en el log del proceso**; el reporte al canal
`#bot-logs` que describe la SPEC (§14) todavía no está implementado.

_Ejemplo:_ `EjercicioDiaJob` publica a las 08:00 `Europe/Madrid` el ejercicio del día en el canal
`canal_ejercicio_dia` de cada servidor configurado, en el idioma del servidor. Se reprograma solo
en hora local (aguanta los cambios de horario) y, si la API no responde, reintenta a los 30 min sin
repetir los servidores ya publicados; la cadena de reintentos se abandona al cambiar de día, para
no publicar de madrugada el ejercicio de mañana.

---

**English**

Scheduled tasks: exercise of the day (8:00), weekly challenges (Monday 9:00), achievement sync
for linked users, ranking resets. **Time zone:** each job pins `Europe/Madrid` explicitly;
Render containers run in UTC, so the system TZ is never trusted (SPEC §3). Job failures go to the
**process log**; the `#bot-logs` channel reporting described in the SPEC (§14) is not implemented
yet.

_Example:_ `EjercicioDiaJob` posts the exercise of the day at 08:00 `Europe/Madrid` in each
configured server's `canal_ejercicio_dia`, in the server's language. It reschedules itself in local
time (so DST changes don't shift it) and, if the API is down, retries in 30 min without
re-posting to the servers already served; the retry chain is abandoned once the date changes, so it
never posts tomorrow's exercise in the middle of the night.
