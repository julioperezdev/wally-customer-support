-- WCS baseline 2026-09-07
-- Structural snapshot of schema wcs, derived from Flyway V1, V2 and V4-V8.
-- Intentionally excludes demo data from V3, customer data and Flyway history.
-- Do not run against the shared production schema without an approved plan.

create schema if not exists wcs;

create table wcs.conversations (
    id uuid primary key,
    channel varchar(32) not null,
    external_conversation_id varchar(128) not null,
    external_customer_id varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_conversations_channel_external unique (channel, external_conversation_id)
);

create table wcs.messages (
    id uuid primary key,
    conversation_id uuid not null references wcs.conversations(id),
    external_message_id varchar(128) not null,
    direction varchar(16) not null,
    message_type varchar(32) not null,
    body text not null,
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    channel varchar(32) not null default 'WHATSAPP',
    constraint uq_messages_channel_external unique (channel, external_message_id)
);

create index ix_messages_conversation_occurred
    on wcs.messages (conversation_id, occurred_at desc);

create table wcs.processing_attempts (
    id uuid primary key,
    message_id uuid not null references wcs.messages(id),
    status varchar(32) not null,
    attempt_count integer not null,
    last_error varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_processing_attempts_message unique (message_id)
);

create table wcs.outbox_messages (
    id uuid primary key,
    aggregate_id uuid not null,
    event_type varchar(64) not null,
    message_type varchar(32) not null,
    body text,
    template_name varchar(128),
    template_language_code varchar(32),
    template_body_parameters text,
    status varchar(32) not null,
    attempts integer not null,
    available_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    sent_at timestamp with time zone,
    last_error varchar(1000),
    version bigint not null default 0,
    recipient_id varchar(128) not null,
    channel varchar(32) not null default 'WHATSAPP'
);

create index ix_outbox_status_available
    on wcs.outbox_messages (status, available_at, created_at);

create table wcs.catalog_products (
    id uuid primary key,
    name varchar(160) not null,
    description varchar(1000) not null,
    image_object_key varchar(512),
    active boolean not null,
    demo boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    product_type varchar(32) not null
);

create index ix_catalog_products_active_name
    on wcs.catalog_products (active, name);

create index ix_catalog_products_active_type_name
    on wcs.catalog_products (active, product_type, name);

create table wcs.catalog_variants (
    id uuid primary key,
    product_id uuid not null references wcs.catalog_products(id),
    sku varchar(80) not null,
    size_label varchar(32) not null,
    color varchar(64) not null,
    price numeric(12, 2) not null,
    currency varchar(3) not null,
    stock integer not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_catalog_variants_sku unique (sku),
    constraint uq_catalog_variants_product_size_color unique (product_id, size_label, color),
    constraint ck_catalog_variants_stock_non_negative check (stock >= 0)
);

create index ix_catalog_variants_product_active
    on wcs.catalog_variants (product_id, active);

create table wcs.business_hours (
    id uuid primary key,
    day_of_week integer not null,
    opens_at time,
    closes_at time,
    closed boolean not null,
    timezone varchar(64) not null,
    active boolean not null,
    demo boolean not null,
    record_version integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_business_hours_day_of_week check (day_of_week between 1 and 7),
    constraint ck_business_hours_schedule check (
        closed = true or (opens_at is not null and closes_at is not null and closes_at > opens_at)
    ),
    constraint uq_business_hours_day_version unique (day_of_week, record_version)
);

create index ix_business_hours_active_day
    on wcs.business_hours (active, day_of_week);

create table wcs.support_policies (
    id uuid primary key,
    policy_key varchar(80) not null,
    title varchar(160) not null,
    content varchar(4000) not null,
    active boolean not null,
    demo boolean not null,
    record_version integer not null,
    published_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_support_policies_key_version unique (policy_key, record_version)
);

create index ix_support_policies_active_key_version
    on wcs.support_policies (active, policy_key, record_version desc);

create table wcs.conversation_memory_states (
    conversation_id uuid primary key references wcs.conversations(id) on delete cascade,
    actor_id varchar(128) not null,
    recent_messages jsonb not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    conversation_summary text,
    summary_version bigint not null default 0,
    summarized_message_count integer not null default 0,
    summary_updated_at timestamp with time zone,
    constraint ck_conversation_memory_actor_not_blank check (length(trim(actor_id)) > 0),
    constraint ck_conversation_memory_version_non_negative check (version >= 0),
    constraint ck_conversation_memory_summary_version_non_negative check (summary_version >= 0),
    constraint ck_conversation_memory_summarized_count_non_negative check (summarized_message_count >= 0)
);

create index ix_conversation_memory_states_actor
    on wcs.conversation_memory_states (actor_id);

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
