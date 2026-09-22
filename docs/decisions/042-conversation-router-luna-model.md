# ADR-042 — Selección de modelo dedicado para el router conversacional

Estado: `Superseded` (Luna diferido; acceso no disponible)
Fecha: 2026-09-21
Relacionado: `WCS-136`, `WCS-130`, `WCS-131`, [ADR-037](037-conversation-router-evaluation.md)

## Contexto

El modelo configurado en `wcs.ai.model` es compartido por distintas etapas.
Cambiarlo globalmente no permite saber si una mejora observada pertenece al
routing o a la generación de respuestas, y además cambia su costo y latencia.
El router necesita interpretar mensajes cortos, coloquiales e incompletos,
manteniendo el contrato estructurado y la validación determinística de WCS.

## Decisión originalmente propuesta

Agregar un perfil de modelo independiente para el router:

| Propiedad | Candidata |
| --- | --- |
| Agente | `conversation-router` |
| Versión propuesta | `conversation-router-v2` |
| Prompt | `conversation-intent-v4` |
| Modelo Bedrock propuesto | `us.openai.gpt-5.6-luna` |
| Endpoint | `bedrock-runtime` mediante Converse |
| Precio corto | USD 0.22 entrada / USD 1.32 salida por millón de tokens |

La generación de respuestas conserva `wcs.ai.model`. El adapter usa client-side
tool use cuando la feature está habilitada; el resultado sigue pasando por el
parser, la allowlist y la reconciliación WCS. El modelo no ejecuta herramientas,
SQL ni operaciones transaccionales por sí mismo.

## Configuración candidata de este cambio

GPT-5.6 Luna no está habilitado para la cuenta y no debe invocarse. La
configuración candidata del router usa `openai.gpt-oss-20b-1:0`, igual que el
modelo ya autorizado, con `reasoning_effort=medium` enviado como
`additionalModelRequestFields` de Converse. La versión es
`conversation-router-v3`, definida en `application.properties`; el costo se
calcula con el pricing OSS estándar ya registrado. El esfuerzo `medium` es
exclusivo del router y queda en los eventos `AI_USAGE_RECORDED` como
`reasoningEffort`. El límite de salida candidato es 2048 tokens, por debajo del
máximo de salida de 16K documentado por AWS para GPT-OSS 20B; la respuesta al
usuario conserva su presupuesto independiente.

AWS documenta para GPT-OSS los niveles de esfuerzo `low`, `medium` y `high`, y
que el campo de modelo `reasoning_effort` se envía a Converse mediante
`additionalModelRequestFields` ([parámetros GPT-OSS en Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-openai.html), [guía AWS sobre razonamiento GPT-OSS](https://aws.amazon.com/blogs/machine-learning/run-nvidia-nemotron-and-openai-gpt-oss-models-on-amazon-bedrock-in-aws-govcloud-us/)).
`medium` se propone como equilibrio inicial frente a `high`; cualquier mejora de calidad debe
medirse con la evaluación offline y el mismo dataset del baseline.

En esta etapa la configuración del candidato vive en el
`application.properties` versionado. No se agregan claves específicas del
router a AppConfig: AppConfig continúa reservado para flags y configuración
operativa. Cuando el Agent Registry esté habilitado, estos valores pasarán a
la versión persistida de `conversation-router` y su activación.
Los eventos de uso incluyen la versión del router, modelo, pricing, tokens,
costo y latencia.

## Rollout y rollback

1. Ejecutar tests locales y la evaluación offline con el mismo dataset que el
   baseline.
2. Verificar que el role de App Runner permite el model ARN de GPT-OSS.
3. Comparar `conversation-router-v3` contra el baseline por intención,
   entidades, fallback, latencia, tokens y costo.
4. Si aparece una regresión, volver mediante una nueva configuración de código
   hasta que el Agent Registry permita un rollback operativo.

No se cambia el schema de PostgreSQL ni se agrega una migración Flyway.

## Consecuencias

El router conserva selección, trazabilidad y pricing separados del modelo de
respuesta. No se requiere actualizar AppConfig para cambiar el router. Se
retiraron los ARNs de Luna del allowlist Terraform de producción; la política
permite solamente GPT-OSS hasta que se apruebe evaluar Luna posteriormente.
