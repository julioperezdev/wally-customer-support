create table wcs.agent_versions (
    id uuid primary key,
    agent_id varchar(128) not null,
    agent_version integer not null,
    name varchar(160) not null,
    purpose varchar(1000) not null,
    state varchar(32) not null,
    model_provider varchar(64) not null,
    model_id varchar(160) not null,
    temperature numeric(4, 3) not null,
    top_p numeric(4, 3) not null,
    system_prompt_version varchar(80) not null,
    system_prompt_hash varchar(64) not null,
    input_schema_version varchar(80) not null,
    output_schema_version varchar(80) not null,
    allowed_tools jsonb not null,
    knowledge_sources jsonb not null,
    memory_policy varchar(160) not null,
    response_policy varchar(160) not null,
    timeout_ms integer not null,
    max_steps integer not null,
    max_input_tokens integer not null,
    max_output_tokens integer not null,
    budget_limit_usd numeric(12, 6) not null,
    fallback_agent_id varchar(128),
    evaluation_suite_version varchar(80) not null,
    created_by varchar(128) not null,
    created_at timestamp with time zone not null,
    approved_by varchar(128),
    approved_at timestamp with time zone,
    constraint uq_agent_versions_agent_version unique (agent_id, agent_version),
    constraint ck_agent_versions_version_positive check (agent_version > 0),
    constraint ck_agent_versions_state check (
        state in ('DRAFT', 'CANDIDATE', 'EVALUATED', 'APPROVED', 'ACTIVE', 'DEPRECATED', 'ROLLED_BACK')
    ),
    constraint ck_agent_versions_temperature check (temperature >= 0 and temperature <= 2),
    constraint ck_agent_versions_top_p check (top_p > 0 and top_p <= 1),
    constraint ck_agent_versions_timeout check (timeout_ms > 0 and timeout_ms <= 60000),
    constraint ck_agent_versions_steps check (max_steps between 1 and 3),
    constraint ck_agent_versions_input_tokens check (max_input_tokens between 1 and 32000),
    constraint ck_agent_versions_output_tokens check (max_output_tokens between 1 and 32000),
    constraint ck_agent_versions_budget check (budget_limit_usd >= 0),
    constraint ck_agent_versions_approval_metadata check (
        (state in ('APPROVED', 'ACTIVE', 'DEPRECATED', 'ROLLED_BACK')
            and approved_by is not null and approved_at is not null)
        or (state in ('DRAFT', 'CANDIDATE', 'EVALUATED')
            and approved_by is null and approved_at is null)
    )
);

create index ix_agent_versions_agent_state_version
    on wcs.agent_versions (agent_id, state, agent_version desc);

create table wcs.agent_activations (
    id uuid primary key,
    agent_id varchar(128) not null,
    agent_version integer not null,
    environment varchar(64) not null,
    channel varchar(32) not null,
    use_case varchar(128) not null,
    reason varchar(500) not null,
    rollout_percentage integer not null,
    enabled boolean not null,
    kill_switch boolean not null,
    previous_version integer,
    activated_at timestamp with time zone not null,
    activated_by varchar(128) not null,
    constraint fk_agent_activations_version
        foreign key (agent_id, agent_version)
        references wcs.agent_versions (agent_id, agent_version),
    constraint ck_agent_activations_version_positive check (agent_version > 0),
    constraint ck_agent_activations_rollout check (rollout_percentage between 0 and 100),
    constraint ck_agent_activations_enabled_rollout check (not enabled or rollout_percentage > 0),
    constraint ck_agent_activations_kill_switch check (not kill_switch or not enabled),
    constraint ck_agent_activations_previous_version check (
        previous_version is null or (previous_version > 0 and previous_version <> agent_version)
    )
);

create index ix_agent_activations_lookup
    on wcs.agent_activations (agent_id, environment, channel, use_case, enabled, kill_switch, activated_at desc);

-- Rollback procedure: drop agent_activations, agent_versions and their indexes
-- only after confirming that no runtime or control-plane version reads them.
