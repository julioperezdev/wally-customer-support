# ADR-041 — Depuración legacy y activación controlada

**Estado:** Implemented locally; awaiting integration and promotion
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

## Smoke local y rollback

El smoke local agrupado se ejecuta con:

```bash
./scripts/smoke-agent-runtime-local.sh
```

La suite verifica la activación autorizada, el kill switch, la validación del
especialista, la restauración de la decisión anterior de feature flag y la
persistencia de una activación de rollback en PostgreSQL/Testcontainers. El
rollback se representa como una nueva referencia inmutable: no se edita la
fila anterior ni se elimina historial.

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
| Constructores de compatibilidad de `AgentActivationResolver`, `AgentRuntimeDefinitionResolver` y `CatalogSpecialistExecutor` | Legacy sólo de tests | Eliminados; los tests usan el wiring explícito actual y Spring mantiene un único camino productivo. |
| Constructores sobrecargados de `ConversationOrchestrator` | Compatibilidad de tests | Mantener temporalmente; todavía son usados por una suite amplia y no duplican una decisión de runtime. |
| `CatalogConversationService.replyFor*` | Ruta activa de composición catálogo+envíos | Mantener; todavía se usa para construir respuestas compuestas desde hechos estructurados. |
| `CartConversationHandler` parser textual y default command bridge | Fallback/ruta activa | Mantener; la orquestación actual aún lo utiliza para comandos y garantiza fallback seguro. |
| `preview-token` y autorización legacy | Fuera de la ruta objetivo | No reintroducir; Cognito/JWT es la única autorización del backoffice. |

## Reglas para la siguiente limpieza

- No eliminar un adapter mock/no-op sólo porque no se usa en producción: es
  parte del fallback y del aislamiento de tests.
- No eliminar adapters mock/no-op ni los bridges de catálogo/carrito mientras
  sigan siendo rutas activas o fallback.
- Los constructores retirados en esta iteración no tenían referencias
  productivas; la suite fue migrada al wiring explícito antes de eliminarlos.
- Toda eliminación debe ser un commit reversible y acompañarse de una prueba
  que demuestre el fallback equivalente.
- La comparación shadow no puede publicar respuestas ni ejecutar operaciones
  mutantes.
- La promoción remota requiere evidencia separada de backend, AppConfig,
  restart y smoke post-deploy.

## Alcance de esta iteración

Este ADR cubre la consolidación de la frontera de validación, el inventario,
la eliminación de compatibilidades sin referencias productivas y las pruebas
locales de activación. La matriz verifica el kill switch en ambiente, canal,
caso de uso, agente y versión; el rollback restaura sólo la versión efectiva
anterior y no afecta dimensiones fuera de alcance. Spring expone un único
registry especialista. No agrega migraciones, recursos Terraform, cambios de
AppConfig remoto ni despliegues.

## Evidencia de cierre local

El gate reproducible queda disponible en:

```bash
./scripts/gate-final-local.sh
```

El 2026-09-19 se ejecutó completo con este resultado:

| Control | Resultado | Evidencia |
| --- | --- | --- |
| `mvn -B verify` | PASS | 491 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`. |
| Testcontainers | PASS | PostgreSQL 16; las suites de integración aplicaron Flyway sobre una base limpia. |
| Smoke de runtime | PASS | Activación, kill switch por ambiente/canal/caso de uso/agente/versión, rollback, fallback seguro y shadow cerrado. |
| Secuencias conversacionales | PASS | 47 tests dirigidos: orquestador, integración Spring/Testcontainers y fixtures de intención. |
| Evaluación offline | PASS | 16 tests; dataset `catalog-response-v1`, 5 escenarios, pass rate 1.0 y scorecard base 1.0. |
| Dashboard y Compose | PASS | JSON de Grafana válido y `docker compose config --quiet` válido. |
| Migraciones | PASS | Archivos contiguos `V1..V25`; PostgreSQL de Testcontainers llegó a V25. |

La validación read-only adicional contra CloudWatch confirmó que las consultas
agregadas de IA y consultas aceptan la sintaxis y filtran eventos WCS. Se
corregió el sobre-escape de comillas en las expresiones de Logs Insights que
podía mostrar líneas de despliegue como si fueran eventos del dashboard. Los
paneles de shadow y scorecard sin ejecuciones recientes quedan correctamente
en cero, no se interpretan como error de consulta.

La evaluación con Bedrock real no forma parte del gate automático: no se
invocó, no generó costo y queda como smoke explícito posterior cuando se
quieran usar credenciales y presupuesto AWS. Los tests de contrato del adapter
Bedrock sí forman parte de `mvn -B verify`.

Los logs de la suite muestran warnings conocidos de Mockito/JDK y de cierre
de recursos de Testcontainers; no alteraron el código de salida ni el resultado
de las pruebas. No se registran conversaciones completas ni secretos. El smoke
cubre resolver, registry único, activación, kill switch por las cinco
dimensiones, rollback de flags, rollback inmutable del registry, shadow cerrado
y los adapters tipados de estado, carrito, checkout y handoff. La suite completa
queda verde antes de abrir el PR de integración. El paso remoto posterior
requiere revisar AppConfig, ejecutar rollout controlado y conservar el rollback
por configuración.
