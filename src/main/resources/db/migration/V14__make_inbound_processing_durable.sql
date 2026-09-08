alter table wcs.processing_attempts
    add column available_at timestamp with time zone not null default current_timestamp;

alter table wcs.processing_attempts
    add column started_at timestamp with time zone;

create index ix_processing_attempts_due
    on wcs.processing_attempts (status, available_at, created_at);

comment on table wcs.processing_attempts is
    'Durable inbound processing queue and sanitized processing outcome per inbound message';

comment on column wcs.processing_attempts.available_at is
    'Earliest time at which a pending inbound message may be claimed';

comment on column wcs.processing_attempts.started_at is
    'Lease start used to recover work abandoned by a stopped process';
