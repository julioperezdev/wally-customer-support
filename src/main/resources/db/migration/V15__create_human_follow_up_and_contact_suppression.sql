create table wcs.human_follow_up_tasks (
    id uuid primary key,
    conversation_id uuid not null references wcs.conversations(id) on delete cascade,
    source_message_id uuid references wcs.messages(id) on delete set null,
    reason varchar(64) not null,
    priority varchar(16) not null,
    status varchar(32) not null,
    due_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint ck_human_follow_up_priority check (priority in ('HIGH', 'NORMAL', 'LOW')),
    constraint ck_human_follow_up_status check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    constraint ck_human_follow_up_completed check (
        (status in ('DONE', 'CANCELLED') and completed_at is not null)
        or (status in ('OPEN', 'IN_PROGRESS') and completed_at is null)
    )
);

alter table wcs.human_follow_up_tasks
    add constraint uq_human_follow_up_source_reason unique (source_message_id, reason);

create index ix_human_follow_up_open_priority_due
    on wcs.human_follow_up_tasks (status, priority, due_at)
    where status in ('OPEN', 'IN_PROGRESS');

create table wcs.contact_suppressions (
    id uuid primary key,
    actor_key varchar(64) not null,
    status varchar(32) not null,
    reason varchar(64) not null,
    source_message_id uuid references wcs.messages(id) on delete set null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_contact_suppressions_actor_key unique (actor_key),
    constraint ck_contact_suppressions_actor_key check (
        char_length(actor_key) = 64 and actor_key ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_contact_suppressions_status check (status in ('DO_NOT_CONTACT', 'REVOKED'))
);

create index ix_contact_suppressions_status on wcs.contact_suppressions (status, updated_at);

-- Rollback procedure: drop suppression indexes/table and follow-up indexes/table
-- only after confirming that no backoffice consumer or scheduled cleanup depends on them.
