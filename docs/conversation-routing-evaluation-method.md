# Método de evaluación y evolución del router conversacional

Registro de las candidatas locales de `conversation-router` y guía para
evolucionarlas con evidencia reproducible. No se guardan aquí prompts completos
ni mensajes reales de clientes; los ejemplos de prueba son sintéticos.

## Aclaración sobre las versiones

| Identificador | Ejemplo | Significado |
| --- | --- | --- |
| Versión SQL del agente / SemVer | `agent_version=3`, `1.0.2` | Snapshot inmutable del agente |
| Versión del prompt | `conversation-routing-v3` | Revisión del contenido del prompt |
| Dataset | `conversation-routing-v2` | Corpus exacto y oráculos evaluados |
| Runtime router | `conversation-router-v3` | Perfil del adaptador; no es el número SQL |

Sólo se puede atribuir una mejora comparando el mismo agente lógico y dataset,
con igual modelo, parámetros y dimensiones. Si cambian a la vez prompt, modelo,
parser o dataset, el resultado no permite identificar la causa.

## ¿Incluimos lenguaje coloquial y dependiente del contexto?

Sí. El corpus contiene frases informales, incompletas, errores de escritura,
negaciones y seguimientos con historial:

| Clase | Ejemplos sintéticos | Regla que se prueba |
| --- | --- | --- |
| Consulta general coloquial | `¿Qué vendes?`, `Tenés ropa` | Listar catálogo sin interpretar la frase como nombre de producto |
| Interés vs. compra | `Quiero un buzo` / `Quiero comprar el buzo negro talle XL` | No iniciar checkout sólo por expresar interés |
| Necesidad semántica | `Necesito algo para el frío` | Inferir categoría apropiada sin inventar productos |
| Error tipográfico | `tenes buzo nullpinter negro?` | Normalizar una entidad probable sin ampliar filtros |
| Aporte de un solo dato | `Soy talle M` | Extraer talle, no inventar producto o categoría |
| Follow-up | Historial: `Busco una remera negra`; turno: `¿Qué opciones tienen?` | Aplicar filtros previos relevantes |
| Referencia y ambigüedad | `Quiero esa remera`, `quiero pagar esa` | Resolver con contexto o pedir dato faltante |
| Negación | `Me gusta esa, pero no la compro ahora` | No mutar carrito ni cobrar |
| Acción/cantidad | `Agregame dos remeras negras M al carrito`, `Sacá 2 remeras` | Clasificar operación y extraer cantidad |
| Fuera de catálogo/desconocido | `¿Venden zapatillas?`, `asdf qwerty` | No inventar coincidencias y usar fallback seguro |

El router sólo propone una decisión estructurada. Backend valida catálogo,
ownership, stock, confirmaciones y permisos; estos casos no prueban por sí solos
la calidad de la respuesta final ni de la ejecución comercial.

## Resultados registrados localmente

Las ejecuciones usaron Bedrock `openai.gpt-oss-20b-1:0`. V2–V4 usaron el mismo
dataset de 34 escenarios; V1 usó la suite histórica de 31.

| Agente SQL | Prompt | Dataset | Pass rate | Score medio | Observación |
| --- | --- | --- | ---: | ---: | --- |
| `1.0.0` (v1) | `conversation-intent-v4` | routing-v1 (31) | 21/31 = 67,74% | 89,25% | 9 fallos de entidades; 1 de intent |
| `1.0.1` (v2) | `conversation-intent-v4` | routing-v2 (34) | 30/34 = 88,24% | 96,57% | Oráculos completados y tres escenarios de cantidad |
| `1.0.2` (v3) | `conversation-routing-v3` | routing-v2 (34) | 33/34 = 97,06% | 99,02% | Regla intent/acción explícita; un desacuerdo de SKU por casing |
| `1.0.3` (v4) | `conversation-routing-v4` | routing-v2 (34) | 34/34 = 100% | 100% | Candidata local, no activa ni desplegada |

Los resultados provienen de ejecuciones locales, no de tráfico de producción.
Las versiones `1.0.1`–`1.0.3` son candidatas.

Los escenarios fallidos registrados fueron:

| Run | Escenarios y dimensión observada |
| --- | --- |
| v1 | `add_quantity_to_cart`, `catalog_context_refinement`, `catalog_reference_not_purchase`, `price_filter`, `remove_cart_line`, `size_context_refinement`, `sku_stock_query`, `typo_normalization`, `unsupported_catalog_category` (entidades); `view_cart` (intent) |
| v2 | `remove_cart_line`, `remove_two_cart_quantity`, `view_cart` (intent); `sku_stock_query` (entidad) |
| v3 | `sku_stock_query` (entidad; diferencia sólo de mayúsculas/minúsculas) |
| v4 | Sin escenarios fallidos en el run registrado |

**Pass rate** exige que todos los checks de un escenario coincidan: intent,
acción, entidades y cantidad cuando se evalúa. **Score medio** promedia checks
individuales; no significa que ese porcentaje de conversaciones haya sido
completamente correcto.

El salto v1→v2 no demuestra mejora del prompt: cambiaron los datasets. V2
completó oráculos de filtros y añadió tres casos de cantidad. La comparación
limpia v2→v3 sí usa los mismos 34 casos y subió de 30/34 a 33/34. El único
desacuerdo fue `sku_stock_query`: modelo devolvió el SKU en minúsculas y el
oráculo lo tenía en mayúsculas. El lookup del catálogo es case-insensitive. Se
ajustó el evaluador para tratar ese valor como equivalente y se agregó cobertura
para asegurar que un SKU distinto siga fallando. **No se reejecutó v3 con el
evaluador corregido**; no debe afirmarse que su pass rate histórico sea 100%.
V4 sí obtuvo 34/34 en su run registrado, pero eso sólo demuestra éxito en este
benchmark pequeño, no comprensión perfecta del lenguaje real.

## Qué causó el cambio v1→v3

No fue simplemente aumentar la cantidad de few-shot:

1. Se agruparon los 10 fallos de v1 por razón: nueve de extracción de entidades
   y uno de intent (`view_cart`).
2. Se fortalecieron los oráculos del dataset: filtros esperados explícitos y
   tres ejemplos de cantidad 1/2/3. Esto mejoró la medición; no el modelo por sí
   mismo.
3. Se definieron contrastes: interés vs. compra; seleccionar vs. mutar carrito;
   revisar vs. confirmar checkout; afirmación vs. negación; filtro parcial vs.
   consulta general; referencia a historial vs. referencia ambigua.
4. V3 agregó una regla explícita para mapear `action` al enum de `intent` y
   evitó combinar `UNKNOWN` con acciones reconocidas. También instruyó
   preservar el texto del SKU; la equivalencia de casing se resuelve en el
   evaluador/lookup según el contrato.
5. V2 y v3 se volvieron a ejecutar sobre el mismo fixture, proveedor, modelo y
   dimensiones, para poder atribuir el cambio.

Las etiquetas del dataset concretan los ejemplos incompletos/contextuales:
`size_only_is_not_product_name`,
`category_after_size_keeps_context`,
`generic_ropa_question_clears_previous_filters`,
`interest_with_deferral`, `ambiguous_payment_reference`, además de casos de
errores ortográficos y cantidad.

## Método para futuras versiones

1. Congelar baseline y registrar agente/SemVer, hash de prompt, modelo,
   temperatura, `topP`, reasoning effort, límites, pricing y dataset exacto.
2. Agrupar fallos por intent, acción, entidad, cantidad, contexto,
   negación/seguridad, schema o fallback; priorizar patrones y severidad, no
   anécdotas aisladas.
3. Escribir una hipótesis falsable por cambio. Ejemplo: “el router confunde
   selección con agregar al carrito; una selección no debe mutar, mientras que
   ‘agregala al carrito’ sí”.
4. Añadir pares contrastivos y holdouts: incluir la frase que enseña la regla y
   paráfrasis cercanas no copiadas al prompt. Así se prueba generalización, no
   memorización.
5. Crear nueva versión del dataset al añadir cobertura o cambiar oráculos;
   nunca editar un snapshot que ya participó en runs. No comparar porcentaje
   entre datasets diferentes como A/B.
6. Ejecutar primero validaciones determinísticas: schema, allowlists,
   coherencia intent/acción y guardrails. Mensajes ambiguos deben pedir
   aclaración, nunca autorizar pagos o mutaciones.
7. Cambiar una variable experimental por vez. Para probar prompt, mantener
   modelo y parámetros; para probar modelo, mantener prompt y dataset.
8. Comparar por escenario y dimensión, revisar cada regresión y observar
   pass-rate, score, tokens, costo y latencia. Repetir runs si el resultado es
   cercano al umbral o variable.
9. Gate sugerido: cero fallos en operaciones sensibles, cero regresiones del
   conjunto estable, al menos 95% de pass rate en una suite ampliada con
   holdouts y costo/latencia dentro de presupuesto. El 100% puede ser requisito
   del corpus curado, nunca una afirmación de exactitud universal.

## Privacidad y límites

Construir frases sintéticas a partir de categorías de fallos. No poner en
fixtures ni prompts del repo nombres, teléfonos, direcciones, pedidos o
identificadores de clientes. Si una observación real inspira una prueba,
parafrasearla hasta que no sea identificable y conservar sólo el
comportamiento/oráculo.

El prompt debe contener pocos ejemplos instructivos; el dataset de evaluación
debe ser más variado y contener holdouts no presentes en el prompt.

## Referencias

- [`docs/ai.md`](ai.md): registro de modelos y protocolo de evaluación.
- [ADR-027](decisions/027-bedrock-evaluation-executor-and-guardrails.md): límites y comparación del executor.
- [ADR-037](decisions/037-conversation-router-evaluation.md): prompt contrastivo.
- Fixtures: `src/main/resources/fixtures/conversation-routing-v1.json` y
  `conversation-routing-v2.json`.
