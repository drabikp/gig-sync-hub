-- Many bands (docs/multi-band-plan.md, phase 1): users, bands and who manages which; every band's
-- row names its band. Everything that existed belongs to band #1 (BandId.FIRST), the band the app
-- ran for until now; its name and slug are set on the first start from the configuration.

-- Who signed in through Rauthy (made at the first sign-in; rauthy_sub is Rauthy's user id).
create table app_user (
    id           uuid         not null primary key,
    rauthy_sub   varchar(255) not null unique,
    email        varchar(320),
    display_name varchar(100),
    created_at   timestamp(6) with time zone not null
);

-- A band the app manages; slug = its name in addresses (/b/<slug>, /gigs/<slug>).
create table band (
    id         uuid         not null primary key,
    slug       varchar(64)  not null unique,
    name       varchar(100) not null,
    created_at timestamp(6) with time zone not null
);

-- Who manages which band, as what (OWNER, MANAGER); display_name = how they appear in that band.
create table membership (
    user_id      uuid         not null references app_user (id) on delete cascade,
    band_id      uuid         not null references band (id) on delete cascade,
    role         varchar(20)  not null,
    display_name varchar(100),
    created_at   timestamp(6) with time zone not null,
    primary key (user_id, band_id)
);
create index membership_band on membership (band_id);

insert into band (id, slug, name, created_at)
values ('00000000-0000-0000-0000-000000000001', 'first', 'My band', now());

-- the catalog: a gig is the band's (the same day and venue in two bands are two gigs)
alter table gig add column band_id uuid;
update gig set band_id = '00000000-0000-0000-0000-000000000001';
alter table gig alter column band_id set not null;
alter table gig add constraint gig_band foreign key (band_id) references band (id) on delete cascade;
alter table gig drop constraint gig_pkey;
alter table gig add primary key (band_id, id);

alter table published_gig add column band_id uuid;
update published_gig set band_id = '00000000-0000-0000-0000-000000000001';
alter table published_gig alter column band_id set not null;
alter table published_gig add constraint published_gig_band foreign key (band_id) references band (id) on delete cascade;
alter table published_gig drop constraint published_gig_pkey;
alter table published_gig add primary key (band_id, platform, gig_id);

-- the sync outbox: each task is a band's (the worker still looks across bands for due work)
alter table sync_task add column band_id uuid;
update sync_task set band_id = '00000000-0000-0000-0000-000000000001';
alter table sync_task alter column band_id set not null;
alter table sync_task add constraint sync_task_band foreign key (band_id) references band (id) on delete cascade;
drop index sync_task_gig;
create index sync_task_gig on sync_task (band_id, gig_id);
-- the log goes with its task when a band is deleted
alter table sync_log drop constraint sync_log_task_id_fkey;
alter table sync_log add constraint sync_log_task foreign key (task_id) references sync_task (id) on delete cascade;

-- a band's own settings (e.g. its sync paused); app_setting stays for the app's own
create table band_setting (
    band_id       uuid          not null references band (id) on delete cascade,
    name          varchar(100)  not null,
    setting_value varchar(1000),
    primary key (band_id, name)
);
insert into band_setting (band_id, name, setting_value)
select '00000000-0000-0000-0000-000000000001', name, setting_value from app_setting where name = 'sync.paused';
delete from app_setting where name = 'sync.paused';

-- the calendar: rules shipped with the app (PRESET) are everyone's (band_id null), a band's own
-- rules (USER) and everything read from its calendar are the band's
alter table calendar_rule add column band_id uuid references band (id) on delete cascade;
update calendar_rule set band_id = '00000000-0000-0000-0000-000000000001' where origin <> 'PRESET';

alter table calendar_decision add column band_id uuid;
update calendar_decision set band_id = '00000000-0000-0000-0000-000000000001';
alter table calendar_decision alter column band_id set not null;
alter table calendar_decision add constraint calendar_decision_band foreign key (band_id) references band (id) on delete cascade;
alter table calendar_decision drop constraint calendar_decision_pkey;
alter table calendar_decision add primary key (band_id, event_uid);

alter table calendar_event add column band_id uuid;
update calendar_event set band_id = '00000000-0000-0000-0000-000000000001';
alter table calendar_event alter column band_id set not null;
alter table calendar_event add constraint calendar_event_band foreign key (band_id) references band (id) on delete cascade;
alter table calendar_event drop constraint calendar_event_pkey;
alter table calendar_event add primary key (band_id, event_uid);

alter table calendar_link add column band_id uuid;
update calendar_link set band_id = '00000000-0000-0000-0000-000000000001';
alter table calendar_link alter column band_id set not null;
alter table calendar_link add constraint calendar_link_band foreign key (band_id) references band (id) on delete cascade;
alter table calendar_link drop constraint calendar_link_pkey;
alter table calendar_link add primary key (band_id, event_uid);
drop index calendar_link_gig;
create index calendar_link_gig on calendar_link (band_id, gig_id);
