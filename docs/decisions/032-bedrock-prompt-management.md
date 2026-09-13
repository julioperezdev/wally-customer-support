# ADR-032 — Prompts externos con Bedrock Prompt Management

- Owner: Product/Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-12
- Related Jira: `WCS-45`
- Related documents: [`ai.md`](../ai.md), [`agent-platform-delivery-plan.md`](../agent-platform-delivery-plan.md)

## Contexto

La plataforma necesita evolucionar los prompts de clasificación y respuesta sin
recompilar el backend por cada nueva versión, manteniendo revisión, rollback,
trazabilidad y límites de seguridad. El cuerpo del prompt no debe vivir en
AppConfig ni ser enviado desde un request del usuario.

## Decisión

1. Bedrock Prompt Management será el proveedor productivo opcional del
   `PromptRegistry` de WCS.
2. Cada prompt se resolverá mediante un identificador y una versión numérica
   inmutable. No se aceptará `DRAFT`, texto arbitrario ni una versión elegida
   por el usuario final.
3. El runtime leerá el prompt con `bedrock:GetPrompt` al iniciar el componente
   Bedrock, extraerá únicamente la variante de texto aprobada y calculará un
   hash SHA-256 para la metadata operativa.
4. AppConfig conservará el selector de proveedor, los identificadores y las
   versiones; Secrets Manager no participa porque estas referencias no son
   secretos.
5. `ClasspathPromptRegistry` permanece como proveedor compatible y rollback
   seguro. Es el default actual; `wcs.ai.prompt.provider=bedrock` habilita el
   proveedor administrado.
6. PostgreSQL seguirá conservando metadata del registry, activaciones,
   evaluaciones y hashes cuando corresponda, pero no será el repositorio del
   cuerpo del prompt.
7. El role de App Runner recibirá `bedrock:GetPrompt` sólo para los ARNs
   explícitamente allowlisted en Terraform. Una lista vacía no concede ese
   permiso.

## Contrato de configuración

Con `classpath` no se requieren referencias de Bedrock:

```properties
wcs.ai.prompt.provider=classpath
```

Para habilitar Bedrock Prompt Management se deben publicar previamente ambos
prompts y configurar referencias reales:

```properties
wcs.ai.prompt.provider=bedrock
wcs.ai.prompt.management.intent-identifier=arn:aws:bedrock:REGION:ACCOUNT:prompt/PROMPT_ID
wcs.ai.prompt.management.intent-version=1
wcs.ai.prompt.management.response-identifier=arn:aws:bedrock:REGION:ACCOUNT:prompt/PROMPT_ID
wcs.ai.prompt.management.response-version=1
```

Los valores son ejemplos de forma, no valores para copiar a producción. Las
versiones deben ser numéricas, inmutables y coincidir con los recursos
publicados. El prompt debe contener una variante de texto; las variantes
estructuradas o multi-mensaje se incorporarán en un contrato posterior.

## Seguridad y fallos

- El contenido, el mensaje del cliente y la respuesta del modelo nunca se
  registran en logs operativos.
- Si falta una referencia, la versión no es válida, no existe la variante o
  falla `GetPrompt`, el componente falla cerrado durante el arranque.
- El rollback operativo consiste en volver a `classpath` o seleccionar la
  versión anterior aprobada, sin cambiar el contrato del orquestador.
- La respuesta continúa limitada por los guardrails de WCS; Prompt Management
  no puede habilitar SQL, tools ni acciones sensibles.

## Consecuencias

Se obtiene un ciclo de edición y versionado administrable desde AWS, con
auditoría y cambio de versión sin rebuild. A cambio, el despliegue productivo
requiere permisos IAM, referencias válidas y una prueba de smoke de ambos
prompts. S3 queda reservado para documentos, datasets y evidencia; no es la
fuente canónica de prompts ejecutables en esta fase.
