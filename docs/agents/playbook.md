# Playbook de agentes de IA — WCS

## Skills del proyecto

- [`jira-github-traceability.md`](jira-github-traceability.md): relacionar issues Jira con branches, commits, Pull Requests, CI y evidencia.

## Antes de trabajar

1. Leer este README y la documentación relacionada.
2. Revisar el issue Jira `WCS-*` y su página canónica de Confluence.
3. Confirmar alcance, fuera de alcance, dependencias y evidencia esperada.
4. Revisar estado actual de la branch, CI y documentación.
5. Aplicar el [skill de trazabilidad Jira–GitHub](jira-github-traceability.md) antes de crear una branch o commit.

## Reglas

- No iniciar desarrollo si el issue no está en `Ready`.
- No inventar políticas de la tienda ni respuestas para datos desconocidos.
- Mantener Meta y LLM detrás de adapters.
- No registrar secretos, firmas, tokens, conversaciones ni PII innecesaria.
- No cambiar migraciones ya aplicadas.
- No agregar features fuera del alcance del issue.
- No afirmar que algo está probado, mergeado o desplegado sin evidencia.

## Al terminar

- Ejecutar tests y validaciones relevantes.
- Actualizar documentación y contratos afectados.
- Adjuntar evidencia en Jira.
- Dejar explícitos bloqueos, supuestos y limitaciones.
- Mover el issue a `In Review` sólo con PR o revisión documental completa.
- Verificar en Jira el panel **Development** después del push y registrar cualquier fallo de sincronización.
