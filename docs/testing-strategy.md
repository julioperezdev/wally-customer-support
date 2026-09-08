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

Los tests de integración que arrancan el contexto Spring y acceden a
persistencia usan un contenedor PostgreSQL 16 administrado por Testcontainers.
El datasource se inyecta con `@DynamicPropertySource`, por lo que la suite no
depende de PostgreSQL local, H2, RDS ni credenciales externas. Ejecutar
`mvn -q verify` requiere que Docker Desktop o un runtime Docker compatible esté
disponible; los tests unitarios y de aplicación continúan usando dobles y no
necesitan iniciar el contenedor.
| E2E Meta | Número controlado, webhook HTTPS y respuesta real | Evidencia sanitizada |
| Data lifecycle | Retención, borrado y `DO_NOT_CONTACT` | Reporte de job + query agregada |
| Operational | Health, readiness, ack rápido, retry, alertas y rollback | CI + runbook |

## Evaluaciones internas sanitizadas

La frontera de ejecución del trigger interno valida la autorización antes de
adquirir la idempotency key y antes de delegar en el servicio de evaluación.
La suite debe cubrir autorización denegada, adquisición única, repetición de
una key ya reclamada, fallo del guard y fallo sanitizado de la evaluación. Los
tests no incluyen prompts, respuestas, tokens, secretos, PII ni la key en
resultados o logs.

La prueba de autorización denegada verifica además que la idempotency key, el
servicio de evaluación y el executor no reciben ninguna invocación.

El control plane read-only mantiene la misma regla de fail-closed: las pruebas
MockMvc deben verificar que una solicitud sin autorización devuelve `403` antes
de invocar histórico, comparación o exportación. Los contratos autorizados
cubren filtros de fecha, paginación acotada, detalle inexistente (`404`),
datasets incompatibles (`409`) y errores de request sanitizados (`400`). Las
respuestas sólo contienen métricas y metadata permitida; nunca prompts,
respuestas completas, tokens secretos o PII.

WCS-70 agrega una prueba de integración con PostgreSQL/Testcontainers para
comprobar que la key se guarda sólo como digest SHA-256, que el reintento es
rechazado y que dos claims concurrentes de la misma key producen exactamente
un éxito.

## Matriz funcional mínima

| ID | Prioridad | Prueba | Resultado esperado | Evidencia |
|---|---|---|---|---|
| `TC-001` | P0 | Saludo inicial | Responde exactamente `Hola, ¿cómo te puedo ayudar?` | JUnit |
| `TC-002` | P0 | Consulta de política demo | Usa sólo el contenido configurado como `DEMO` | JUnit + fixture |
| `TC-003` | P0 | Consulta de producto por nombre/SKU | Devuelve producto, precio, moneda y disponibilidad desde PostgreSQL | JUnit + SQL agregado |
| `TC-004` | P0 | Filtros por talle/color | Sólo devuelve variantes que cumplen todos los filtros | JUnit |
| `TC-004A` | P0 | Filtros por rango de precio | Sólo devuelve variantes dentro del mínimo/máximo solicitado | JUnit + Testcontainers |
| `TC-005` | P0 | Producto inexistente o sin stock | No inventa datos y ofrece aclaración o seguimiento | JUnit |
| `TC-006` | P1 | Consulta ambigua | Hace una pregunta concreta de aclaración | JUnit |
| `TC-007` | P0 | Solicitud de atención humana | El bot responde, crea una tarea priorizada con contexto mínimo y vencimiento dentro de 24 horas | JUnit + SQL agregado |
| `TC-008` | P0 | `BAJA`, `STOP` o “no me escribas más” | Marca `DO_NOT_CONTACT` y no crea respuestas ni seguimientos automáticos | JUnit + SQL agregado |
| `TC-009` | P1 | Mensaje posterior a la baja | No envía mensajes proactivos mientras la supresión esté activa | JUnit |
| `TC-010` | P1 | Fuera del horario demo | Informa el horario configurado y mantiene la conversación en estado correcto | JUnit |
| `TC-010A` | P0 | Catálogo general sin filtros | Devuelve una lista acotada desde PostgreSQL | JUnit + Testcontainers |
| `TC-010B` | P0 | Seguimiento de variante única | Reconsulta stock/precio vigente usando el contexto previo | Application + Testcontainers |
| `TC-010C` | P1 | Seguimiento ambiguo | Solicita SKU o producto exacto sin elegir arbitrariamente | Application |
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
| `TC-043` | P1 | Preferencia explícita permitida | Persiste/reemplaza `preferred_color` sólo con un color permitido y confirmado | Unit + Testcontainers |
| `TC-044` | P1 | Preferencia vencida o inválida | No expone valores vencidos ni guarda colores no permitidos | Unit + Testcontainers |
| `TC-045` | P1 | Ownership y borrado de preferencias | Un actor no lee preferencias ajenas y el borrado elimina el alcance solicitado | Unit + Testcontainers |
| `TC-046` | P1 | Captura explícita en inbound | Confirma una frase explícita, no captura colores incidentales y no invoca al LLM para guardar | Unit + flujo común |
| `TC-047` | P1 | Persistencia de evaluación completada | Guarda y recupera métricas y resultados por escenario sin contenido conversacional | Testcontainers PostgreSQL |
| `TC-048` | P1 | Inmutabilidad de evaluación | Rechaza sobrescribir un `runId` y una segunda fila del mismo escenario | Testcontainers PostgreSQL |
| `TC-049` | P1 | Historial filtrado y paginado | Devuelve sólo runs que cumplen los filtros, con límite de página y orden estable | Unit + Testcontainers PostgreSQL |
| `TC-050` | P1 | Detalle de evaluación inexistente | Devuelve `Optional.empty` sin filtrar errores de infraestructura ni contenido | Unit + Testcontainers PostgreSQL |
| `TC-051` | P1 | Comparación de runs compatibles | Calcula deltas de calidad y métricas operativas disponibles sin contenido | Unit + Testcontainers PostgreSQL |
| `TC-052` | P1 | Comparación con datos faltantes o incompatibles | Marca metadata faltante como no disponible y rechaza datasets diferentes | Unit + Testcontainers PostgreSQL |
| `TC-053` | P1 | Export de evidencia sanitizada | Devuelve schema versionado desde una comparación sin contenido conversacional | Unit + Testcontainers PostgreSQL |
| `TC-054` | P1 | Límite de exportación | Rechaza una comparación con más de 1.000 escenarios antes de exportar | Unit |
| `TC-055` | P1 | Política de retención activa | Calcula el vencimiento y mantiene un run activo antes del límite | Unit |
| `TC-056` | P1 | Límite de retención | Marca el run expirado en el instante límite, sin borrarlo ni modificarlo | Unit |
| `TC-057` | P1 | Revisión paginada de retención | Conserva el orden de una página, calcula estados y agrega contadores | Unit |
| `TC-058` | P1 | Revisión vacía | Devuelve una revisión sin decisiones ni errores para una página vacía | Unit |
| `TC-059` | P1 | Gate no solicitado | Devuelve `NOT_REQUESTED` sin exigir metadata de aprobación | Unit |
| `TC-060` | P1 | Gate aprobado o rechazado | Acepta evidencia completa no futura y rechaza metadata faltante o futura | Unit |
| `TC-061` | P1 | Autorización exacta del trigger | Autoriza sólo la capacidad y ambiente permitidos | Unit |
| `TC-062` | P1 | Denegación por defecto | Deniega metadata incompleta, provider no confirmante o error del provider | Unit |
| `TC-063` | P1 | API read-only denegada | Devuelve `403` y no llama histórico, comparación ni exportación | MockMvc + Mockito |
| `TC-064` | P1 | API read-only autorizada | Respeta filtros, paginación y orden del histórico | MockMvc + application |
| `TC-065` | P1 | Errores del control plane | Mapea request inválido a `400`, run ausente a `404` y dataset incompatible a `409` | MockMvc |
| `TC-066` | P1 | Acceso observable y sanitizado | Registra operación, capacidad, resultado y duración sin actor crudo ni contenido | MockMvc + logs |
| `TC-041` | P1 | Uso real de Bedrock | Emite `AI_USAGE_RECORDED` con modelo, tokens, latencia, pricing version y costo estimado | Test del adapter + log sanitizado |
| `TC-042` | P1 | Consultas de observabilidad | CloudWatch agrega consultas, IA, RAG y entregas sin errores de campos | Logs Insights/Grafana |

## End-to-end y operación

- `TC-033`: ciclo mock completo `inbound → persistencia → LLM → outbox → outbound`.
- `TC-034`: ciclo real controlado `Meta → webhook HTTPS → RDS → Meta`.
- `TC-035`: readiness falla de forma visible si faltan AppConfig, Secrets Manager o base de datos.
- `TC-036`: health/readiness y correlación permiten diagnosticar sin exponer PII.
- `TC-037`: rollback de una versión de aplicación sin alterar migraciones ya aplicadas.
- `TC-038`: alerta o métrica ante aumento de errores, retries, duplicados o tareas de seguimiento vencidas.
- `TC-041`: cada llamada de Bedrock exitosa o fallida registra el uso disponible sin
  incluir prompt, respuesta ni credenciales; una ejecución mock no simula costo
  de proveedor.
- `TC-042`: las queries versionadas de CloudWatch funcionan tanto con campos JSON
  descubiertos como con mensajes que tengan prefijo textual de Spring.
- `TC-046`: con preferencias habilitadas, enviar `Prefiero el negro` debe
  confirmar la captura; enviar `Busco una remera negra talle M` debe ejecutar
  catálogo y no guardar una preferencia.
- `TC-049`: consultar el histórico con dataset/agente y dos páginas debe
  respetar todos los filtros, no duplicar runs y desempatar por `runId`.
- `TC-050`: consultar un `runId` inexistente debe producir un resultado vacío
  tipado, sin excepción de persistencia ni exposición de contenido.
- `TC-051`: comparar runs del mismo dataset debe devolver identidad, deltas y
  estados por escenario sin prompts ni respuestas.
- `TC-052`: metadata ausente no se interpreta como cero y un dataset diferente
  debe rechazarse con un error de aplicación sanitizado.
- `TC-053`: el export debe conservar la identidad, métricas y escenarios de la
  comparación bajo `wcs.agent-evaluation-evidence.v1`, sin prompts ni
  respuestas.
- `TC-054`: un export que excede el límite de escenarios debe rechazarse con un
  error sanitizado y no debe escribir ni modificar datos.
- `TC-055`: una política positiva debe calcular `completedAt + retention` y
  devolver `ACTIVE` antes del vencimiento.
- `TC-056`: el instante exacto de vencimiento debe devolver `EXPIRED`; la
  decisión no implica una operación de borrado.
- `TC-057`: la revisión debe conservar el orden de entrada y los contadores
  deben coincidir con las decisiones `ACTIVE` y `EXPIRED`.
- `TC-058`: una página histórica vacía debe producir cero decisiones y cero
  contadores sin invocar escrituras.
- `TC-059`: una solicitud no realizada debe devolver `NOT_REQUESTED` sin exigir
  ambiente, actor o referencia.
- `TC-060`: una aprobación completa en el límite temporal debe devolver
  `APPROVED_FOR_REVIEW`; metadata faltante o futura debe devolver `REJECTED` con
  una razón tipada.
- `TC-061`: el trigger sólo debe autorizar la capacidad exacta
  `agent-evaluation.execute` y el ambiente configurado.
- `TC-062`: metadata incompleta, capacidad/ambiente no permitido o un error del
  provider debe devolver `DENIED` sin propagar credenciales ni excepciones.
- `TC-063`: la API interna debe devolver `403` con `ACCESS_DENIED` y ningún
  servicio de evaluación puede recibir una invocación.
- `TC-064`: una lectura autorizada debe construir el filtro tipado y la página
  acotada antes de delegar al servicio de histórico.
- `TC-065`: UUID/fecha/paginación inválidos deben devolver `INVALID_REQUEST`;
  una comparación incompatible debe devolver `INCOMPATIBLE_DATASET` y un run
  inexistente `RUN_NOT_FOUND`.
- `TC-066`: cada intento de acceso debe producir un evento estructurado con la
  operación y el resultado, sin registrar el valor del actor ni contenido de
  evaluación.

### Prueba manual de catálogo por Telegram

Con AppConfig seleccionando `wcs.telegram.enabled=true` y
`wcs.telegram.adapter=telegram`, una API local expuesta por HTTPS y el webhook
registrado, enviar al bot:

```text
¿Tienen remera negra talle M?
```

La respuesta esperada debe incluir `Remera NullPointer`, `18.900,00 ARS` y
`stock disponible: 12`. La respuesta debe provenir del catálogo demo en
PostgreSQL y no de una respuesta generada por el LLM mock. También verificar:

```text
¿Tienen remera blanca talle M?
¿Está disponible el SKU RP-REM-NP-NEG-M?
Busco Remera Fantasma
```

El primer caso debe informar `sin stock`, el segundo debe devolver la variante
correspondiente y el tercero no debe inventar un producto.

### Prueba manual del orquestador de intenciones

Con `wcs.ai.provider=bedrock` y `wcs.ai.model=openai.gpt-oss-20b-1:0` en
AppConfig, probar variaciones de lenguaje natural. El modelo debe producir una
decisión estructurada, pero el cliente sólo recibe el resultado del caso de
uso:

```text
¿Tienen camisetas oscuras en mediano?
¿A qué hora están abiertos el sábado?
¿Cómo funcionan los envíos?
Hola
```

Las consultas deben enrutarse respectivamente a catálogo, horarios, política y
saludo. Verificar que una consulta ambigua solicite aclaración, que el precio y
stock sigan viniendo de PostgreSQL y que el LLM no pueda inventar esos valores.

Para catálogo general y seguimiento conversacional, verificar también:

```text
¿Qué productos tienen?
Busco una remera negra talle M que cueste menos de 20.000 pesos
¿Está disponible?
¿Cuánto cuesta?
```

La consulta general debe devolver una lista acotada desde PostgreSQL. Si el
último resultado es único, los seguimientos deben volver a consultar la
variante y responder el stock o precio vigente. Si hay múltiples variantes o
no existe contexto previo, el bot debe pedir el SKU o una identificación más
precisa sin elegir arbitrariamente.

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
- `WallyCustomerSupportApplicationIntegrationTest`: arranque Spring Boot con JPA, migraciones Flyway V1–V9, consulta filtrada y general del catálogo demo, filtros de precio, seguimiento conversacional, horarios y políticas contra PostgreSQL 16 de Testcontainers.
- La integración PostgreSQL real se ejecuta de forma reproducible con Testcontainers antes de cerrar WCS-12; no se usa H2 para validar el esquema, las queries ni las migraciones.
