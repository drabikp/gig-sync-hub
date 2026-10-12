-- Each band's own platform accounts and calendar (docs/multi-band-plan.md, phase 2). Secrets are
-- sealed with AES-256-GCM by the app (keys outside the database: bzscraper.accounts.data-keys);
-- key_id names the key, the row's band (and platform) is part of what was sealed.

-- A band's account on a platform: settings = what isn't secret (a login, the band's name there) as
-- JSON; secrets = the sealed JSON of its passwords / authenticator secret; the last sign-in test.
create table band_platform_account (
    band_id       uuid          not null references band (id) on delete cascade,
    platform      varchar(40)   not null,
    settings      text          not null,
    secrets       bytea,
    key_id        varchar(40),
    checked_at    timestamp(6) with time zone,
    check_ok      boolean,
    check_message varchar(1000),
    updated_at    timestamp(6) with time zone not null,
    primary key (band_id, platform)
);

-- A band's calendar: its private iCal address (sealed — it grants reading the whole calendar) and the
-- zone its times without one are in.
create table band_calendar (
    band_id    uuid          not null primary key references band (id) on delete cascade,
    ical_url   bytea         not null,
    key_id     varchar(40)   not null,
    zone       varchar(64)   not null,
    updated_at timestamp(6) with time zone not null
);
