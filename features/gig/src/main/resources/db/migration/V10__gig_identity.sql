-- A gig's identity stops being something it says about itself. Until now it WAS the day and the
-- venue ("2026-10-01|klub 007"), so moving a gig to another day changed who it was, and every row
-- that named it — its publications, its queued platform work, the calendar's link — had to be
-- re-keyed in the same transaction, with listeners to carry the change across features. From here a
-- gig has an id of its own and keeps it; the day and the venue stay, as the MATCH key, which is
-- what they are actually needed for: a platform's event carries no id of ours, so Import and the
-- nightly check recognise a copy by the day and the place.

-- 1. One identity per gig key, including the keys of gigs that are already gone: a published_gig
--    record and a delete task outlive their catalog row on purpose, so they have no gig to take an
--    identity from and mint their own. All three tables must agree on it — a delete task has to go
--    on naming the publication it is removing — which is why this is one table and not three
--    separate updates.
create table gig_identity (
    band_id uuid         not null,
    gig_key varchar(255) not null,
    id      uuid         not null default gen_random_uuid(),
    primary key (band_id, gig_key)
);

insert into gig_identity (band_id, gig_key)
select band_id, id from gig;

insert into gig_identity (band_id, gig_key)
select band_id, gig_id
from (select band_id, gig_id from published_gig
      union
      select band_id, gig_id from sync_task
      union
      select band_id, gig_id from calendar_link) named
where not exists (select 1 from gig_identity i where i.band_id = named.band_id and i.gig_key = named.gig_id);

-- 2. The gig itself: the old key becomes match_key, and the identity becomes a column of its own.
--    unique (band_id, match_key) is the rule that used to be the primary key and still holds — one
--    gig per band per day and venue — so a gig moved onto another's day is refused, not merged.
alter table gig rename column id to match_key;
alter table gig add column id uuid;

update gig g set id = i.id
from gig_identity i
where i.band_id = g.band_id and i.gig_key = g.match_key;

alter table gig alter column id set not null;
alter table gig drop constraint gig_pkey;
alter table gig add primary key (band_id, id);
alter table gig add constraint gig_match unique (band_id, match_key);

-- the list's index walks (band_id, show_at, id); it followed the rename, so it is rebuilt on the id
drop index gig_band_show;
create index gig_band_show on gig (band_id, show_at, id);

-- 3. Everything that names a gig names that identity now. Dropping each old column takes its index
--    with it, so each is created again on the new one.
alter table published_gig add column gig_identity uuid;
update published_gig p set gig_identity = i.id
from gig_identity i
where i.band_id = p.band_id and i.gig_key = p.gig_id;
alter table published_gig drop constraint published_gig_pkey;
alter table published_gig drop column gig_id;
alter table published_gig rename column gig_identity to gig_id;
alter table published_gig alter column gig_id set not null;
alter table published_gig add primary key (band_id, platform, gig_id);
create index published_gig_band_gig on published_gig (band_id, gig_id);

alter table sync_task add column gig_identity uuid;
update sync_task t set gig_identity = i.id
from gig_identity i
where i.band_id = t.band_id and i.gig_key = t.gig_id;
alter table sync_task drop column gig_id;
alter table sync_task rename column gig_identity to gig_id;
alter table sync_task alter column gig_id set not null;
create index sync_task_gig on sync_task (band_id, gig_id);

alter table calendar_link add column gig_identity uuid;
update calendar_link l set gig_identity = i.id
from gig_identity i
where i.band_id = l.band_id and i.gig_key = l.gig_id;
alter table calendar_link drop column gig_id;
alter table calendar_link rename column gig_identity to gig_id;
alter table calendar_link alter column gig_id set not null;
create index calendar_link_gig on calendar_link (band_id, gig_id);

drop table gig_identity;

-- The band's last platform check names its gigs by the old key, inside its JSON. It is a kept
-- answer, not a source of truth: the nightly run writes a new one, and the page's own button asks
-- for one at any time. So it goes rather than being rewritten here.
delete from band_check;
