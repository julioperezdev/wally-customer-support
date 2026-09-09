# Runbook de piloto controlado — WCS

Owner: Product/Tech Lead
Status: `In Progress`
Related Jira: `WCS-23`
Related documentation: `docs/observability.md`, `docs/operations.md`, `docs/testing-strategy.md`

## Propósito

Medir el comportamiento del bot con tráfico controlado antes de considerarlo
apto para una operación más amplia. El piloto no es una habilitación general
de producción: sólo participan testers autorizados, con datos sintéticos o
consultas explícitamente aprobadas.

La respuesta activa continúa siendo la única que puede llegar al canal. Las
ejecuciones shadow, si existieran, sólo producen evidencia y nunca publican
mensajes.

## Alcance de la primera ejecución

| Elemento | Decisión inicial |
| --- | --- |
| Ambiente | App Runner `prod`, versión identificada por commit/deployment SHA |
| Canales | Telegram como canal inicial; WhatsApp sólo si el webhook está validado |
| Duración | 60–90 minutos de tráfico controlado |
| Participantes | Tester/negocio autorizados; no clientes finales |
| Volumen máximo | 30 mensajes inbound o 10 conversaciones, lo que ocurra primero |
| Tipo de mensajes | Texto; sin imágenes, audio, grupos, pagos ni datos sensibles |
| Datos | Catálogo y políticas `DEMO`; nunca secretos, tarjetas, contraseñas o PII innecesaria |
| Modelo | El provider y modelo efectivos se registran desde AppConfig y los eventos de IA |
| Costo máximo de ejecución | USD 5 estimados, salvo aprobación explícita del responsable |

El piloto debe registrar el SHA de la imagen desplegada, la versión de
AppConfig, el período UTC y el canal. No se debe pegar un token, teléfono
completo, chat ID ni conversación completa en Jira, Confluence o capturas.

## Preparación

Antes de comenzar, completar el encabezado del reporte en
[`pilot-report-template.md`](pilot-report-template.md) y verificar:

1. El PR que contiene el código está mergeado y el workflow de Backend pasó la
   verificación.
2. El deploy de App Runner terminó correctamente y
   `/actuator/health` responde `UP`.
3. La versión de AppConfig contiene el provider esperado y los límites de IA.
4. `wcs.agent-runtime.shadow-enabled=false` y
   `wcs.agent-runtime.shadow-traffic-percentage=0`, salvo que exista una
   aprobación específica para una prueba en `test`.
5. Grafana puede consultar el datasource `WCS CloudWatch` con el perfil de
   sólo lectura.
6. El tester conoce los casos funcionales y el procedimiento de rollback.

Comandos de referencia, sin secretos:

```bash
curl --fail --silent --show-error \
  https://<host-publico>/actuator/health

aws sts get-caller-identity --profile julio_dev

docker compose --env-file observability/grafana/.env \
  -f observability/grafana/docker-compose.yml up -d
```

## Guion de prueba funcional

Ejecutar los casos en conversaciones nuevas y anotar sólo el resultado
agregado (`pass`, `fail`, `not-applicable`) y un comentario sin contenido de
usuario.

| Caso | Consulta representativa | Resultado esperado |
| --- | --- | --- |
| P-01 | saludo | Respuesta de bienvenida configurada |
| P-02 | productos disponibles | Lista sólo resultados del catálogo demo |
| P-03 | filtro por tipo, color, talle y precio | Todas las condiciones se aplican simultáneamente |
| P-04 | disponibilidad de un SKU encontrado | Stock y precio provienen de PostgreSQL |
| P-05 | horario o ubicación | Respuesta con la configuración publicada |
| P-06 | envíos/cambios/política | Respuesta basada en la fuente autorizada o fallback seguro |
| P-07 | consulta ambigua | Pregunta de aclaración o fallback controlado |
| P-08 | producto inexistente | No inventa producto, precio ni stock |
| P-09 | solicitud de agente | Resultado `HANDOFF` y respuesta dentro del SLA informado |
| P-10 | mensaje duplicado controlado | No genera una segunda respuesta outbound |

El tester puede reformular cada caso una vez para medir tolerancia al lenguaje
natural. No se deben usar conversaciones reales como fixture ni intentar
acciones sensibles como pagos, reembolsos, cancelaciones o cambios.

## Métricas del scorecard

Las consultas reproducibles están en
[`pilot-scorecard.md`](../observability/grafana/queries/pilot-scorecard.md).
Usar el mismo período UTC para todos los paneles.

| Métrica | Fuente | Criterio inicial |
| --- | --- | --- |
| Resolución/reply | `CONVERSATION_QUERY_COMPLETED` con `outcome=REPLIED` | ≥ 80% de consultas en alcance |
| Fallback | `CONVERSATION_QUERY_COMPLETED` con `outcome=FALLBACK` o `LOW_CONFIDENCE` | ≤ 20%; investigar toda regresión en P0 |
| Handoff | `CONVERSATION_QUERY_COMPLETED` con `outcome=HANDOFF` | Registrar volumen y causa; no se considera fallo automático |
| Latencia conversacional | `durationMs` de `CONVERSATION_QUERY_COMPLETED` | p95 ≤ 8 s como objetivo inicial |
| Entrega outbound | `OUTBOUND_MESSAGE_DISPATCHED` | 100% `SENT` para mensajes aceptados o incidente documentado |
| Errores | `INBOUND_MESSAGE_FAILED`, `OUTBOUND_MESSAGE_DISPATCHED=FAILED`, HTTP 5xx | 0 incidentes críticos; detener ante repetición |
| Duplicados | `INBOUND_MESSAGE_ENQUEUED` con `result=DUPLICATE` | 0 respuestas duplicadas; los duplicados recibidos se contabilizan |
| IA | `AI_USAGE_RECORDED` | Modelo, tokens, latencia y costo estimado completos |
| Seguridad | revisión de logs y respuestas | 0 secretos, tokens, prompts, mensajes completos o PII innecesaria |

La ausencia de un campo de uso del proveedor se registra como `unavailable`,
nunca como cero. El costo del evento de IA es estimado y no sustituye la
facturación de AWS.

## Criterios de pausa y rollback

Pausar inmediatamente el tráfico y conservar la evidencia sanitizada ante
cualquiera de estos eventos:

- secreto, token, prompt, mensaje completo o PII en logs;
- respuesta duplicada o envío a un destinatario no autorizado;
- dos fallos consecutivos del worker, del proveedor o del outbox;
- HTTP 5xx persistente o p95 por encima de 8 s durante dos ventanas de 5
  minutos;
- costo estimado por encima del presupuesto del piloto;
- el bot inventa precio, stock, política o ubicación;
- una candidata shadow intenta publicar una respuesta.

Rollback operativo:

1. Detener el envío de nuevos mensajes de prueba.
2. Si se activó shadow, publicar
   `wcs.agent-runtime.shadow-enabled=false`,
   `wcs.agent-runtime.shadow-provider=noop` y
   `wcs.agent-runtime.shadow-traffic-percentage=0`.
3. Si el problema es de configuración, revertir la versión de AppConfig y
   ejecutar `Restart Backend (AppConfig)` con la aprobación del environment
   `production`.
4. Si el problema es de código, seleccionar la imagen App Runner anterior o
   ejecutar el rollback documentado por CI/CD. No modificar ni borrar
   migraciones aplicadas.
5. Verificar health, errores y ausencia de nuevos envíos antes de cerrar el
   incidente.

## Cierre y decisión

El piloto se puede mover a revisión cuando el reporte contenga:

- período, versión, canal y volumen;
- resultados P-01 a P-10;
- scorecard de resolución, handoff, fallback, latencia, costo, errores y
  duplicados;
- feedback del tester/negocio sin PII;
- incidentes, decisiones y evidencia de rollback si aplicó;
- una decisión explícita: `CONTINUAR`, `AJUSTAR` o `DETENER`.

`CONTINUAR` no habilita tráfico irrestricto. Sólo autoriza preparar el
siguiente incremento con otro issue aceptado, conservando los límites y las
protecciones del piloto.
