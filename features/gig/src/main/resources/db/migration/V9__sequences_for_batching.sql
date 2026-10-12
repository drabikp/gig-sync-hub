-- Identity columns cannot be batched: to insert a row the driver has to go and fetch the id the
-- database just assigned, which forces one round trip per row. Everything written here arrives in
-- bulk — a batch publish queues a task per gig per platform, every run appends its log, replacing a
-- band's calendar rules writes the lot — so these ids come from sequences instead, which Hibernate
-- draws in blocks (pooled-lo, 50 at a time) and whose inserts it can then batch.
--
-- Each sequence starts a safe distance above what the table already holds, so no block an optimizer
-- hands out can ever land on an existing row. Each is OWNED BY its column, so it still answers to
-- pg_get_serial_sequence (the H2 import moves these counters past the rows it copies) and goes when
-- the column does.

create sequence sync_task_seq increment by 50;
select setval('sync_task_seq', coalesce((select max(id) from sync_task), 0) + 1000, false);
alter table sync_task alter column id drop identity;
alter table sync_task alter column id set default nextval('sync_task_seq');
alter sequence sync_task_seq owned by sync_task.id;

create sequence sync_log_seq increment by 50;
select setval('sync_log_seq', coalesce((select max(id) from sync_log), 0) + 1000, false);
alter table sync_log alter column id drop identity;
alter table sync_log alter column id set default nextval('sync_log_seq');
alter sequence sync_log_seq owned by sync_log.id;

create sequence calendar_rule_seq increment by 50;
select setval('calendar_rule_seq', coalesce((select max(id) from calendar_rule), 0) + 1000, false);
alter table calendar_rule alter column id drop identity;
alter table calendar_rule alter column id set default nextval('calendar_rule_seq');
alter sequence calendar_rule_seq owned by calendar_rule.id;

create sequence audit_event_seq increment by 50;
select setval('audit_event_seq', coalesce((select max(id) from audit_event), 0) + 1000, false);
alter table audit_event alter column id drop identity;
alter table audit_event alter column id set default nextval('audit_event_seq');
alter sequence audit_event_seq owned by audit_event.id;

-- V6's default privileges already cover sequences made later by this owner; said again here so that a
-- database restored or built another way cannot be subtly different.
grant usage, select on sequence sync_task_seq, sync_log_seq, calendar_rule_seq, audit_event_seq
    to bzscraper_app;
