# Reporte de piloto WCS — plantilla

Related Jira: `WCS-23`
Status: `Draft`
Owner:
Decision date:

## Identificación de la ejecución

| Campo | Valor |
| --- | --- |
| Ambiente AppConfig | `prod` / `test` |
| Canal | `telegram` / `whatsapp` |
| App Runner deployment/image SHA |  |
| AppConfig version |  |
| Inicio UTC |  |
| Fin UTC |  |
| Mensajes inbound |  |
| Conversaciones |  |
| Tester/negocio |  |

No completar este documento con tokens, teléfonos completos, chat IDs,
mensajes, prompts, respuestas completas ni otros datos personales.

## Resultado funcional

| Caso | Resultado (`pass`/`fail`/`n/a`) | Observación sanitizada |
| --- | --- | --- |
| P-01 saludo |  |  |
| P-02 productos |  |  |
| P-03 filtros |  |  |
| P-04 disponibilidad |  |  |
| P-05 horario/ubicación |  |  |
| P-06 políticas/envíos |  |  |
| P-07 ambigüedad |  |  |
| P-08 inexistente |  |  |
| P-09 handoff |  |  |
| P-10 duplicado |  |  |

## Scorecard

| Métrica | Valor | Objetivo | Estado |
| --- | ---: | ---: | --- |
| Consultas totales |  |  |  |
| `REPLIED` |  | ≥ 80% |  |
| `FALLBACK` + `LOW_CONFIDENCE` |  | ≤ 20% |  |
| `HANDOFF` |  | informar |  |
| p50 conversacional |  | informar |  |
| p95 conversacional |  | ≤ 8 s |  |
| Outbound `SENT` |  | 100% |  |
| Inbound fallidos |  | 0 críticos |  |
| Outbound fallidos |  | 0 críticos |  |
| Duplicados recibidos |  | 0 duplicados enviados |  |
| Tokens de entrada |  | informar |  |
| Tokens de salida |  | informar |  |
| Costo estimado USD |  | ≤ 5 |  |

## Incidentes y feedback

| Referencia | Severidad | Descripción sin PII | Acción | Estado |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

Feedback del tester/negocio:

```text
- Lo que funcionó:
- Lo que no se entendió:
- Respuestas incorrectas o incompletas:
- Casos que deberían incorporarse:
- Observaciones de tono:
```

## Decisión

Seleccionar una única opción:

- [ ] `CONTINUAR`: preparar el siguiente incremento con un issue nuevo.
- [ ] `AJUSTAR`: corregir hallazgos y repetir un piloto acotado.
- [ ] `DETENER`: rollback y no ampliar tráfico.

Justificación:

```text

```

Evidencia enlazada:

- Dashboard/export sanitizado:
- Consulta o período de CloudWatch:
- Run de CI/CD:
- Incidente/rollback:
