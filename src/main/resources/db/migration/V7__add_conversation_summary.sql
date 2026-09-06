alter table wcs.conversation_memory_states
    add column conversation_summary text,
    add column summary_version bigint not null default 0,
    add column summarized_message_count integer not null default 0,
    add column summary_updated_at timestamp with time zone;

alter table wcs.conversation_memory_states
    add constraint ck_conversation_memory_summary_version_non_negative
        check (summary_version >= 0),
    add constraint ck_conversation_memory_summarized_count_non_negative
        check (summarized_message_count >= 0);

-- Rollback procedure: remove the constraints and summary columns only after
-- confirming that no runtime version still reads the summary fields.
