create table wcs.carts (
    id uuid primary key,
    conversation_id uuid not null references wcs.conversations(id) on delete cascade,
    actor_key varchar(128) not null,
    channel varchar(32) not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    version bigint not null default 0,
    checkout_order_id uuid references wcs.orders(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    record_version bigint not null default 0,
    constraint uq_carts_conversation unique (conversation_id),
    constraint ck_carts_actor_key_not_blank check (length(trim(actor_key)) > 0),
    constraint ck_carts_version_non_negative check (version >= 0),
    constraint ck_carts_status check (status in (
        'ACTIVE', 'CHECKOUT_PENDING', 'CHECKED_OUT', 'ABANDONED', 'EXPIRED', 'CANCELLED'
    ))
);

create index ix_carts_actor_channel on wcs.carts (actor_key, channel);

create table wcs.cart_items (
    id uuid primary key,
    cart_id uuid not null references wcs.carts(id) on delete cascade,
    sku varchar(80) not null,
    quantity integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_cart_items_cart_sku unique (cart_id, sku),
    constraint ck_cart_items_quantity check (quantity between 1 and 100)
);

create index ix_cart_items_cart_id on wcs.cart_items (cart_id);

alter table wcs.orders
    add column cart_id uuid references wcs.carts(id),
    add column cart_version bigint;

create index ix_orders_cart_id on wcs.orders (cart_id, cart_version);

-- Rollback procedure: stop conversational checkout, export cart/order evidence,
-- then drop the order columns, cart_items and carts after no runtime version
-- references this migration.
