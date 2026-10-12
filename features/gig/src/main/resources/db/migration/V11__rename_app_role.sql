-- The app's own database login follows the app's name: bzscraper_app -> gigsynchub_app.
--
-- It is renamed here rather than in V6, which created it, because V6 has been applied: Flyway
-- checksums every migration it has run, so changing one byte of V6 — a comment included — stops the
-- app from starting against any database that already has it. So V6 goes on creating the old name
-- and this undoes that, which is why a brand-new database makes a role only to rename it a moment
-- later. The grants, the row-level security policies and the default privileges all follow the role
-- itself (they hold its oid, not its name), so a rename is the whole of it.
--
-- `spring.datasource.hikari.connection-init-sql` and SPRING_DATASOURCE_USERNAME name the new role,
-- and Flyway runs as the owner on its own connection, so this has happened before the app's pool
-- first signs in. `AppRoleLogin` gives the role its password afterwards, as it always has.
do $$
begin
    if exists (select from pg_roles where rolname = 'bzscraper_app') then
        alter role bzscraper_app rename to gigsynchub_app;
    elsif not exists (select from pg_roles where rolname = 'gigsynchub_app') then
        -- a database whose V6 somehow left no role (it cannot, but a missing login is a dead app)
        create role gigsynchub_app nologin;
        grant gigsynchub_app to current_user;
        grant usage on schema public to gigsynchub_app;
        grant select, insert, update, delete on all tables in schema public to gigsynchub_app;
        grant usage, select on all sequences in schema public to gigsynchub_app;
        alter default privileges in schema public
            grant select, insert, update, delete on tables to gigsynchub_app;
        alter default privileges in schema public grant usage, select on sequences to gigsynchub_app;
        revoke all on flyway_schema_history from gigsynchub_app;
    end if;
end $$;
