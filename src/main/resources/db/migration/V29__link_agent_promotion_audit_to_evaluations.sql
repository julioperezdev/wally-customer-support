alter table wcs.agent_registry_audit_events
    add column baseline_evaluation_run_id uuid,
    add column candidate_evaluation_run_id uuid,
    add column evaluation_dataset_version varchar(80),
    add column evaluation_assessment_outcome varchar(40);

alter table wcs.agent_registry_audit_events
    add constraint ck_agent_registry_audit_evaluation_evidence_complete check (
        (baseline_evaluation_run_id is null
            and candidate_evaluation_run_id is null
            and evaluation_dataset_version is null
            and evaluation_assessment_outcome is null)
        or (baseline_evaluation_run_id is not null
            and candidate_evaluation_run_id is not null
            and baseline_evaluation_run_id <> candidate_evaluation_run_id
            and evaluation_dataset_version is not null
            and evaluation_assessment_outcome is not null)
    );

create index ix_agent_registry_audit_evaluation_candidate
    on wcs.agent_registry_audit_events (candidate_evaluation_run_id)
    where candidate_evaluation_run_id is not null;

-- Rollback: drop the index and constraint, then drop the four nullable columns.
