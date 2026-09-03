# Requerimientos funcionales — WCS

Estado: `Accepted` para el MVP. Aprobado por el Product Owner el 2026-09-03. La validación legal de retención y supresión sigue siendo requisito previo a producción.

## Actores

- Cliente de la tienda.
- Bot de soporte.
- Agente humano de soporte.
- Operador técnico.
- Administrador de la cuenta Meta/WhatsApp.

## Decisiones de alcance del MVP

- La tienda de demostración se llama **Ropa de Programador**.
- El canal del MVP es WhatsApp y sólo se procesan mensajes de texto.
- Telegram se incorpora como canal técnico de desarrollo/prueba con los mismos
  casos de uso conversacionales; el dominio recibe un contrato interno con
  `Channel` y no conoce payloads ni APIs de proveedores.
- El bot responde siempre la primera interacción. El pedido de atención humana crea una tarea priorizada para el backoffice, con el contexto de la conversación y vencimiento recomendado dentro de 24 horas.
- El MVP no ejecuta cancelaciones, reembolsos, pagos ni modificaciones de pedidos.
- Se utilizará un catálogo ficticio en PostgreSQL. Las imágenes se almacenarán en S3, pero no se enviarán como media por WhatsApp durante esta fase.
- Los horarios y las políticas de demostración serán datos configurables en PostgreSQL.
- El runtime objetivo usa integraciones reales. Los dobles quedan disponibles para pruebas automatizadas y contractuales, no como perfil operativo normal.

## Requerimientos iniciales

| ID | Requerimiento | Prioridad | Estado |
|---|---|---|---|
| FR-001 | Recibir mensajes de texto entrantes desde WhatsApp. | Alta | Draft |
| FR-002 | Verificar el webhook de Meta mediante challenge y token. | Alta | Draft |
| FR-003 | Validar la firma HMAC de cada POST recibido. | Alta | Draft |
| FR-004 | Persistir una conversación y su historial mínimo. | Alta | Draft |
| FR-005 | No procesar dos veces el mismo `external_message_id`. | Alta | Draft |
| FR-006 | Generar una respuesta mediante un cliente LLM desacoplado. | Alta | Draft |
| FR-007 | Enviar una respuesta de texto a WhatsApp. | Alta | Draft |
| FR-008 | Responder rápido al webhook y procesar el mensaje de forma asíncrona. | Alta | Draft |
| FR-009 | Reintentar errores transitorios con un máximo configurable. | Alta | Draft |
| FR-010 | Ejecutar pruebas automatizadas con dobles controlados sin exponer credenciales reales. | Alta | Draft |
| FR-011 | Rechazar o derivar mensajes fuera de la ventana de atención. | Alta | Draft |
| FR-012 | Registrar estado, duración, correlación y errores sin contenido sensible. | Alta | Draft |
| FR-013 | Permitir una ruta explícita de fallback o escalamiento humano. | Alta | TBD negocio |
| FR-014 | Responder sólo con información autorizada por la tienda. | Alta | TBD negocio |
| FR-015 | Responder el saludo inicial con `Hola, ¿cómo te puedo ayudar?`. | Alta | Draft |
| FR-016 | Responder consultas generales usando políticas y horarios versionados/configurables. | Alta | Draft |
| FR-017 | Consultar productos, variantes, precio y stock desde resultados determinísticos de PostgreSQL. | Alta | Draft |
| FR-018 | Mantener una referencia de imagen de producto almacenada en S3 sin enviar media en el MVP. | Media | Draft |
| FR-019 | Evaluar el horario de atención desde datos persistidos y aplicar la política fuera de horario. | Alta | Draft |
| FR-020 | Crear una tarea de seguimiento humano priorizada con contexto, motivo, estado y vencimiento. | Alta | Draft |
| FR-021 | Respetar una solicitud de baja y evitar respuestas o seguimientos automáticos posteriores. | Alta | Draft |
| FR-022 | Aplicar retención diferenciada para contenido, metadatos, métricas y lista de supresión. | Alta | TBD legal |

## Casos de uso

### UC-001 — Cliente consulta por texto

**Dado** un mensaje de texto válido y una conversación dentro de la ventana de atención, **cuando** el webhook lo recibe y persiste, **entonces** el sistema encola el procesamiento, genera una respuesta no vacía y registra el mensaje saliente.

### UC-002 — Meta verifica el webhook

**Dado** un challenge con token válido, **cuando** se solicita `GET /webhook/whatsapp`, **entonces** responde `200` con el challenge exacto. Con token inválido responde `403`.

### UC-003 — Mensaje duplicado

**Dado** un `external_message_id` ya procesado, **cuando** Meta reenvía el evento, **entonces** el sistema conserva una única ocurrencia y no envía una segunda respuesta.

### UC-004 — Firma inválida

**Dado** un POST con firma ausente o inválida, **cuando** llega al webhook, **entonces** responde `403`, no persiste un mensaje procesable y no llama al LLM.

### UC-005 — Proveedor no disponible

**Dado** un error transitorio de Meta o LLM, **cuando** se procesa el mensaje, **entonces** se reintenta según política, se registra el estado y se ejecuta fallback si se agotan los intentos.

### UC-006 — Escalamiento humano

**Dado** que la consulta está fuera del conocimiento autorizado o requiere una operación sensible, **cuando** el bot la clasifica, **entonces** responde que una persona revisará el caso y crea una tarea priorizada con el contexto mínimo necesario, motivo, prioridad y vencimiento recomendado dentro de 24 horas. El MVP no incluye el panel del backoffice.

### UC-007 — Solicitud de baja

**Dado** un mensaje como `BAJA`, `STOP` o “no me escribas más”, **cuando** el sistema lo recibe, **entonces** marca el contacto como `DO_NOT_CONTACT`, detiene las respuestas automáticas y no crea seguimientos proactivos. Este caso es distinto de solicitar atención humana.

### UC-008 — Consulta de catálogo

**Dado** un producto o filtro de nombre, SKU, talle o color, **cuando** el cliente consulta, **entonces** el sistema obtiene precio y stock desde PostgreSQL y el bot redacta la respuesta usando únicamente esos resultados.

### UC-009 — Consulta ambigua o sin evidencia

**Dado** un mensaje ambiguo o sin coincidencias confiables, **cuando** el bot lo procesa, **entonces** hace una pregunta de aclaración o informa que no puede confirmar la respuesta y ofrece seguimiento humano. No adivina.

### UC-010 — Horario y ventana de WhatsApp

**Dado** el horario configurado de Ropa de Programador o una conversación fuera de la ventana de servicio de WhatsApp, **cuando** el sistema debe responder, **entonces** aplica la respuesta configurada. Dentro de las 24 horas puede usar texto libre; fuera de esa ventana sólo usa una plantilla aprobada si la conversación debe iniciarse o retomarse.

### UC-011 — Retención y eliminación

**Dado** que se cumple el plazo de retención configurado, **cuando** se ejecuta el proceso de limpieza, **entonces** elimina el contenido vencido, conserva sólo los metadatos permitidos y mantiene el mínimo identificador necesario para respetar una baja.

## Datos demo iniciales

- Horario propuesto: lunes a viernes de 09:00 a 18:00, sábado de 10:00 a 14:00 y domingo cerrado.
- Zona horaria: `America/Argentina/Buenos_Aires`.
- El catálogo, las políticas y las respuestas de prueba deben estar marcados como `DEMO` y ser reemplazables antes de producción.
- El mensaje de bienvenida es: `Hola, ¿cómo te puedo ayudar?`.

## Retención propuesta para el MVP

- Contenido de mensajes: 30 días.
- Metadatos mínimos de conversación: 90 días.
- Métricas agregadas sin teléfono ni contenido: 12 meses.
- Registro `DO_NOT_CONTACT`: conservar sólo mientras sea necesario para respetar la solicitud.
- La política debe validarse con la legislación aplicable antes de producción.

## Decisiones funcionales pendientes

- Contenido final de las políticas reales de la tienda.
- Canal exacto del backoffice que consumirá las tareas de seguimiento.
- Plantilla de WhatsApp para seguimientos que excedan la ventana de 24 horas.
- Validación legal de retención, supresión y eliminación.
