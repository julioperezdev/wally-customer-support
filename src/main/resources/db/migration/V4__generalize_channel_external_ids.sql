alter table wcs.conversations
    rename column customer_wa_id to external_customer_id;

alter table wcs.conversations
    alter column external_customer_id type varchar(128);

alter table wcs.outbox_messages
    rename column recipient_wa_id to recipient_id;

alter table wcs.outbox_messages
    alter column recipient_id type varchar(128);

alter table wcs.messages
    add column channel varchar(32) not null default 'WHATSAPP';

alter table wcs.messages
    drop constraint uq_messages_external_id;

alter table wcs.messages
    add constraint uq_messages_channel_external unique (channel, external_message_id);

alter table wcs.outbox_messages
    add column channel varchar(32) not null default 'WHATSAPP';
