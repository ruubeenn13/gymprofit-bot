# `jobs/`

**Español**

Tareas programadas: ejercicio del día (8:00), retos semanales (lunes 9:00), sync de logros de
usuarios vinculados, resets de ranking. **Zona horaria:** cada job fija `Europe/Madrid`
explícitamente; los contenedores de Render van en UTC, así que no se confía en la TZ del sistema
(SPEC §3). Los fallos de job se reportan al canal `#bot-logs` (§14).

_Ejemplo:_ `EjercicioDiaJob` publica a las 08:00 `Europe/Madrid` el ejercicio del día en el canal
`canal_ejercicio_dia` de cada servidor configurado, en el idioma del servidor. Se reprograma solo
en hora local (aguanta los cambios de horario) y, si la API no responde, reintenta a los 30 min sin
repetir los servidores ya publicados.

---

**English**

Scheduled tasks: exercise of the day (8:00), weekly challenges (Monday 9:00), achievement sync
for linked users, ranking resets. **Time zone:** each job pins `Europe/Madrid` explicitly;
Render containers run in UTC, so the system TZ is never trusted (SPEC §3). Job failures are
reported to the `#bot-logs` channel (§14).

_Example:_ `EjercicioDiaJob` posts the exercise of the day at 08:00 `Europe/Madrid` in each
configured server's `canal_ejercicio_dia`, in the server's language. It reschedules itself in local
time (so DST changes don't shift it) and, if the API is down, retries in 30 min without
re-posting to the servers already served.
