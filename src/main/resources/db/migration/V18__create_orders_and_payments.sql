create table wcs.orders (
    id uuid primary key,
    customer_reference varchar(128),
    status varchar(32) not null,
    currency varchar(3) not null,
    total numeric(12, 2) not null,
    payment_provider varchar(32) not null,
    payment_preference_id varchar(128),
    payment_url varchar(2048),
    external_payment_id varchar(128),
    idempotency_key varchar(128) not null,
    request_hash varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_orders_idempotency_key unique (idempotency_key),
    constraint ck_orders_status check (status in (
        'PENDING_PAYMENT', 'PAID', 'REJECTED', 'CANCELLED', 'EXPIRED'
    )),
    constraint ck_orders_total_non_negative check (total >= 0)
);

create index ix_orders_status_created_at
    on wcs.orders (status, created_at desc);

create table wcs.order_items (
    id uuid primary key,
    order_id uuid not null references wcs.orders(id) on delete cascade,
    sku varchar(80) not null,
    product_name varchar(160) not null,
    quantity integer not null,
    unit_price numeric(12, 2) not null,
    currency varchar(3) not null,
    line_total numeric(12, 2) not null,
    constraint ck_order_items_quantity_positive check (quantity > 0),
    constraint ck_order_items_unit_price_non_negative check (unit_price >= 0),
    constraint ck_order_items_line_total_non_negative check (line_total >= 0),
    constraint uq_order_items_order_sku unique (order_id, sku)
);

create index ix_order_items_order_id on wcs.order_items (order_id);

create table wcs.payment_events (
    id uuid primary key,
    order_id uuid references wcs.orders(id) on delete set null,
    provider varchar(32) not null,
    provider_event_id varchar(160) not null,
    event_type varchar(64) not null,
    payment_id varchar(128),
    payment_status varchar(32),
    payload_hash varchar(64) not null,
    occurred_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint uq_payment_events_provider_event unique (provider, provider_event_id)
);

create index ix_payment_events_order_created_at
    on wcs.payment_events (order_id, created_at desc);

-- Rollback procedure: first stop payment webhooks and order creation, then
-- drop payment_events, order_items and orders after exporting the audit evidence.
