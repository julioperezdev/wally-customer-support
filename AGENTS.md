# WCS — contexto para agentes

WCS (Wally Customer Support) es un sistema de atención conversacional para una tienda, inicialmente sobre WhatsApp Cloud API.

## Antes de cambiar código

1. Leer `CONTRIBUTING.md`.
2. Leer el issue `WCS-*` de Jira y su página canónica de Confluence.
3. Revisar la documentación relacionada en `docs/`.
4. Confirmar alcance, fuera de alcance, criterios de aceptación, dependencias y evidencia.

## Reglas principales

- No iniciar desarrollo si el requerimiento y la página canónica no están aceptados.
- Mantener Meta y el proveedor LLM detrás de adapters.
- No poner secretos en el repositorio, logs, tests o respuestas.
- No registrar conversaciones completas ni PII innecesaria.
- No modificar migraciones ya aplicadas; agregar una nueva migración reversible.
- Mantener idempotencia, ownership y trazabilidad en los flujos de mensajes.
- No afirmar pruebas, merge, despliegue o disponibilidad sin evidencia verificable.

## Documentación de referencia

- Dirección y fases: `docs/roadmap.md`.
- Requerimientos: `docs/functional-requirements.md`.
- Arquitectura: `docs/architecture.md`.
- Datos: `docs/data-model.md`.
- IA: `docs/ai.md`.
- Testing: `docs/testing-strategy.md`.
- Operación: `docs/operations.md`.
- Trazabilidad: `docs/documentation-system.md`.
- Skill de relación Jira–GitHub: `docs/agents/jira-github-traceability.md`.
- Trabajo inicial: `planning/jira-backlog.md`.
