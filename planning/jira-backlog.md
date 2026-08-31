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

### WCS-20 — Seleccionar modelo, versionar prompt y definir guardrails

**Tipo:** Task · **Priority:** Highest · **Estimate:** 4d · **Depends on:** WCS-11

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

## EPIC-WCS-06 — Piloto

### WCS-23 — Ejecutar piloto y validar métricas de soporte

**Tipo:** Story · **Priority:** High · **Estimate:** 4d · **Depends on:** WCS-22

**Métricas:** resolución, handoff, fallback, latencia, costo, fallos, duplicados y feedback.

## Template común de issue

Cada issue real debe agregar contexto, objetivo, alcance, fuera de alcance, FR/UC, criterios Given/When/Then, pruebas, evidencia, dependencias, riesgos, documentación, rollout y rollback.
