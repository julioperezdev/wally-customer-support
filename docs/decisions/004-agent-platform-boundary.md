# ADR-004 — Frontera de la plataforma de agentes

- Owner: Product/Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-07
- Related Jira: `WCS-45`
- Related document: [`agent-platform-roadmap.md`](../agent-platform-roadmap.md)
- Rollback reference: [`wcs-baseline-2026-09-07`](../baselines/wcs-baseline-2026-09-07.md)

## Contexto

WCS ya tiene canales, casos de uso, PostgreSQL, Bedrock, Knowledge Base,
memoria conversacional y observabilidad. El siguiente objetivo es poder crear
agentes especialistas, medirlos y cambiar sus versiones sin acoplar el dominio
a un proveedor o permitir que un modelo controle directamente los datos de
negocio.

## Decisión propuesta

1. El runtime tendrá un orquestador acotado y agentes especialistas con
   contratos de entrada y salida.
2. Las operaciones reales se ejecutarán mediante tools WCS determinísticas,
   con allowlist, validación, ownership, timeout y límites.
3. PostgreSQL será autoridad para datos transaccionales; Knowledge Base será
   autoridad para documentación editorial; la memoria no reemplazará ninguna
   de las dos.
4. El humanizador sólo podrá transformar hechos validados en una respuesta de
   canal. No podrá introducir hechos nuevos.
5. Las versiones de agentes serán inmutables, evaluables y activables mediante
   referencias auditables y feature flags.
6. AppConfig administrará punteros y límites operativos; Secrets Manager
   conservará secretos; ninguno será el registry completo de prompts y agentes.
7. MCP PostgreSQL quedará fuera del runtime productivo inicial y, si se evalúa,
   será read-only, allowlisted, auditado y aislado para desarrollo o backoffice.
8. LangChain y LangGraph no serán dependencias del backend. Sus patrones se
   podrán estudiar, pero los contratos serán propios de WCS.

## Alternativas descartadas por ahora

- **Un único prompt que conoce todo:** dificulta permisos, evaluación,
  trazabilidad y control de datos.
- **Agentes autónomos sin plan:** aumenta costo, latencia y superficie de
  errores sin aportar un límite operativo aceptable para el MVP.
- **LLM que genera SQL:** puede filtrar datos, producir consultas incorrectas y
  romper la frontera entre interpretación y negocio.
- **MCP directo a la base productiva:** no es necesario para los casos core y
  tiene un riesgo superior al de tools tipadas.
- **Prompts mutables sólo en AppConfig:** dificulta revisión, auditoría,
  evaluación y rollback de artefactos complejos.

## Consecuencias

### Positivas

- se puede comparar una versión de agente completa, no sólo un modelo;
- se conserva una frontera clara entre IA y reglas de negocio;
- se puede revertir por configuración antes de restaurar código;
- los canales permanecen independientes del runtime conversacional;
- las pruebas pueden usar contratos y datasets reproducibles.

### Costos y riesgos

- se debe construir un registry y un proceso de promoción;
- el backoffice agrega superficie de seguridad y permisos;
- la evaluación requiere datasets y criterios de calidad mantenidos;
- un diseño multiagente puede aumentar latencia y costo si no se limita;
- el registry debe evitar divergencia entre Git, base y configuración activa.

## Criterios de aceptación

- la propuesta de plataforma está aceptada en Confluence;
- las issues hijas definen contratos antes de implementar el runtime;
- existe un dataset mínimo para catálogo, Knowledge Base, fallback y
  humanización;
- se puede activar y revertir una versión sin modificar código;
- el acceso de agentes a datos dinámicos sigue siendo determinístico;
- el baseline actual continúa restaurable.
