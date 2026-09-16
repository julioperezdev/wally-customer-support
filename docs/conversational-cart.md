# Carrito conversacional multiítem — WCS-129

Status: `Implemented / pending end-to-end smoke`
Related Jira: [WCS-129](https://julioperezdev.atlassian.net/browse/WCS-129)
Related migration: `V22__create_conversational_carts.sql`
Related checkout: [`conversational-checkout.md`](conversational-checkout.md)
Related data model: [`data-model.md`](data-model.md)

## Objetivo

Permitir que una persona arme y modifique un carrito desde Telegram, WhatsApp
o cualquier canal que implemente el mismo contrato. El cliente puede agregar
varias variantes y cantidades, consultar el resumen, quitar líneas, cancelar
un checkout anterior y confirmar una sola compra por el total del carrito.

El carrito pertenece a una conversación y a su actor pseudonimizado. El canal
no conoce SQL, entidades JPA, el proveedor de pagos ni el mecanismo de
idempotencia.

## Conversación soportada

Los comandos son determinísticos y aceptan lenguaje natural en español. No se
necesita que el cliente conozca los nombres internos de las acciones:

| Intención | Ejemplos | Resultado |
| --- | --- | --- |
| Agregar | `Agregá 2 remeras NullPointer negras talle M al carrito`; `Sumá un buzo Spring Boot negro talle XL` | Resuelve una variante única y acumula la cantidad |
| Ver | `¿Qué hay en mi carrito?`; `Mostrame la cesta` | Muestra líneas, subtotales, total y disponibilidad actual |
| Quitar | `Sacá 1 remera NullPointer negra talle M`; `Quitá el buzo del carrito` | Reduce la cantidad o elimina la línea |
| Vaciar | `Vaciar carrito` | Elimina todas las líneas mientras no haya checkout activo |
| Confirmar | `Confirmar compra`; `Generame el link de pago` | Crea un único pedido multiítem y devuelve un link |
| Cancelar checkout | `Cancelar el link de pago`; `Quiero modificar el carrito` | Cancela pedidos pendientes del carrito y lo reabre |
| Postergar | `No quiero comprar todavía` | No crea un pedido; si había checkout activo, lo cancela y conserva el carrito |

Si una frase apunta a varias variantes, el bot no elige arbitrariamente: pide
los datos faltantes (producto, talle y color). Si no hay coincidencias, no
agrega una línea ni inventa stock.

## Flujo

```text
Mensaje del canal
      |
      v
ConversationOrchestrator
      |
      | comando de carrito reconocido
      v
ConversationalCartService
      |
      +--> CatalogConversationService -> PostgreSQL (SKU, precio, stock)
      |
      +--> carts + cart_items (versión del carrito)
      |
      | confirmar compra
      v
OrderCartCheckoutCreator -> OrderApplicationService
      |
      +--> orders + order_items (snapshot validado)
      +--> PaymentGateway -> Mock o Mercado Pago
      |
      v
Outbox -> adapter Telegram / WhatsApp
```

El parser y el servicio de carrito se ejecutan antes de la clasificación del
LLM. Esto evita que una pregunta como `confirmar compra` o `sacá el buzo` se
convierta en una respuesta genérica por una clasificación de baja confianza.
El LLM no puede inventar SKU, precio, moneda ni stock.

## Persistencia y ownership

La migración V22 crea:

- `wcs.carts`, un carrito activo por `conversation_id`, con canal, moneda,
  estado, `version` de negocio y el pedido asociado al checkout;
- `wcs.cart_items`, una línea por SKU y carrito, con cantidad entre 1 y 100;
- `cart_id` y `cart_version` opcionales en `wcs.orders` para trazar el pedido
  hasta la versión del carrito que lo originó.

El identificador externo del canal no se guarda como owner en el carrito. WCS
calcula un HMAC con el secreto de observabilidad y conserva sólo esa clave
pseudónima. Un actor o canal diferente no puede leer ni modificar el carrito
de la conversación.

El precio y el stock se vuelven a consultar al mostrar el carrito y se validan
otra vez al confirmar. El carrito no congela precios ni reserva stock al
agregar una línea. La reserva con expiración, descuento, envío e impuestos
dinámicos quedan fuera de este slice.

## Checkout, idempotencia y links anteriores

Cada modificación del contenido incrementa la versión de negocio del carrito.
La confirmación usa la key:

```text
cart-{cartId}-v{cartVersion}
```

La key se delega al servicio de pedidos, que vuelve a validar cada SKU, stock,
moneda y precio y guarda el snapshot en `order_items`. Repetir la confirmación
de la misma versión devuelve el mismo pedido/link; confirmar después de
modificar el carrito genera una nueva versión y una nueva operación idempotente.

Mientras existe un checkout `CHECKOUT_PENDING`, no se permiten cambios
silenciosos. El cliente debe decir `cancelar compra`, `cancelar el link` o
`quiero modificar el carrito`. WCS entonces:

1. marca como `CANCELLED` los pedidos `PENDING_PAYMENT` de ese carrito;
2. reabre el carrito como `ACTIVE` e incrementa su versión;
3. permite modificarlo y generar un nuevo link.

El link anterior puede seguir siendo accesible técnicamente en el proveedor,
pero su pedido queda cancelado en WCS y el webhook no puede revivirlo porque
las transiciones desde un estado terminal se ignoran. En una evolución futura
se puede solicitar la expiración/cancelación nativa al proveedor si la API lo
permite.

El checkout se crea con todos los items del carrito en una sola operación. No
se generan links parciales por cada producto.

## Respuestas y privacidad

Las respuestas contienen nombre comercial, SKU, cantidad, precio vigente,
subtotal, moneda, stock y total. No contienen actor externo, prompts, SQL,
tokens, secretos ni la key de idempotencia. Los logs registran operación,
resultado, canal y correlation id; nunca el contenido completo de la
conversación ni la URL de pago.

## Pruebas automatizadas

La implementación cubre:

- parseo de comandos de agregar, ver, quitar, vaciar, confirmar, cancelar y
  postergar;
- cantidades y filtros de catálogo con género/plural (`remeras negras`);
- acumulación de varias líneas y cálculo de subtotales/total;
- ownership pseudonimizado y estado `CHECKOUT_PENDING`;
- creación de un checkout con todas las líneas y versión del carrito;
- cancelación de pedidos pendientes y reapertura del carrito;
- aplicación de Flyway V22 y persistencia/relectura con PostgreSQL 16 en
  Testcontainers.

Comandos desde la raíz:

```bash
mvn -q -Dtest='CartCommandParserTest,ConversationalCartServiceTest,OrderCartCheckoutCreatorTest,OrderApplicationServiceTest' test
mvn -q -Dtest='CartPersistenceIntegrationTest' test
mvn -q verify
```

## Smoke manual por Telegram

1. Enviar `/start` para comenzar una conversación limpia.
2. Enviar `Agregá 2 remeras NullPointer negras talle M al carrito`.
3. Enviar `Sumá 1 buzo Spring Boot negro talle XL al carrito`.
4. Enviar `¿Qué hay en mi carrito?` y verificar dos líneas, subtotales y total.
5. Enviar `Sacá 1 remera NullPointer negra talle M` y verificar el nuevo total.
6. Enviar `Confirmar compra` y verificar un único link por el total restante.
7. Repetir `Confirmar compra`: no debe aparecer un segundo pedido para la misma
   versión.
8. Enviar `Cancelar el link de pago`; el bot debe cancelar el checkout anterior.
9. Agregar o quitar una línea y confirmar otra vez: debe generarse un pedido
   nuevo asociado a una versión posterior del carrito.
10. Enviar `No quiero comprar todavía`: no debe crear otro pedido.

Registrar sólo resultado, timestamp, estado y un identificador sanitizado. No
adjuntar el chat completo ni URLs de pago.

## Límites conocidos

- No se reserva stock al agregar ni se descuenta al generar el link.
- El carrito es uno por conversación; no hay múltiples carritos guardados por
  usuario en esta primera versión.
- No hay cupones, impuestos, costo de envío, checkout invitado ni reembolsos.
- El adapter no puede garantizar que el proveedor invalide inmediatamente una
  URL ya emitida; la protección definitiva está en el estado terminal de WCS y
  el webhook idempotente.
