# Pedidos y pagos — WCS-122

Status: `In Review`
Related Jira: [WCS-122](https://julioperezdev.atlassian.net/browse/WCS-122)
Related migration: `V18__create_orders_and_payments.sql`

Este slice agrega venta asistida al backoffice sin acoplar la lógica de WCS a
Mercado Pago. El backend valida el catálogo vigente, persiste el pedido y
delega la creación del checkout al puerto `PaymentGateway`. El adapter de
Mercado Pago usa el SDK oficial Java (`com.mercadopago:sdk-java`) y el proveedor
actual puede ser `mock` para pruebas y `mercadopago` para Checkout Pro Sandbox.

## Flujo

```text
Backoffice autenticado
        |
        | POST /internal/backoffice/orders + Idempotency-Key
        v
WCS valida SKU, precio, moneda y stock con lock de PostgreSQL
        |
        v
orders + order_items (PENDING_PAYMENT)
        |
        v
PaymentGateway -> Mock o Mercado Pago Checkout Pro
        |
        v
link de checkout para el operador
        |
        v
POST /webhook/mercadopago -> firma -> GET payment -> estado idempotente
```

El precio y el nombre se toman de PostgreSQL en el momento de crear el pedido;
no se aceptan desde el navegador ni desde el LLM. La versión actual verifica
stock bajo lock, pero no lo descuenta ni lo reserva de manera temporal. Por lo
tanto, todavía no debe presentarse como inventario transaccional de producción:
la reserva definitiva será un incremento posterior con expiración y
compensación.

## Contrato del backoffice

Los endpoints están bajo el boundary interno y requieren que el backoffice
esté habilitado:

| Método | Endpoint | Capacidad | Uso |
| --- | --- | --- | --- |
| `POST` | `/internal/backoffice/orders` | `backoffice.orders.write` | Valida una línea o varias, crea el pedido y devuelve el link |
| `GET` | `/internal/backoffice/orders` | `backoffice.orders.read` | Lista pedidos recientes, opcionalmente filtrados por estado |
| `GET` | `/internal/backoffice/orders/{id}` | `backoffice.orders.read` | Consulta detalle y líneas del pedido |
| `POST` | `/webhook/mercadopago` | webhook público firmado | Recibe notificaciones y reconcilia el pago |

Ejemplo de creación:

```http
POST /internal/backoffice/orders
Authorization: Bearer <token>
Idempotency-Key: operator-checkout-2026-09-13-001
Content-Type: application/json

{
  "customerReference": "telegram:demo",
  "items": [{ "sku": "RP-REM-NP-NEG-M", "quantity": 1 }]
}
```

La misma key con el mismo contenido devuelve el mismo pedido. La misma key con
otro cliente o líneas devuelve `409 IDEMPOTENCY_KEY_REUSED`. Un SKU inexistente,
inactivo, sin stock suficiente o con moneda no soportada devuelve `400` y no
crea un link. Si el proveedor no está disponible, queda un pedido
`PENDING_PAYMENT` sin link y el endpoint devuelve `502` para que el operador
pueda reintentar con la misma key.

La pantalla de `backoffice/` ya permite crear un pedido demo, listar pedidos y
abrir el link recibido. En preview sólo se expone lectura; la creación exige
un JWT con `SCOPE_backoffice.orders.write`.

## Estados y webhook

Los estados internos son `PENDING_PAYMENT`, `PAID`, `REJECTED`, `CANCELLED` y
`EXPIRED`. El webhook no confía en el estado del cuerpo de la notificación:
usa el `data.id`, consulta el pago al proveedor y toma el `external_reference`
como UUID del pedido. Esto evita aceptar un estado inventado por el emisor.

El adapter verifica `x-signature` con HMAC-SHA256 y una ventana de 15 minutos.
El manifiesto firmado es:

```text
id:{data.id};request-id:{x-request-id};ts:{timestamp};
```

Cada evento se guarda una sola vez por `provider + provider_event_id` en
`wcs.payment_events`. Los reintentos de Mercado Pago devuelven `DUPLICATE` y
no vuelven a aplicar una transición. Un evento válido cuyo pedido no existe se
registra como `IGNORED` para poder auditarlo sin crear datos incompletos.

## Configuración

La configuración no sensible vive en el perfil `runtime` de AppConfig:

```json
{
  "wcs.payment.provider": "mock",
  "wcs.payment.currency": "ARS",
  "wcs.payment.notification-url": "https://<backend>/webhook/mercadopago",
  "wcs.payment.webhook.signature-required": true,
  "wcs.external-config.secrets-manager.mercado-pago-secret-id": "wcs/prod/mercado-pago"
}
```

El secret dedicado de Secrets Manager tiene esta forma, con valores escritos
fuera del repositorio:

```json
{
  "access-token": "<MERCADO_PAGO_ACCESS_TOKEN>",
  "webhook-secret": "<MERCADO_PAGO_WEBHOOK_SECRET>"
}
```

Terraform crea el contenedor `wcs/<environment>/mercado-pago` con placeholders
seguros y lo incluye en el allow-list de secretos del role de App Runner. El
placeholder no habilita Mercado Pago: para activar el adapter hay que cargar el
token real, cargar el secreto de firma, configurar la URL pública y cambiar
`wcs.payment.provider` a `mercadopago`. No guardar tokens en AppConfig,
`terraform.tfvars`, logs, tests ni el frontend.

## Pruebas y evidencia

Pruebas automatizadas incorporadas en este slice:

- una creación toma precio/moneda/nombre del catálogo y calcula el total;
- una segunda solicitud idempotente reutiliza el pedido y no crea otra
  preferencia;
- stock insuficiente se rechaza antes de persistir o llamar al proveedor;
- una firma de webhook fresca es aceptada;
- una firma incorrecta o vencida es rechazada;
- el modo sin firma sólo funciona cuando está explícitamente configurado para
  un entorno de prueba.

Comandos locales:

```bash
mvn -q -Dtest='OrderApplicationServiceTest,PaymentWebhookSignatureVerifierTest' test
cd backoffice && npm run check
```

La prueba real de Sandbox requiere credenciales de prueba de Mercado Pago y no
debe ejecutarse contra la base productiva sin revisar antes el plan de datos.
El smoke debe conservar: request/response sanitizados, `orderId`, preference
ID, estado final, evento deduplicado y evidencia de que no se loguearon tokens
ni payloads completos.

## Rollout y siguientes límites

1. Mantener `mock` hasta probar el flujo completo del backoffice y la migración.
2. Ejecutar Terraform `plan` y revisar que sólo cree el secret dedicado y sus
   permisos; no hacer `apply` sin aprobación explícita.
3. Cargar credenciales Sandbox fuera del código y reiniciar App Runner para
   que el bootstrap lea Secrets Manager.
4. Configurar el webhook público y probar una preferencia con un SKU demo.
5. Repetir la notificación y verificar `DUPLICATE`.
6. Activar `mercadopago` sólo en el ambiente de prueba antes de producción.

Fuera de este slice quedan reserva con expiración, carrito persistido,
descuentos, reembolsos, logística, tarjetas y pagos productivos. El puerto
permite incorporar otro proveedor sin cambiar los casos de uso.
