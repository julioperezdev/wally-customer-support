# ADR-015 — Runner de evaluación de agentes

- Estado: Accepted for WCS-57
- Fecha: 2026-09-07
- Alcance: ejecución local/interna de suites sintéticas versionadas

## Decisión

WCS agrega `AgentEvaluationRunner` como una pieza de aplicación que recibe una
lista de `AgentEvaluationScenario` y un `AgentEvaluationExecutor` inyectable.
Ordena los escenarios por identificador, valida que pertenezcan a una única
versión de dataset y delega cada resultado al `ResponsePolicyEvaluator`.

El runner devuelve `AgentEvaluationSuiteResult` con los resultados sanitizados
por escenario, total, aprobados, fallidos, pass rate, score promedio y razones
agrupadas. No conserva el texto de la respuesta, no persiste ejecuciones y no
invoca Bedrock.

## Límites

- La suite debe ser no vacía y no puede contener IDs duplicados.
- Todos los escenarios de una ejecución deben compartir `datasetVersion`.
- El ejecutor futuro puede envolver un agente o modelo, pero debe devolver el
  contrato `ResponseHumanizationResult`.
- El runner no es todavía una API, un job productivo ni un mecanismo de
  promoción.

## Evolución

Una fase posterior podrá agregar metadata separada de proveedor, modelo,
latencia, tokens y costo, junto con persistencia o exportación de métricas. Esa
extensión no debe introducir conversaciones completas, PII ni alterar la
autoridad de los resultados estructurados de WCS.
