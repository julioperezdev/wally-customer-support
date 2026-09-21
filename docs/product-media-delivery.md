# Entrega de imágenes de producto por canales

## Resolución por variante

La imagen visible debe corresponder a la variante seleccionada, no sólo al
producto. `catalog_variants.image_object_key` es la referencia preferida para
el SKU; `catalog_products.image_object_key` se utiliza como fallback cuando la
variante no tiene una imagen propia. Esto permite que Remera NullPointer negro
y blanco, por ejemplo, compartan producto pero no imagen.

El catálogo demo mantiene cinco assets en S3:

| SKU | Key |
|---|---|
| `RP-REM-NP-NEG-M` y `RP-REM-NP-NEG-L` | `wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-NEG-M.png` |
| `RP-REM-NP-BLA-M` | `wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-BLA-M.png` |
| `RP-BUZ-SB-GRI-L` | `wcs/catalog/10000000-0000-0000-0000-000000000002/RP-BUZ-SB-GRI-L.png` |
| `RP-BUZ-SB-NEG-XL` | `wcs/catalog/10000000-0000-0000-0000-000000000002/RP-BUZ-SB-NEG-XL.png` |
| `RP-CAM-DF-AZU-M` | `wcs/catalog/10000000-0000-0000-0000-000000000003/RP-CAM-DF-AZU-M.png` |

La migración `V26` asigna estas referencias y deja la referencia de producto
como fallback para variantes sin media propia. Los adapters de Telegram y WhatsApp reciben una referencia opaca y
la convierten en una URL prefirmada de corta duración; si el objeto no está
disponible, conservan el fallback textual.

Estado: `Implemented` para el catálogo demo; pendiente de validación manual en
Telegram y de la activación productiva de WhatsApp.

## Objetivo

Cuando una búsqueda del catálogo devuelve una única variante inequívoca con una
imagen cargada, WCS puede acompañar la respuesta textual con esa imagen. El
caso de uso y el dominio no conocen Telegram, WhatsApp, S3 ni URLs públicas.

La política actual es deliberadamente conservadora:

- Una coincidencia exacta inicial: imagen con el texto como caption.
- Listados con varias variantes, resultados alternativos, aclaraciones y
  consultas de seguimiento: sólo texto.
- Si el texto supera el límite de caption del canal (1024 caracteres), sólo se
  entrega texto para conservar la respuesta completa.
- Sin imagen, objeto inválido o error de presignado: la respuesta textual se
  entrega igualmente.

## Flujo

```text
catalog_variants.image_object_key
              | (fallback: catalog_products.image_object_key)
              |
              v
CatalogSearchResult (facts + imagen por SKU)
              |
              v
ConversationExecutionResult.mediaReference
              |
              v
outbox_messages.media_reference  -- sólo la referencia, no una URL
              |
              v
OutboundMediaUrlResolver en el despacho
              |
      +-------+--------+
      |                |
      v                v
Telegram sendPhoto   Meta image message
      |                |
      +---- fallback --+
           sendMessage/text
```

La URL prefirmada se crea en el momento de despachar el outbox y dura cinco
minutos. No se persiste, no se escribe en logs y no se incluye en respuestas de
la API. El resolver acepta únicamente referencias bajo el prefijo configurado
`wcs.backoffice.media.prefix` y rechaza traversal de rutas.

## Contratos y persistencia

- `DeliveryType.IMAGE` es un tipo de entrega agnóstico al proveedor.
- `OutboundMessage.mediaReference` es opaco para la aplicación; en el adapter
  S3 representa la key del objeto.
- `V21__add_outbound_media_reference.sql` agrega `media_reference` al outbox
  sin modificar migraciones aplicadas.
- Los adapters Telegram y Meta traducen `IMAGE` a sus payloads nativos.
- Los adapters mock aceptan el tipo para no romper pruebas de aplicación.

## Seguridad y operación

- El bucket continúa privado; Telegram o Meta reciben sólo una URL temporal.
- Una referencia antigua, fuera de prefijo o no disponible nunca provoca que
  se envíe la key como texto: se registra un fallback sanitizado y se entrega
  el caption como texto.
- No se registran URL prefirmadas, keys, tokens, teléfonos ni contenido de
  conversaciones.
- Desactivar `wcs.backoffice.media.enabled` deja el sistema en modo texto sin
  cambiar la lógica de catálogo.

## Pruebas

Automáticas:

1. Una búsqueda devuelve la referencia sólo para una coincidencia única.
2. Telegram usa `sendPhoto` con URL resuelta y caption.
3. Meta usa el payload `type=image` con link y caption.
4. Resolver ausente o fallido conserva el envío textual.
5. El outbox rehidrata `media_reference` desde PostgreSQL.

Manuales:

1. Cargar o reemplazar una imagen en un producto desde el backoffice.
2. En Telegram enviar `Busco la remera NullPointer negra talle M` y comprobar
   que llega una imagen con la respuesta.
3. Enviar `¿Qué remeras tienen?` y comprobar que llega un listado textual sin
   una secuencia de imágenes.
4. Repetir `¿Está disponible?` y comprobar que el seguimiento es textual.
5. Eliminar temporalmente la referencia o desactivar media y comprobar que el
   cliente sigue recibiendo la respuesta textual.

## Rollback

El rollback funcional inmediato es desactivar `wcs.backoffice.media.enabled`
en el ambiente correspondiente; el catálogo y las respuestas de texto siguen
operativos. El rollback de código consiste en volver al release anterior, sin
eliminar `media_reference` ni modificar la migración V21.
