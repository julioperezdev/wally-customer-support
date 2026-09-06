alter table wcs.catalog_products
    add column product_type varchar(32) not null default 'other';

update wcs.catalog_products
set product_type = case
    when lower(name) like '%remera%' then 'remera'
    when lower(name) like '%buzo%' then 'buzo'
    when lower(name) like '%campera%' then 'campera'
    else 'other'
end;

alter table wcs.catalog_products
    alter column product_type drop default;

create index ix_catalog_products_active_type_name
    on wcs.catalog_products (active, product_type, name);

-- Rollback procedure: drop index ix_catalog_products_active_type_name and
-- drop column product_type from wcs.catalog_products in a controlled migration.
