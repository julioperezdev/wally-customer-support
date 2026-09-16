# Runbook de pruebas manuales — WCS MVP

Estado: `Ready for execution`
Última revisión: `2026-09-16`
Responsable de ejecución: Tester funcional
Ambiente de aceptación actual: `prod` de AppConfig, Telegram y backoffice local

Este documento separa las verificaciones manuales de las pruebas automatizadas.
La tester debe registrar el resultado de cada caso como `PASS`, `FAIL` o
`BLOCKED`, junto con fecha, ambiente, versión visible y una observación breve.

No adjuntar en Jira, Confluence, capturas ni planillas: contraseñas, tokens,
cookies, números completos de teléfono, mensajes completos de clientes,
prompts, payloads de proveedores ni URLs que contengan credenciales.

## 1. Qué está probado automáticamente

Ejecutado localmente el `2026-09-16`:

| Suite | Comando | Resultado |
|---|---|---|
| Backend unitario, aplicación, contrato e integración | `mvn -q verify` | `PASS`; incluye PostgreSQL 16 con Testcontainers y Flyway hasta V21 |
| Backoffice | `cd backoffice && npm run check` | `PASS`; 29 tests Vitest y build TypeScript/Vite |

Estas suites cubren persistencia, migraciones, idempotencia, seguridad JWT
sintética, catálogo, stock, memoria, evaluación, feature flags, webhooks y
contratos del frontend. No sustituyen las pruebas manuales de navegador,
Telegram, App Runner, S3, AppConfig ni Mercado Pago.

## 2. Valores efectivos conocidos

Snapshot consultado en AWS AppConfig Data API el `2026-09-16`. Los valores
secretos se omiten deliberadamente.

| Área | Valor actual | Qué significa para la prueba |
|---|---|---|
| Backoffice | `wcs.backoffice.enabled=true` | El panel está habilitado |
| Preview legacy | `wcs.backoffice.preview.enabled=false` | No se debe usar `preview-token` |
| Autenticación | `wcs.backoffice.auth.enabled=true` | El acceso es sólo mediante Cognito/JWT |
| Cookies | `secure-cookies=true`, `same-site=None` | El navegador debe aceptar cookies seguras con credenciales |
| CORS | `http://localhost:5173,http://localhost:5174` | Esos son los orígenes actualmente permitidos por el backend |
| Media | `wcs.backoffice.media.enabled=true` | Las imágenes usan URLs prefirmadas y bucket privado |
| Media | Prefijo `wcs/catalog` | Las imágenes nuevas deben quedar bajo el prefijo permitido |
| Telegram | `enabled=true`, adapter `telegram` | Es el canal manual de aceptación |
| WhatsApp | Adapter `meta` | No ejecutar pruebas productivas de WhatsApp en este ciclo |
| RAG | Provider `bedrock-kb`, máximo `5` resultados | Las preguntas estáticas deben responderse con la KB de WCS |
| Preferencias | `enabled=true`, TTL `PT24H`, máximo `5`, máximo `64` caracteres | Se pueden probar preferencias explícitas |
| Memoria de sesión | Efectivamente `false` por ausencia de habilitación | No usar la prueba manual para afirmar memoria productiva |
| Resumen | Efectivamente `false` | El resumen automático no participa en esta aceptación |
| Retención automática | Efectivamente `false` | Se valida por código, no esperando días en Telegram |
| Runtime de agentes | `activation-enabled=false` | El flujo conversacional estable continúa siendo la autoridad |
| Shadow | `enabled=false`, provider `noop`, tráfico `0` | Los paneles shadow pueden estar sin datos |
| Pagos | Provider actual `mercadopago`, moneda `ARS`, firma requerida | No completar pagos reales; ver sección de pagos |
| Feature flags | Versión `wcs-121-initial`; una flag de `catalog-specialist` habilitada para `prod` | No implica que todos los agentes estén activados |

Los valores anteriores son una fotografía de operación, no una autorización
para modificar `prod`. La tester no debe cambiar AppConfig, Secrets Manager,
Terraform, Cognito ni los webhooks.

## 3. Precondiciones

Antes de iniciar:

1. Abrir el backoffice desde la URL local acordada, normalmente
   `http://localhost:5173`.
2. Confirmar que el frontend apunta al backend desplegado de App Runner y no a
   un backend local accidental.
3. Confirmar que el endpoint de salud del backend responde `200` con estado
   `UP`.
4. Usar el usuario Cognito de pruebas entregado por fuera de este documento.
   No registrar la contraseña ni los tokens.
5. Usar datos demo del catálogo. No crear datos de clientes reales.
6. Para Telegram, usar únicamente el chat de prueba autorizado.
7. Si se prueba desde un celular, usar el origen HTTPS/ngrok vigente y
   confirmar que ese origen esté incluido en CORS antes de comenzar.

Si falla una precondición, marcar los casos afectados como `BLOCKED` y anotar
la causa; no intentar corregir infraestructura durante la ejecución funcional.

## 4. Matriz manual para la tester

### A. Acceso y autorización — WCS-128

| ID | Valor actual | Acción manual | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-001` | `auth.enabled=true`; preview deshabilitado | Abrir el panel sin sesión | Se muestra login; no se muestran datos del backoffice | `PENDIENTE` |
| `MAN-002` | Cognito habilitado | Ingresar usuario y contraseña válidos | El panel carga; `/me` muestra identidad y capacidades sin exponer tokens | `PENDIENTE` |
| `MAN-003` | Login propio del panel | Ingresar una contraseña incorrecta | Se informa autenticación rechazada; no se crea una sesión utilizable | `PENDIENTE` |
| `MAN-004` | Cookies HttpOnly/Secure | Recargar el navegador después del login | La sesión permanece sin guardar tokens en `localStorage` | `PENDIENTE` |
| `MAN-005` | Refresh implementado | Mantener el panel abierto hasta renovar la sesión o provocar una llamada después de expirar el access token | Se renueva una vez y la operación continúa; si falla, vuelve al login | `PENDIENTE` |
| `MAN-006` | Logout implementado | Presionar cerrar sesión y volver a abrir una ruta interna | La sesión se limpia y la API responde `401` o el panel vuelve al login | `PENDIENTE` |
| `MAN-007` | Sin preview-token | Intentar operar sin sesión desde una ventana privada | Ningún endpoint interno entrega datos; se obtiene `401` | `PENDIENTE` |
| `MAN-008` | Capacidades separadas | Usar un usuario válido sin la capacidad de una operación de escritura | La lectura permitida funciona y la escritura devuelve `403` | `BLOCKED` hasta disponer de usuario con scope limitado |

Para `MAN-008` se necesita un usuario/grupo Cognito de pruebas con permisos
limitados. Un usuario `admin` no sirve para demostrar el `403` por scope.

### B. Catálogo, stock, imágenes y solicitudes humanas — WCS-119

| ID | Valor actual | Acción manual | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-009` | Catálogo demo en PostgreSQL | Abrir Catálogo y recorrer productos y variantes | Se muestran nombre, tipo, SKU, talle, color, precio, moneda y stock | `PENDIENTE` |
| `MAN-010` | Stock no negativo | Aplicar un aumento de stock a una variante y actualizar la vista | El nuevo stock se conserva después de recargar y aparece la operación auditada | `PENDIENTE` |
| `MAN-011` | Validación de stock | Intentar aplicar un ajuste que deje stock negativo | La operación se rechaza, el stock no cambia y aparece un error claro | `PENDIENTE` |
| `MAN-012` | Idempotencia de stock | Repetir una misma acción con la misma clave, si la UI o el procedimiento de prueba la expone | No se duplica el ajuste ni se aplica dos veces | `PENDIENTE` |
| `MAN-013` | S3 media habilitado; bucket privado | Seleccionar una imagen JPEG, PNG o WebP válida y subirla | La carga termina correctamente; la imagen aparece en el producto | `PASS` informado para carga inicial; revalidar |
| `MAN-014` | Referencia de imagen reemplazable | Subir una segunda imagen al mismo producto y recargar el panel | Se visualiza la segunda imagen; la referencia anterior deja de ser la activa | `PASS` informado por Julio; revalidar en celular |
| `MAN-015` | Restricción de media | Intentar un archivo no permitido o mayor a 5 MB | La UI lo rechaza y no crea una referencia inválida en el catálogo | `PENDIENTE` |
| `MAN-016` | Bandeja de seguimiento humano | Abrir solicitudes, filtrar por estado y prioridad | Sólo aparecen los elementos que cumplen el filtro y el contexto es mínimo | `PENDIENTE` |
| `MAN-017` | Ownership | Tomar una solicitud, liberarla y volver a tomarla | Sólo el owner puede ejecutar la transición; la tarea conserva su historial | `PENDIENTE` |
| `MAN-018` | Resolución | Resolver una solicitud tomada | Pasa a `DONE` y desaparece de la vista de abiertas | `PENDIENTE` |

El resultado `MAN-013`/`MAN-014` ya fue observado durante el desarrollo; la
revalidación de la tester debe confirmar además la visualización responsive.

### C. Mapa y control de agentes — WCS-120

| ID | Valor actual | Acción manual | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-019` | Registry con baseline de agentes core; feature flag visible para `catalog-specialist` | Abrir el mapa de agentes | Se muestran casos de uso, relaciones, agente, versión, estado y fallback sanitizados | `PENDIENTE` |
| `MAN-020` | Filtros alimentados por `filter-options` | Abrir los filtros de agente, ambiente, canal y caso de uso | Los valores aparecen como opciones seleccionables; no hay que recordarlos manualmente | `PENDIENTE` |
| `MAN-021` | Simulación read-only | Simular la desactivación de un agente | Devuelve fallback, handoff o `NO_CHANGE` sin modificar activaciones | `PENDIENTE` |
| `MAN-022` | `authoring-write-enabled=false` efectivo | Intentar crear o editar una versión en el ambiente actual | La operación queda bloqueada de forma segura; no se crea una versión | `EXPECTED BLOCKED` |
| `MAN-023` | `activation-write-enabled=false`; runtime apagado | Intentar activar, hacer kill switch o rollback | La API rechaza la mutación; el runtime conversacional no cambia | `EXPECTED BLOCKED` |
| `MAN-024` | Auditoría y ejecuciones sanitizadas | Abrir auditoría y trazas | Se ven operación, estado, versión y métricas permitidas, sin prompts, SQL, mensajes ni PII | `PENDIENTE` |

Para probar creación, lifecycle, activación, kill switch y rollback realmente
se necesita primero un ambiente `test` operativo o una ventana controlada
aprobada. No habilitar estas escrituras en `prod` para una prueba exploratoria.

### D. Feature flags en caliente — WCS-121

| ID | Valor actual | Cambio requerido antes de probar | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-025` | Perfil `feature-flags` desplegado; flag de catálogo visible | Abrir el panel con capacidad `feature-flags.read` | Se muestra versión efectiva, estado `stale` y auditoría sanitizada | `PENDIENTE` |
| `MAN-026` | `wcs.feature-flags.publisher.enabled=false` por defecto | Habilitar temporalmente el publisher y su IAM acotado sólo en `test` | El usuario autorizado puede preparar una publicación; otro recibe `403` | `BLOCKED` hasta tener `test` |
| `MAN-027` | Snapshot con polling periódico | Publicar una flag inocua en `test`, sin reiniciar App Runner | El backend toma una nueva versión dentro del intervalo de polling y registra refresh aceptado | `BLOCKED` hasta tener `test` |
| `MAN-028` | Rollback disponible por contrato | Publicar una segunda versión y volver a la anterior | El snapshot efectivo vuelve a la versión anterior, sin borrar historial | `BLOCKED` hasta tener `test` |
| `MAN-029` | Kill switch expresado en la flag | Activar y luego desactivar una flag de un caso no crítico | La conducta cambia de forma controlada y el rollback deja el comportamiento anterior | `BLOCKED` hasta tener `test` |

El perfil `runtime` no se debe editar para simular un cambio en caliente: sus
valores bootstrap se cargan al iniciar y requieren restart. WCS-121 se prueba
exclusivamente con el perfil `feature-flags`.

### E. Conversación por Telegram

| ID | Valor actual | Mensaje o acción | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-030` | Telegram habilitado | `Hola` | Responde `Hola, ¿cómo te puedo ayudar?` | `PENDIENTE` |
| `MAN-031` | Catálogo PostgreSQL | `Busco una remera negra talle M que cueste menos de 20000` | Devuelve sólo la variante que cumple nombre/tipo, color, talle y precio | `PENDIENTE` |
| `MAN-032` | Contexto estructurado del turno | `¿Está disponible?` y luego `¿Cuánto cuesta?` | Reconsulta stock y precio vigentes del producto correcto | `PENDIENTE` |
| `MAN-033` | Refinamiento multi-turno | `Quiero algo para el frío`; luego `Mejor un buzo`; luego `¿Qué opciones tienen?` | Conserva el filtro válido y devuelve buzos, sin mezclar remeras | `PENDIENTE` |
| `MAN-034` | Pregunta inexistente | `¿Venden zapatillas?` | No inventa productos; informa que no hay coincidencias o deriva | `PENDIENTE` |
| `MAN-035` | Knowledge Base WCS | `¿Dónde están ubicados?` | Responde con la ubicación publicada en la KB, sin inventar datos | `PENDIENTE` |
| `MAN-036` | Knowledge Base WCS | `¿A qué hora están abiertos el sábado?` | Responde sábado `10:00 a 14:00` y la zona horaria configurada | `PENDIENTE` |
| `MAN-037` | Política de envíos | `¿Cómo funcionan los envíos?` | Responde sólo con la política demo publicada | `PENDIENTE` |
| `MAN-038` | Handoff humano | `Necesito hablar con un humano` | Crea una solicitud priorizada; informa atención dentro de 24 horas | `PENDIENTE` |
| `MAN-039` | Opt-out | `BAJA` o `STOP` | Suprime el contacto, no llama a IA y no envía una respuesta automática posterior | `PENDIENTE` |
| `MAN-040` | Reactivación | `ALTA`, `REANUDAR` o `/start` después de una baja | Reactiva el chat, limpia el contexto anterior y permite una nueva conversación | `PENDIENTE` |
| `MAN-041` | Catálogo con una única variante e imagen cargada | `Busco la remera NullPointer negra talle M` | Telegram entrega una imagen del producto y el texto como caption | `PENDIENTE` |
| `MAN-042` | Catálogo con varias variantes | `¿Qué remeras tienen?` | Telegram entrega un listado textual sin enviar una imagen por cada variante | `PENDIENTE` |
| `MAN-043` | Seguimiento de una variante | `¿Está disponible?` después de una búsqueda única | La respuesta de stock es textual y no repite la imagen | `PENDIENTE` |

Para `MAN-039` y `MAN-040` registrar sólo el resultado y el identificador
pseudonimizado de la evidencia; no adjuntar el chat completo.

### F. Pedidos y pagos — WCS-122

El provider efectivo en `prod` es `mercadopago`. El fallo anterior de
redirecciones en Checkout Sandbox hace que no se deba usar el pago real como
criterio de cierre del MVP.

| ID | Valor actual | Cambio requerido antes de probar | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-041` | Provider configurado en Mercado Pago | Cambiar temporalmente a `mock` en un ambiente de prueba o usar el modo mock ya disponible | Se crea un pedido con SKU, precio y moneda tomados del backend y se muestra un link mock | `PENDIENTE` |
| `MAN-042` | Idempotencia implementada | Crear el mismo pedido dos veces con la misma operación | Se reutiliza el pedido y no se generan dos pedidos ni dos links | `PENDIENTE` |
| `MAN-043` | Validación de catálogo y stock | Intentar crear pedido con SKU inexistente o stock insuficiente | Se rechaza antes de llamar al proveedor y no queda un pedido inconsistente | `PENDIENTE` |
| `MAN-044` | Mercado Pago Sandbox y firma requerida | Sólo después de validar mock, cargar credenciales Sandbox fuera del repositorio y configurar URLs autorizadas | Se genera checkout Sandbox; el webhook firmado actualiza el estado una sola vez | `BLOCKED` hasta corregir/verificar redirección |
| `MAN-045` | Dedupe de webhook | Repetir la misma notificación de pago | Se registra como duplicada y no vuelve a aplicar la transición | `PENDIENTE` |

No ingresar datos de tarjeta reales. El MVP todavía no implementa reserva de
stock con expiración, carrito persistido, reembolsos ni pagos productivos.

### H. Compra conversacional — WCS-122

La compra desde el chat reutiliza el mismo pedido idempotente del backoffice.
El bot no debe generar un link por una consulta de catálogo, por “me interesa”
ni por una selección con múltiples variantes. Para esta sección, usar un
producto demo con una única variante inequívoca y stock suficiente. El detalle
del flujo está en [`conversational-checkout.md`](conversational-checkout.md).

| ID | Valor actual | Mensaje o acción | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-050` | Telegram habilitado; provider definido por AppConfig | `Busco la remera NullPointer negra talle M` y luego `Quiero comprarla` | El bot valida la variante, crea un pedido `PENDING_PAYMENT` y responde con un link de checkout; no dice que el pago ya fue realizado | `PENDIENTE` |
| `MAN-051` | Contexto de catálogo acotado | `¿Qué remeras tienen?` y luego `Quiero comprarla` | El bot solicita producto, talle y color; no crea pedido ni link con una selección ambigua | `PENDIENTE` |
| `MAN-052` | Stock leído desde PostgreSQL | Solicitar `Quiero comprar 99 unidades de ...` o una variante sin stock | El bot informa que no hay disponibilidad y no llama al proveedor de pagos | `PENDIENTE` |
| `MAN-053` | Solicitud explícita requerida | Enviar `Me interesa esa remera` o `¿Cuánto cuesta?` | Se responde catálogo/precio; no se crea pedido ni link | `PENDIENTE` |
| `MAN-054` | Idempotencia por conversación, variante y cantidad | Repetir el mismo mensaje explícito de compra | Se reutiliza el pedido/link existente y no se crean dos pedidos para la misma operación | `PENDIENTE` |
| `MAN-055` | Provider temporalmente no disponible | Simular o usar una configuración de prueba sin respuesta del proveedor | El bot informa que no pudo generar el link y aclara que el pedido no fue confirmado | `PENDIENTE` |
| `MAN-056` | Mercado Pago Sandbox con firma requerida | Con credenciales Sandbox y configuración aprobada, abrir el link y repetir el webhook | El checkout abre según la configuración del proveedor; la notificación firmada actualiza el pedido una sola vez y el duplicado queda auditado | `BLOCKED` hasta verificar redirección Sandbox |
| `MAN-057` | Rescate determinístico de catálogo | Reiniciar contexto y enviar `Busco la remera NullPointer negra talle M` | Aunque Bedrock tenga una clasificación de baja confianza, se devuelve la variante correcta y Telegram entrega su imagen | `PENDIENTE` |
| `MAN-058` | Refinamiento contextual de catálogo | Enviar `Busco una remera negra` y luego `Quiero la talle M` | El segundo turno conserva remera + negro + talle M y devuelve la variante correcta | `PENDIENTE` |
| `MAN-059` | Compra postergada | Enviar `No quiero comprar todavía` después de una búsqueda | El bot confirma que no creó ningún pedido ni link y permite continuar la conversación | `PENDIENTE` |

### G. Observabilidad

| ID | Valor actual | Acción manual | Resultado esperado | Estado inicial |
|---|---|---|---|---|
| `MAN-046` | Grafana y logs estructurados disponibles | Enviar un mensaje de Telegram y consultar el dashboard | Aparece canal, caso de uso, resultado, latencia y request/correlation id sin mensaje completo | `PENDIENTE` |
| `MAN-047` | Eventos `AI_USAGE_RECORDED` disponibles cuando se llama Bedrock | Ejecutar una pregunta que use IA y revisar el panel IA | Se muestran proveedor, modelo, tokens, costo estimado, latencia y fallos si están disponibles | `PENDIENTE` |
| `MAN-048` | Shadow apagado | Revisar paneles shadow | `No data` es un resultado esperado mientras `shadow-enabled=false` y el tráfico sea `0` | `EXPECTED EMPTY` |
| `MAN-049` | Sanitización obligatoria | Buscar en los logs el identificador de una prueba | No aparecen contraseña, access token, prompt, mensaje completo ni teléfono sin pseudonimizar | `PENDIENTE` |

## 5. Pruebas explícitamente diferidas

Estas pruebas no bloquean el cierre del MVP con Telegram:

- WhatsApp Cloud API productivo, App Review, templates y ventana de 24 horas.
- Activación real de agentes y canary en `prod`.
- Shadow con tráfico distinto de `0`; por eso sus paneles pueden estar vacíos.
- Pago real o checkout Sandbox hasta resolver la redirección.
- Retención esperando 24 horas, 30 días, 90 días o 365 días. Se valida con
  reloj controlado y Testcontainers.
- Habilitación de memoria de sesión/resumen en `prod`; el código está cubierto,
  pero la política y el rollout siguen pendientes.

## 6. Plantilla de evidencia

Para cada caso, registrar:

```text
ID:
Fecha/hora:
Ambiente:
Canal:
Resultado: PASS | FAIL | BLOCKED
Versión de AppConfig o aplicación, si corresponde:
Descripción breve:
Evidencia sanitizada:
Incidencia Jira, si corresponde:
```

Una evidencia válida puede ser una captura recortada sin PII, el estado visible
del panel, un `requestId` pseudonimizado o una consulta agregada de Grafana.
Para un `FAIL`, incluir el resultado esperado y el observado sin copiar tokens,
cookies, payloads completos ni conversaciones.

## 7. Criterio de cierre

El MVP conversacional se puede declarar cerrado cuando:

- `MAN-001` a `MAN-007` pasan y `MAN-008` tiene evidencia de permisos o queda
  documentado como bloqueado por falta de usuario limitado;
- catálogo, stock, imágenes y handoff pasan;
- Telegram pasa saludo, catálogo, seguimiento, KB, handoff, opt-out y
  reactivación;
- Grafana muestra trazabilidad suficiente y no hay secretos ni PII indebida;
- los casos bloqueados tienen causa explícita y no se presentan como fallos del
  producto;
- WCS-120 y WCS-121 tienen smoke en `test` antes de habilitar escrituras o
  tráfico de agentes en `prod`;
- pagos permanecen en `mock` hasta cerrar su smoke de Sandbox.

El cierre de Jira debe adjuntar esta matriz completada y la evidencia
sanitizada correspondiente. El estado `Done` requiere además que el PR esté
mergeado y que el despliegue afectado tenga health y smoke verificados.
