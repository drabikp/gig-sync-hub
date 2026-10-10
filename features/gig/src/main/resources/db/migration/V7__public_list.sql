-- A band's gigs at the public address /gigs/<slug> only when the band wants them there (off for a new band);
-- band #1 had them there before, so it keeps them.
alter table band add column public_list boolean not null default false;
update band set public_list = true where id = '00000000-0000-0000-0000-000000000001';
