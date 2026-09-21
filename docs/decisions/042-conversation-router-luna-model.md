# ADR-042 — Modelo dedicado para el router conversacional

Estado: `Proposed`
Fecha: 2026-09-21
Relacionado: `WCS-136`, `WCS-130`, `WCS-131`, [ADR-037](037-conversation-router-evaluation.md)

## Contexto

El modelo configurado en `wcs.ai.model` es compartido por distintas etapas.
Cambiarlo globalmente no permite saber si una mejora observada pertenece al
routing o a la generación de respuestas, y además cambia su costo y latencia.
El router necesita interpretar mensajes cortos, coloquiales e incompletos,
manteniendo el contrato estructurado y la validación determinística de WCS.

## Decisión

Agregar un perfil de modelo independiente para el router:

| Propiedad | Candidata |
| --- | --- |
| Agente | `conversation-router` |
| Versión | `conversation-router-v2` |
| Prompt | `conversation-intent-v4` |
| Modelo Bedrock | `us.openai.gpt-5.6-luna` |
| Endpoint | `bedrock-runtime` mediante Converse |
| Precio corto | USD 0.22 entrada / USD 1.32 salida por millón de tokens |

La generación de respuestas conserva `wcs.ai.model`. El adapter usa client-side
tool use cuando la feature está habilitada; el resultado sigue pasando por el
parser, la allowlist y la reconciliación WCS. Luna no ejecuta herramientas,
SQL ni operaciones transaccionales por sí mismo.

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
2. Verificar que el role de App Runner permite el inference profile de Luna y
   los recursos de modelo requeridos por AWS.
3. Comparar `conversation-router-v2` contra el baseline por intención,
   entidades, fallback, latencia y costo.
4. Si aparece una regresión, volver mediante una nueva configuración de código
   hasta que el Agent Registry permita un rollback operativo.

No se cambia el schema de PostgreSQL ni se agrega una migración Flyway.

## Consecuencias

El cambio es atribuible, medible y reversible. Requiere permisos IAM antes del
smoke real, pero no una actualización de AppConfig para seleccionar el router.
AWS documenta que el identificador `us.openai.gpt-5.6-luna`
es un perfil cross-Region para `bedrock-runtime` y que las capacidades de
structured outputs y server-side tool use no están disponibles en ese
endpoint, por lo que WCS mantiene su validación de aplicación.
