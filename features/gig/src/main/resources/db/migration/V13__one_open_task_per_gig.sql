-- One piece of WAITING work per gig, platform and action (2026-10-13).
--
-- SyncRequests reads a gig's open work and then queues whatever is missing (#publish, #update,
-- #onPublishedPlatforms, #delete). That is a read-then-write, and under READ COMMITTED two
-- requests for the same gig both see nothing open and both queue. The engine then puts BOTH rows
-- in one workload (WorkflowEngine#whatIsDue) and -- because every intake is computed before the
-- first batch runs -- MootWork's "already published" guard answers false for both. On a platform
-- whose create makes a new event each time, that publishes the gig twice and the second event's
-- id overwrites the first in published_gig: the first is then a live public event that nothing
-- here has a pointer to, and no band can ever remove it from this app. A double-clicked Publish,
-- two tabs or a retried POST was enough.
--
-- The queueing rules were all advisory until now. This index is the first of them the database
-- actually holds, which is why it is worth the one it does not hold: see SyncRequests' javadoc.
--
-- PENDING only, deliberately. A RUNNING task and a newly queued PENDING one of the same action
-- are legal and wanted: OpenWork#updateAlreadyComing asks about PENDING work alone, because a
-- running update may already have sent the details the user has only just changed, so an edit
-- queues a fresh update behind it. Including RUNNING here would refuse that edit.
--
-- Losing the race is not an error anyone sees: SpringTransactions runs the unit of work once more,
-- and the re-read then finds the winner's row and answers as it would have a moment later
-- ("already being published"). The index is the invariant; the retry is the manners.

-- Whatever the bug has already left behind: keep the oldest of each group (the one the engine
-- would have run first) and discard the rest, so the index below can be created at all.
update sync_task t
set status     = 'DISCARDED',
    message    = 'discarded: the same work was queued twice (V13)',
    updated_at = now()
where status = 'PENDING'
  and exists (select 1
              from sync_task o
              where o.band_id = t.band_id
                and o.gig_id = t.gig_id
                and o.platform = t.platform
                and o.action = t.action
                and o.status = 'PENDING'
                and o.id < t.id);

create unique index sync_task_open_work
    on sync_task (band_id, gig_id, platform, action)
    where status = 'PENDING';
