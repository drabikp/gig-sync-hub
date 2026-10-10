-- Row-level security (docs/multi-band-plan.md, phase 4): every band's table shows and takes only the rows of
-- the band the work is about — a net under the app's own band checks. The app's connections work as the role
-- bzscraper_app (no superuser, so the policies hold; the pool switches to it: SET ROLE), each told at checkout
-- which band's rows it may see: app.band = a band's id, '' for none, '*' for every band's (the app's own work
-- across bands). The migrations stay the owner's.

do $$
begin
    if not exists (select from pg_roles where rolname = 'bzscraper_app') then
        create role bzscraper_app nologin;
    end if;
end
$$;
grant bzscraper_app to current_user;
grant usage on schema public to bzscraper_app;
grant select, insert, update, delete on all tables in schema public to bzscraper_app;
grant usage, select on all sequences in schema public to bzscraper_app;
alter default privileges in schema public grant select, insert, update, delete on tables to bzscraper_app;
alter default privileges in schema public grant usage, select on sequences to bzscraper_app;
revoke all on flyway_schema_history from bzscraper_app;

-- may the work see (and write) a row of this band?
create function band_visible(row_band uuid) returns boolean
    language sql stable
as $$
    select current_setting('app.band', true) = '*' or row_band::text = current_setting('app.band', true)
$$;

do $$
declare
    t text;
begin
    foreach t in array array['gig', 'published_gig', 'sync_task', 'band_setting', 'calendar_decision',
                             'calendar_event', 'calendar_link', 'band_platform_account', 'band_calendar',
                             'band_check', 'audit_event']
    loop
        execute format('alter table %I enable row level security', t);
        execute format('create policy band_rows on %I using (band_visible(band_id)) with check (band_visible(band_id))', t);
    end loop;
end
$$;

-- a task's log goes with its task
alter table sync_log enable row level security;
create policy band_rows on sync_log
    using (exists (select 1 from sync_task t where t.id = sync_log.task_id))
    with check (exists (select 1 from sync_task t where t.id = sync_log.task_id));

-- the shipped calendar rules (no band) are everyone's to read; only the app's own work writes them
alter table calendar_rule enable row level security;
create policy band_rows on calendar_rule
    using (band_id is null or band_visible(band_id))
    with check (case when band_id is null then current_setting('app.band', true) = '*' else band_visible(band_id) end);
