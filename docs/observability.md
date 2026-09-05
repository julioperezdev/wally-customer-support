# Observabilidad — WCS

Owner: Tech Lead
Status: `In Progress`
Related Jira: `WCS-21`, `WCS-22`
Related repository paths: `observability/grafana/`, `src/main/java/com/wally/customersupport/conversation/infrastructure/http/`, `src/main/java/com/wally/customersupport/conversation/application/service/`, `src/main/java/com/wally/customersupport/shared/infrastructure/observability/`

## Objetivo de esta iteración

Proveer una vista local de operación para el backend desplegado en AWS App
Runner. Grafana corre en Docker en la computadora del desarrollador y consulta
CloudWatch directamente mediante un perfil AWS de sólo lectura. No se copian
logs a la computadora ni se agregan access keys al repositorio.

```text
Telegram / WhatsApp
        │
        ▼
App Runner ──► CloudWatch Logs + AWS/AppRunner metrics
                                      ▲
                                      │ read-only
                             Grafana local en Docker
```

## Qué se instrumenta

El backend emite eventos operativos como una línea JSON con el prefijo lógico
`WCS_EVENT`. Cada evento tiene `eventFamily`, `schemaVersion`, `eventType`,
`service` y `occurredAt`, además de sus dimensiones específicas. El formato
JSON permite conservar tipos numéricos y booleanos para agregaciones; las
consultas versionadas usan `parse` explícito porque Spring Boot puede agregar
un prefijo textual al mensaje antes de enviarlo a CloudWatch.

El enfoque sigue el patrón de [wide events](https://loggingsucks.com/): un
evento resume cada consulta y cada dependencia relevante, con el contexto
operacional necesario para diagnóstico y costo, pero sin contenido de negocio.

| Evento | Campos | Uso |
| --- | --- | --- |
| `WEBHOOK_ACCEPTED` | `channel`, `commandCount` | Webhook recibido con autenticación válida |
| `WEBHOOK_REJECTED` | `channel`, `reason` | Firma o secret inválido, payload malformado |
| `INBOUND_MESSAGE_PROCESSED` | `channel`, `result`, `durationMs` | Mensaje aceptado, duplicado o ignorado |
| `INTENT_CLASSIFIED` | `intent`, `confidence`, `durationMs` | Clasificación del orquestador |
| `INTENT_CLASSIFICATION_FAILED` | `errorType`, `durationMs` | Fallo del clasificador |
| `CONVERSATION_QUERY_COMPLETED` | `queryType`, `outcome`, `responseGenerated`, `durationMs`, `correlationId` | Resultado y latencia total de la consulta |
| `GENERAL_SUPPORT_FAILED` | `errorType`, `correlationId` | Fallback de conocimiento/LLM |
| `RAG_RETRIEVAL_RECORDED` | `provider`, `success`, `resultCount`, `durationMs`, `errorType` | Resultado y latencia de Knowledge Base |
| `AI_USAGE_RECORDED` | `stage`, `operation`, `provider`, `model`, `success`, `inputTokens`, `outputTokens`, `totalTokens`, `estimatedCostUsd`, `pricingVersion`, `durationMs`, `providerLatencyMs`, `errorType` | Cada llamada real a un proveedor de IA |
| `OUTBOUND_MESSAGE_DISPATCHED` | `channel`, `result`, `errorType`, `durationMs`, `correlationId` | Entrega o reintento del outbox |

No se registran texto de usuario, prompts, respuestas completas, números de
teléfono, chat IDs, secretos, firmas ni payloads de proveedores. Los campos
`inputTokens`, `outputTokens` y `totalTokens` son cantidades consumidas por el
modelo, no tokens de autenticación. `correlationId` es el UUID interno de la
conversación/outbox y no contiene el identificador externo del canal.

`AI_USAGE_RECORDED` se emite para Bedrock Converse con los contadores
normalizados que devuelve el proveedor. `estimatedCostUsd` se calcula con el
precio por millón de tokens de entrada/salida vigente en la configuración
efectiva y `pricingVersion` identifica la tabla utilizada. Es una estimación
operativa y no una conciliación de facturación. Si el provider está en `mock`,
no existe una llamada de IA real y no se emite este evento.

Configuración no sensible relacionada:

```text
wcs.ai.pricing-version
wcs.ai.input-price-usd-per-million-tokens
wcs.ai.output-price-usd-per-million-tokens
```

El default versionado para `openai.gpt-oss-20b-1:0` en `us-east-1` es
`0.0721` USD por millón de tokens de entrada y `0.3090` USD por millón de
tokens de salida. Debe revisarse cuando cambie el modelo, la región o la
tarifa; AppConfig permite reemplazarlo sin modificar el código.

## Arranque local

Requisitos:

- Docker Desktop o Docker Engine con Compose.
- AWS CLI configurado con el perfil `julio_dev` u otro perfil con permisos de
  lectura de CloudWatch.
- Sesión AWS vigente si el perfil usa SSO.

Desde la raíz del repositorio:

```bash
cp observability/grafana/.env.example observability/grafana/.env
# Editar observability/grafana/.env y cambiar GRAFANA_ADMIN_PASSWORD.
aws sts get-caller-identity --profile julio_dev
docker compose --env-file observability/grafana/.env \
  -f observability/grafana/docker-compose.yml config
docker compose --env-file observability/grafana/.env \
  -f observability/grafana/docker-compose.yml up -d
docker compose --env-file observability/grafana/.env \
  -f observability/grafana/docker-compose.yml ps
```

En la computadora, abrir <http://localhost:3000> —o el puerto definido en
`GRAFANA_PORT`— e ingresar con el usuario y la contraseña del archivo `.env`.
El compose publica el puerto en `0.0.0.0` para permitir el acceso desde una
VPN. Desde el teléfono usar `http://IP_VPN_DE_LA_COMPUTADORA:PUERTO`, por
ejemplo `http://100.x.y.z:3001` si la computadora usa ese puerto.

`0.0.0.0` escucha en todas las interfaces del host. Antes de usarlo, limitar
el puerto `GRAFANA_PORT` con el firewall del sistema a la subred de la VPN y
no configurar port-forwarding público en el router. El datasource `WCS
CloudWatch` se provisiona automáticamente.

El dashboard `WCS · Observabilidad local` usa por defecto:

- Región: `us-east-1`.
- Servicio App Runner: `wally-customer-support-prod-backend`.
- Log group por prefijo: `/aws/apprunner/wally-customer-support-prod-backend`.
- Ventana: últimos 7 días.

Para detener Grafana:

```bash
docker compose --env-file observability/grafana/.env \
  -f observability/grafana/docker-compose.yml down
```

El volumen local `grafana_data` conserva la sesión y preferencias. No usar
`down -v` sin confirmar que se pueden eliminar esas preferencias.

## Credenciales y permisos

Compose monta `${HOME}/.aws` del host como sólo lectura dentro del contenedor.
El perfil AWS debe existir en `config` y, si corresponde, el caché de SSO debe
estar disponible. El datasource usa la cadena de credenciales del SDK; el
repositorio no contiene credenciales.

La policy de referencia está en
[`observability/grafana/iam/cloudwatch-readonly.json`](../observability/grafana/iam/cloudwatch-readonly.json).
Incluye únicamente consulta de métricas y Logs Insights. Debe asignarse en AWS
a un perfil/rol de lectura según la política real de la cuenta; el JSON no se
aplica automáticamente.

Si Grafana muestra `failed to get shared config profile`, comprobar que el
nombre de `AWS_PROFILE` coincida con el perfil dentro del `config` montado. Si
muestra `AccessDeniedException`, falta un permiso de CloudWatch en ese perfil.

## Consultas y costo

Las consultas reutilizables están en
[`observability/grafana/queries/cloudwatch-logs-insights.md`](../observability/grafana/queries/cloudwatch-logs-insights.md).
El dashboard se refresca cada cinco minutos y las consultas de Logs Insights y
métricas pueden generar cargos normales de AWS. Se consulta por prefijo porque
App Runner crea nuevos IDs de revisión sin cambiar el nombre lógico del
servicio.

La solución es deliberadamente sólo de diagnóstico local. No configura
port-forwarding público ni consulta directamente PostgreSQL. El binding en
`0.0.0.0` debe combinarse con una VPN y reglas de firewall; no reemplaza
alarmas, retención ni un sistema de trazas productivo.

## Validación con las preguntas de Telegram

Los eventos `WCS_EVENT` comienzan a estar disponibles después de desplegar la
versión del backend que contiene esta instrumentación; las preguntas recibidas
por revisiones anteriores no se reconstruyen retroactivamente.

Después de iniciar Grafana:

1. Abrir el dashboard y seleccionar `us-east-1` y
   `wally-customer-support-prod-backend`.
2. Enviar una pregunta al bot de Telegram.
3. Esperar hasta que el panel **WCS · últimos eventos operativos** se refresque.
4. Buscar `WEBHOOK_ACCEPTED`, `INBOUND_MESSAGE_PROCESSED`,
   `INTENT_CLASSIFIED` y `OUTBOUND_MESSAGE_DISPATCHED`.
5. Si AppConfig usa `wcs.ai.provider=bedrock`, verificar los paneles **IA ·
   tokens, costo y latencia** y **Consultas · tipo, resultado y latencia**.
6. Usar el panel **App Runner · errores** sólo para diagnóstico y no compartir
   su contenido sin revisar PII.

La presencia de `INBOUND_MESSAGE_PROCESSED` confirma que el webhook llegó y se
procesó; `CONVERSATION_QUERY_COMPLETED` registra el resultado del orquestador;
`OUTBOUND_MESSAGE_DISPATCHED result=SENT` confirma que el adapter saliente
envió la respuesta. Las métricas de App Runner no prueban por sí solas que
Telegram haya entregado el mensaje.

## Evolución

La siguiente iteración puede agregar métricas Micrometer/CloudWatch para
latencias y contadores sin parsear logs, alertas de 4xx/5xx y fallos de
entrega, correlation ID entre webhook y outbox, y eventualmente trazas. La
fuente de verdad seguirá siendo esta documentación junto con las consultas y
dashboards versionados.
