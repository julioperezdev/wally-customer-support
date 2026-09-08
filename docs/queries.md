# Queries operativas — WCS

Las consultas reales deben vivir en Confluence y versionarse aquí cuando sean ejecutables. Todos los ejemplos deben usar ventanas acotadas, agregados y datos sanitizados.

Las consultas de Logs Insights para el dashboard local están en
[`../observability/grafana/queries/cloudwatch-logs-insights.md`](../observability/grafana/queries/cloudwatch-logs-insights.md).

## SQL — salud del procesamiento

```sql
SELECT processing_status, COUNT(*) AS messages
FROM message
WHERE created_at >= :since
GROUP BY processing_status
ORDER BY processing_status;
```

## SQL — fallos por código

```sql
SELECT error_code, COUNT(*) AS failures
FROM message
WHERE processing_status = 'FAILED'
  AND updated_at >= :since
GROUP BY error_code
ORDER BY failures DESC;
```

## CloudWatch Logs Insights — errores por servicio

```text
fields @timestamp, service, status, error_code, request_id
| filter level = "ERROR"
| filter ispresent(error_code)
| stats count() as failures by service, error_code
| sort failures desc
```

## CloudWatch Logs Insights — latencia agregada

```text
fields @timestamp, operation, duration_ms
| filter ispresent(duration_ms)
| stats pct(duration_ms, 50) as p50,
        pct(duration_ms, 95) as p95,
        count() as calls
  by operation
```

Antes de usar una query en producción documentar log group, ventana, permisos, campos disponibles, PII y limitaciones.

## SQL — baseline del registry de agentes

La consulta sólo devuelve metadata operativa y no incluye prompts, actores ni
secretos:

```sql
SELECT agent_id,
       agent_version,
       state,
       model_provider,
       model_id,
       system_prompt_version,
       evaluation_suite_version,
       created_at,
       approved_at
FROM wcs.agent_versions
WHERE agent_id = :agent_id
ORDER BY agent_version DESC;
```

Para validar el puntero de una activación sin exponer identidad del operador:

```sql
SELECT agent_id,
       agent_version,
       environment,
       channel,
       use_case,
       rollout_percentage,
       enabled,
       kill_switch,
       activated_at
FROM wcs.agent_activations
WHERE agent_id = :agent_id
ORDER BY activated_at DESC
LIMIT :limit;
```
