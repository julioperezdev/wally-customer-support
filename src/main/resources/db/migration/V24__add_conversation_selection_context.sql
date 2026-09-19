alter table wcs.conversation_memory_states
    add column selection_context jsonb not null default '{}'::jsonb;

-- The selection is bounded context, not transactional authority. It is kept
-- separately from recent messages so the router can consume typed filters
-- without replaying the complete conversation.

create index ix_conversation_memory_states_selection_stage
    on wcs.conversation_memory_states ((selection_context ->> 'stage'));

-- Rollback procedure: disable the structured-selection reader, verify no
-- runtime version requires selection_context, then drop the index and column.
