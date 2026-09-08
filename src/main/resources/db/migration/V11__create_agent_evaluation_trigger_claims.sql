create table wcs.agent_evaluation_trigger_claims (
    id uuid primary key,
    key_hash varchar(64) not null,
    claimed_at timestamp with time zone not null default current_timestamp,
    constraint uq_agent_evaluation_trigger_claims_key_hash unique (key_hash),
    constraint ck_agent_evaluation_trigger_claims_key_hash check (
        char_length(key_hash) = 64 and key_hash ~ '^[0-9a-f]{64}$'
    )
);

create index ix_agent_evaluation_trigger_claims_claimed_at
    on wcs.agent_evaluation_trigger_claims (claimed_at);

-- Rollback procedure: drop the index and table only after confirming that no
-- evaluation trigger depends on the durable idempotency claims.
