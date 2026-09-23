# ADR-044 — Memoria estructurada y contexto mínimo por agente

- Owner: Tech Lead
- Status: `Accepted`
- Last reviewed: 2026-09-23
- Related Jira: `WCS-141`
- Related documents: [`architecture.md`](../architecture.md), [`ai.md`](../ai.md), [`data-model.md`](../data-model.md), [`privacy-retention.md`](../privacy-retention.md)

## Contexto

El router necesita entender referencias humanas entre turnos como “soy M” y
luego “quiero un buzo”. Enviar el transcript completo a cada agente mezcla
responsabilidades, aumenta ruido y expone más contexto del necesario. La tabla
`wcs.customer_preferences` ya guarda claves tipadas con ownership, TTL y
confirmación, pero el contrato inicial sólo usaba color.

## Decisión

1. Se reutiliza `wcs.customer_preferences` y su migración V8. La memoria durable
   sólo contiene preferencias de bajo riesgo permitidas: `preferred_color` y
   `preferred_size`; no se persiste un blob JSON ni se agrega una migración.
2. La captura es determinística y explícita. “Soy M”/“uso talle M” puede guardar
   talle M; un filtro mencionado dentro de una búsqueda es temporal. Valores
   fuera de allowlist no se guardan.
3. Un olvido dirigido elimina sólo la preferencia nombrada. Una referencia
   ambigua como “no quiero eso” requiere aclaración y no muta preferencias,
   carrito ni pedido.
4. El router recibe un JSON efímero, acotado y sin identificadores externos:
   ventana/resumen permitido, preferencias explícitas y selección activa. La
   lógica determinística de WCS decide precedencia y aplicación.
5. Los filtros expresos del turno actual tienen precedencia. Una preferencia
   puede completar sólo una búsqueda específica incompleta; nunca restringe
   una lista general ni se aplica a mutaciones de carrito/checkout.
6. `catalog-specialist` recibe `CatalogQuery` normalizado y el último turno
   para resolver lenguaje/clarificación; no recibe el historial completo ni
   la memoria durable.
7. `response-generation` recibe sólo mensaje/historial/resumen acotados y
   conocimiento aprobado. Placeholders legacy para selección y preferencias
   se renderizan vacíos para no romper versiones publicadas.
8. `response-humanization` conserva su frontera: únicamente hechos
   estructurados validados; nunca recibe memoria o transcript.
9. No se guardan stock, precio, carrito, pedidos, respuestas del modelo ni
   hechos transaccionales como preferencias. PostgreSQL/servicios de dominio
   siguen siendo fuente de verdad.

## Consecuencias

- Una preferencia explícita puede completar búsquedas en mensajes posteriores
  sin depender de que el router recupere de nuevo el texto original.
- El estado es interpretable, borrable y evaluable por clave; no crece como un
  transcript o JSON libre.
- Cada agente recibe menos ruido y un contrato auditable de datos de entrada.
- Los flujos de stock, precio, carrito, pagos, schema y configuración AWS no
  cambian.
- La activación sigue controlada por `wcs.conversation.preferences.enabled`;
  esta decisión no publica ni cambia valores de AppConfig.

## Validación

La suite comprueba captura multi-turn, precedencia, lista general, carrito,
borrado por clave, ambigüedad, ownership en PostgreSQL/Testcontainers y ausencia
de memoria tipada en el prompt de respuesta general. Véanse `TC-071` a
`TC-074` en [`testing-strategy.md`](../testing-strategy.md).
