create table wcs.conversation_memory_states (
    conversation_id uuid primary key references wcs.conversations(id) on delete cascade,
    actor_id varchar(128) not null,
    recent_messages jsonb not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint ck_conversation_memory_actor_not_blank check (length(trim(actor_id)) > 0),
    constraint ck_conversation_memory_version_non_negative check (version >= 0)
);

create index ix_conversation_memory_states_actor
    on wcs.conversation_memory_states (actor_id);

-- Rollback procedure: delete this table and its index only after confirming
-- that no runtime version still reads conversation_memory_states.
