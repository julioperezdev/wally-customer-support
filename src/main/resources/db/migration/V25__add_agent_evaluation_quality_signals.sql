alter table wcs.agent_evaluation_scenario_results
    add column execution_routed_intent varchar(128),
    add column execution_entity_types jsonb,
    add column execution_tool_name varchar(128),
    add column execution_tool_succeeded boolean,
    add column execution_grounded boolean,
    add column evaluated_dimensions jsonb;

-- Rollback: drop these nullable execution-signal columns only after no
-- evaluation history reader depends on the quality dimensions.
