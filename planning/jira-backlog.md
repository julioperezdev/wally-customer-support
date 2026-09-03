# Backlog inicial para Jira — Space `WCS`

El Space Jira `WCS` fue creado el 2026-08-29 y el backlog inicial fue cargado. Las keys reales quedaron registradas debajo; los identificadores `WCS-BL-*` son referencias estables del blueprint.

## Mapeo de keys reales

| Blueprint | Jira |
| --- | --- |
| EPIC-WCS-01 | WCS-1 |
| EPIC-WCS-02 | WCS-2 |
| EPIC-WCS-03 | WCS-3 |
| EPIC-WCS-04 | WCS-4 |
| EPIC-WCS-05 | WCS-5 |
| EPIC-WCS-06 | WCS-6 |
| WCS-BL-01 | WCS-9 |
| WCS-BL-02 | WCS-7 |
| WCS-BL-03 | WCS-8 |
| WCS-BL-04 | WCS-10 |
| WCS-BL-05 | WCS-11 |
| WCS-BL-06 | WCS-12 |
| WCS-BL-07 | WCS-13 |
| WCS-BL-08 | WCS-14 |
| WCS-BL-09 | WCS-15 |
| WCS-BL-10 | WCS-16 |
| WCS-BL-11 | WCS-17 |
| WCS-BL-12 | WCS-18 |
| WCS-BL-13 | WCS-19 |
| WCS-BL-14 | WCS-20 |
| WCS-BL-15 | WCS-21 |
| WCS-BL-16 | WCS-22 |
| WCS-BL-17 | WCS-23 |
| WCS-BL-18 | WCS-29 |

## EPIC-WCS-01 — Gobierno y documentación

### WCS-9 — Definir espacio Confluence y matriz de fuentes de verdad

**Tipo:** Task · **Prioridad:** Highest · **Estimate:** 3d

**Objetivo:** crear el Home y el árbol documental del proyecto.

**Criterios de aceptación:**

- Existe el Home de WCS con owner, estado, roadmap y enlaces.
- Existe la matriz Confluence/Jira/repo.
- Cada página tiene owner, estado y fecha de revisión.
- Se documenta la limitación de acceso a Confluence si continúa.

**Pruebas/evidencia:** URL de páginas, captura de jerarquía y revisión de enlaces.

### WCS-7 — Configurar workflow, templates y Definition of Done de Jira

**Tipo:** Task · **Prioridad:** Highest · **Estimate:** 2d · **Depends on:** WCS-9

**Criterios de aceptación:** workflow, template de issue, labels/components y reglas de evidencia documentados.

### WCS-8 — Publicar playbook de agentes y onboarding del proyecto

**Tipo:** Task · **Prioridad:** High · **Estimate:** 1d · **Depends on:** WCS-9

## EPIC-WCS-02 — Definición funcional

### WCS-10 — Validar casos de uso y políticas de atención de la tienda

**Tipo:** Story · **Priority:** Highest · **Estimate:** 5d

**Criterios de aceptación:** FR/UC aceptados para consultas, desconocido, escalamiento, horarios, privacidad, pedidos y devoluciones.

### WCS-11 — Definir fuente de conocimiento y política de respuestas

**Tipo:** Task · **Priority:** Highest · **Estimate:** 3d · **Depends on:** WCS-10

### WCS-12 — Aprobar matriz de pruebas funcionales y evidencia

**Tipo:** Task · **Priority:** High · **Estimate:** 2d · **Depends on:** WCS-10

## EPIC-WCS-03 — Fundaciones y mock

### WCS-13 — Crear esqueleto Spring Boot, PostgreSQL y Flyway

**Tipo:** Task · **Priority:** High · **Estimate:** 3d · **Depends on:** WCS-10

### WCS-14 — Implementar modelo de conversación, mensajes e idempotencia

**Tipo:** Story · **Priority:** High · **Estimate:** 5d · **Depends on:** WCS-13

### WCS-15 — Implementar webhook mock, Mock LLM y Mock WhatsApp

**Tipo:** Story · **Priority:** High · **Estimate:** 5d · **Depends on:** WCS-14

### WCS-16 — Agregar tests de contrato, integración y ciclo mock completo

**Tipo:** Task · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-15

## EPIC-WCS-04 — Meta WhatsApp

### WCS-17 — Implementar verificación y firma HMAC del webhook

**Tipo:** Story · **Priority:** Highest · **Estimate:** 3d · **Depends on:** WCS-16

### WCS-18 — Implementar cliente Meta con RestClient y ventana de atención

**Tipo:** Story · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-17

### WCS-19 — Ejecutar prueba controlada con número de Meta

**Tipo:** Task · **Priority:** High · **Estimate:** 2d · **Depends on:** WCS-18

## EPIC-WCS-05 — LLM y operación

### WCS-20 — Orquestar intenciones, seleccionar modelo y definir guardrails

**Tipo:** Task · **Priority:** Highest · **Estimate:** 4d · **Depends on:** WCS-11

El vertical slice incluye un `ConversationIntentClassifier` detrás de un puerto,
un `ConversationOrchestrator` y routing a catálogo, horarios, políticas, saludo,
handoff y soporte general. Bedrock sólo clasifica y extrae filtros
estructurados; los casos de uso consultan las fuentes autorizadas y no reciben
SQL ni hechos generados por el modelo.

### WCS-21 — Implementar integración real del LLM y métricas de costo/latencia

**Tipo:** Story · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-20, WCS-16

### WCS-22 — CI/CD, secrets, observabilidad, smoke y rollback

**Tipo:** Task · **Priority:** High · **Estimate:** 6d · **Depends on:** WCS-18, WCS-21

La base inicial de Terraform, ECR, AppConfig, Secrets Manager, OIDC y los
workflows de validación/deploy queda versionada en `infra/`, `.github/` y
`ci/`. El loader inicial de AppConfig y la resolución allow-listed de
Secrets Manager quedan implementados en WCS-22, con bypass local y fail-fast
productivo. Esto no autoriza un `apply`; la activación productiva requiere
configurar referencias reales, red, plan, deploy, health y smoke como
evidencia.

### WCS-29 — Agregar canal Telegram desacoplado e infraestructura de credenciales

**Tipo:** Task · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-14, WCS-15, WCS-22

Agregar Telegram como canal de desarrollo y prueba sin que el dominio ni los
casos de uso conozcan Telegram o WhatsApp. Incluye adapter inbound/outbound,
enrutamiento del outbox por canal, webhook protegido por secret token,
configuración AppConfig, secret `wcs/{environment}/telegram`, permiso IAM
mínimo, migración de identificadores externos genéricos y tests/documentación.

Fuera de alcance: reemplazar WhatsApp, grupos/archivos/pagos de Telegram,
Bedrock/RAG, cambio de RDS o publicación productiva.

## EPIC-WCS-06 — Piloto

### WCS-23 — Ejecutar piloto y validar métricas de soporte

**Tipo:** Story · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-22

**Métricas:** resolución, handoff, fallback, latencia, costo, fallos, duplicados y feedback.

## Orden de ejecución vigente — 2026-09-03

El backlog se reordenó por dependencia funcional y por el estado real del
repositorio. La rama de trabajo parte de `main` y concentra el primer vertical
slice de datos de negocio en `WCS-25`; `WCS-28` se implementa junto con él
porque ambos comparten migraciones, seeds y consulta de configuración.

| Orden | Jira | Estado al iniciar | Entrega | Dependencias relevantes |
| ---: | --- | --- | --- | --- |
| 1 | WCS-25 | In Progress | Catálogo demo, variantes, stock, precio, referencia S3 y consulta determinística | WCS-10, WCS-11, WCS-13 |
| 1 | WCS-28 | In Progress | Horarios y políticas demo versionadas/configurables | WCS-11, WCS-13 |
| 2 | WCS-26 | To Do | Seguimiento humano priorizado, opt-out y retención | WCS-25 |
| 3 | WCS-27 | To Do | Recepción rápida y procesamiento asíncrono durable | WCS-25 |
| 4 | WCS-20 | To Do | Prompt, selección de modelo y guardrails | WCS-11, WCS-25, WCS-28 |
| 5 | WCS-21 | To Do | Bedrock real, métricas de costo y latencia | WCS-20, WCS-27 |
| 6 | WCS-18 | To Do | Cliente Meta real detrás del adapter | WCS-17, WCS-27 |
| 7 | WCS-19 | To Do | Prueba controlada end-to-end con Meta | WCS-18, WCS-26, WCS-27 |
| 8 | WCS-22 | In Progress | Deploy, health, smoke, observabilidad y rollback productivo | WCS-18, WCS-21 |
| 9 | WCS-29 | In Progress | Canal Telegram, routing por canal, webhook, secret y configuración AWS | WCS-14, WCS-15, WCS-22 |

`WCS-14`–`WCS-16` y `WCS-17` ya tienen la fundación o el contrato inicial
versionado; deben completarse/verificarse según la evidencia de cada issue antes
de cerrar las dependencias. El orden no implica cerrar automáticamente issues:
cada transición requiere pruebas, evidencia y PR asociado.

## Template común de issue

Cada issue real debe agregar contexto, objetivo, alcance, fuera de alcance, FR/UC, criterios Given/When/Then, pruebas, evidencia, dependencias, riesgos, documentación, rollout y rollback.
