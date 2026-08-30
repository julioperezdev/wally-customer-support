# Requerimientos funcionales — WCS

Estado: `Draft`. Los requerimientos siguientes derivan del specification inicial y requieren validación con la tienda.

## Actores

- Cliente de la tienda.
- Bot de soporte.
- Agente humano de soporte.
- Operador técnico.
- Administrador de la cuenta Meta/WhatsApp.

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
| FR-010 | Ejecutar localmente sin credenciales externas mediante mocks. | Alta | Draft |
| FR-011 | Rechazar o derivar mensajes fuera de la ventana de atención. | Alta | Draft |
| FR-012 | Registrar estado, duración, correlación y errores sin contenido sensible. | Alta | Draft |
| FR-013 | Permitir una ruta explícita de fallback o escalamiento humano. | Alta | TBD negocio |
| FR-014 | Responder sólo con información autorizada por la tienda. | Alta | TBD negocio |

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

**Dado** que la consulta está fuera del conocimiento autorizado o requiere una operación sensible, **cuando** el bot la clasifica, **entonces** informa el límite y deriva según el proceso definido por la tienda.

## Decisiones funcionales pendientes

- Catálogo de productos, pedidos, envíos, cambios, devoluciones y reembolsos.
- Fuente de conocimiento autorizada para respuestas.
- Horarios de atención y SLA.
- Método de handoff y ownership de la conversación.
- Consentimiento, opt-out y eliminación de datos.
- Mensaje seguro ante información desconocida.
