alter table wcs.agent_evaluation_scenario_results
    add column execution_routed_action varchar(64);

-- Rollback: drop the nullable execution_routed_action column.
