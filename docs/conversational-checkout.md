# Compra conversacional y links de pago — WCS-122

Status: `Implemented / pending end-to-end smoke`
Related Jira: [WCS-122](https://julioperezdev.atlassian.net/browse/WCS-122)
Related model: [`data-model.md`](data-model.md)
Related tests: `CatalogQueryParserTest`, `ConversationOrchestratorTest`, `OrderApplicationServiceTest`

## Objetivo

Permitir que una persona pase de consultar un producto a comprarlo dentro del
mismo chat. Cuando expresa de forma explícita que quiere comprar, WCS valida la
variante y el stock en PostgreSQL, crea un pedido pendiente y devuelve un link
de checkout por el mismo canal de origen.

El chat no captura datos de tarjeta, no confirma el pago por el texto del
usuario y no conoce la API de Mercado Pago. El estado final del pago se obtiene
únicamente desde el webhook firmado del proveedor.

## Cuándo se genera un link

La solicitud debe ser explícita. Son ejemplos válidos:

```text
Quiero comprarla
Quiero comprar 2 remeras negras talle M
Pasame el link de pago para esa remera
Me la llevo
Quiero pagar esa variante
```

Estos mensajes no generan un pedido:

```text
¿Qué remeras tienen?
Me interesa esa remera
¿Cuánto cuesta?
Quiero esa remera
No quiero comprarla todavía
```

La última regla evita que una expresión de interés o una respuesta ambigua se
interprete como autorización de compra. La intención de compra se puede
expresar en un turno posterior, utilizando el contexto acotado de catálogo.

## Flujo completo

```text
Telegram / WhatsApp
        |
        v
Webhook + outbox durable
        |
        v
ConversationOrchestrator
        |
        | solicitud explícita de compra
        v
Reconstrucción de selección desde el contexto acotado
        |
        v
CatalogConversationService -> PostgreSQL
        |
        | exactamente una variante activa y stock suficiente
        v
PurchaseLinkCreator (puerto de conversación)
        |
        v
OrderApplicationService
        | valida nuevamente SKU, moneda y stock
        | crea o recupera el pedido por idempotencia
        v
PaymentGateway -> Mock o Mercado Pago Checkout Pro
        |
        v
Link enviado por el adapter del canal
        |
        v
Webhook firmado del proveedor -> estado PAID/REJECTED/etc.
```

### 1. Interpretación segura

`CatalogQueryParser` detecta la acción explícita de compra y reconstruye los
filtros relevantes del diálogo reciente. La cantidad se interpreta de forma
determinística y por defecto es `1`. El LLM puede clasificar la intención, pero
no puede inventar el SKU, el precio, la moneda ni el stock.

El orquestador fuerza la ruta `PURCHASE_LINK` cuando existe un marcador
explícito de compra. Así, una clasificación general del modelo no puede
convertir una solicitud inequívoca en una respuesta informativa ni una
consulta de catálogo en un pedido.

### 2. Validación de la variante

WCS consulta el catálogo dinámico mediante PostgreSQL y sólo continúa si el
resultado es `MATCHED` con exactamente una variante. Debe cumplirse todo lo
siguiente:

- SKU activo.
- Producto y variante inequívocos.
- Stock disponible.
- Cantidad solicitada menor o igual al stock.
- Moneda compatible con la configuración de pagos.

Si hay más de una variante, el bot pide producto, talle y color. Si no hay
stock, informa la indisponibilidad. En ambos casos no se llama a Mercado Pago.

### 3. Creación idempotente del pedido

`PurchaseLinkCreator` es el puerto que desacopla la conversación del módulo de
pedidos. `OrderPurchaseLinkCreator` lo implementa delegando en
`OrderApplicationService`, que vuelve a validar los datos desde PostgreSQL y
crea el snapshot de la línea del pedido.

La operación usa una key derivada de:

```text
conversationId | sku | quantity
```

La key se almacena como parte del pedido mediante el contrato de idempotencia
existente. Repetir la misma compra en la misma conversación devuelve el mismo
pedido y link sin crear una nueva preferencia. Una petición distinta debe usar
otra operación. La respuesta no expone la key.

El pedido comienza en `PENDING_PAYMENT`. Crear el link no reserva ni descuenta
stock todavía. Esa limitación es deliberada y está fuera de este slice; una
reserva real requiere expiración, compensación y una política para la
concurrencia de compras.

### 4. Provider de pagos

El módulo de pedidos usa el puerto `PaymentGateway`:

- `MockPaymentGateway` permite probar el flujo local sin credenciales.
- `MercadoPagoPaymentGateway` usa el SDK oficial Java y `PreferenceClient` para
  Checkout Pro Sandbox. El access token se carga desde Secrets Manager.

Por eso esta implementación usa la API de Preferencias/Checkout Pro existente.
La migración a la API de Orders puede evaluarse en otro slice cuando se
necesite procesamiento manual, múltiples transacciones u operaciones más
avanzadas; no es necesaria para validar esta venta asistida de una línea.

La configuración sensible se mantiene fuera del repositorio:

```json
{
  "access-token": "<MERCADO_PAGO_ACCESS_TOKEN>",
  "webhook-secret": "<MERCADO_PAGO_WEBHOOK_SECRET>"
}
```

La configuración no sensible se publica en AppConfig:

```json
{
  "wcs.payment.provider": "mercadopago",
  "wcs.payment.currency": "ARS",
  "wcs.payment.notification-url": "https://<backend>/webhook/mercadopago",
  "wcs.payment.webhook.signature-required": true,
  "wcs.external-config.secrets-manager.mercado-pago-secret-id": "wcs/prod/mercado-pago"
}
```

En local se puede cambiar el provider a `mock` dentro de un perfil de prueba.
No colocar tokens reales en AppConfig, `application.properties`, Terraform,
logs, capturas, tests ni frontend.

### 5. Entrega y confirmación

La respuesta del orquestador contiene el nombre, SKU, cantidad, total y link de
checkout. El mensaje se persiste en el outbox y el adapter de Telegram o
WhatsApp lo entrega sin que el dominio conozca el canal.

La URL nunca se registra en logs estructurados. Se registra solamente el
resultado operacional:

```text
CONVERSATIONAL_PURCHASE_LINK
operation=conversation.purchase-link
result=LINK_CREATED|VARIANT_REQUIRED|VARIANT_NOT_UNIQUE|
       INSUFFICIENT_STOCK|LINK_UNAVAILABLE
channel, correlationId, actorKey opcional
```

Mercado Pago notifica el resultado en `/webhook/mercadopago`. WCS verifica la
firma, consulta el pago al proveedor usando el identificador recibido,
relaciona el `external_reference` con el pedido y deduplica el evento en
`payment_events`.

## Contratos de aplicación

La frontera de conversación expone sólo:

```java
PurchaseLinkCreator.create(CreatePurchaseLinkRequest)
```

El request contiene `conversationId`, una referencia pseudónima del cliente,
SKU, cantidad y key de idempotencia. El resultado contiene `orderId`,
descripción comercial, total, moneda, provider y `checkoutUrl` para que el
canal pueda entregar el link. Ningún prompt, secreto ni payload del proveedor
cruza esta frontera.

La autoridad de precio, stock, moneda, estado del pedido y proveedor permanece
en el módulo de órdenes. La autoridad de selección de catálogo permanece en la
consulta determinística. El LLM sólo ayuda a interpretar el lenguaje natural
y a redactar otras respuestas; no ejecuta SQL ni crea pedidos directamente.

## Pruebas

### Automatizadas

La cobertura del slice valida:

- detección de compra explícita y rechazo de interés/negación;
- recuperación de una selección desde turnos anteriores;
- cantidad por defecto y cantidad explícita;
- ruta `PURCHASE_LINK` del orquestador;
- ausencia de creación cuando la variante es ambigua;
- delegación al servicio de órdenes con SKU, cantidad y key correctos;
- idempotencia, precio/moneda desde catálogo y stock insuficiente;
- webhook firmado y deduplicación, según la suite existente de pagos.

Ejecutar desde la raíz:

```bash
mvn -q -Dtest='CatalogQueryParserTest,ConversationExecutionPlanFactoryTest,BedrockConversationIntentClassifierTest,ConversationOrchestratorTest,OrderApplicationServiceTest,PaymentWebhookSignatureVerifierTest' test
mvn -q verify
```

### Smoke manual por Telegram

1. Iniciar una conversación nueva o reactivarla con `/start`.
2. Enviar `Busco la remera NullPointer negra talle M`.
3. Confirmar que la variante tiene stock.
4. Enviar `Quiero comprarla`.
5. Verificar que llega un link, que el texto dice que el pedido está preparado
   para pagar y que no afirma que el pago ya fue realizado.
6. Repetir el mensaje exacto y verificar en el backoffice que no aparece un
   segundo pedido.
7. Repetir con una búsqueda que devuelva varias variantes y confirmar que el
   bot solicita una única combinación.
8. Probar una cantidad mayor que el stock y confirmar que no se genera link.

Registrar sólo evidencia sanitizada: `orderId` parcial o pseudonimizado,
estado, resultado del dashboard y timestamp. No compartir URLs de pago con
credenciales, tokens, teléfonos ni la conversación completa.

## Rollout y rollback

1. Desplegar el backend que contiene el nuevo flujo y la migración vigente
   `V21` si todavía no está aplicada.
2. Mantener el proveedor `mock` en local o en un ambiente de prueba hasta
   validar la lógica de conversación.
3. Para Sandbox, cargar el access token y la firma en Secrets Manager,
   verificar AppConfig y reiniciar/redeployar App Runner según el mecanismo de
   configuración vigente.
4. Ejecutar el smoke del link y del webhook firmado.
5. Si el proveedor falla, volver temporalmente a `mock` en un ambiente de
   prueba o deshabilitar el canal de checkout; el chat debe continuar
   respondiendo consultas sin confirmar pedidos.

No borrar pedidos ni migraciones durante un rollback. Los pedidos pendientes
sin link quedan auditables y pueden reintentarse con la misma idempotency key.

## Fuera de alcance de este slice

- Reserva o descuento de stock al crear el link.
- Carrito persistido y múltiples líneas iniciadas desde el chat.
- Cupones, descuentos, impuestos o costo de envío calculado dinámicamente.
- Cancelaciones, reembolsos y pagos productivos.
- Captura de tarjeta dentro de WCS.
- Confirmación de pago basada sólo en un redirect del navegador.
- Uso de MCP para que el LLM ejecute SQL.

Estas capacidades deben agregarse detrás de puertos y con nuevas migraciones,
tests de concurrencia, permisos y evidencias de negocio.
