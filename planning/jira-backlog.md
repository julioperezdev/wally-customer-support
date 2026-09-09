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
| WCS-BL-19 | WCS-34 |
| WCS-BL-20 | WCS-35 |

## WCS-33 — Mantener contexto conversacional en búsquedas multi-turno

**Tipo:** Story · **Priority:** Highest · **Estimate:** 4d · **Estado:** Done

Implementar la Fase 1 del plan de memoria conversacional: conservar el estado
tipado de una búsqueda entre turnos, incorporar `productType`, fusionar filtros
de manera explícita y responder de forma segura cuando no hay coincidencias.

**Dependencias:** WCS-20, WCS-21 y WCS-25 cuando sus cambios de catálogo aún no
estén disponibles.

**Fuera de alcance:** AgentCore Memory, LangChain, LangGraph, preferencias
persistentes, resúmenes, Knowledge Base, infraestructura AWS y datos
transaccionales provenientes de memoria.

**Evidencia:** pruebas unitarias y de aplicación, Testcontainers con
PostgreSQL, contratos del clasificador, `mvn verify` y logs sanitizados.

**Documentación:**
`docs/conversational-memory-and-agentcore-plan.md`, `docs/architecture.md`,
`docs/data-model.md` y [WCS — Conversational Memory, Context & AgentCore
Plan](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/6684674/WCS+Conversational+Memory+Context+AgentCore+Plan).

## WCS-34 — Definir contrato de memoria conversacional, privacidad y retención

**Tipo:** Task · **Prioridad:** High · **Estimate:** 3d · **Estado:** In Progress

Implementar la Fase 2 del plan: contrato `ConversationMemory`, estado inmutable,
ownership por actor/conversación, límites de contexto, expiración, borrado y
adapter en memoria para pruebas. La persistencia PostgreSQL corresponde a la
Fase 3 y AgentCore continúa fuera de alcance.

**Evidencia:** tests de aislamiento/expiración/borrado/límites, `mvn verify`,
`docs/privacy-retention.md` y actualización de arquitectura, modelo y plan.

## WCS-117 — Mejorar refinamiento contextual y consultas compuestas del catálogo

**Tipo:** Story · **Prioridad:** High · **Estimate:** 4d · **Estado:** In Progress
· **Depends on:** WCS-33, WCS-20, WCS-21

Conservar filtros estructurados en seguimientos como “qué opciones tienen” y
“de lo anterior”, evitar que las respuestas outbound contaminen el contexto,
componer consultas acotadas de catálogo y envíos, y unificar el fallback de
categorías que no existen en el catálogo. PostgreSQL continúa siendo la fuente
de verdad y el LLM no genera SQL ni hechos de negocio.

**Evidencia:** tests de parser y merge, tests de aplicación para consultas
compuestas, Testcontainers para contexto inbound-only, `mvn verify`, smoke
sanitizado por Telegram y documentación de IA/observabilidad.

## WCS-35 — Persistir memoria de sesión conversacional en PostgreSQL

**Tipo:** Story · **Prioridad:** High · **Estimate:** 5d · **Estado:** In Progress

Implementar la Fase 3 del plan de memoria: migración V6, entidad y repositorio
JPA, adapter detrás de `ConversationMemory`, TTL, ownership, versión optimista,
limpieza de estados vencidos e integración tolerante a fallos con el flujo
inbound. La activación productiva queda protegida por
`wcs.conversation.memory.enabled` y requiere aprobación previa de la política
de retención.

**Fuera de alcance:** AgentCore Memory, LangChain/LangGraph, memoria semántica,
preferencias persistentes, resumen de conversaciones y cambio de RDS.

**Evidencia:** migración aplicada en Testcontainers, pruebas de reinicio,
ownership, conflicto de versión, expiración, borrado y fallback no-op; `mvn
verify`; logs sanitizados; actualización de arquitectura, modelo, operaciones,
plan de memoria y política de retención.

## WCS-36 — Implementar resumen controlado de conversaciones extensas

**Tipo:** Task · **Prioridad:** High · **Estimate:** 4d · **Estado:** In Progress ·
**Depends on:** WCS-35

Implementar la Fase 5 del plan de memoria con un resumen opcional y versionado
del prefijo antiguo, una ventana reciente separada, checkpoint de cantidad de
mensajes y fallback seguro. PostgreSQL continúa siendo la fuente de verdad; el
resumen no reemplaza filtros tipados, catálogo, stock, precio, carrito ni
pedidos.

La primera entrega agrega `ConversationSummarizer`, adapters Bedrock/mock,
configuración `wcs.conversation.summary.*`, migración V7 y eventos
sanitizados para medir tamaño, activación, éxito y fallback. El flag queda
desactivado por defecto.

**Evidencia:** tests unitarios, persistencia PostgreSQL/Testcontainers,
`mvn verify`, logs sin cuerpos completos ni PII y documentación actualizada.

## WCS-37 — Persistir preferencias explícitas de cliente

**Tipo:** Story · **Prioridad:** High · **Estimate:** 4d · **Estado:** In Progress ·
**Depends on:** WCS-34, WCS-35, WCS-36

Implementar preferencias de bajo riesgo separadas del contexto temporal. La
primera entrega soporta sólo `preferred_color`, con valores permitidos,
confirmación explícita, alcance de actor/conversación, TTL, ownership y
borrado. Las preferencias llegan al contexto como información auxiliar y no
reemplazan filtros actuales ni datos de catálogo, stock, precio, carrito o
pedidos.

Fuera de alcance: extracción automática desde texto, memoria semántica de
AgentCore, acciones sensibles y cualquier autoridad transaccional basada en
preferencias.

**Evidencia:** migración V8, adapter PostgreSQL/no-op, unit tests,
Testcontainers, `mvn verify`, configuración desactivada por defecto y
documentación de privacidad, arquitectura, datos y operación.

## WCS-38 — Integrar captura explícita de preferencias en el flujo conversacional

**Tipo:** Task · **Prioridad:** High · **Estimate:** 3d · **Estado:** In Progress ·
**Depends on:** WCS-37, WCS-20

Conectar la persistencia de WCS-37 al flujo común de inbound mediante un parser
determinístico y acotado. Sólo frases como “prefiero el negro” pueden guardar
una preferencia; “busco una remera negra” debe seguir siendo un filtro de
catálogo. La respuesta confirma el guardado y el LLM no decide qué persistir.

Fuera de alcance: extracción automática, AgentCore, preferencias sensibles y
autoridad transaccional basada en memoria.

**Evidencia:** tests del parser, flujo común de WhatsApp/Telegram,
Testcontainers, logs sanitizados y documentación actualizada.

## WCS-39 — Alinear baseline de AppConfig v5 en Terraform

**Tipo:** Task · **Prioridad:** High · **Estimate:** 1d · **Estado:** In Progress

Mantener en Terraform el baseline no sensible de AppConfig versión 5 y agregar
las cuatro claves de preferencias explícitas con sus valores aprobados. No se
incluyen secretos ni se elimina la protección `ignore_changes` que evita
sobrescribir cambios administrados desde AWS.

**Evidencia:** diff de Terraform, `terraform fmt`, `terraform validate` y
revisión de valores contra la versión 5 activa en AWS.

### WCS-40 — Agregar workflow manual para reiniciar App Runner y recargar AppConfig

**Tipo:** Task · **Prioridad:** High · **Estimate:** 1d · **Depends on:** WCS-22, WCS-39

Agregar un workflow manual que reinicie App Runner usando la imagen actualmente
configurada para que el bootstrap de WCS consuma la última versión desplegada de
AppConfig sin repetir Maven Verify, build de Docker ni push a ECR. El workflow
requiere aprobación del Environment `production`, espera la operación y valida
health.

Fuera de alcance: refresh dinámico de propiedades Spring sin restart, cambio de
imagen, modificación de secrets y cambios de Terraform.

**Evidencia:** validación del workflow, ejecución manual sin pasos de Maven o
Docker, operación App Runner, health check y documentación de rollout/rollback.

### WCS-41 — Sincronizar y validar la Knowledge Base documental de WCS

**Tipo:** Task · **Prioridad:** High · **Estimate:** 2d · **Depends on:** WCS-30, WCS-39, WCS-40 · **Estado:** In Progress

Agregar una operación manual y aprobada para iniciar la ingesta de los
documentos Markdown de WCS y verificar que la data source pertenece a la
Knowledge Base propia. El flujo reporta estadísticas sanitizadas, aplica
timeout y falla si hay documentos fallidos. No cambia PostgreSQL, catálogo,
stock, la imagen de App Runner ni la KB histórica.

**Evidencia:** workflow manual, script con timeout/estadísticas, validación de
shell/YAML/Terraform, job de ingesta exitoso y smoke tests documentales.

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

La integración de Bedrock Converse registra un evento JSON por llamada real con
operación, etapa, proveedor, model ID, tokens de entrada/salida/total, latencia,
éxito y costo USD estimado según la versión de pricing configurada. El
orquestador registra el tipo y resultado de cada consulta completa; los eventos
no contienen prompts, respuestas, secretos ni PII. Las consultas reutilizables
y el dashboard local de CloudWatch/Grafana están versionados en
`observability/grafana/` y documentados en `docs/observability.md`.

### WCS-22 — CI/CD, secrets, observabilidad, smoke y rollback

**Tipo:** Task · **Priority:** High · **Estimate:** 6d · **Depends on:** WCS-18, WCS-21

La base inicial de Terraform, ECR, AppConfig, Secrets Manager, OIDC y los
workflows de validación/deploy queda versionada en `infra/`, `.github/` y
`ci/`. El loader inicial de AppConfig y la resolución allow-listed de
Secrets Manager quedan implementados en WCS-22, con bypass local y fail-fast
productivo. Esto no autoriza un `apply`; la activación productiva requiere
configurar referencias reales, red, plan, deploy, health y smoke como
evidencia. La primera iteración de observabilidad queda documentada en
`docs/observability.md` y `observability/grafana/`: Grafana local consulta
CloudWatch read-only y el backend emite eventos operativos sanitizados.

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

## Orden de ejecución vigente — 2026-09-09

El backlog se reordenó por dependencia funcional y por el estado real del
repositorio. `WCS-33` ya resolvió la pérdida de contexto observada en búsquedas
de catálogo. `WCS-34` fijó el contrato y los límites de memoria; `WCS-35`
continúa con la persistencia PostgreSQL antes de evaluar resumen, memoria
semántica o AgentCore.

| Orden | Jira | Estado al iniciar | Entrega | Dependencias relevantes |
| ---: | --- | --- | --- | --- |
| 1 | WCS-25 | In Progress | Catálogo demo, variantes, stock, precio, referencia S3 y consulta determinística | WCS-10, WCS-11, WCS-13 |
| 1 | WCS-28 | In Progress | Horarios y políticas demo versionadas/configurables | WCS-11, WCS-13 |
| 2 | WCS-26 | In Progress | Seguimiento humano priorizado, opt-out y retención | WCS-25 |
| 3 | WCS-27 | To Do | Recepción rápida y procesamiento asíncrono durable | WCS-25 |
| 4 | WCS-20 | To Do | Prompt, selección de modelo y guardrails | WCS-11, WCS-25, WCS-28 |
| 5 | WCS-21 | To Do | Bedrock real, métricas de costo y latencia | WCS-20, WCS-27 |
| 6 | WCS-18 | To Do | Cliente Meta real detrás del adapter | WCS-17, WCS-27 |
| 7 | WCS-19 | To Do | Prueba controlada end-to-end con Meta | WCS-18, WCS-26, WCS-27 |
| 8 | WCS-22 | In Progress | Deploy, health, smoke, observabilidad y rollback productivo | WCS-18, WCS-21 |
| 9 | WCS-29 | In Progress | Canal Telegram, routing por canal, webhook, secret y configuración AWS | WCS-14, WCS-15, WCS-22 |
| 10 | WCS-33 | Done | Contexto conversacional, filtros multi-turno y `productType` | WCS-20, WCS-25 |
| 11 | WCS-117 | In Progress | Refinamiento contextual, consultas catálogo+envíos y fallback uniforme | WCS-33, WCS-20, WCS-21 |
| 12 | WCS-34 | In Progress | Contrato de memoria, privacidad, retención, aislamiento y borrado | WCS-33 |
| 13 | WCS-35 | In Progress | Memoria de sesión PostgreSQL, TTL, ownership, conflictos y activación controlada | WCS-34 |
| 14 | WCS-36 | In Progress | Resumen versionado, ventana reciente, checkpoint y fallback controlado | WCS-35 |
| 15 | WCS-37 | In Progress | Preferencias explícitas PostgreSQL, TTL, ownership, borrado y contexto auxiliar | WCS-34, WCS-35, WCS-36 |
| 16 | WCS-38 | In Progress | Captura determinística de preferencias explícitas en inbound | WCS-20, WCS-37 |
| 17 | WCS-39 | Done | Baseline AppConfig v5 y preferencias explícitas declarados en Terraform | WCS-22, WCS-38 |
| 18 | WCS-40 | Done | Restart manual de App Runner para recargar AppConfig sin recompilar | WCS-22, WCS-39 |
| 19 | WCS-41 | In Progress | Ingesta manual, validación y smoke de Knowledge Base documental WCS | WCS-30, WCS-39, WCS-40 |

## Extensión de plataforma de agentes — 2026-09-07

| Jira | Estado | Entrega |
| --- | --- | --- |
| WCS-56 | In Review | Contrato y dataset sintético de evaluación |
| WCS-57 | In Review | Runner determinístico de suites |
| WCS-58 | In Review | Metadata de latencia, tokens y costo |
| WCS-59 | In Review | Consultas conversacionales del catálogo |
| WCS-60 | In Progress | Entrada interna y run trazable de evaluaciones |
| WCS-61 | In Progress | Persistencia histórica de resultados sanitizados de evaluación |
| WCS-62 | In Progress | Consulta interna filtrable y paginada del histórico de evaluaciones |
| WCS-63 | In Progress | Comparación sanitizada de runs y versiones de evaluación |
| WCS-64 | In Progress | Exportación versionada de evidencia sanitizada |
| WCS-65 | In Progress | Política determinística de retención sin purga automática |
| WCS-66 | In Progress | Revisión paginada de retención de evidencia |
| WCS-67 | In Progress | Gate auditable para activar la retención de evaluaciones |
| WCS-68 | In Progress | Frontera de autorización del disparador interno de evaluaciones |
| WCS-69 | In Progress | Ejecución interna idempotente después de autorización |
| WCS-70 | In Progress | Guard PostgreSQL atómico para idempotencia de triggers |
| WCS-71 | In Progress | Composición Spring del trigger interno con denegación por defecto |
| WCS-72 | In Progress | Completar prueba de denegación antes de reclamar el trigger |
| WCS-73 | In Progress | Frontera provider-neutral de acceso read-only al control plane |
| WCS-74 | In Progress | API read-only para histórico, comparación y evidencia de evaluaciones |
| WCS-75 | In Progress | Contratos HTTP y observabilidad del control plane de evaluaciones |
| WCS-76 | In Progress | Conectar el control plane con identidad JWT configurable |
| WCS-77 | In Progress | Asegurar rutas del control plane y preservar webhooks públicos |
| WCS-78 | In Progress | Cubrir contratos JWT y fallback deny-by-default |
| WCS-79 | In Progress | Trigger HTTP autenticado para ejecuciones de evaluación |
| WCS-80 | In Progress | Scopes separados de lectura y ejecución del control plane |
| WCS-81 | In Progress | Idempotencia, errores y observabilidad del trigger de evaluación |
| WCS-82 | In Progress | Executor opcional de evaluaciones con Amazon Bedrock |
| WCS-83 | In Progress | Metadata real de tokens, latencia y costo en evaluaciones |
| WCS-84 | In Progress | Límites de presupuesto y preflight para evaluaciones Bedrock |
| WCS-85 | In Progress | Contrato agregado read-only para el backoffice de evaluaciones |
| WCS-86 | In Progress | Backoffice React/TypeScript read-only para evaluaciones |
| WCS-87 | In Progress | CI de frontend y contrato operativo del backoffice |
| WCS-88 | In Progress | Exponer API read-only del registry de agentes |
| WCS-89 | In Progress | Agregar vista de agentes y activaciones al backoffice |
| WCS-90 | In Progress | Asegurar contrato y observabilidad del registry read-only |
| WCS-91 | In Progress | Verificar contrato read-only del registry contra PostgreSQL |
| WCS-92 | In Progress | Cargar baseline versionado de catalog-specialist en el registry |
| WCS-93 | In Progress | Agregar smoke operativo y documentación del baseline del registry |
| WCS-94 | In Progress | Implementar servicio de activación controlada del registry |
| WCS-96 | In Progress | Exponer control plane write-only protegido para activaciones |
| WCS-95 | In Progress | Cubrir activación, kill switch y rollback con pruebas y runbook |
| WCS-97 | In Progress | Implementar preflight determinístico para activaciones de agentes |
| WCS-98 | In Progress | Integrar preflight e historial de activaciones en el backoffice |
| WCS-99 | In Progress | Preparar shadow/canary con métricas comparables y rollback |
| WCS-100 | In Progress | Agregar frontera runtime para ejecutar candidatos en modo shadow |
| WCS-101 | In Progress | Registrar evidencia y aplicar límites de costo del flujo shadow |
| WCS-102 | In Progress | Cubrir shadow end-to-end con pruebas y runbook de habilitación gradual |
| WCS-103 | In Progress | Agregar comparación de calidad shadow sin persistir respuestas |
| WCS-104 | In Progress | Implementar executor Bedrock shadow para catálogo con contexto sanitizado |
| WCS-105 | In Progress | Cubrir candidata Bedrock shadow con integración y runbook de habilitación en test |
| WCS-106 | In Progress | Definir scorecard y gates de promoción para shadow/canary |
| WCS-107 | In Progress | Crear smoke reproducible para validar shadow sin publicar respuestas |
| WCS-108 | In Progress | Agregar observabilidad de comparación shadow y scorecard en Grafana |
| WCS-109 | In Progress | Documentar contrato de rollout shadow sólo para test |
| WCS-110 | In Progress | Blindar shadow por ambiente autorizado y porcentaje de tráfico |
| WCS-111 | In Progress | Cubrir rollout shadow con pruebas de integración y fallback activo |

WCS-60 no agrega todavía API, persistencia ni ejecución de Bedrock. WCS-61
agrega persistencia create-only en el schema `wcs` para runs completados y
escenarios sanitizados. WCS-62 agrega sólo una frontera interna de lectura con
filtros y límites; la siguiente evolución debe definir autorización, retención
y formato de exportación antes de permitir acceso desde un backoffice o job
compartido. WCS-63 agrega evidencia comparativa, pero no decide promociones.
WCS-64 agrega un envelope versionado para transportar esa evidencia sin
contenido conversacional, con un límite explícito de escenarios.
WCS-65 agrega el cálculo de vigencia y vencimiento de un run; no borra ni
archiva datos y deja pendiente la aprobación de la activación operativa.
WCS-66 agrega una revisión read-only de páginas históricas con decisiones y
contadores sanitizados; no expone endpoint ni ejecuta acciones de retención.
WCS-67 agrega evidencia tipada de aprobación operativa y la distingue de la
autenticación técnica; no persiste aprobaciones ni habilita purgas.
WCS-68 agrega la frontera provider-neutral de autorización técnica, con
denegación por defecto; no expone endpoint ni selecciona todavía OIDC, IAM u
otro proveedor. WCS-69 conecta esa autorización con el servicio de evaluación
mediante un guard idempotente explícito, sin agregar endpoint, scheduler,
persistencia nueva ni ejecución externa. WCS-70 implementa ese guard en
PostgreSQL con un hash SHA-256 único y una inserción atómica; no guarda la key
cruda ni habilita todavía un trigger remoto.
WCS-71 compone los servicios de autorización y ejecución en Spring, conecta el
guard existente y deja un authorizer fail-closed reemplazable por un adapter
explícito. No agrega endpoint, scheduler, proveedor técnico ni permisos AWS.
WCS-72 completa la evidencia del camino denegado verificando que no se
reclama la key y que tampoco se invocan el servicio de evaluación ni el
executor. No cambia la lógica productiva ni habilita el trigger.

WCS-73, WCS-74 y WCS-75 se entregan como un único slice funcional: la primera
define el contrato de acceso provider-neutral con capacidad exacta
`agent-evaluation.read` y denegación por defecto; la segunda expone lecturas
sanitizadas del histórico, detalle, comparación y export; la tercera cubre
MockMvc, errores estables y eventos de acceso. No incluyen OIDC/IAM concreto,
Spring Security, ejecución remota, escrituras ni backoffice. Hasta conectar un
authorizer explícito, la API permanece cerrada.

WCS-76, WCS-77 y WCS-78 se entregan como el siguiente slice agrupado:
Spring Security Resource Server valida issuer, expiración, audience y el scope
exacto `agent-evaluation.read`; el subject del JWT se traduce al actor del
port provider-neutral; el filtro sólo protege `/internal/agent-evaluations/**`;
y las pruebas cubren `200/401/403`, tokens inválidos y webhooks públicos. No se
provisiona un IdP, IAM, backoffice ni endpoint de ejecución. La flag permanece
deshabilitada por defecto hasta una aprobación operativa posterior.

WCS-79, WCS-80 y WCS-81 se entregan como un único slice: el POST interno
permanece deshabilitado por defecto, usa el subject del JWT como actor, exige
`Idempotency-Key`, separa los scopes `read` y `execute`, y delega en el guard
PostgreSQL y el servicio de evaluación existentes. La primera ejecución usa
el executor determinístico explícito de la política de respuesta; no habilita
Bedrock real, SQL generado, prompts remotos, SQS ni backoffice. El PR debe
incluir los contratos HTTP, pruebas de seguridad y Testcontainers,
observabilidad sanitizada y rollback por configuración.

WCS-82, WCS-83 y WCS-84 se entregan como un slice ampliado: agregan un puerto
provider-neutral medido, un executor Bedrock sólo para escenarios sintéticos,
metadata real cuando el proveedor la devuelve y un preflight de límites. El
executor `deterministic` permanece como default. La configuración real no se
activa en este PR ni requiere apply de Terraform; el rollback se realiza desde
AppConfig cambiando el executor a `deterministic`.

WCS-85, WCS-86 y WCS-87 se entregan como un slice de backoffice read-only: el
contrato agrega métricas operativas sólo cuando están disponibles, la UI
consulta runs/detalle/comparaciones sin escribir en el control plane y el CI de
frontend queda separado del backend. No se embeben tokens, no se habilita JWT,
no se ejecutan evaluaciones desde el navegador y no hay cambios Terraform.

WCS-88, WCS-89 y WCS-90 se entregan como el siguiente slice agrupado: el
backend consulta el registry mediante su port, expone versiones y activaciones
sanitizadas bajo `agent-registry.read`, y el backoffice las presenta en modo
read-only con filtros acotados. Se cubren límites, errores estables, eventos
de acceso y separación entre el scope de evaluación y el del registry. No se
agregan escrituras, publicación, canary, rollback, kill switch, IdP, cambios
de schema ni infraestructura AWS.

WCS-91, WCS-92 y WCS-93 son el slice de bootstrap observable: una migración
posterior a V11 carga sólo metadata del baseline `catalog-specialist`, la
prueba de integración valida la respuesta read-only y un script/salida SQL
permite verificarlo sin secretos. El seed no crea activaciones ni habilita el
runtime.

WCS-94, WCS-96 y WCS-95 forman el slice de activación controlada: una versión
APPROVED se transforma en una referencia inmutable sólo con autorización y
evidencia de aprobación; la API separa `agent-registry.write`, exige
idempotencia y deja activación, kill switch y rollback detrás de una flag falsa.
La migración V13 guarda hashes de keys, no las keys crudas, y las pruebas
verifican que el runtime conversacional y los webhooks públicos no cambian.

WCS-97, WCS-98 y WCS-99 forman el siguiente slice amplio: el preflight valida
una solicitud completa sin mutar el registry, el backoffice lo muestra junto
con el historial sanitizado usando sólo lectura, y se prepara el contrato de
shadow/canary con métricas comparables y rollback. No se habilita el runtime,
no se activa tráfico candidato y no se ejecutan llamadas adicionales a Bedrock.

WCS-100–WCS-102 continúan ese slice con la primera integración runtime segura:
el orquestador conserva la respuesta activa, el candidato se ejecuta detrás de
un puerto sin capacidad de publicación, los resultados se limitan por timeout,
tokens y costo, y la evidencia se registra sin contenido conversacional. La
configuración permanece cerrada por defecto y el executor real de proveedor
queda fuera de esta entrega.

WCS-103–WCS-105 amplían el slice en una sola entrega operativa: la respuesta
activa sigue siendo la autoridad, la comparación usa hashes efímeros y sólo
publica `MATCH`, `MISMATCH` o `UNKNOWN`, y el candidato Bedrock recibe un
contexto de catálogo normalizado sin mensaje crudo, identidad, SQL, MCP,
outbox ni capacidad de publicación. `shadow-provider=noop` es el default; el
smoke de Bedrock se valida con un executor fake y el rollback consiste en
deshabilitar shadow, volver a noop y apagar la activación.

`WCS-106`–`WCS-108` forman la siguiente entrega agrupada: el scorecard
determinístico define cuándo la evidencia es insuficiente, bloquea una
regresión o queda lista para revisión humana; el smoke usa datos sintéticos y
no publica candidatos; y Grafana/CloudWatch muestra comparación, latencia,
tokens y costo sin contenido conversacional. No se habilita promoción
automática, Bedrock real en CI, Terraform ni tráfico productivo.

`WCS-109`–`WCS-111` forman la siguiente entrega agrupada: el runtime exige una
allowlist de ambiente y un porcentaje determinístico antes de invocar shadow,
la documentación ordena el rollout desde `0%` en `test`, y las pruebas
verifican que la respuesta activa siga siendo la única publicada. No se habilita
`prod`, tráfico real ni nueva infraestructura.

`WCS-14`–`WCS-16` y `WCS-17` ya tienen la fundación o el contrato inicial
versionado; deben completarse/verificarse según la evidencia de cada issue antes
de cerrar las dependencias. El orden no implica cerrar automáticamente issues:
cada transición requiere pruebas, evidencia y PR asociado.

| Key | Estado | Resumen |
| --- | --- | --- |
| WCS-112 | In Progress | Provisionar ambiente AWS test aislado para WCS |
| WCS-113 | In Progress | Preparar CI, bootstrap y smoke del ambiente test |
| WCS-114 | In Progress | Bootstrap seguro del Environment test y primer plan Terraform |

WCS-112 y WCS-113 forman el siguiente bloque operativo. El primero descubre la
aplicación y el profile existentes de AppConfig y crea sólo el environment
`test`, sus secretos bootstrap y el ECR/roles no productivos en un state
separado. El segundo prepara la selección explícita del target en CI y el
perfil Spring `test`. El App Runner test permanece desactivado y shadow en
`0%` hasta contar con secretos, base de datos, plan y aprobación separados de
producción.

WCS-114 resuelve el bootstrap circular del primer rol: un root local crea sólo
el rol OIDC de Terraform para `test` con un principal AWS autorizado. Después
de configurar el ARN y las variables del Environment `test` de GitHub, se
ejecuta el primer `plan` remoto y se revisan acciones, state y prefijos antes
de cualquier `apply`.

## Template común de issue

Cada issue real debe agregar contexto, objetivo, alcance, fuera de alcance, FR/UC, criterios Given/When/Then, pruebas, evidencia, dependencias, riesgos, documentación, rollout y rollback.
