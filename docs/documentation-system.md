# Sistema de documentación y trazabilidad — WCS

## Objetivo

Mantener una única referencia navegable para el producto sin duplicar la verdad ejecutable del repositorio. Cada cambio debe poder seguirse desde una decisión o requerimiento hasta una tarea Jira, un PR, sus pruebas y la evidencia de despliegue.

## Distribución de responsabilidades

| Información | Fuente canónica | Evidencia o vínculo |
|---|---|---|
| Visión, objetivos y alcance | Confluence | Página de producto y roadmap |
| Requerimientos y casos de uso | Confluence | IDs `FR-*` y `UC-*` |
| Trabajo, estados y dependencias | Jira `WCS` | Issues, links y changelog |
| Arquitectura y decisiones | Confluence + ADR en repo | `ADR-*`, página y archivo |
| API, migraciones, tests y CI | Repositorio | Código y workflows |
| Schema explicado | Confluence | Migraciones ejecutables en repo |
| Modelos y prompts | Confluence + archivos versionados | Registry y fixtures |
| Runbooks y operación | Confluence | Scripts, queries y workflows |
| Resultado de una tarea | Jira | PR, CI, test, smoke y logs sanitizados |

Confluence es la fuente canónica para el conocimiento compartido. El repositorio conserva una versión mínima de contexto para agentes y la evidencia ejecutable; no se mantienen dos descripciones detalladas independientes.

## Árbol de Confluence

```text
WCS — Wally Customer Support
├── 00. Home / Control Center
├── 01. Product & Business
├── 02. Conversation Design
├── 03. Architecture & Services
├── 04. Data
├── 05. AI
├── 06. Quality & Testing
├── 07. Operations
├── 08. Delivery & Cost
└── 09. AI Agents
```

Cada página debe comenzar con:

```text
Owner:
Status: Draft | Proposed | Accepted | Deprecated
Last reviewed:
Related Jira:
Related repository paths:
Decision/source:
```

## Workflow de Jira

```text
Backlog → Ready → In Progress → Blocked → In Review → Done
                                      └──────────────→ Canceled
```

Reglas:

- `Ready` requiere alcance, criterios de aceptación, pruebas, dependencia y estimación.
- `In Progress` implica branch o cambio documental identificado.
- `In Review` requiere PR o revisión documental explícita.
- `Done` requiere aceptación, evidencia y verificación post-merge cuando aplique.
- `Blocked` siempre incluye causa, owner del desbloqueo y próximo evento esperado.

## Definition of Ready

- Objetivo y valor claros.
- Alcance y fuera de alcance.
- Requerimientos/casos de uso relacionados.
- Criterios observables.
- Estrategia de pruebas y evidencia esperada.
- Dependencias, riesgos y decisiones pendientes.
- Página de Confluence vinculada.
- Estimación y owner.

## Definition of Done

- Criterios de aceptación verificados.
- Tests proporcionales al riesgo ejecutados.
- Documentación y contratos actualizados.
- PR revisado y mergeado, si hay código.
- CI verde.
- Deploy y smoke verificados, si aplica.
- Evidencia vinculada en Jira.
- Página canónica actualizada y fecha de revisión renovada.

## Evidencia permitida

- Logs de CI sin secretos ni PII.
- Reportes de tests y fixtures sanitizados.
- Screenshots de UI o consola sin números de teléfono reales.
- URLs de PR, build, release, dashboard o runbook.
- Queries reproducibles con ventana, filtros y limitaciones.

Nunca adjuntar tokens, passwords, payloads completos de clientes o conversaciones reales.

## Identificadores

| Tipo | Formato | Ejemplo |
|---|---|---|
| Requerimiento funcional | `FR-###` | `FR-001` |
| Caso de uso | `UC-###` | `UC-001` |
| No funcional | `NFR-###` | `NFR-003` |
| Decisión | `ADR-###` | `ADR-001` |
| Caso de prueba | `TC-###` | `TC-014` |
| Riesgo | `RISK-###` | `RISK-002` |

Los IDs permanecen estables aunque cambie el título de una página o issue.
