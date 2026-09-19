# ADR-041 — Depuración legacy y activación controlada

**Estado:** Accepted for local implementation  
**Fecha:** 2026-09-19  
**Jira:** WCS-139

## Contexto

WCS llegó a la fase de cierre con varias capas que fueron agregándose en
iteraciones: routing conversacional, registry de agentes, flags dinámicos,
shadow y adapters mock. El riesgo no es sólo tener clases sin uso: también es
que dos componentes mantengan reglas distintas para decidir si un agente y una
tool pueden ejecutarse.

## Decisión local

El resolver de runtime es la única frontera que valida el límite del
especialista antes de ejecutar un plan. `ConversationOrchestrator` conserva la
orquestación, pero ya no crea ni consulta un registry paralelo. La validación
rechaza el plan y vuelve al fallback seguro si el owner, caso de uso, tool o
schema no coincide con la definición publicada.

La activación continúa cerrada por defecto y separada en dos niveles:

1. `wcs.agent-runtime.activation-enabled` es el gate global de configuración.
2. `FeatureFlagRuntimeService` evalúa flags dinámicos por ambiente, canal, caso
   de uso y agente, incluyendo kill switch.

No se cambia todavía la configuración remota ni se habilita tráfico candidato.

## Inventario y disposición

| Componente | Estado | Disposición |
| --- | --- | --- |
| `ConversationRoutingService` y `ConversationIntentClassifier` | Ruta activa | Mantener; son la frontera única de interpretación. |
| `ConversationExecutionPlanFactory` | Ruta activa | Mantener; produce planes acotados y fallback seguro. |
| `AgentRuntimeDefinitionResolver` | Ruta activa | Mantener y centralizar la validación pre-ejecución. |
| `AgentSpecialistRegistry` | Fuente activa | Mantener una instancia Spring; no crear registries paralelos en la orquestación. |
| `FeatureFlagRuntimeService` | Ruta activa | Mantener; refresca flags sin reiniciar y conserva rollback local. |
| `NoOpAgentShadowExecutor` | Fallback intencional | Mantener; es el estado seguro cuando shadow está apagado. |
| `MockLlmClient`, `MockKnowledgeRetriever`, adapters mock | Fallback de local/test | Mantener; permiten pruebas sin credenciales y no son rutas productivas. |
| Constructores de compatibilidad para tests | Legacy controlado | Mantener temporalmente; retirar sólo después de migrar tests al wiring único. |
| `preview-token` y autorización legacy | Fuera de la ruta objetivo | No reintroducir; Cognito/JWT es la única autorización del backoffice. |

## Reglas para la siguiente limpieza

- No eliminar un adapter mock/no-op sólo porque no se usa en producción: es
  parte del fallback y del aislamiento de tests.
- No eliminar constructores de compatibilidad en el mismo cambio que la
  activación remota; primero migrar tests y medir cobertura.
- Toda eliminación debe ser un commit reversible y acompañarse de una prueba
  que demuestre el fallback equivalente.
- La comparación shadow no puede publicar respuestas ni ejecutar operaciones
  mutantes.
- La promoción remota requiere evidencia separada de backend, AppConfig,
  restart y smoke post-deploy.

## Alcance de esta iteración

Este ADR sólo cubre la consolidación de la frontera de validación, el inventario
y sus pruebas locales. También verifica que la activación dinámica pueda
aplicar el kill switch en las dimensiones exactas de ambiente, canal, caso de
uso, agente y versión, y que Spring exponga un único registry especialista. No
agrega migraciones, recursos Terraform, cambios de AppConfig remoto ni
despliegues.
