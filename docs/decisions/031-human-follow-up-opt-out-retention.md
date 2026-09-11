# Decisión 031 — Seguimiento humano, opt-out y retención operativa

Estado: `Proposed for review`  
Related Jira: `WCS-26`  
Related migration: `V15__create_human_follow_up_and_contact_suppression.sql`

## Decisión

El flujo común de inbound debe resolver tres políticas antes de enviar una
respuesta:

1. detectar `BAJA`, `STOP` y equivalentes antes de memoria, preferencias o IA;
2. aceptar sólo `ALTA`, `REANUDAR` o `/start` como reactivación explícita;
3. cortar el flujo si el actor ya está en `DO_NOT_CONTACT`;
4. persistir una tarea humana cuando el orquestador entrega `HANDOFF`,
   `LOW_CONFIDENCE` o `FALLBACK`.

Los canales no implementan estas reglas. WhatsApp, Telegram y futuros canales
entregan el mismo `InboundMessageCommand`, y la aplicación resuelve ownership,
idempotencia, supresión y seguimiento.

## Privacidad

La lista de supresión no guarda el identificador externo: guarda un SHA-256
estable de `channel + externalCustomerId`. Las tareas guardan referencias
internas a conversación y mensaje, motivo, prioridad y vencimiento; no guardan
el cuerpo del usuario ni un resumen libre. El opt-out limpia la memoria y las
preferencias de la conversación y no crea un mensaje de salida automático. La
reactivación explícita cambia la supresión a `REVOKED`, limpia nuevamente el
contexto y conserva el historial sujeto a retención. No se permite que un
mensaje común reactive al contacto.

## Retención

La limpieza es configurable y apagada por defecto. Redacciona cuerpos a los 30
días y elimina mensajes/metadata a los 90 días en lotes. Las métricas agregadas
y logs no se purgan por este componente. Product/Legal debe aprobar períodos,
backup y auditoría antes de activar el job en producción. La reactivación
explícita queda limitada a las órdenes documentadas y auditadas por la
aplicación.

## Idempotencia y rollback

La tarea usa una unicidad de base de datos por `source_message_id + reason`; la
supresión usa `actor_key` único. La migración es nueva y no edita V1–V14.
Rollback operativo: desactivar `wcs.conversation.retention.enabled`; rollback
de código: desplegar una versión anterior sin borrar V15 ni sus datos.

## Evidencia requerida

* pruebas unitarias de detector, tareas y retención;
* prueba PostgreSQL/Testcontainers de V15, conflictos e integridad referencial;
* logs sanitizados `MESSAGE_OPTED_OUT`, `MESSAGE_SUPPRESSED`,
  `HUMAN_FOLLOW_UP_TASK_READY` y `RETENTION_CLEANUP_COMPLETED`;
* consulta agregada de tareas abiertas por prioridad y estado.
