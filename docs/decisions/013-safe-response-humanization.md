# ADR-013 — Humanización segura sobre hechos verificados

- Estado: Accepted for WCS-55
- Fecha: 2026-09-07
- Alcance: primera política de presentación del catálogo

## Contexto

WCS-54 separó los hechos del catálogo (`CatalogFact`) y el resultado de la
búsqueda (`CatalogSearchResult`) del texto que se envía al cliente. La
plataforma necesita una frontera estable para poder reemplazar la presentación
determinística por un humanizador Bedrock en el futuro sin permitir que un
modelo agregue datos de negocio.

## Decisión

Se define `ResponseHumanizer` como contrato de aplicación. Su entrada es un
`ResponseHumanizationRequest` con el caso de uso, canal y resultado estructurado;
su salida es `ResponseHumanizationResult` con el texto, la política y su
versión, además del resultado operativo.

La implementación actual es `DeterministicResponseHumanizer` (`v1`). Reutiliza
el renderer de catálogo existente y sólo puede presentar los hechos que ya
fueron validados por el caso de uso. La política no recibe SQL, secretos,
prompts, mensajes completos ni acceso a repositorios.

Si la entrada es inválida o falla el render, devuelve un fallback seguro y
emite un evento sanitizado. WhatsApp y Telegram comparten la política; el
contrato de canal queda disponible para futuras restricciones de formato.

## Límites

- El humanizador no es autoridad para precio, stock, SKU, talle, color,
  horarios, políticas, carrito ni pedidos.
- No se ejecuta Bedrock en esta entrega.
- No se cambia AppConfig, Secrets Manager, Terraform ni la activación
  productiva.
- No se registran textos de respuesta, prompts ni PII en el evento.

## Observabilidad

La política emite `RESPONSE_POLICY_APPLIED` cuando presenta un resultado y
`RESPONSE_POLICY_FALLBACK` cuando necesita el fallback. Ambos eventos incluyen
`useCase`, `channel`, `policyId`, `policyVersion`, `resultStatus`,
`resultCount`, `outcome`, motivo de fallback cuando aplica y duración.

## Evolución

Una futura implementación Bedrock podrá cumplir el mismo contrato, pero deberá
recibir hechos estructurados, validar que su texto no agregue afirmaciones y
mantener el fallback determinístico. Su activación requerirá una versión de
agente evaluada y un feature flag con rollback.
