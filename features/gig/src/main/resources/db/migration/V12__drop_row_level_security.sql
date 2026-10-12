-- Row-level security goes; the role it was built on stays.
--
-- V6 put tenant isolation in the database: every band's table carried a policy band_rows, and the
-- app told each pooled connection at checkout which band's rows it might see (app.band, read by the
-- function band_visible). It was always described as "a net under the ports' own band checks", and
-- that is exactly what it turned out to be — so it comes out, and the net is now where it can be
-- enforced on any database rather than on this one.
--
-- What replaces it (October 2026, in this order and all of it already in place):
--   * every query over a band's table names the band, in a derived method's own name or in a
--     predicate of its @Query — and BandScopedQueriesTest ENUMERATES the repository interfaces on
--     the classpath and insists on it, so a query added later is covered the moment it exists. The
--     eight reads that genuinely belong to no band carry @AcrossBands with their reason.
--   * two ArchUnit rules keep anyone from going round it: nothing is annotated
--     @Query(nativeQuery = true) and nothing calls createNativeQuery.
--   * each band-scoped store has a persistence test that writes two bands' rows and reads one
--     band's back, the last three of them added just before this migration.
--
-- Why it was safe to take away, in one sentence: the feature modules' persistence tests connect as
-- the user that OWNS the tables, and a table's owner bypasses row-level security (V6 never asked
-- for FORCE ROW LEVEL SECURITY), so those tests were already proving the queries' own predicates
-- and never the policies. The policies were load-bearing in the app module's tests alone.
--
-- And what it never protected against, which is worth knowing before anyone proposes putting it
-- back: app.band was settable by whoever held the connection. Anyone able to connect as
-- gigsynchub_app could simply "select set_config('app.band', '*', false)" — there was no privilege
-- check on it. So this was a guard against a mistake in the app's own queries, never against a
-- leaked database password.

drop policy band_rows on gig;
drop policy band_rows on published_gig;
drop policy band_rows on sync_task;
drop policy band_rows on sync_log;
drop policy band_rows on band_setting;
drop policy band_rows on calendar_decision;
drop policy band_rows on calendar_event;
drop policy band_rows on calendar_link;
drop policy band_rows on calendar_rule;
drop policy band_rows on band_platform_account;
drop policy band_rows on band_calendar;
drop policy band_rows on band_check;
drop policy band_rows on audit_event;

alter table gig                   disable row level security;
alter table published_gig         disable row level security;
alter table sync_task             disable row level security;
alter table sync_log              disable row level security;
alter table band_setting          disable row level security;
alter table calendar_decision     disable row level security;
alter table calendar_event        disable row level security;
alter table calendar_link         disable row level security;
alter table calendar_rule         disable row level security;
alter table band_platform_account disable row level security;
alter table band_calendar         disable row level security;
alter table band_check            disable row level security;
alter table audit_event           disable row level security;

drop function band_visible(uuid);

-- The role gigsynchub_app, its grants and its default privileges all STAY. Least privilege is worth
-- having for its own sake and has nothing to do with RLS: the app's connections still cannot alter a
-- table and still cannot read flyway_schema_history, so a mistake in the app cannot change the
-- schema. The app goes on connecting as it (set role, spring.datasource.hikari.connection-init-sql)
-- and AppRoleLogin goes on giving it its password after each migration. Every other database has a
-- limited-rights user too, so none of that stands in the way of moving.
