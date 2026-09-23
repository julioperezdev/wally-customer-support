ALTER TABLE wcs.agent_evaluation_scenario_results
    ADD COLUMN execution_routed_quantity INTEGER;

ALTER TABLE wcs.agent_evaluation_scenario_results
    ADD CONSTRAINT ck_agent_eval_routed_quantity_range
        CHECK (execution_routed_quantity IS NULL OR execution_routed_quantity BETWEEN 1 AND 100);
