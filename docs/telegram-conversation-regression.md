# Suite de regresión conversacional por Telegram

Estado: `Ready after backend deployment`

La suite automatizada se divide en dos evidencias:

1. `ConversationRegressionSuiteTest` valida localmente el contrato de routing
   estructurado contra 51 escenarios sintéticos.
2. `scripts/telegram-conversation-regression.sh` envía las mismas secuencias
   al webhook real de WCS para validar Bedrock, PostgreSQL, RAG, memoria,
   carrito, handoff y pagos en conjunto.

El fixture es
[`conversation-regression-v1.json`](../src/test/resources/fixtures/conversation-regression-v1.json).
No contiene teléfonos, tokens, passwords, prompts productivos ni conversaciones
reales.

## Cobertura

| Nivel | Casos | Qué verifica |
|---|---:|---|
| Fácil | 17 | saludos, catálogo, filtros simples, políticas, horario, handoff, carrito y producto inexistente |
| Medio | 17 | lenguaje natural, errores de escritura, cantidades, precio, checkout incompleto y opt-out |
| Difícil | 17 | continuidad, referencias como “ese/de lo anterior”, cambios de carrito, ambigüedad, normalización y cancelación |

Los límites de seguridad que deben mantenerse son:

- `Quiero un buzo` es interés de catálogo, no compra.
- Sólo una compra explícita o una confirmación sobre un carrito válido puede
  generar un pedido/link.
- Precio, stock, carrito y pedido se validan contra PostgreSQL; el LLM sólo
  propone intención y filtros allow-listed.
- `BAJA` o una solicitud equivalente corta el flujo antes de IA y no genera
  outbox automático.
- Las frases ambiguas deben pedir el dato faltante, nunca elegir una variante
  arbitraria.

## Ejecución local

Desde la raíz del repositorio:

\`\`\`bash
mvn -B -Dtest=ConversationRegressionSuiteTest test
\`\`\`

Para revisar qué se enviaría sin contactar ningún servicio:

\`\`\`bash
./scripts/telegram-conversation-regression.sh --dry-run
./scripts/telegram-conversation-regression.sh --dry-run --difficulty hard
./scripts/telegram-conversation-regression.sh --dry-run --case H-001
\`\`\`

La prueba con Bedrock real sigue siendo opcional y tiene costo. La suite local
no consume tokens porque simula la salida estructurada del proveedor y valida
el parser/contrato. Para evaluar calidad real del modelo se deben comparar las
respuestas observadas en Telegram con los criterios del caso y con los eventos
sanitizados de observabilidad.

## Ejecución posterior al deploy

No ejecutar el modo `--live` hasta confirmar:

- backend desplegado y `GET /actuator/health` en `UP`;
- AppConfig y Secrets Manager cargados para el ambiente esperado;
- `wcs.telegram.enabled=true`;
- webhook HTTPS registrado y aceptando el secreto configurado;
- el chat de prueba no está suprimido por un `BAJA` anterior, o se inició una
  conversación nueva.

Primero ejecutar un caso individual:

\`\`\`bash
./scripts/telegram-conversation-regression.sh \\
  --live \\
  --chat-id '<TEST_CHAT_ID>' \\
  --case H-001 \\
  --delay-seconds 4
\`\`\`

Después ejecutar por nivel:

\`\`\`bash
./scripts/telegram-conversation-regression.sh --live --chat-id '<TEST_CHAT_ID>' --difficulty easy --delay-seconds 4
./scripts/telegram-conversation-regression.sh --live --chat-id '<TEST_CHAT_ID>' --difficulty medium --delay-seconds 4
./scripts/telegram-conversation-regression.sh --live --chat-id '<TEST_CHAT_ID>' --difficulty hard --delay-seconds 4
\`\`\`

El script reutiliza `scripts/smoke-telegram-message.sh`: obtiene sólo el
secreto del webhook desde Secrets Manager, no acepta ni imprime el bot token y
simula un inbound Telegram con un `update_id` nuevo por mensaje. Las respuestas
son entregadas por el adapter normal al chat indicado; el endpoint sólo
confirma el ack del webhook, por lo que las respuestas se deben revisar en
Telegram.

## Criterio de aceptación manual

Para cada escenario registrar únicamente `PASS`/`FAIL`, timestamp, ambiente,
versión de la aplicación, `id` del caso y una observación sanitizada. No
adjuntar el texto completo de la conversación, teléfonos, tokens, prompts ni
URLs de pago.

Un caso pasa si:

1. el intent/action observado coincide con la ruta esperada;
2. los filtros aplicados coinciden con los datos expresados y el resultado no
   inventa precio/stock;
3. los mensajes de seguimiento preservan el contexto correcto o piden sólo el
   dato faltante;
4. no se crea un pedido/link antes de la confirmación requerida;
5. las métricas y logs permiten correlacionar la etapa sin contenido sensible.

La evidencia operativa mínima para cerrar el smoke es:

- conteo de casos ejecutados por dificultad;
- intents/actions observados: `CATALOG_SEARCH`, `BUSINESS_HOURS`,
  `POLICY_QUERY`, `GENERAL_SUPPORT`, `HUMAN_HANDOFF`, `ADD_TO_CART`,
  `REMOVE_FROM_CART`, `CONFIRM_CHECKOUT`, `CANCEL_CHECKOUT`, `OPT_OUT`;
- errores de proveedor, fallbacks `UNKNOWN`, duplicados e idempotencia;
- latencia y uso de IA agregados, sin prompts/respuestas completas;
- resultado de health check y referencia del deploy.

No se debe marcar el MVP como validado sólo porque el webhook devuelva HTTP
`200`: ese `200` demuestra el ack de entrada, no la calidad de la respuesta.
