-- Complete the immutable runtime metadata for the current execution plan.
-- This migration does not enable the runtime; AppConfig and the activation
-- records remain the rollout gate.

update wcs.agent_versions
set system_prompt_version = 'knowledge-system-v2',
    system_prompt_hash = 'f257258bb932f92b0b22657b64590d8ac7f79eb0999855e502072388b389447f'
where agent_id = 'knowledge-specialist'
  and agent_version = 1
  and system_prompt_version = 'knowledge-system-v1';

insert into wcs.agent_versions (
    id,
    agent_id,
    agent_version,
    name,
    purpose,
    state,
    model_provider,
    model_id,
    temperature,
    top_p,
    system_prompt_version,
    system_prompt_hash,
    input_schema_version,
    output_schema_version,
    allowed_tools,
    knowledge_sources,
    memory_policy,
    response_policy,
    timeout_ms,
    max_steps,
    max_input_tokens,
    max_output_tokens,
    budget_limit_usd,
    fallback_agent_id,
    evaluation_suite_version,
    created_by,
    created_at,
    approved_by,
    approved_at
) values (
    '00000000-0000-0000-0000-000000000905',
    'checkout-specialist',
    1,
    'Checkout specialist baseline',
    'Manage cart and payment-link operations through validated WCS capabilities',
    'APPROVED',
    'wcs',
    'deterministic-checkout-v1',
    0.000,
    1.000,
    'checkout-system-v1',
    '0000000000000000000000000000000000000000000000000000000000000000',
    'checkout-input-v1',
    'checkout-output-v1',
    '["cart.manage", "checkout.create"]'::jsonb,
    '[]'::jsonb,
    'conversation-summary-v1',
    'grounded-customer-support-v1',
    10000,
    2,
    2000,
    1000,
    0.000000,
    'support-safety',
    'checkout-v1',
    'system-migration',
    timestamp with time zone '2026-09-18 12:00:00+00',
    'system-migration',
    timestamp with time zone '2026-09-18 12:00:00+00'
)
on conflict (agent_id, agent_version) do nothing;

insert into wcs.agent_activations (
    id,
    agent_id,
    agent_version,
    environment,
    channel,
    use_case,
    reason,
    rollout_percentage,
    enabled,
    kill_switch,
    previous_version,
    activated_at,
    activated_by
) values
('00000000-0000-0000-0000-000000000938', 'checkout-specialist', 1, 'prod', 'telegram', 'CART', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000939', 'checkout-specialist', 1, 'prod', 'telegram', 'PURCHASE_LINK', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000940', 'checkout-specialist', 1, 'prod', 'telegram', 'PURCHASE_DEFERRED', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000941', 'checkout-specialist', 1, 'prod', 'whatsapp', 'CART', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000942', 'checkout-specialist', 1, 'prod', 'whatsapp', 'PURCHASE_LINK', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration'),
('00000000-0000-0000-0000-000000000943', 'checkout-specialist', 1, 'prod', 'whatsapp', 'PURCHASE_DEFERRED', 'baseline routing declaration; runtime activation disabled', 0, false, false, null, timestamp with time zone '2026-09-18 12:00:00+00', 'system-migration')
on conflict do nothing;

-- Rollback: disable use of this metadata, then remove the six activation
-- declarations and the checkout-specialist version after no runtime reference
-- remains. Restore the knowledge-specialist prompt metadata only if a prior
-- snapshot has been retained.
