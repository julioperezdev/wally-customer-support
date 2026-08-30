# Contribuir a WCS

## Flujo obligatorio

1. Cada cambio parte de una issue Jira `WCS-*`.
2. La issue debe estar en `Ready` antes de empezar.
3. Crear una branch desde la rama principal.
4. Mover la issue a `In Progress`.
5. Implementar sólo el alcance acordado y actualizar documentación afectada.
6. Ejecutar las pruebas declaradas en la issue.
7. Abrir PR y mover la issue a `In Review`.
8. Verificar CI y, cuando corresponda, deploy y smoke.
9. Adjuntar evidencia en Jira y mover a `Done` sólo después de la verificación.

## Convención de branches

```text
feature/WCS-123-descripcion-corta
fix/WCS-123-descripcion-corta
docs/WCS-123-descripcion-corta
chore/WCS-123-descripcion-corta
```

## PR mínimo

```markdown
## Objetivo

## Cambios

## Fuera de alcance

## Pruebas

## Documentación actualizada

## Riesgos, rollout y rollback

## Evidencia
```

No incluir secretos, conversaciones reales, números de teléfono completos ni logs con PII.
