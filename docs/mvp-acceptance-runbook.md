# Runbook de cierre del MVP funcional — WCS

Estado: `Ready for execution`

Este runbook define cómo cerrar el MVP funcional sin esperar todavía la
habilitación productiva de WhatsApp. Telegram es el canal técnico de prueba
para la aceptación conversacional; las pruebas específicas de Meta y la
ventana de 24 horas de WhatsApp quedan diferidas.

## Alcance de esta aceptación

Incluye:

- saludo, catálogo, filtros, stock, precio y seguimientos conversacionales;
- horarios y políticas demo autorizadas;
- fallback, aclaración, seguimiento humano y `BAJA`/`STOP`;
- persistencia e idempotencia con PostgreSQL;
- memoria de sesión, ownership, TTL, borrado y retención operativa;
- ausencia de secretos, PII innecesaria y contenido conversacional en logs;
- ejecución local reproducible con Testcontainers y smoke manual por Telegram.

No bloquea el cierre funcional de este ciclo:

- alta productiva del número de WhatsApp;
- challenge, HMAC y tráfico real de Meta;
- templates fuera de la ventana de 24 horas;
- pagos, checkout, carrito, cancelaciones, reembolsos o cambios de pedidos;
- validación legal definitiva de los períodos de retención.

## Políticas que se aceptan para el MVP demo

| Área | Política autorizada | Fuente de verdad | Fallback |
| --- | --- | --- | --- |
| Catálogo | Sólo informa productos, variantes, precio y stock retornados por PostgreSQL | `wcs.catalog_products` / `wcs.catalog_variants` | No inventar; pedir precisión u ofrecer coincidencias reales |
| Contexto | Conserva filtros estructurados del turno anterior durante la ventana configurada | PostgreSQL, no el LLM | Recalcular o pedir identificación si el contexto expiró |
| Pregunta compuesta | Puede combinar catálogo con envío/otra política publicada | PostgreSQL + política activa | Responder sólo el componente respaldado |
| Horarios | Usa horarios demo versionados y marca domingo como cerrado | `wcs.business_hours` | Informar que no hay información confirmada |
| Políticas | Usa únicamente contenido activo y marcado como demo | `wcs.support_policies` / Knowledge Base validada | Derivar o pedir revisión humana |
| Human handoff | Una consulta sensible, desconocida o de baja confianza crea tarea priorizada | `wcs.human_follow_up_tasks` | Fallback seguro sin operación sensible |
| Opt-out | `BAJA`, `STOP` o equivalente corta el flujo antes de IA, limpia memoria y evita outbox automático | `wcs.contact_suppressions` | Mantener `DO_NOT_CONTACT` pseudonimizado |
| Retención | Memoria 24 h; cuerpos 30 días; metadata 90 días; métricas agregadas 365 días | Configuración WCS | Todo permanece apagado hasta aprobación legal |
| WhatsApp | Texto libre sólo dentro de la ventana; templates sólo cuando exista configuración aprobada | Meta + configuración del canal | No enviar si no hay template autorizado |

La retención anterior es una recomendación técnica de prueba, no una aprobación
legal. En producción permanecen desactivadas `wcs.conversation.memory.enabled`
y `wcs.conversation.retention.enabled` hasta completar ese gate.

## Matriz de ejecución

Registrar cada caso con `id`, fecha, ambiente, versión de aplicación, resultado
`PASS`/`FAIL`, referencia de evidencia sanitizada y observación. No adjuntar
capturas con teléfonos, tokens, mensajes completos ni URLs con credenciales.

| ID | Caso | Canal/ambiente | Resultado esperado | Prioridad |
| --- | --- | --- | --- | --- |
| MVP-001 | Saludo inicial | Telegram / test | `Hola, ¿cómo te puedo ayudar?` | P0 |
| MVP-002 | Consulta de catálogo general | Telegram / test | Lista acotada proveniente de PostgreSQL | P0 |
| MVP-003 | Filtros de tipo, talle y color | Telegram / test | Sólo variantes que cumplen todos los filtros | P0 |
| MVP-004 | Filtro de precio | Telegram / test | Sólo resultados dentro del rango solicitado | P0 |
| MVP-005 | Producto inexistente o sin stock | Telegram / test | No inventa disponibilidad ni precio | P0 |
| MVP-006 | Seguimiento de disponibilidad/precio | Telegram / test | Reconsulta el producto activo y devuelve dato vigente | P0 |
| MVP-007 | Seguimiento “qué opciones” | Telegram / test | Conserva el tipo/filtros activos | P0 |
| MVP-008 | Precio + envío | Telegram / test | Compone catálogo determinístico y política publicada | P1 |
| MVP-009 | Categoría no existente | Telegram / test | Fallback único sin confirmar existencia | P1 |
| MVP-010 | Horarios | Telegram / test | Usa horarios persistidos, incluido domingo cerrado | P0 |
| MVP-011 | Solicitud humana | Telegram / test | Crea tarea priorizada con vencimiento; no guarda PII innecesaria | P0 |
| MVP-012 | `BAJA`/`STOP` | Telegram / test | Suprime antes de IA, limpia memoria y no crea outbox | P0 |
| MVP-013 | Mensaje posterior a baja | Telegram / test | No genera respuesta proactiva ni seguimiento | P0 |
| MVP-014 | Memoria aislada | Testcontainers | Actor/conversación incorrectos no leen el estado ajeno | P0 |
| MVP-015 | Memoria expirada | Testcontainers | TTL devuelve estado vacío y elimina la fila vencida | P0 |
| MVP-016 | Borrado explícito | Testcontainers | `clear` elimina memoria y preferencias del alcance solicitado | P0 |
| MVP-017 | Conflicto de versión | Testcontainers | Escritura stale se rechaza sin sobrescribir estado | P1 |
| MVP-018 | Retención de contenido | Testcontainers | Cuerpo vencido se redacta sin borrar todavía metadata | P0 |
| MVP-019 | Retención de metadata | Testcontainers | Metadata vencida se elimina en lotes; métricas agregadas permanecen | P1 |
| MVP-020 | Logs sanitizados | Local/test | No aparecen secretos, teléfonos, prompts ni mensajes completos | P0 |

Los casos WhatsApp `TC-013` a `TC-025` quedan en una matriz separada y no
impiden esta aceptación mientras el canal no esté habilitado.

## Procedimiento de prueba conversacional por Telegram

Usar un chat de prueba limpio y datos demo. Enviar, en este orden:

```text
Busco un buzo negro talle L
¿Qué opciones tienen?
¿Está disponible?
¿Cuánto cuesta y cómo se hace el envío?
¿Venden gorras?
Necesito hablar con un humano
BAJA
```

Verificar que cada respuesta se apoye en PostgreSQL o en la política publicada.
La última instrucción debe detener el flujo antes de Bedrock y no generar una
respuesta automática de salida.

Para reactivar el mismo chat de prueba, enviar:

```text
ALTA
```

También son válidos `REANUDAR` y `/start`. Debe recibirse una confirmación,
observarse `MESSAGE_REACTIVATED`/`CONTACT_REACTIVATED` y comprobarse que una
consulta posterior no hereda el contexto ni las preferencias anteriores.
Un mensaje común antes de la orden explícita no debe producir respuesta.

## Procedimiento para probar memoria

La prueba debe ejecutarse localmente con Testcontainers o en el ambiente AWS
`test`, nunca contra el RDS productivo. En el ambiente de prueba publicar
temporalmente:

```text
wcs.conversation.memory.enabled=true
wcs.conversation.memory.ttl=PT24H
wcs.conversation.memory.max-messages=20
wcs.conversation.memory.max-message-characters=2000
wcs.conversation.preferences.enabled=true
wcs.conversation.preferences.ttl=PT24H
wcs.conversation.preferences.max-preferences=5
wcs.conversation.preferences.max-value-characters=64
```

Ejecutar la conversación `MVP-003` y luego `MVP-006`. El segundo mensaje debe
usar el producto/filtros activos, pero precio y stock deben volver a salir de
PostgreSQL. Para comprobar aislamiento, repetir con un actor y conversación
synthetic distintos; el estado anterior no debe aparecer.

La expiración no se valida esperando 24 horas en Telegram. Se valida con un
reloj controlado en Testcontainers:

1. guardar un estado synthetic con `updated_at` anterior al TTL;
2. cargarlo con `JpaConversationMemoryAdapter`;
3. comprobar que devuelve vacío y elimina la fila vencida;
4. intentar leerlo con otro `actor_id` y comprobar que no hay acceso;
5. ejecutar `clear` y verificar que memoria y preferencias del alcance quedan
   eliminadas;
6. guardar una versión stale y comprobar `ConversationMemoryConflictException`.

Las verificaciones SQL deben ser agregadas y usar sólo fixtures sintéticas:

```sql
select count(*) as memory_rows,
       count(distinct actor_id) as actors,
       min(updated_at) as oldest_update,
       max(updated_at) as newest_update
from wcs.conversation_memory_states;

select count(*) as active_preferences
from wcs.customer_preferences
where expires_at > current_timestamp;
```

No seleccionar `recent_messages`, valores de preferencias ni cuerpos para
adjuntar evidencia.

## Procedimiento para probar retención y opt-out

La retención se prueba en Testcontainers con `ConversationRetentionCleanupService`
y un `Clock` fijo. No existe un endpoint público para disparar cleanup, por lo
que la prueba correcta es de aplicación/integración y no una operación manual
contra producción.

1. Insertar fixtures synthetic con mensajes más antiguos que el período de
   contenido y otros más recientes.
2. Ejecutar `run(fixedNow)` con retención corta sólo en test, por ejemplo
   contenido `PT1H`, metadata `PT2H` y batch pequeño.
3. Verificar que los cuerpos vencidos sean
   `[REDACTED_AFTER_RETENTION]` y que aún exista metadata dentro de su período.
4. Ejecutar nuevamente después del vencimiento de metadata y verificar que las
   filas se eliminen por lotes.
5. Confirmar que las métricas agregadas no se modifiquen por este cleanup.
6. Enviar `BAJA` por Telegram y comprobar los eventos sanitizados
   `MESSAGE_OPTED_OUT` y `MESSAGE_SUPPRESSED`, sin `AI_USAGE_RECORDED` ni
   `outbox_messages` nuevos.

La evidencia mínima es: conteos antes/después, resultado del servicio, eventos
sanitizados y `mvn -q verify`. Nunca se debe probar la purga reduciendo valores
en AppConfig `prod` ni ejecutando SQL destructivo sobre el RDS compartido.

## Gate de cierre

Se puede pasar WCS-12 a `In Review` cuando todos los casos P0 estén ejecutados,
exista evidencia sanitizada y no haya defectos críticos abiertos. Se puede
pasar a `Done` sólo después de revisión del tester/product owner.

WCS-10 requiere además que la política de retención deje de estar en estado
`Proposed` para producción. Mientras eso no ocurra, el MVP queda aceptado como
demo funcional en Telegram/test, no como rollout productivo completo.

## Plantilla de evidencia

```text
Case: MVP-___
Environment: local-test | aws-test
Application commit:
Configuration version:
Executed at:
Result: PASS | FAIL
Expected:
Observed:
Evidence: test report / sanitized log query / aggregate SQL
Follow-up Jira:
```
