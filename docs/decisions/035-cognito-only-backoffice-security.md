# ADR-035 — Cognito como único acceso del backoffice

## Estado

Aceptada para WCS-128.

## Contexto

El rollout de WCS-128 dejó coexistiendo tres beans de
`AgentEvaluationControlPlaneAuthorizer`: un authorizer JWT, un authorizer de
preview-token y un fallback deny-by-default. Además, una cadena y un filtro de
Spring Security podían autenticar el preview de forma independiente. Esa
composición hacía que el arranque dependiera de condiciones de configuración y
podía provocar más de un bean candidato en App Runner.

## Decisión

El backoffice y su control plane usan únicamente el flujo:

`login Cognito -> cookies HttpOnly -> JWT validado -> scopes/capabilities`.

La composición registra exactamente un `AgentEvaluationControlPlaneAuthorizer`,
`JwtAgentEvaluationControlPlaneAuthorizer`. Se eliminan el authorizer de
preview-token, su filtro, su cadena de seguridad y el bypass local. La cadena
JWT sigue condicionada por `wcs.agent-evaluation.control-plane.security.enabled`
para permitir un arranque seguro con el control plane cerrado, pero cuando se
habilita no existe otro authorizer alternativo.

La API mantiene el resolver de cookie HttpOnly y el soporte de header Bearer
para pruebas técnicas con un JWT válido; ese header no es un mecanismo de
preview ni acepta tokens estáticos.

## Contrato de seguridad

- Sin JWT válido: `401 Unauthorized`.
- JWT válido sin el scope/capability requerido: `403 Forbidden`.
- JWT válido con subject, issuer, audience y scope correctos: la solicitud llega
  al handler autorizado.
- Webhooks y health permanecen fuera de la frontera interna.
- No se guardan ni se registran tokens, contraseñas, prompts o conversaciones.

## Migración y rollback

1. Desplegar el backend corregido manteniendo AppConfig en la versión 13.
2. Verificar arranque y smoke `401/403/200` con un JWT de Cognito.
3. Publicar la nueva versión de AppConfig con Cognito habilitado.
4. Reiniciar App Runner y repetir login, `/internal/auth/me` y lecturas del
   backoffice.

El secreto histórico `wcs/{environment}/backoffice` puede permanecer
provisionalmente en Terraform para evitar una destrucción accidental. La
aplicación ya no lo lee y su referencia fue retirada de AppConfig; eliminarlo
requiere un cambio de infraestructura separado con revisión explícita del
plan.
