alter table wcs.agent_versions drop constraint ck_agent_versions_state;

alter table wcs.agent_versions
    add constraint ck_agent_versions_state check (
        state in ('DRAFT', 'CANDIDATE', 'EVALUATED', 'APPROVED', 'ACTIVE', 'RETIRED', 'DEPRECATED', 'ROLLED_BACK')
    );

alter table wcs.agent_versions drop constraint ck_agent_versions_approval_metadata;

alter table wcs.agent_versions
    add constraint ck_agent_versions_approval_metadata check (
        (state in ('APPROVED', 'ACTIVE', 'RETIRED', 'DEPRECATED', 'ROLLED_BACK')
            and approved_by is not null and approved_at is not null)
        or (state in ('DRAFT', 'CANDIDATE', 'EVALUATED')
            and approved_by is null and approved_at is null)
    );

create table wcs.agent_registry_audit_events (
    id uuid primary key,
    operation varchar(80) not null,
    agent_id varchar(128) not null,
    agent_version integer,
    previous_state varchar(32),
    resulting_state varchar(32),
    environment varchar(64),
    channel varchar(32),
    use_case varchar(128),
    actor_id varchar(128) not null,
    reason varchar(500) not null,
    occurred_at timestamp with time zone not null,
    constraint ck_agent_registry_audit_version check (agent_version is null or agent_version > 0)
);

create index ix_agent_registry_audit_agent_time
    on wcs.agent_registry_audit_events (agent_id, occurred_at desc);

create table wcs.agent_execution_traces (
    id uuid primary key,
    correlation_id varchar(128),
    actor_key varchar(128),
    agent_id varchar(128),
    agent_version integer,
    environment varchar(64) not null,
    channel varchar(32) not null,
    use_case varchar(128) not null,
    outcome varchar(32) not null,
    resolution_status varchar(32) not null,
    provider varchar(64),
    model_id varchar(160),
    duration_ms bigint not null,
    input_tokens bigint,
    output_tokens bigint,
    estimated_cost_usd numeric(12, 8),
    error_type varchar(160),
    executed_at timestamp with time zone not null,
    constraint ck_agent_execution_trace_version check (agent_version is null or agent_version > 0),
    constraint ck_agent_execution_trace_duration check (duration_ms >= 0),
    constraint ck_agent_execution_trace_input_tokens check (input_tokens is null or input_tokens >= 0),
    constraint ck_agent_execution_trace_output_tokens check (output_tokens is null or output_tokens >= 0),
    constraint ck_agent_execution_trace_cost check (estimated_cost_usd is null or estimated_cost_usd >= 0)
);

create index ix_agent_execution_traces_lookup
    on wcs.agent_execution_traces (agent_id, use_case, executed_at desc);

-- Rollback: stop writing these two tables, then drop their indexes and tables.
