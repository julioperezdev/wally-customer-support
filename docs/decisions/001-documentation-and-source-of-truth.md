# ADR-001 — Documentación y fuentes de verdad de WCS

## Estado

Proposed — 2026-08-28

## Contexto

El proyecto necesita crecer con varios agentes, tareas y futuras integraciones sin perder trazabilidad. El patrón de `tesis.dev` separa roadmap, documentación técnica y ejecución; WCS agrega Jira y Confluence como herramientas explícitas.

## Decisión

- Confluence será la fuente canónica de conocimiento compartido.
- Jira tendrá la ejecución con key `WCS`.
- El repositorio mantendrá la verdad ejecutable y un contexto mínimo para agentes.
- Toda tarea Jira debe enlazar requerimiento, página canónica, PR y evidencia.
- No se duplicarán contratos detallados en varios sistemas sin un owner y una regla de sincronización.

## Consecuencias

- Hay una separación clara entre dirección, ejecución y evidencia.
- Confluence requiere instalación/autorización en la instancia Atlassian.
- La key `WCS` debe reservarse para este producto y tratarse como estable.
- Las migraciones, tests y workflows siguen siendo verificables en el repositorio.
