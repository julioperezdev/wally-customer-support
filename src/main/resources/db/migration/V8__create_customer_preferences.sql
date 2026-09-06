create table wcs.customer_preferences (
    id uuid primary key,
    conversation_id uuid references wcs.conversations(id) on delete cascade,
    actor_id varchar(128) not null,
    preference_key varchar(64) not null,
    preference_value varchar(128) not null,
    preference_scope varchar(32) not null,
    confidence numeric(4, 3) not null,
    origin varchar(32) not null,
    confirmed boolean not null,
    updated_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint ck_customer_preferences_confidence check (confidence >= 0 and confidence <= 1),
    constraint ck_customer_preferences_scope check (
        (preference_scope = 'ACTOR' and conversation_id is null)
        or (preference_scope = 'CONVERSATION' and conversation_id is not null)
    ),
    constraint ck_customer_preferences_confirmed check (confirmed = true),
    constraint ck_customer_preferences_expiry check (expires_at > updated_at)
);

create index ix_customer_preferences_actor_active
    on wcs.customer_preferences (actor_id, expires_at);

create unique index uq_customer_preferences_actor_key
    on wcs.customer_preferences (actor_id, preference_key, preference_scope)
    where conversation_id is null;

create unique index uq_customer_preferences_conversation_key
    on wcs.customer_preferences (actor_id, conversation_id, preference_key, preference_scope)
    where conversation_id is not null;

-- Rollback procedure: delete this table and its indexes only after confirming
-- that no runtime version still reads customer preferences.
