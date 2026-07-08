<!--
  Plantilla de PR de GymProBot / GymProBot PR template.
  El merge a main solo se hace con CI en verde (ver CLAUDE.md).
-->

## Qué cambia / What changes
<!-- Resumen en 1-3 líneas / 1-3 line summary -->

## Fase / Phase
<!-- F1 Núcleo · F2 Economía · F3 Vinculación · F4 Competición (SPEC §5) -->

## Checklist de review / Review checklist
> Detalle completo en [`rules/review-checklist.md`](../rules/review-checklist.md).

- [ ] `./mvnw verify` en verde (build + tests) con evidencia
- [ ] Tests para todo lo nuevo (services, cliente API con MockWebServer)
- [ ] Sin secretos en el código ni en logs
- [ ] Textos en **ES y EN** sincronizados (`messages_es`/`messages_en`)
- [ ] Permisos del comando correctos (`setDefaultPermissions` en staff) y cooldown si escribe BD/API
- [ ] Embeds solo por `EmbedFactory`, con la paleta de la categoría (SPEC §7)
- [ ] Documentación afectada actualizada **en este mismo PR** (README de carpeta, `docs/`, tablas del README, `CHANGELOG.md`)
- [ ] Dependencias nuevas justificadas en `docs/decisions.md`
