# ADR-037 — Prompt contrastivo y dataset de evaluación del router conversacional

Estado: `Accepted`
Fecha: 2026-09-17
Relacionado: `WCS-130`, `WCS-131`, [`ADR-036`](036-bedrock-structured-conversation-router.md)

## Contexto

El router estructurado ya convierte mensajes en decisiones allow-listed, pero
un modelo puede confundir el interés por una categoría con una operación de
compra. La diferencia es importante: `quiero un buzo` debe consultar catálogo,
mientras que `quiero comprar el buzo negro talle XL` puede iniciar checkout.
También se deben cubrir errores de escritura, referencias a turnos anteriores,
variaciones regionales y mensajes incompletos sin registrar conversaciones
reales en el repositorio.

## Decisión

Se adopta `conversation-intent-v4` como prompt empaquetado por defecto. La
versión contiene ejemplos few-shot agrupados y ejemplos contrastivos de los
casos que tienen acciones cercanas. Mantiene el mismo contrato JSON de v3 para
no acoplar el prompt con un caso de uso nuevo.

El dataset sintético
`src/test/resources/fixtures/conversation-intent-v4.json` es un contrato de
regresión, no un corpus de producción. Sus escenarios sólo contienen mensajes
genéricos, intención/acción esperada y filtros no sensibles. Los tests validan
que la salida esperada pertenece a las listas allow-listed y que existen casos
contrastivos mínimos.

Como defensa independiente del modelo, el orquestador reconstruye una consulta
de catálogo cuando el mensaje contiene filtros o una categoría y no contiene
marcadores explícitos de compra, incluso si Bedrock devolvió
`PURCHASE_LINK`. Esto no se aplica a `comprar`, `pagar`, `confirmar`, `link de
pago` u otras instrucciones operativas; esos mensajes siguen el flujo de
checkout y sus validaciones de stock, ownership e idempotencia.

## Alternativas descartadas

- Permitir que el modelo ejecute directamente SQL o herramientas: aumenta la
  superficie de seguridad y rompe la fuente de verdad transaccional.
- Resolver todos los mensajes con regex: no cubre contexto, errores ni
  variaciones lingüísticas y desplaza demasiada lógica al parser.
- Cambiar de modelo sin dataset: no permite atribuir mejoras ni detectar
  regresiones.
- Persistir el prompt en tablas de negocio: mezcla authoring con runtime y
  dificulta el rollback del artefacto; el registry empaquetado/administrado
  conserva versiones inmutables y hashes trazables.

## Rollout y evaluación

1. Ejecutar tests locales sin AWS.
2. Publicar el artefacto con v4 manteniendo el proveedor y el contrato actual.
3. Probar en Telegram frases de categoría, compra explícita, carrito,
   seguimiento, política y handoff.
4. Comparar en Grafana intent, action, resultado, latencia, tokens y costo; no
   se requiere guardar el texto del cliente.
5. Si hay regresión, volver a `conversation-intent-v3` mediante AppConfig o
   la configuración equivalente, sin modificar código de catálogo o pagos.

## Consecuencias

La calidad conversacional queda medible y el prompt puede evolucionar sin
alterar los servicios de negocio. El dataset no reemplaza una evaluación real
de Bedrock: los smoke y métricas productivas siguen siendo necesarios, y los
ejemplos deben ampliarse cuando aparezcan nuevos casos de uso.
