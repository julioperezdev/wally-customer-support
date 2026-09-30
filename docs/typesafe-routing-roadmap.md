# TypeSafe como selector de caso de uso por WhatsApp

- Owner: Product/Tech Lead
- Status: Accepted — WCS-142 en implementación
- Last reviewed: 2026-09-29
- Jira: [WCS-142](https://julioperezdev.atlassian.net/browse/WCS-142), bajo WCS-134
- Dependencias: [WCS-136](https://julioperezdev.atlassian.net/browse/WCS-136), [WCS-138](https://julioperezdev.atlassian.net/browse/WCS-138)
- Confluence canónico: [WCS — TypeSafe como selector de caso de uso por WhatsApp](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/19759105/WCS+TypeSafe+como+selector+de+caso+de+uso+por+WhatsApp)
- Parent: [WCS — Refactor conversacional y fundación de datos — Roadmap](https://julioperezdev.atlassian.net/wiki/spaces/SD/pages/13533186)

## Objetivo

Evaluar TypeSafe System One como proveedor que selecciona el caso de uso de WCS
a partir del mensaje actual de WhatsApp y el contexto permitido. Es un cambio
acotado del provider de routing; mantiene contratos, ejecución y canales en
WCS.

## Contrato y límites

```text
Webhook WhatsApp -> InboundMessageCommand (actual, idempotente)
  -> guards determinísticos y ConversationContextBuilder
  -> router WCS
       -> Bedrock actual (default/fallback)
       -> TypeSafe Choice (shadow/active; sólo selecciona el caso de uso)
  -> ConversationIntentDecision vigente + reconciliador WCS
  -> resolución de entidades y parámetros en WCS
  -> tool/caso de uso WCS allow-listed
  -> respuesta y outbox actuales -> WhatsApp
```

- TypeSafe puede escoger sólo opciones cerradas que mapean a los enums actuales
  de `ConversationIntent` y `ConversationAction`, más `UNKNOWN`/`other`.
- La salida interna permanece como `ConversationIntentDecision`; el adapter no
  introduce un segundo contrato de mensaje de WhatsApp.
- WCS conserva precedencia determinística, umbrales de confianza, resolución de
  `catalogQuery`/entidades, ownership, validación de carrito/checkout, tools,
  políticas de respuesta y ejecución. TypeSafe no puede ejecutar tools ni
  modificar estado transaccional.
- `wcs.ai.provider=bedrock` sigue controlando la generación de respuesta. El
  router obtiene configuración separada, por ejemplo
  `wcs.ai.routing.provider` y modo `off | shadow | active`; default `off`.
- En shadow no cambia el caso de uso ejecutado. La llamada igualmente procesa
  el texto en un proveedor externo, por lo que mensajes reales requieren el
  gate de privacidad descrito abajo.
- Si TypeSafe devuelve una opción inválida, confianza insuficiente, error HTTP,
  timeout o salida inválida, no se ejecuta ninguna tool: se usa Bedrock o el
  fallback seguro vigente.

## Secrets Manager

La configuración informada es un secreto existente llamado
`wcs/prod/typesafe`, en us-east-1, con la clave `API_KEY`. Esta documentación no
lee ni replica el valor.

La implementación amplía el loader allowlisted de Secrets Manager para mapear
sólo `API_KEY` al adapter. Terraform referencia el secreto existente por nombre
y agrega su ARN al allowlist IAM de lectura; AppConfig conserva la referencia,
nunca el valor. No se crea ni duplica el secreto. El código de infraestructura
está versionado, pero no se ejecutó plan ni apply en AWS.

El loader actual toma el snapshot al iniciar el proceso. Si la nueva integración
usa ese loader, los cambios requieren el despliegue/reinicio controlado
documentado en operaciones.

## Implementación y configuración

La aplicación usa `POST https://api.typesafe.ai/v1/systemone` con una pregunta
`choice` de opciones fijas y versión de modelo `jev-1.13.0`. La respuesta se
valida por tipo, acción allowlisted y confianza numérica de 0 a 1. El adapter
envía el mensaje actual y hasta seis mensajes anteriores (sin repetir el actual
si aparece en memoria), con máximo 2.000 caracteres por defecto; no serializa
IDs de WCS, teléfono, selección, preferencias ni resumen.

| Propiedad | Default | Función |
| --- | --- | --- |
| `wcs.ai.routing.provider` | `bedrock` | Selecciona `bedrock` o `typesafe` para el router |
| `wcs.ai.routing.mode` | `off` | `off`, `shadow` o `active`; otro valor deja TypeSafe deshabilitado |
| `wcs.ai.routing.minimum-confidence` | `0.65` | Debajo de este umbral se usa el clasificador Bedrock |
| `wcs.ai.routing.typesafe.model` | `jev-1.13.0` | Versión fija del modelo |
| `wcs.ai.routing.typesafe.request-timeout` | `PT5S` | Timeout acotado, máximo 30 s |
| `wcs.ai.routing.typesafe.max-history-messages` | `6` | Ventana previa enviada al selector |
| `wcs.ai.routing.typesafe.max-state-characters` | `2000` | Límite total del estado remoto |

`off` conserva íntegramente el router Bedrock. `shadow` registra la propuesta
TypeSafe y devuelve Bedrock. `active` devuelve la acción TypeSafe si la salida
es válida y supera el umbral; si falla, usa Bedrock. WCS invoca el clasificador
vigente sólo para intentar extraer entidades y acepta esa extracción cuando su
acción coincide exactamente con TypeSafe. La acción seleccionada sigue pasando
por el reconciliador y handlers existentes. Los eventos `ROUTING_SHADOW_EVALUATED`
y `ROUTING_PROVIDER_EVALUATED` contienen métricas y enums, nunca texto ni keys.

El contrato de request/response se basa en la [documentación de Quick start de
TypeSafe](https://docs.typesafe.ai/introduction/quickstart) y sus páginas de
[Choice y primitivas](https://docs.typesafe.ai/primitives) y
[confidence](https://docs.typesafe.ai/confidence). Esta referencia documenta el
formato del API; no constituye permiso para enviar conversaciones reales.

## Roadmap directo

| Paso | Trabajo | Salida/gate |
| --- | --- | --- |
| 1. Aceptar | Completo: página aceptada y WCS-142 en `In Progress`; opciones Choice y mapeo preservan `intent/action`. | Scope, datos permitidos, confianza y fallback fijados antes del código. |
| 2. Implementar en un PR | En curso: adapter HTTP, configuración aislada, secret mapping/IAM, cliente fake y fallback Bedrock. | Contratos y pruebas locales; queda revisión de PR. Terraform está escrito pero sin plan/apply. |
| 3. Evaluar offline | Pendiente: ejecutar TypeSafe y Bedrock sobre el mismo dataset sintético/runner de WCS-138 una vez habilitada una prueba de proveedor aprobada. | Métricas comparables de calidad, seguridad, latencia y costo; sin regresión en acciones mutantes. |
| 4. Activar gradual | Shadow y luego canary/active con rollback por configuración. | Sólo tras gate de privacidad; conservar Bedrock hasta evidencia de cierre WCS-139. |

Es una sola tarea de integración, no cuatro entregas Jira: los pasos ordenan sus
gates y evidencia. La aceptación ya ocurrió; shadow/active con conversaciones
reales permanece apagado hasta cerrar el gate de privacidad.

## Criterios de aceptación de WCS-142

1. El webhook y `InboundMessageCommand` mantienen el contrato vigente y no
   duplican efectos.
2. TypeSafe sólo puede proponer un caso de uso permitido; el reconciliador WCS
   entrega un `ConversationIntentDecision` compatible y conserva la autoridad.
3. Unknown, confianza baja, timeout, error HTTP, API key ausente o schema
   inválido nunca alcanzan una tool y activan el fallback esperado.
4. Seleccionar o ver un producto no muta carrito, checkout, pago ni pedido.
5. La generación de respuestas continúa en Bedrock y puede revertirse el router
   a Bedrock por configuración.
6. Cliente fake cubre request/response, mapping, confidence, fallos, fallback,
   sanitización y contrato WhatsApp; evaluación offline reutiliza los mismos
   datos sintéticos para baseline y candidato.
7. App Runner lee sólo la clave necesaria del secreto existente por ARN
   allowlisted; el plan Terraform no destruye ni reemplaza recursos.
8. El adapter actual se conserva hasta evidenciar rollback y cierre de WCS-139.
9. No hay shadow/canary con mensajes reales hasta revisar y aprobar DPA,
   términos aplicables, residencia, retención y minimización.

## Gate de privacidad

La política pública de TypeSafe indica que sus servicios están alojados en
Estados Unidos, que los inputs no se usan para entrenar modelos y que los datos
personales se conservan mientras sean razonablemente necesarios. Antes de
procesar conversaciones reales, revisar los términos vigentes del tenant, DPA,
subprocesadores, residencia y retención; limitar el estado enviado al mínimo
necesario. La evaluación inicial debe usar fixtures sintéticos.

## Dependencias y fuera de alcance

- WCS-136 define el contrato estable del router, enums y reconciliación.
- WCS-138 aporta dataset/runner/scorecard comparables.
- WCS-134 es la épica padre del trabajo conversacional.
- Fuera de alcance: reemplazar Bedrock para humanización/RAG/generación,
  cambiar WhatsApp, dar tools a TypeSafe, borrar Bedrock, hacer rollout
  productivo, modificar migraciones aplicadas o destruir recursos AWS.

## Referencias oficiales

- [Introduction](https://docs.typesafe.ai/introduction) y [Quick start](https://docs.typesafe.ai/introduction/quickstart): System One, endpoint HTTP, autenticación y request/response.
- [Primitives](https://docs.typesafe.ai/primitives): `Choice`, `Score` y `Noul`; `Choice` para seleccionar entre opciones conocidas.
- [Confidence](https://docs.typesafe.ai/confidence): uso de confianza en función del riesgo y fallback.
- [API reference](https://docs.typesafe.ai/api): contrato HTTP.
- [TypeSafe Privacy Policy](https://typesafe.ai/legal/privacy-policy), [DPA](https://typesafe.ai/legal/data-processing), [Trust Center](https://trust.typesafe.ai/).
- [AWS SDK Java: GetSecretValue](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets-java-sdk.html): permiso requerido y recomendación de cachear lecturas.

La documentación de TypeSafe y sus términos pueden cambiar. Volver a validarlos
antes de procesar datos reales.
