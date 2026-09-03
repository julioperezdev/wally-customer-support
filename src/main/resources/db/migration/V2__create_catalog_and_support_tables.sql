create table wcs.catalog_products (
    id uuid primary key,
    name varchar(160) not null,
    description varchar(1000) not null,
    image_object_key varchar(512),
    active boolean not null,
    demo boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index ix_catalog_products_active_name
    on wcs.catalog_products (active, name);

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
