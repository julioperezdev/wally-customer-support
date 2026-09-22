-- Improve the humanizer prompt without weakening the deterministic fact validator.
-- The previous version remains immutable and can be selected as a rollback target.

insert into wcs.agent_versions (
    id, agent_id, agent_version, semantic_version, name, purpose, state,
    model_provider, model_id, temperature, top_p,
    system_prompt_version, system_prompt_hash, system_prompt_body, user_prompt_template,
    input_schema_version, output_schema_version, input_schema_json, output_schema_json,
    reasoning_effort, structured_tool_calling, pricing_version,
    input_price_usd_per_million_tokens, output_price_usd_per_million_tokens,
    allowed_tools, knowledge_sources, memory_policy, response_policy,
    timeout_ms, max_steps, max_input_tokens, max_output_tokens, budget_limit_usd,
    fallback_agent_id, evaluation_suite_version, created_by, created_at, approved_by, approved_at
)
select
    '00000000-0000-0000-0000-000000000978'::uuid,
    base.agent_id, 2, '1.0.1',
    'Response humanization fact checklist',
    'Rewrite deterministic catalog facts with an explicit per-product fact checklist',
    'APPROVED', base.model_provider, base.model_id, base.temperature, base.top_p,
    base.system_prompt_version, base.system_prompt_hash, base.system_prompt_body,
    $humanizer_user_v2$
<request_metadata>
<use_case>{{use_case}}</use_case>
<channel>{{channel}}</channel>
</request_metadata>
<approved_knowledge>
{{approved_knowledge}}
</approved_knowledge>
<required_facts>
{{required_facts}}
</required_facts>
<response_contract>
Redactá una respuesta breve y natural en español argentino.
Usá únicamente los hechos aprobados. Cada producto debe aparecer con todos
estos campos, sin excepción: nombre, SKU, color, talle, precio exacto,
moneda y stock. Copiá los valores de <required_facts>; no redondees, no
omitas precio ni stock y no reemplaces una variante por otra.
Si hay varios productos, mantené exactamente el orden de product[1], product[2]
y siguientes. No agregues datos, promociones, políticas, envíos ni instrucciones.
Devolvé únicamente el mensaje final para el cliente.
</response_contract>
$humanizer_user_v2$,
    'response-humanization-input-v2', base.output_schema_version,
    '{"type":"object","properties":{"use_case":{"type":"string"},"channel":{"type":"string","enum":["TELEGRAM","WHATSAPP"]},"approved_knowledge":{"type":"string"},"required_facts":{"type":"string"}},"required":["use_case","channel","approved_knowledge","required_facts"],"additionalProperties":false}',
    base.output_schema_json,
    base.reasoning_effort, base.structured_tool_calling, base.pricing_version,
    base.input_price_usd_per_million_tokens, base.output_price_usd_per_million_tokens,
    base.allowed_tools, base.knowledge_sources, base.memory_policy, base.response_policy,
    base.timeout_ms, base.max_steps, base.max_input_tokens, base.max_output_tokens,
    base.budget_limit_usd, base.fallback_agent_id, 'humanization-v2',
    'system-migration', timestamp with time zone '2026-09-22 15:00:00+00',
    'system-migration', timestamp with time zone '2026-09-22 15:00:00+00'
from wcs.agent_versions base
where base.agent_id = 'response-humanization'
  and base.agent_version = 1
on conflict (agent_id, agent_version) do nothing;

insert into wcs.agent_activations (
    id, agent_id, agent_version, environment, channel, use_case, reason,
    rollout_percentage, enabled, kill_switch, previous_version, activated_at, activated_by
) values
('00000000-0000-0000-0000-000000000979', 'response-humanization', 2, 'prod', 'telegram', 'CATALOG_SEARCH',
 'Patch 1.0.1: explicit structured fact checklist prevents omitted catalog prices and stock',
 100, true, false, 1, timestamp with time zone '2026-09-22 15:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000980', 'response-humanization', 2, 'prod', 'whatsapp', 'CATALOG_SEARCH',
 'Patch 1.0.1: explicit structured fact checklist prevents omitted catalog prices and stock',
 100, true, false, 1, timestamp with time zone '2026-09-22 15:00:00+00', 'system-migration')
on conflict do nothing;

-- Rollback: disable the version 1.0.1 activations and restore version 1.0.0
-- activations before removing version 2 from the registry.
