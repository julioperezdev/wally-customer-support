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

Para verificar que la idempotencia del control plane guarda sólo hashes:

    SELECT count(*) AS claims,
           min(length(key_hash)) AS min_hash_length,
           max(length(key_hash)) AS max_hash_length
    FROM wcs.agent_activation_command_claims;

No se debe consultar ni exportar la key cruda: la tabla sólo contiene su
hash SHA-256 y timestamp de claim.

## SQL — tareas humanas abiertas

La consulta devuelve sólo metadata operativa para el backoffice y no incluye
contenido del cliente ni identificadores externos:

```sql
SELECT priority,
       status,
       reason,
       COUNT(*) AS open_tasks
FROM wcs.human_follow_up_tasks
WHERE status IN ('OPEN', 'IN_PROGRESS')
GROUP BY priority, status, reason
ORDER BY priority, status, reason;
```

Para auditar una supresión sin revelar el actor se puede contar por estado:

```sql
SELECT status, reason, COUNT(*) AS contacts
FROM wcs.contact_suppressions
GROUP BY status, reason
ORDER BY status, reason;
```

## SQL — pedidos por estado y proveedor

Consulta agregada para el seguimiento operativo. No devuelve referencias del
cliente, keys de idempotencia ni links de pago:

```sql
SELECT status,
       payment_provider,
       COUNT(*) AS orders,
       SUM(total) AS amount
FROM wcs.orders
WHERE created_at >= :since
GROUP BY status, payment_provider
ORDER BY status, payment_provider;
```

Para detectar pedidos que necesitan reintento del link, sin inspeccionar el
contenido del pedido:

```sql
SELECT status,
       payment_provider,
       COUNT(*) AS pending_without_link
FROM wcs.orders
WHERE status = 'PENDING_PAYMENT'
  AND payment_url IS NULL
  AND created_at >= :since
GROUP BY status, payment_provider;
```

Para medir reintentos y eventos ignorados del webhook:

```sql
SELECT event_type,
       payment_status,
       COUNT(*) AS events
FROM wcs.payment_events
WHERE created_at >= :since
GROUP BY event_type, payment_status
ORDER BY events DESC;
```
