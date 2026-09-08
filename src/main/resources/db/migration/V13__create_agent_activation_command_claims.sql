create table wcs.agent_activation_command_claims (
    id uuid primary key,
    key_hash varchar(64) not null,
    claimed_at timestamp with time zone not null default current_timestamp,
    constraint uq_agent_activation_command_claims_key_hash unique (key_hash),
    constraint ck_agent_activation_command_claims_key_hash check (
        char_length(key_hash) = 64 and key_hash ~ '^[0-9a-f]{64}$'
    )
);

create index ix_agent_activation_command_claims_claimed_at
    on wcs.agent_activation_command_claims (claimed_at);

-- Rollback procedure: drop this table only after confirming no activation
-- command depends on its durable idempotency claims.
