# Operación y configuración — WCS

Owner: Tech Lead  
Status: `Accepted`
Last reviewed: 2026-09-08
Related Jira: `WCS-13`, `WCS-21`, `WCS-22`, `WCS-30`, `WCS-35`, `WCS-36`, `WCS-37`, `WCS-38`, `WCS-39`, `WCS-40`, `WCS-41`, `WCS-42`, `WCS-76`, `WCS-77`, `WCS-78`, `WCS-85`, `WCS-86`, `WCS-87`, `WCS-88`, `WCS-89`, `WCS-90`, `WCS-91`, `WCS-92`, `WCS-93`, `WCS-94`, `WCS-95`, `WCS-96`, `WCS-103`, `WCS-104`, `WCS-105`
Related repository paths: `src/main/resources`, `backoffice/`, `.github/workflows`, `infra/`

## Ambientes

Por el momento WCS opera únicamente con el environment `prod` de AppConfig. La
aplicación de AppConfig es siempre `wally-customer-support` y el profile hosted
es siempre `runtime`; ambos identificadores están versionados en el bootstrap.

- `prod`: cuenta Meta, secrets gestionados y monitoreo obligatorio.
- `test`: perfil interno de pruebas con datos sintéticos y dobles de los adapters;
  no representa un ambiente operativo.

El Environment `production` de GitHub Actions es un gate de despliegue y no
debe confundirse con el environment `prod` de Spring/AppConfig.

## CI/CD objetivo

```text
Pull request
  → format / compile
  → unit + integration tests
  → contract tests
  → security/static checks

main
  → build image
  → publish artifact
  → deploy staging/production según aprobación
  → health/readiness
  → smoke E2E
  → evidencia en Jira
```

No se debe declarar un release operativo sólo por tener el PR verde.

## Salida de red de App Runner

El entorno temporal de WCS usa `backend_egress_type = "DEFAULT"`, que deja la
salida pública administrada por App Runner. Esto permite que el backend llame a
la API pública de Telegram sin agregar un NAT Gateway. El RDS compartido está
temporalmente público para pruebas y queda fuera de la administración de este
stack.

El modo `VPC` sólo debe habilitarse cuando las subnets del VPC connector tengan
NAT Gateway para APIs públicas o endpoints privados para cada dependencia AWS.
Un VPC connector con subnets privadas sin ruta de salida permite llegar a
recursos internos, pero bloquea Telegram, WhatsApp y cualquier API pública.
Antes de cerrar el acceso público del RDS hay que diseñar la conectividad
privada y cambiar explícitamente `backend_egress_type` a `VPC`.

## Base de infraestructura y pipelines

La base versionada en el repositorio está organizada así:

```text
infra/bootstrap/                  límites y verificaciones del state
infra/environments/prod/          composición del entorno WCS
infra/modules/appconfig/          application, environment y profile
infra/modules/runtime-secrets/    contenedor de Secrets Manager sin valores
infra/modules/bedrock-knowledge-base/  S3, S3 Vectors, Bedrock KB y service role
infra/modules/backend-apprunner/  ECR, IAM, App Runner opcional
infra/modules/github-backend-deploy/  OIDC y permisos de despliegue
ci/backend-deploy.sh              despliegue por digest y health-check
.github/workflows/backend.yml     verify; deploy manual
.github/workflows/backend-restart.yml  restart manual para recargar AppConfig
.github/workflows/frontend.yml    verify de tests y build del backoffice
.github/workflows/knowledge-base-sync.yml  ingesta manual de la KB WCS
.github/workflows/terraform.yml   validate; plan/apply manual
knowledge-base/wcs/                documentos Markdown versionados para la KB
```

La validación de Terraform corre en pull requests y en cambios a `main` sin
acceder al backend remoto. El plan y el apply se ejecutan desde
`workflow_dispatch`: el plan escribe un resumen sin valores sensibles en el
resumen del workflow y el apply sólo puede ejecutarse desde `main`, con
`confirm_apply=true`, un rol AWS con OIDC y la aprobación del Environment
`production`. El workflow bloquea destrucciones y reemplazos, y aplica el plan
generado en esa misma ejecución. El deploy de backend también es manual y usa
una imagen identificada por digest.

El backoffice no forma parte de la imagen ni del deploy del backend. Su
workflow sólo instala dependencias, ejecuta tests y genera el build cuando
cambian sus archivos; no tiene permisos AWS y no publica el artefacto.

### Recargar AppConfig sin recompilar

WCS carga AppConfig una vez durante el arranque del proceso. Cuando sólo cambia
la configuración desplegada en AppConfig y la imagen actual ya contiene el
código compatible, no es necesario ejecutar Maven, construir una imagen ni
publicar otra versión en ECR. El workflow manual
`.github/workflows/backend-restart.yml` ejecuta `aws apprunner start-deployment`
contra la imagen actualmente configurada, espera la operación y valida
`/actuator/health`.

El workflow sólo puede ejecutarse desde `main`, requiere
`confirm_restart=true` y aprobación del Environment `production`. Mantiene el
rol OIDC de deploy existente y no modifica AppConfig, Secrets Manager,
Terraform ni la imagen del servicio. App Runner igualmente tarda lo necesario
para crear el contenedor y ejecutar sus health checks, pero se evita el build y
el push de la pipeline completa.

Usar este flujo para cambios de AppConfig o rotación de secrets que sólo
requieren que el bootstrap vuelva a leer la configuración. Usar
`.github/workflows/backend.yml` para cambios de código, Dockerfile, dependencias
o cualquier cambio que requiera una nueva imagen. Si la operación devuelve
`FAILED`, `ERROR` o cualquier estado `ROLLBACK_*`, el workflow falla de forma
explícita y no declara el restart como exitoso.

Ejecución por CLI:

```bash
gh workflow run "Restart Backend (AppConfig)" \
  --ref main \
  -f confirm_restart=true
```

El refresh dinámico sin reiniciar queda fuera de alcance: requeriría polling
con `GetLatestConfiguration` o AppConfig Agent y un diseño explícito para
actualizar beans que hoy se seleccionan con propiedades de Spring al arranque.

Para habilitar Terraform en GitHub se deben configurar en el Environment
`production`:

- `AWS_TERRAFORM_ROLE_ARN`: ARN de un rol preaprobado con trust OIDC para
  `julioperezdev/wally-customer-support` y el subject del Environment
  `production`. GitHub puede usar el formato inmutable
  `repo:owner@owner_id/repository@repository_id:environment:production`; el
  trust policy de WCS contempla ambos formatos y los IDs deben coincidir con
  el repositorio real.
- `TERRAFORM_VARS`: archivo HCL con variables revisadas y referencias de ARN,
  nunca passwords, tokens, claves privadas ni otros valores secretos.
- una regla de aprobación con al menos un reviewer requerido para el
  Environment `production`.

El workflow no crea ni amplía automáticamente el rol AWS de Terraform. La
confianza OIDC y sus permisos deben revisarse en AWS, incluyendo el acceso a
la key de state de WCS y sólo los recursos que este stack administra. El apply
no se dispara por un push: primero se ejecuta `plan`, se revisa su resumen y
luego se vuelve a lanzar el workflow con `action=apply` y
`confirm_apply=true`.

La base de datos existente se configura como `shared_rds_*` y el runtime recibe
referencias a AppConfig/Secrets Manager. La carga efectiva de esos valores en
Spring sigue siendo el alcance de WCS-22; por eso App Runner queda desactivado
por defecto y esta base no constituye un deployment productivo.

El CI de Terraform usa un rol IAM dedicado de WCS, no el rol de `tesis-dev`.
Su trust OIDC está restringido al repositorio WCS y al Environment `production`;
su policy limita el state al prefijo de WCS y los permisos de servicio a los
recursos administrados por este stack. El primer apply del rol requiere un
bootstrap autorizado, porque un rol no puede autenticarse antes de existir.

## Configuración productiva

La configuración productiva se divide deliberadamente:

### AWS AppConfig — configuración no sensible

```text
wcs.whatsapp.graph-api-version
wcs.whatsapp.graph-api-base-url
wcs.whatsapp.phone-number-id
wcs.whatsapp.business-account-id
wcs.whatsapp.connect-timeout
wcs.whatsapp.read-timeout
wcs.telegram.enabled
wcs.telegram.adapter
wcs.telegram.api-base-url
wcs.telegram.allowed-chat-id
wcs.telegram.connect-timeout
wcs.telegram.read-timeout
wcs.outbox.max-attempts
wcs.conversation.memory.enabled
wcs.conversation.memory.ttl
wcs.conversation.memory.max-messages
wcs.conversation.memory.max-message-characters
wcs.conversation.summary.enabled
wcs.conversation.summary.trigger-message-count
wcs.conversation.summary.recent-message-count
wcs.conversation.summary.trigger-characters
wcs.conversation.summary.max-summary-characters
wcs.conversation.preferences.enabled
wcs.conversation.preferences.ttl
wcs.conversation.preferences.max-preferences
wcs.conversation.preferences.max-value-characters
wcs.ai.provider
wcs.ai.model
wcs.ai.region
wcs.ai.pricing-version
wcs.ai.input-price-usd-per-million-tokens
wcs.ai.output-price-usd-per-million-tokens
wcs.rag.provider
wcs.rag.max-results
wcs.rag.knowledge-base-id (cuando el adapter Bedrock KB esté habilitado)
wcs.agent-evaluation.control-plane.security.enabled
wcs.agent-evaluation.control-plane.security.issuer-uri
wcs.agent-evaluation.control-plane.security.audience
```

### AWS Secrets Manager — secretos

```text
wcs/{environment}/whatsapp
  access-token
  verify-token
  app-secret

wcs/{environment}/telegram
  bot-token
  webhook-secret-token

wcs/{environment}/database
  username
  password
  jdbc-url (si no lo provee el runtime)

wcs/{environment}/providers
  api keys de proveedores externos, sólo si fueran necesarias
```

Bedrock debe autenticarse preferentemente con IAM Role del workload; no se crea una API key para Bedrock. Los valores de Secrets Manager no se pasan a Jira, Confluence, la base de datos ni los logs.

El runtime implementa `AwsExternalConfigurationEnvironmentPostProcessor` como fuente temprana de configuración de Spring. Primero carga un snapshot de AWS AppConfig Data API y luego resuelve únicamente campos allow-listed de los secretos referenciados en AWS Secrets Manager. El nombre de la aplicación y el environment `prod` están versionados en `application.properties`; la región AWS es temporalmente `us-east-1`. Las credenciales se resuelven mediante la cadena estándar del SDK. El starter agrega los valores como `PropertySource` en memoria antes del binding de Spring: no genera ni modifica un `application.properties` en runtime. Los valores nunca se escriben en `application.properties`, el repositorio ni los logs.

El baseline no sensible de AppConfig v5 se mantiene en
`infra/environments/prod/main.tf`. La configuración remota continúa siendo la
fuente de runtime; el módulo de Terraform conserva `ignore_changes` sobre el
contenido hosted para no reemplazar cambios operativos hechos en AppConfig.

### Contrato del documento de AppConfig

El profile hosted usa JSON. Puede ser plano o anidado; el loader aplana objetos a
claves separadas por punto. AppConfig contiene configuración no sensible y
referencias a secretos, por ejemplo:

```json
{
  "wcs.whatsapp.graph-api-version": "v25.0",
  "wcs.whatsapp.graph-api-base-url": "https://graph.facebook.com",
  "wcs.external-config.secrets-manager.database-secret-id": "tesis-dev-prod/rds",
  "wcs.external-config.secrets-manager.whatsapp-secret-id": "wcs/prod/whatsapp",
  "wcs.external-config.secrets-manager.telegram-secret-id": "wcs/prod/telegram",
  "wcs.telegram.enabled": false,
  "wcs.telegram.adapter": "disabled",
  "wcs.telegram.api-base-url": "https://api.telegram.org",
  "wcs.ai.provider": "bedrock",
  "wcs.ai.model": "openai.gpt-oss-20b-1:0",
  "wcs.ai.region": "us-east-1",
  "wcs.ai.pricing-version": "aws-bedrock-us-east-1-standard-2026-09",
  "wcs.ai.input-price-usd-per-million-tokens": 0.0721,
  "wcs.ai.output-price-usd-per-million-tokens": 0.3090,
  "wcs.rag.provider": "bedrock-kb",
  "wcs.rag.max-results": 5,
  "wcs.rag.knowledge-base-id": "REPLACE_ME_WCS_KNOWLEDGE_BASE_ID"
}
```

El proveedor `bedrock-kb` representa la Knowledge Base documental propia de
WCS. Su fuente inicial es S3 y su vector store objetivo es S3 Vectors con Titan
Text Embeddings V2 de 1024 dimensiones. El identificador de la Knowledge Base
es configuración no sensible y el service role sólo debe ser utilizado por
Bedrock para leer la fuente y el índice propios de WCS.

Las consultas de catálogo, stock, carrito y pedidos no deben resolverse con
`wcs.rag.provider`. Esas capacidades se implementan como tools/casos de uso
WCS y consultan sus fuentes transaccionales con autorización, ownership y
queries parametrizadas.

Las referencias admitidas actualmente son:

| Referencia AppConfig | Campos JSON allow-listed | Propiedades Spring resultantes |
| --- | --- | --- |
| `database-secret-id` | `jdbc-url`/`jdbc_url`/`url`, `username`/`user`, `password` | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` |
| `whatsapp-secret-id` | `access-token`, `verify-token`, `app-secret` y variantes snake/camel | `wcs.whatsapp.access-token`, `wcs.whatsapp.verify-token`, `wcs.whatsapp.app-secret` |
| `telegram-secret-id` | `bot-token`, `webhook-secret-token` y variantes snake/camel | `wcs.telegram.bot-token`, `wcs.telegram.webhook-secret-token` |
| `runtime-secret-id` | combinación explícita de los campos anteriores | propiedades correspondientes |

El `secret-id` legacy sólo se usa como referencia genérica cuando no hay
referencias dedicadas. Los campos desconocidos se ignoran deliberadamente.
Esto evita que un nuevo campo agregado a un secret se convierta de forma
accidental en una propiedad de Spring.

### Memoria conversacional PostgreSQL

`WCS-35` agrega la migración `V6__create_conversation_memory_states.sql` y la
tabla `wcs.conversation_memory_states` en el RDS compartido. El estado contiene
una ventana acotada de mensajes, `actor_id` pseudónimo, `updated_at` y una
versión para control de concurrencia. El adapter elimina estados vencidos al
cargarlos y registra sólo operación, resultado, cantidad, duración y un
identificador interno de correlación.

`WCS-36` agrega mediante `V7__add_conversation_summary.sql` un resumen
opcional, su versión, cantidad de mensajes incluidos y fecha de actualización.
El resumen se genera detrás de `ConversationSummarizer`, con implementación
Bedrock cuando `wcs.ai.provider=bedrock` y un doble determinístico cuando el
provider es `mock`. No se activa por defecto.

La persistencia está protegida por `wcs.conversation.memory.enabled=false` en
el bootstrap. Con ese valor se usa un adapter no-op y los canales no retienen
memoria; los tests de integración lo habilitan explícitamente. Sólo después de
aprobar la política de retención se puede publicar en AppConfig:

```text
wcs.conversation.memory.enabled=true
wcs.conversation.memory.ttl=PT24H
wcs.conversation.memory.max-messages=20
wcs.conversation.memory.max-message-characters=2000
wcs.conversation.summary.enabled=false
wcs.conversation.summary.trigger-message-count=12
wcs.conversation.summary.recent-message-count=6
wcs.conversation.summary.trigger-characters=12000
wcs.conversation.summary.max-summary-characters=4000
```

#### Rollback operativo

Ante un problema del adapter, volver a publicar la configuración con
`wcs.conversation.memory.enabled=false` detiene nuevas lecturas y escrituras sin
borrar el estado. `wcs.conversation.summary.enabled=false` deja de generar y
usar resúmenes sin borrar la ventana ni el resumen almacenado. Si se necesita
revertir la versión de aplicación, primero se
despliega la versión anterior y luego se verifica que ningún runtime lea la
tabla. Las migraciones V6 y V7 no se editan ni se revierten automáticamente en producción;
el borrado de la tabla requiere una migración posterior, revisión del plan y
evidencia de que la retención, auditoría y backups fueron tratados.

### Preferencias explícitas PostgreSQL

`WCS-37` agrega la migración `V8__create_customer_preferences.sql` y un
adapter PostgreSQL para preferencias explícitas de bajo riesgo. La
configuración recomendada inicial es:

```text
wcs.conversation.preferences.enabled=false
wcs.conversation.preferences.ttl=PT24H
wcs.conversation.preferences.max-preferences=5
wcs.conversation.preferences.max-value-characters=64
```

El servicio sólo acepta valores permitidos y confirmados; la primera
preferencia soportada es `preferred_color`. El adapter no-op queda activo si
la flag es falsa o falta. Para rollback funcional se vuelve a publicar la flag
en `false`; la tabla se conserva para no modificar migraciones aplicadas.
Antes de habilitarla en producción deben aprobarse retención, borrado,
ownership y observabilidad sin PII.

### Activación de agentes desde el runtime

`WCS-50` agrega una lectura opcional del registry antes de ejecutar el plan
conversacional. El bootstrap mantiene:

```text
wcs.agent-runtime.activation-enabled=false
wcs.agent-runtime.environment=prod
wcs.agent-runtime.shadow-enabled=false
wcs.agent-runtime.shadow-timeout=PT5S
wcs.agent-runtime.shadow-provider=noop
```

La flag falsa evita toda consulta al registry y conserva el flujo
determinístico. Sólo después de aprobar una activación persistida y revisar
sus métricas se puede publicar `true` en AppConfig. Si la activación no existe,
está deshabilitada, tiene kill switch o el registry no está disponible, el
orquestador continúa con el flujo anterior y registra únicamente la razón
sanitizada. `WCS-51` valida además la versión exacta y sus límites antes de
construir una definición ejecutable; `WCS-52` expone esa resolución en
`AGENT_ROUTED` y `AGENT_EXECUTION_STARTED`, pero no cambia el modelo ni el
prompt utilizado. Si falta la versión, hay mismatch, el estado no es publicable
o falla el registry, se conserva el flujo anterior. Para rollback se vuelve a
publicar la flag en `false`; no se eliminan activaciones ni se modifica la
migración V9.

### Mutaciones controladas del registry

La escritura del registry tiene una flag independiente y permanece cerrada:

```properties
wcs.agent-registry.activation-write-enabled=false
```

Cuando se habilite en un ambiente autorizado, sólo el scope
`agent-registry.write` puede alcanzar los endpoints internos de activación.
Cada request exige `Idempotency-Key`; la key se hashea con SHA-256 y se
persiste únicamente su hash en `wcs.agent_activation_command_claims`. El actor
se obtiene del subject autenticado y nunca del body. Activar, kill switch y
rollback crean nuevas filas de `wcs.agent_activations`; no se actualizan ni se
eliminan referencias históricas.

El rollout recomendado es: probar primero con una versión `APPROVED` sintética,
verificar los eventos `AGENT_REGISTRY_MUTATION_*`, confirmar que
`wcs.agent-runtime.activation-enabled` sigue en `false`, y recién después
evaluar una activación controlada. El rollback operativo es deshabilitar la
flag de escritura y mantener el runtime en `false`; no se ejecuta `destroy` ni
se modifican migraciones aplicadas.

### Seguridad del control plane de evaluaciones

El endpoint read-only `/internal/agent-evaluations/**` se protege de forma
condicional con Spring Security Resource Server. La configuración estable es:

```properties
wcs.agent-evaluation.control-plane.security.enabled=false
wcs.agent-evaluation.control-plane.security.issuer-uri=
wcs.agent-evaluation.control-plane.security.audience=
```

Al habilitarla, el runtime descubre las claves públicas del `issuer-uri`,
valida firma, issuer, expiración, audience y exige el scope exacto
`agent-evaluation.read`. El `sub` del JWT se usa como actor para la frontera
provider-neutral; no se acepta un actor libre en un header. La seguridad se
mantiene deshabilitada por defecto porque todavía no se provisionó un IdP ni
se aprobó un cliente del backoffice.

La cadena protegida sólo hace match con `/internal/agent-evaluations/**`.
Webhooks de Telegram/WhatsApp, actuator y demás rutas públicas conservan su
comportamiento existente. Una solicitud sin token devuelve `401`; un token
válido sin el scope devuelve `403`; un token válido con subject y scope
correctos llega al servicio de aplicación, que conserva su propia autorización
deny-by-default y sus logs sanitizados.

Para el rollout, primero provisionar el IdP y verificar issuer, audience y
scopes en un ambiente no productivo. Luego publicar las tres propiedades en
AppConfig, reiniciar App Runner y ejecutar los casos `200/401/403` sin exponer
tokens. Para rollback, volver `enabled` a `false` y reiniciar; esto no modifica
datos ni migraciones. Este slice no crea Cognito, IAM, WAF ni permisos nuevos.

### Precedencia y modos de ejecución

La precedencia efectiva es: argumentos de línea de comandos y propiedades del
sistema, Secrets Manager resuelto, AppConfig y defaults versionados en
`application.properties`. Los valores de los secretos no son variables de
entorno productivas.

AppConfig y Secrets Manager están habilitados permanentemente en el runtime
normal y usan fail-fast: la aplicación no arranca con configuración productiva
incompleta. Los tests sobrescriben esas propiedades en
`src/test/resources/application-test.properties` y usan dobles sintéticos.

El loader crea clientes AWS temporalmente en `us-east-1` y usa la cadena estándar
de credenciales del SDK. Sólo se hace una lectura al arranque; el redeploy o
restart consume la última versión desplegada de AppConfig y permite rotar
secrets sin recompilar.

### IAM mínimo

El rol de runtime necesita `appconfig:StartConfigurationSession`,
`appconfig:GetLatestConfiguration` y
`secretsmanager:GetSecretValue` sobre los ARNs concretos de WCS y del RDS
compartido, más los ARNs declarados para cualquier secret adicional referenciado
por AppConfig (por ejemplo, WhatsApp o Telegram). No se permite
`secretsmanager:ListSecrets` ni se usan access keys embebidas. Terraform
prepara los permisos y deja los valores fuera de su estado; el documento de
AppConfig puede versionarse porque sólo contiene referencias no sensibles.

No se requieren variables de entorno de aplicación para el arranque normal. En
local, el SDK usa una sesión o perfil AWS configurado fuera del repositorio; en
AWS usa el rol IAM del workload. Los secretos siguen siendo exclusivos de
Secrets Manager.

El entorno prod incluye por defecto una configuración hosted y versiones
bootstrap falsas para `wcs/{environment}/database`,
`wcs/{environment}/whatsapp` y `wcs/{environment}/telegram`. Los valores se
deben reemplazar desde la consola
antes de habilitar App Runner. Las versiones iniciales de Terraform ignoran
cambios posteriores hechos en consola para no revertir una rotación manual;
Terraform no debe recibir valores reales.

### Knowledge Base documental de WCS

La Knowledge Base propia se administra mediante el módulo
`infra/modules/bedrock-knowledge-base`. El bucket fuente y el vector store son
recursos separados de cualquier KB histórica. Los documentos aprobados viven
en `knowledge-base/wcs/` y Terraform los publica bajo `documents/` con
versionado y cifrado SSE-S3.

`knowledge-base/wcs/location.md` contiene una ubicación ficticia marcada como
`DEMO` para validar recuperación documental. No debe presentarse como una sede
real ni reemplazar la fuente operativa cuando se defina la ubicación de la
tienda.

La configuración objetivo es:

- Amazon Bedrock Knowledge Bases con una data source S3.
- Amazon S3 Vectors como vector store.
- `amazon.titan-embed-text-v2:0`, `FLOAT32`, 1024 dimensiones y distancia
  euclídea.
- IAM exclusivo para Bedrock con lectura de los documentos y acceso al índice
  de WCS.

Después del apply, iniciar y revisar la ingesta de manera explícita:

```bash
./scripts/start-wcs-knowledge-ingestion.sh \
  --knowledge-base-id "$(terraform -chdir=infra/environments/prod output -raw knowledge_base_id)" \
  --data-source-id "$(terraform -chdir=infra/environments/prod output -raw knowledge_base_data_source_id)" \
  --wait
```

La operación debe registrar en Jira el job de ingesta, cantidad indexada y
cantidad fallida. No se incluyen conversaciones reales, PII, secretos ni
contenido del catálogo dinámico en esta fuente.

### Sincronización operativa de la Knowledge Base

La ingesta de documentos no ocurre automáticamente con cada commit ni con cada
reinicio de App Runner. Después de un `terraform apply` que publique o cambie
documentos, ejecutar el workflow manual
`.github/workflows/knowledge-base-sync.yml`. El workflow:

1. sólo acepta ejecuciones desde `main` con `confirm_sync=true`;
2. requiere aprobación del Environment `production`;
3. usa OIDC con `AWS_TERRAFORM_ROLE_ARN`;
4. valida que los IDs apunten a `wally-customer-support-prod-knowledge-base` y
   `wcs-markdown-source`;
5. espera la ingesta, aplica timeout y reporta documentos escaneados,
   indexados, fallidos y eliminados;
6. falla si el job termina en `FAILED`/`STOPPED`, si hay documentos fallidos o
   si la fuente no escanea ningún documento.

Una reejecución sin cambios puede reportar `indexed=0`: significa que no hay
documentos nuevos o modificados para indexar y no es un error si el job termina
en `COMPLETE` y no reporta documentos fallidos.

Configurar en el Environment `production` las variables no sensibles
`WCS_KNOWLEDGE_BASE_ID` y `WCS_KNOWLEDGE_BASE_DATA_SOURCE_ID` con los outputs de
Terraform `knowledge_base_id` y `knowledge_base_data_source_id`. El nombre
esperado puede dejarse con su valor por defecto o definirse explícitamente como
`WCS_KNOWLEDGE_BASE_NAME`.

El rol Terraform requiere también `bedrock:GetIngestionJob` para poder esperar
el resultado y leer las estadísticas del job. El cambio de IAM debe pasar por
el workflow de Terraform: revisar primero el plan y aprobar el `apply`; esta
issue no ejecuta el `apply` automáticamente.

Ejecución por CLI:

```bash
gh workflow run "Sync WCS Knowledge Base" \
  --ref main \
  -f confirm_sync=true
```

La ingesta sólo actualiza el índice documental. No requiere reiniciar App
Runner: el runtime ya consulta la misma Knowledge Base por su ID de AppConfig.
El workflow `backend-restart.yml` se reserva para cambios de AppConfig o
Secrets Manager que deban ser leídos durante el bootstrap.

Después de una ejecución exitosa, realizar smoke tests sintéticos por el canal
habilitado: ubicación, horarios, envíos, cambios/devoluciones y una pregunta
sin evidencia. Las consultas de producto, precio, talle, color y stock deben
seguir pasando por PostgreSQL; una respuesta documental no puede reemplazar
esos datos dinámicos.

## Observabilidad mínima

- correlation/request ID;
- estado y dirección del mensaje;
- duración LLM y Meta;
- intentos;
- error code sanitizado;
- volumen y costo agregado;
- health y readiness;
- alertas por fallos, latencia, backlog y costo.

No registrar texto completo, firmas, tokens, números de teléfono completos ni payloads de Meta en producción.

La primera implementación operativa está documentada en
[`observability.md`](observability.md). Incluye Grafana local en Docker con el
datasource read-only de CloudWatch, métricas de App Runner y consultas por
prefijo para los log groups dinámicos de las revisiones. El dashboard no expone
Grafana a Internet ni consulta PostgreSQL directamente.

El backend emite eventos sanitizados con prefijo `WCS_EVENT` para observar
webhooks, procesamiento inbound, intención y despacho outbound. Estos eventos
no contienen texto de usuario, prompts, números de teléfono, chat IDs,
secretos ni payloads de proveedores.

## Runbooks requeridos

- Webhook no recibe eventos.
- Firmas inválidas.
- Backlog de procesamiento.
- Meta devuelve error o rate limit.
- LLM no responde o responde contenido inválido.
- Reintentos duplican mensajes.
- Rotación de secrets.
- Rollback de aplicación.
- Borrado de conversaciones según política.

## Reglas de disponibilidad

- La aplicación normal arranca con AppConfig y Secrets Manager; si una
  integración productiva requerida no está disponible, falla explícitamente.
- Los tests pueden activar dobles de cada adapter de forma independiente.
- Un provider no configurado falla de forma explícita y controlada; no se hace fallback silencioso a producción con datos sintéticos.
- Health verifica proceso y dependencias esenciales; readiness declara qué integración está deshabilitada o degradada sin imprimir secretos.
- La rotación de Secrets Manager debe ser probada sin recompilar ni cambiar código.

## Registro del webhook de Telegram

Cuando el endpoint público ya esté disponible, registrar el webhook mediante el
helper que obtiene el bot token y el secret desde `wcs/prod/telegram`:

```bash
./scripts/register-telegram-webhook.sh \
  --url https://<host-publico>/webhook/telegram
```

La URL debe ser HTTPS y terminar exactamente en `/webhook/telegram`. Para una
prueba local se puede usar una URL HTTPS temporal de ngrok. El registro se debe
repetir si cambia la URL temporal; no incluir el token en el comando.
La implementación usa webhook; el long polling no forma parte del runtime de
WCS. Para una prueba local, se expone la aplicación con ngrok y se registra esa
URL temporal.

## Trigger de evaluación del control plane

### Executor y límites de evaluación

El trigger puede seguir usando el executor determinístico o seleccionar el
executor Bedrock para un dataset sintético. El default es seguro:

```properties
wcs.agent-evaluation.executor=deterministic
wcs.agent-evaluation.max-scenarios=10
wcs.agent-evaluation.max-input-tokens-per-scenario=4000
wcs.agent-evaluation.max-output-tokens=512
wcs.agent-evaluation.max-estimated-cost-usd=0.0500
wcs.agent-evaluation.timeout=PT30S
```

Para una prueba aprobada, usar `wcs.agent-evaluation.executor=bedrock` junto
con `wcs.ai.provider=bedrock` y el mismo `wcs.ai.model` en el request. El
preflight rechaza una suite que exceda escenarios o costo estimado antes de
invocar al proveedor. Si Bedrock no informa una métrica, se conserva como no
disponible; no se interpreta como cero. Para rollback volver a
`wcs.agent-evaluation.executor=deterministic`. Esta configuración no habilita
por sí sola el endpoint HTTP ni requiere Terraform.

El endpoint `POST /internal/agent-evaluations/runs` permanece deshabilitado por
defecto mediante `wcs.agent-evaluation.trigger.enabled=false`. Su activación
requiere además seguridad JWT habilitada, issuer, audience, el scope exacto
`agent-evaluation.execute` y un authorizer aprobado. El scope
`agent-evaluation.read` sólo permite las consultas GET del histórico.

Ejemplo de request sintético, sin secretos:

```bash
curl -i -X POST "https://<host>/internal/agent-evaluations/runs" \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: evaluation-2026-09-08-001" \
  -H "Content-Type: application/json" \
  -d '{"datasetVersion":"catalog-response-v1","agentId":"catalog-specialist","agentVersion":"v1","provider":"mock","modelId":"deterministic-v1"}'
```

La key se reclama después de autorizar y se guarda únicamente como digest
SHA-256. Un duplicado devuelve `409 ALREADY_PROCESSED`; no se reejecuta la
suite. Los eventos pueden filtrarse por `AGENT_EVALUATION_TRIGGER_*`, outcome,
agentId, model y duración. No registrar tokens JWT, keys crudas, prompts ni
respuestas.

Para rollback, volver `wcs.agent-evaluation.trigger.enabled` a `false` en
AppConfig y mantener el runtime conversacional actual. Este slice no requiere
apply de Terraform ni reinicio salvo que la configuración se lea sólo durante
el bootstrap.

## Preflight y shadow/canary del registry

Antes de cualquier activación se puede consultar
`POST /internal/agent-registry/activations/preflight` con el scope
`agent-registry.read`. Es una validación sin mutaciones: no reclama
`Idempotency-Key`, no persiste claims, no cambia AppConfig y no publica ningún
mensaje. El resultado `READY` sólo significa que la solicitud supera los
checks; todavía requiere la API write-only, aprobaciones válidas y la flag de
escritura habilitada por un procedimiento operativo separado.

La migración controlada usa tres modos conceptuales:

- `SHADOW`: puede evaluar una candidata, pero nunca publica su respuesta; el
  runtime activo sigue siendo la respuesta al usuario.
- `CANARY`: selecciona de forma determinística un porcentaje de conversaciones
  pseudonimizadas y hace fallback a la versión activa fuera del bucket.
- `ACTIVE`: conserva el runtime actual como fuente de verdad durante esta fase.

Los eventos de comparación sólo pueden contener metadatos sanitizados:
`requestId`, conversación pseudonimizada, canal, caso de uso, agente/versión,
modo, outcome, latencia, tokens, costo, razón de fallback y si se publicó la
respuesta candidata. Nunca se almacenan prompts, mensajes completos, secretos
ni números de teléfono. Hasta que exista evidencia de WCS-97/98/99, no se
ejecuta Bedrock adicional ni se habilita tráfico shadow/canary en producción.

### Runtime shadow WCS-100–102

El runtime actual sigue siendo la única fuente de respuesta para el usuario.
`AgentShadowRuntimeService` sólo se invoca después de construir el resultado
activo y su puerto `AgentShadowExecutor` no tiene autoridad para crear outbox,
enviar mensajes o cambiar el plan. El adapter instalado por defecto es
`NoOpAgentShadowExecutor`, por lo que la configuración cerrada no produce
llamadas externas.

Para una futura prueba controlada, revisar que la definición tenga timeout,
`maxInputTokens`, `maxOutputTokens` y `budgetLimitUsd` aprobados. El servicio
cancela la ejecución al alcanzar el timeout efectivo, transforma un exceso de
tokens/costo en `LIMIT_EXCEEDED`, y emite `AGENT_TRAFFIC_COMPARISON_RECORDED`
con la conversación sólo en forma pseudonimizada. Si hay cualquier error,
mantener la flag en `false`; el fallback activo no depende de la evidencia
shadow.

Rollback: publicar `wcs.agent-runtime.shadow-enabled=false` y conservar
`wcs.agent-runtime.activation-enabled=false` y
`wcs.agent-runtime.shadow-provider=noop`. No requiere migración ni Terraform;
si la configuración se carga sólo durante el bootstrap, reiniciar App Runner
después de verificar el cambio en AppConfig.

### Comparación y candidata Bedrock WCS-103–105

La comparación de calidad no almacena respuestas candidatas. El servicio
calcula hashes sólo en memoria y emite `comparisonOutcome=MATCH`, `MISMATCH` o
`UNKNOWN`; un resultado `UNKNOWN` es el valor esperado cuando faltan tokens,
respuesta o digest. El runtime activo continúa siendo la única fuente de
respuesta y el candidato no tiene acceso al outbox.

El provider por defecto es `noop`. Para un ambiente de prueba autorizado se
puede usar este cambio acotado en AppConfig, siempre con una definición activa
compatible y el modelo Bedrock configurado:

```properties
wcs.agent-runtime.shadow-enabled=true
wcs.agent-runtime.shadow-provider=bedrock
wcs.agent-runtime.activation-enabled=true
```

El adapter Bedrock sólo soporta por ahora `CATALOG_SEARCH`, valida que el
provider/model de la definición coincida con `wcs.ai`, aplica los límites de la
definición y recibe filtros normalizados más una referencia source-backed
acotada. Si falta el cliente, hay timeout, error, mismatch de modelo o límite,
la ejecución candidata falla y la respuesta activa no cambia. El primer smoke
debe usar un executor fake en tests; no se habilita Bedrock real en CI ni en
producción para validar este slice.

Rollback inmediato: `shadow-enabled=false`, `shadow-provider=noop` y
`activation-enabled=false`. No requiere migración, Terraform ni cambio de
secrets.
