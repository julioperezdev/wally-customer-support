# ADR-030 — Rollout shadow controlado por ambiente y porcentaje

## Estado

Aceptado para WCS-109, WCS-110 y WCS-111. La protección queda desplegada, pero
el tráfico continúa en cero por defecto.

## Contexto

La flag `shadow-enabled` por sí sola no es una barrera suficiente: una
configuración incorrecta en `prod` podría iniciar llamadas del candidato y
generar costo sin publicar la respuesta. Además, el rollout necesita comenzar
con una muestra controlada y aumentar el porcentaje sólo con evidencia.

## Decisión

El runtime exige dos condiciones adicionales antes de invocar el executor:

1. el ambiente efectivo pertenece a `shadow-allowed-environments`;
2. `shadow-traffic-percentage` selecciona determinísticamente la conversación
   pseudonimizada.

Los defaults seguros son:

```properties
wcs.agent-runtime.shadow-allowed-environments=test
wcs.agent-runtime.shadow-traffic-percentage=0
```

Un valor inválido de porcentaje vuelve a cero. Un ambiente no permitido nunca
invoca el executor, aunque `shadow-enabled=true` y el provider sea Bedrock.
El porcentaje se calcula sobre el bucket estable existente; no depende de
datos del usuario ni se puede modificar desde el webhook.

## Autoridad y privacidad

El candidato sigue sin autoridad para publicar mensajes, escribir outbox,
modificar el registry o generar SQL. Los skips emiten sólo ambiente, razón y
porcentaje; no contienen conversaciones, teléfonos, prompts, respuestas ni
secretos.

## Rollout aprobado

El orden operativo es `0%` → smoke sintético → observar scorecard y Grafana →
porcentaje explícito en `test` → revisión humana. No se habilita `prod` como
parte de esta decisión.

## Rollback

Publicar `shadow-enabled=false`, `shadow-provider=noop` y
`shadow-traffic-percentage=0`. No requiere migración, Terraform ni reinicio si
la configuración no se carga durante el bootstrap.
