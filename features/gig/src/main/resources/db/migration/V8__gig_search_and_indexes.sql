-- The gig list is a QUERY now, not the whole table shipped to the browser and filtered there: the
-- period, the text and the order live in the database, and a page is read by keyset. This is what
-- makes it read a page's worth of rows rather than a band's.

-- When the BAND plays, which is what tells upcoming from past (Gig.isPast reads the show day, i.e.
-- the slot when a longer event has one) and what the list is ordered by. Generated, so no writer can
-- forget it, and a plain column, so the keyset (show_at, id) > (?, ?) is an index range scan.
alter table gig
    add column show_at timestamp(6) generated always as (coalesce(slot_start, start_date_time)) stored;

create index gig_band_show on gig (band_id, show_at, id);

-- What the list searches: title, venue and city folded as TextFold folds them (lower case, no
-- accents, single spaces) so that "kosice" finds "Košice". The app writes it with every save; this
-- fills what is already there, unaccent standing in for the same folding.
alter table gig add column search_text varchar(800) not null default '';

create extension if not exists unaccent;

update gig
set search_text = trim(regexp_replace(
        lower(unaccent(coalesce(title, '') || ' ' || coalesce(venue, '') || ' ' || coalesce(city, ''))),
        '\s+', ' ', 'g'));

-- A substring search ("like '%brno%'") cannot use a btree, so a trigram index is what keeps it off a
-- sequential scan as a band's catalog grows. Both the extension and the index are optional: without
-- them the search still answers, it just scans the one band's rows — so a deployment whose database
-- user may not create extensions keeps working rather than failing to start.
do $$
    begin
        create extension if not exists pg_trgm;
        create index gig_search on gig using gin (search_text gin_trgm_ops);
    exception
        when insufficient_privilege or feature_not_supported or undefined_file then
            raise warning 'no pg_trgm: the gig search will scan each band''s rows instead of a trigram index';
    end
$$;

-- A gig's publications are read BY GIG (the list's platform states, a gig's own page). The primary
-- key (band_id, platform, gig_id) has the platform in the middle, so it cannot serve that lookup.
create index published_gig_band_gig on published_gig (band_id, gig_id);
