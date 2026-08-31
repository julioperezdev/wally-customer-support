create schema if not exists wcs;

create table wcs.conversations (
    id uuid primary key,
    channel varchar(32) not null,
    external_conversation_id varchar(128) not null,
    customer_wa_id varchar(32) not null,
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
    constraint uq_messages_external_id unique (external_message_id)
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
    recipient_wa_id varchar(32) not null,
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
    version bigint not null default 0
);

create index ix_outbox_status_available
    on wcs.outbox_messages (status, available_at, created_at);
