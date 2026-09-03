# Estrategia de testing — WCS MVP

## Objetivo

Demostrar que Ropa de Programador recibe consultas de texto, responde sólo con
información autorizada, consulta el catálogo de forma determinística, crea
seguimientos humanos cuando corresponde y respeta seguridad, idempotencia,
retención y las reglas de WhatsApp.

Las pruebas usan datos sintéticos marcados como `DEMO`. No se guardan tokens,
números reales, conversaciones reales ni PII innecesaria en fixtures, logs o
capturas.

## Capas

| Capa | Cubre | Evidencia |
|---|---|---|
| Unit | Parser, HMAC, intención, filtros, horarios, opt-out, retención y mapeos | Reporte JUnit |
| Application | Orquestación, fallback, seguimiento, idempotencia y políticas con dobles | Reporte JUnit |
| Contract | Payloads de Meta, respuestas de Meta, LLM y repositorios | Fixtures versionados |
| Integration | PostgreSQL, Flyway, constraints, stock, outbox y ownership | Testcontainers / reporte SQL |
| E2E mock | Inbound → persistencia → respuesta → outbox → outbound | Log de correlación sanitizado |

La matriz de adapters debe ejecutar el mismo caso de uso con `Channel.WHATSAPP`
y `Channel.TELEGRAM`. Los contratos de cada canal verifican sólo la traducción
del payload, autenticación del webhook y request outbound; no duplican reglas de
catálogo, IA, ownership o idempotencia.
| E2E Meta | Número controlado, webhook HTTPS y respuesta real | Evidencia sanitizada |
| Data lifecycle | Retención, borrado y `DO_NOT_CONTACT` | Reporte de job + query agregada |
| Operational | Health, readiness, ack rápido, retry, alertas y rollback | CI + runbook |

## Matriz funcional mínima

| ID | Prioridad | Prueba | Resultado esperado | Evidencia |
|---|---|---|---|---|
| `TC-001` | P0 | Saludo inicial | Responde exactamente `Hola, ¿cómo te puedo ayudar?` | JUnit |
| `TC-002` | P0 | Consulta de política demo | Usa sólo el contenido configurado como `DEMO` | JUnit + fixture |
| `TC-003` | P0 | Consulta de producto por nombre/SKU | Devuelve producto, precio, moneda y disponibilidad desde PostgreSQL | JUnit + SQL agregado |
| `TC-004` | P0 | Filtros por talle/color | Sólo devuelve variantes que cumplen todos los filtros | JUnit |
| `TC-005` | P0 | Producto inexistente o sin stock | No inventa datos y ofrece aclaración o seguimiento | JUnit |
| `TC-006` | P1 | Consulta ambigua | Hace una pregunta concreta de aclaración | JUnit |
| `TC-007` | P0 | Solicitud de atención humana | El bot responde, crea una tarea priorizada con contexto mínimo y vencimiento dentro de 24 horas | JUnit + SQL agregado |
| `TC-008` | P0 | `BAJA`, `STOP` o “no me escribas más” | Marca `DO_NOT_CONTACT` y no crea respuestas ni seguimientos automáticos | JUnit + SQL agregado |
| `TC-009` | P1 | Mensaje posterior a la baja | No envía mensajes proactivos mientras la supresión esté activa | JUnit |
| `TC-010` | P1 | Fuera del horario demo | Informa el horario configurado y mantiene la conversación en estado correcto | JUnit |
| `TC-011` | P0 | Acción sensible | No ejecuta cancelaciones, reembolsos, pagos ni cambios; crea seguimiento | JUnit |
| `TC-012` | P1 | Imagen de catálogo | Persiste una referencia S3 válida; no intenta enviar media en el MVP | Integration |
| `TC-039` | P0 | Seed demo de catálogo | Carga productos, variantes, SKU únicos y stock no negativo | Spring Boot + Flyway |
| `TC-040` | P0 | Configuración demo de atención | Carga siete días y políticas versionadas; domingo permanece cerrado | Spring Boot + Flyway |

## Matriz de WhatsApp y seguridad

| ID | Prioridad | Prueba | Resultado esperado | Evidencia |
|---|---|---|---|---|
| `TC-013` | P0 | Challenge válido | `GET /webhook/whatsapp` devuelve `200` y el challenge exacto | MockMvc |
| `TC-014` | P0 | Challenge inválido o incompleto | Devuelve `403` | MockMvc |
| `TC-015` | P0 | Firma HMAC válida | Acepta el body original | JUnit |
| `TC-016` | P0 | Firma ausente o inválida | Devuelve `403`, no persiste ni llama al LLM | MockMvc + Mockito |
| `TC-017` | P0 | Payload de texto válido | Extrae sólo mensajes soportados | Contract fixture |
| `TC-018` | P1 | Evento de estado o tipo no soportado | Ignora el evento sin error ni respuesta duplicada | Contract fixture |
| `TC-019` | P0 | `external_message_id` duplicado | Conserva una ocurrencia y no envía segunda respuesta | Integration |
| `TC-020` | P0 | Duplicados concurrentes | La constraint transaccional mantiene idempotencia | PostgreSQL/Testcontainers |
| `TC-021` | P0 | Ack rápido del webhook | Persiste/encola y responde sin esperar al LLM o Meta | Integration + métrica |
| `TC-022` | P0 | Error transitorio | Reintenta hasta el máximo configurado y luego ejecuta fallback | JUnit + logs sanitizados |
| `TC-023` | P1 | Fallo permanente de proveedor | No entra en retry infinito ni genera mensajes duplicados | JUnit |
| `TC-024` | P0 | Respuesta dentro de 24 horas | Envía texto libre | E2E Meta |
| `TC-025` | P1 | Seguimiento fuera de 24 horas | Usa sólo plantilla aprobada; si no existe, no envía | Contract + E2E controlado |

## Persistencia y ciclo de vida

| ID | Prioridad | Prueba | Resultado esperado | Evidencia |
|---|---|---|---|---|
| `TC-026` | P0 | Migración en PostgreSQL limpio | Crea schema `wcs` y tablas esperadas | Flyway + Testcontainers |
| `TC-027` | P0 | Conversación e historial mínimo | Guarda relación, dirección, timestamps y estado sin datos extra | SQL agregado |
| `TC-028` | P1 | Stock no negativo | Impide stock inválido y mantiene consistencia de variantes | Integration |
| `TC-029` | P0 | Retención de 30 días | Elimina contenido vencido | Reporte de limpieza |
| `TC-030` | P1 | Retención de metadatos de 90 días | Elimina metadatos fuera de plazo según configuración | Reporte de limpieza |
| `TC-031` | P1 | Métricas agregadas | Conserva métricas sin teléfono ni contenido | Query agregada |
| `TC-032` | P0 | Logs y errores | No contienen tokens, mensajes completos, teléfonos ni prompts | Revisión automatizada |

## End-to-end y operación

- `TC-033`: ciclo mock completo `inbound → persistencia → LLM → outbox → outbound`.
- `TC-034`: ciclo real controlado `Meta → webhook HTTPS → RDS → Meta`.
- `TC-035`: readiness falla de forma visible si faltan AppConfig, Secrets Manager o base de datos.
- `TC-036`: health/readiness y correlación permiten diagnosticar sin exponer PII.
- `TC-037`: rollback de una versión de aplicación sin alterar migraciones ya aplicadas.
- `TC-038`: alerta o métrica ante aumento de errores, retries, duplicados o tareas de seguimiento vencidas.

## Datos y fixtures

- Catálogo, horarios y políticas demo versionados y marcados como `DEMO`.
- WhatsApp IDs sintéticos, por ejemplo `wamid.demo.001`.
- Reloj controlado para probar ventana de 24 horas y retención.
- Respuestas de Meta, Bedrock y S3 simuladas en unit/contract tests.
- El test real con Meta usa evidencia sanitizada y nunca incorpora secretos al repositorio.

## Gate de aceptación del MVP

No alcanza con que compile. Para aceptar el MVP:

- todos los casos P0 deben pasar;
- no puede existir un defecto crítico abierto;
- debe existir evidencia de ciclo mock y de prueba controlada con Meta;
- deben verificarse idempotencia, ack rápido, fallback y ausencia de secretos/PII en logs;
- la página canónica y `WCS-12` deben contener la matriz ejecutada y sus evidencias;
- los datos demo deben estar identificados y no pueden habilitarse como información productiva sin aprobación.

## Evidencia actual

- `mvn clean test`: tests unitarios de HMAC, parser, controller, servicio de aplicación y adapter Meta.
- `WallyCustomerSupportApplicationTest`: arranque Spring Boot con JPA, migraciones Flyway V1–V3, consulta del catálogo demo, horarios y políticas con adapters de prueba.
- La integración PostgreSQL real debe ejecutarse con Testcontainers o el PostgreSQL local documentado antes de cerrar WCS-12.
