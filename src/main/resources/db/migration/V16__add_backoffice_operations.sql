alter table wcs.human_follow_up_tasks
    add column assigned_to varchar(128);

alter table wcs.catalog_variants
    add column record_version integer not null default 0;

alter table wcs.catalog_variants
    alter column record_version drop default;

create index ix_human_follow_up_assigned_to
    on wcs.human_follow_up_tasks (assigned_to, status, due_at);

create table wcs.catalog_stock_adjustments (
    id uuid primary key,
    sku varchar(80) not null,
    previous_stock integer not null,
    delta integer not null,
    new_stock integer not null,
    reason varchar(256) not null,
    actor_key varchar(128) not null,
    idempotency_key varchar(128) not null,
    created_at timestamp with time zone not null,
    constraint uq_catalog_stock_adjustments_idempotency unique (idempotency_key),
    constraint ck_catalog_stock_adjustments_stock_non_negative check (new_stock >= 0),
    constraint ck_catalog_stock_adjustments_delta_non_zero check (delta <> 0)
);

create index ix_catalog_stock_adjustments_sku_created_at
    on wcs.catalog_stock_adjustments (sku, created_at desc);

-- Rollback procedure: remove the stock-adjustment table and index, then drop
-- the human-follow-up assignment index and assigned_to column after confirming
-- that no backoffice consumer depends on them.
