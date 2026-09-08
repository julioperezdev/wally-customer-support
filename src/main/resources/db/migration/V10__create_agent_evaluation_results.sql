create table wcs.agent_evaluation_runs (
    id uuid primary key,
    dataset_version varchar(80) not null,
    agent_id varchar(128) not null,
    agent_version varchar(80) not null,
    provider varchar(64) not null,
    model_id varchar(160) not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone not null,
    duration_ms bigint not null,
    total_scenarios integer not null,
    passed_scenarios integer not null,
    failed_scenarios integer not null,
    pass_rate numeric(12, 10) not null,
    average_score numeric(12, 10) not null,
    failure_reasons jsonb not null,
    constraint ck_agent_evaluation_runs_time check (completed_at >= started_at),
    constraint ck_agent_evaluation_runs_duration check (duration_ms >= 0),
    constraint ck_agent_evaluation_runs_total check (total_scenarios > 0),
    constraint ck_agent_evaluation_runs_passed check (
        passed_scenarios between 0 and total_scenarios
    ),
    constraint ck_agent_evaluation_runs_failed check (
        failed_scenarios = total_scenarios - passed_scenarios
    ),
    constraint ck_agent_evaluation_runs_pass_rate check (pass_rate >= 0 and pass_rate <= 1),
    constraint ck_agent_evaluation_runs_average_score check (average_score >= 0 and average_score <= 1)
);

create table wcs.agent_evaluation_scenario_results (
    id uuid primary key,
    run_id uuid not null,
    scenario_id varchar(128) not null,
    dataset_version varchar(80) not null,
    passed boolean not null,
    score numeric(12, 10) not null,
    failure_reasons jsonb not null,
    execution_agent_id varchar(128),
    execution_agent_version varchar(80),
    execution_provider varchar(64),
    execution_model_id varchar(160),
    execution_duration_ms bigint,
    provider_latency_ms bigint,
    input_tokens integer,
    output_tokens integer,
    total_tokens integer,
    estimated_cost_usd numeric(12, 6),
    pricing_version varchar(80),
    constraint fk_agent_evaluation_scenario_run
        foreign key (run_id)
        references wcs.agent_evaluation_runs (id)
        on delete cascade,
    constraint uq_agent_evaluation_scenario_run unique (run_id, scenario_id),
    constraint ck_agent_evaluation_scenario_score check (score >= 0 and score <= 1),
    constraint ck_agent_evaluation_scenario_duration check (
        execution_duration_ms is null or execution_duration_ms >= 0
    ),
    constraint ck_agent_evaluation_scenario_provider_latency check (
        provider_latency_ms is null or provider_latency_ms >= 0
    ),
    constraint ck_agent_evaluation_scenario_input_tokens check (
        input_tokens is null or input_tokens >= 0
    ),
    constraint ck_agent_evaluation_scenario_output_tokens check (
        output_tokens is null or output_tokens >= 0
    ),
    constraint ck_agent_evaluation_scenario_total_tokens check (
        total_tokens is null or total_tokens >= 0
    ),
    constraint ck_agent_evaluation_scenario_cost check (
        estimated_cost_usd is null or estimated_cost_usd >= 0
    )
);

create index ix_agent_evaluation_runs_agent_version_completed
    on wcs.agent_evaluation_runs (agent_id, agent_version, completed_at desc);

create index ix_agent_evaluation_runs_dataset_completed
    on wcs.agent_evaluation_runs (dataset_version, completed_at desc);

create index ix_agent_evaluation_scenario_results_run
    on wcs.agent_evaluation_scenario_results (run_id, scenario_id);

-- Rollback procedure: drop scenario results first, then evaluation runs and indexes
-- only after confirming that no job or backoffice reads the evaluation history.
