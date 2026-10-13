-- The sync log becomes a band's own (2026-10-13).
--
-- sync_log was the one table holding a band's data with no band_id: a line belonged to whichever
-- band its TASK belonged to, and nothing else. That is true, and it is also why the table fell out
-- of every check — BandScopedQueriesTest works out which tables keep a band's rows by looking for a
-- bandId, finds none, and skips the repository whole. Its one read carried @AcrossBands and the band
-- was checked a layer up, by hand, in SyncLogService.
--
-- A column of its own puts the log in line with every other table here (gig, published_gig,
-- sync_task, calendar_event…) and inside the enumeration rather than beside it: from now on a query
-- over sync_log that leaves the band out fails the build like any other.
--
-- Deliberately a COLUMN and not a mapped association to sync_task, which was the other way to let
-- the band be reached. An association would have cost a lazy-loading hazard in the table with the
-- most rows in this schema, to spare a column that every sibling table already has; and Hibernate
-- will not have both a raw task_id and an association over one physical column anyway. See
-- JpaSyncOutbox#get for the one thing the association WOULD have fixed, and why it is a comment
-- instead.

alter table sync_log add column band_id uuid;

-- Every line goes with its task, which is what "its band" has always meant.
update sync_log l set band_id = t.band_id from sync_task t where t.id = l.task_id;

-- Any line whose task is already gone has no band to belong to, and nothing can read it: the only
-- way in is by task. (There should be none — the foreign key is ON DELETE CASCADE.)
delete from sync_log where band_id is null;

alter table sync_log alter column band_id set not null;
alter table sync_log add constraint sync_log_band foreign key (band_id) references band (id) on delete cascade;

-- What the log is read by: one task's lines, for the band that asked.
create index sync_log_band_task on sync_log (band_id, task_id, id);
