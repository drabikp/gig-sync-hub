-- Each band's latest platform check (multi-band plan, phase 3): kept, so a restart doesn't forget what
-- the platforms differ in. result = the check as JSON (its differences, the platforms not read, the
-- platforms' events no gig is published as).
create table band_check (
    band_id    uuid not null primary key references band (id) on delete cascade,
    result     text not null,
    checked_at timestamp(6) with time zone not null
);
