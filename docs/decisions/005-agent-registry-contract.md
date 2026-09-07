# ADR-005 — Contrato de registry y activación de agentes

- Owner: Product/Tech Lead
- Status: `Accepted`
- Related Jira: `WCS-47`
- Related roadmap: [`agent-platform-roadmap.md`](../agent-platform-roadmap.md)

## Contexto

La plataforma necesita comparar agentes y cambiar su comportamiento sin editar
el runtime ni mutar una versión que ya fue evaluada. El primer contrato debe
ser independiente de PostgreSQL, AppConfig, un backoffice o un proveedor de
modelos, para que esas implementaciones puedan cambiar sin alterar las reglas
de seguridad del dominio.

## Decisión

WCS modela una versión de agente como un artefacto inmutable. Contiene la
identidad y versión del agente, el modelo, parámetros acotados, versiones y
hash de prompts, schemas de entrada/salida, allowlists de tools y fuentes,
políticas de memoria/respuesta, límites operativos, presupuesto, fallback y
metadatos de evaluación/aprobación.

El contenido del prompt no forma parte del objeto ni de los logs operativos:
se identifica por versión y hash SHA-256. Los datos de negocio tampoco forman
parte de la definición del agente.

El ciclo de vida permitido es:

```text
DRAFT -> CANDIDATE -> EVALUATED -> APPROVED -> ACTIVE
                                             |       |
                                             v       v
                                        DEPRECATED  ROLLED_BACK
                                                       ^
                                                       |
                                            DEPRECATED -+
```

Una activación apunta a `agentId + agentVersion` y captura ambiente, canal,
caso de uso, motivo, porcentaje de rollout, actor, timestamp y versión anterior. Sólo
una versión `APPROVED` puede iniciar una activación. Una versión anterior
`ACTIVE` o `APPROVED` puede usarse como destino explícito de rollback, siempre
que coincida con la referencia guardada.

La activación incluye kill switch. El kill switch deshabilita el tráfico sin
alterar la versión publicada. Rollback crea una nueva referencia de activación
con la versión anterior y conserva la versión que fue reemplazada como
`previousVersion`.

## Límites del contrato v1

- máximo de 3 pasos por ejecución;
- timeout máximo de 60 segundos;
- máximo de 32.000 tokens de entrada y 32.000 de salida;
- rollout entre 0 y 100%;
- `temperature` entre 0 y 2 y `topP` mayor que 0 y hasta 1;
- presupuesto no negativo por ejecución;
- no contiene secretos, credenciales, prompts completos, conversaciones ni
  PII;
- no persiste aún en JPA/Flyway ni publica configuración en AppConfig;
- no permite que un LLM genere SQL ni habilita MCP en producción.

## Consecuencias

El contrato puede probarse sin AWS ni base de datos y permite que una futura
implementación elija PostgreSQL para el registry, Git para la promoción y
AppConfig para los punteros operativos. La persistencia, API de backoffice,
evaluación y feature flags quedan como tareas posteriores; ninguna de ellas
puede relajar las validaciones de este dominio.
