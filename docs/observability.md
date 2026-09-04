# Observabilidad — WCS

Owner: Tech Lead
Status: `In Progress`
Related Jira: `WCS-22`
Related repository paths: `observability/grafana/`, `src/main/java/com/wally/customersupport/adapter/in/web/`, `src/main/java/com/wally/customersupport/application/service/`

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

El backend emite eventos operativos con el prefijo `WCS_EVENT`:

| Evento | Campos | Uso |
| --- | --- | --- |
| `WEBHOOK_ACCEPTED` | `channel`, `commandCount` | Webhook recibido con autenticación válida |
| `WEBHOOK_REJECTED` | `channel`, `reason` | Firma o secret inválido, payload malformado |
| `INBOUND_MESSAGE_PROCESSED` | `channel`, `result`, `durationMs` | Mensaje aceptado, duplicado o ignorado |
| `INTENT_CLASSIFIED` | `intent`, `confidence`, `durationMs` | Clasificación del orquestador |
| `INTENT_CLASSIFICATION_FAILED` | `errorType`, `durationMs` | Fallo del clasificador |
| `GENERAL_SUPPORT_FAILED` | `errorType` | Fallback de conocimiento/LLM |
| `OUTBOUND_MESSAGE_DISPATCHED` | `channel`, `result`, `errorType`, `durationMs` | Entrega o reintento del outbox |

No se registran texto de usuario, prompts, respuestas completas, números de
teléfono, chat IDs, tokens, firmas ni payloads de proveedores. `WCS_EVENT` usa
pares `clave=valor` para que Logs Insights pueda agregarlos sin depender del
formato textual de una excepción.

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

Abrir <http://localhost:3000> e ingresar con el usuario y la contraseña del
archivo `.env`. El puerto queda ligado a `127.0.0.1` y el datasource `WCS
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

La solución es deliberadamente sólo de diagnóstico local. No expone Grafana a
Internet, no consulta directamente PostgreSQL y no reemplaza alarmas, retención
ni un sistema de trazas productivo.

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
5. Usar el panel **App Runner · errores** sólo para diagnóstico y no compartir
   su contenido sin revisar PII.

La presencia de `INBOUND_MESSAGE_PROCESSED` confirma que el webhook llegó y se
procesó; `OUTBOUND_MESSAGE_DISPATCHED result=SENT` confirma que el adapter
saliente envió la respuesta. Las métricas de App Runner no prueban por sí solas
que Telegram haya entregado el mensaje.

## Evolución

La siguiente iteración puede agregar métricas Micrometer/CloudWatch para
latencias y contadores sin parsear logs, alertas de 4xx/5xx y fallos de
entrega, correlation ID entre webhook y outbox, y eventualmente trazas. La
fuente de verdad seguirá siendo esta documentación junto con las consultas y
dashboards versionados.
