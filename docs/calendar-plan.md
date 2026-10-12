# Band calendar: classifying events (plan)

Bands keep gigs in a shared calendar next to everything else (rehearsals, travel,
absences, calls). The app reads that calendar and sorts its events into **gig / not sure
/ not a gig**, so gigs can be linked, checked against the platforms and published. The
calendar is only read, never written, and its format is whatever the band already uses.

Every band writes its calendar differently, so the code knows only *kinds* of rules; the
band's conventions are **data** — a band profile of weighted rules.

## Step 1 — classifier + band profile (implemented)

- **Reading**: `IcsCalendarFeed` fetches the calendar's private iCal address
  (`gigsynchub.calendar.ical-url`, a secret) and `IcsParser` turns it into
  `CalendarEvent`s: uid, title, location, notes (HTML → text), start/end in the band's
  zone, all-day, repeating, shown-as-free, the calendar's own status, last modified.
- **Profile**: a list of `ProfileRule(kind, value, weight, origin, enabled)`. `value`
  may hold alternatives separated by `|`; a rule fires at most once. Origin is
  `PRESET` (shipped with the app), `LEARNED` (step 2) or `USER`.
  - Weighted kinds: `TITLE_STARTS_WITH`, `TITLE_CONTAINS`, `MEMBER`, `NOTES_LABEL`,
    `NOTES_CONTAIN`, and the language-independent `REPEATING`, `LONGER_THAN_DAYS n`,
    `ALL_DAY_FREE`, `TITLE_REPEATED n`, `CATALOG_GIG_SAME_DAY`, `TRAVEL_LEADS_TO`.
  - Role kinds (no weight): `TRAVEL` (which titles are travel), `CANCELLED_TITLE`,
    `CANCELLED_NOTES`, `TENTATIVE_TITLE`, `TENTATIVE_NOTES`, `CONFIRMED_FIELD`
    (`stav=potvrd`: a `Stav:` field not saying "potvrd…" means tentative).
- **Decision order** per event: the user's remembered verdict (by uid) → score.
  Score ≥ `gig-score` (4) → GIG; ≤ `not-gig-score` (−1) → NOT_GIG, except that an event
  with some evidence for a gig and none strongly against (no rule ≤ `strong-negative`,
  −4) is never silently dropped (→ NOT SURE). `CATALOG_GIG_SAME_DAY` doesn't count when
  a strong negative fired (travel on a gig day stays travel). Every fired rule is kept as
  a reason. Status (confirmed / tentative / cancelled) is decided separately.
- **Where the rules come from**: `classpath:calendar/base.profile` (language-independent
  weights) and the presets in `gigsynchub.calendar.presets` (e.g. `sk-cz`) are re-synced
  as `PRESET` rules on every start; the band's own file
  (`gigsynchub.calendar.profile-file`, default `./data/calendar-profile.txt`, never in git
  — it holds members' names) is re-synced as `USER` rules. Until the rules editor exists
  (step 3) that file is where a band edits its own rules. Format: one rule per line,
  `KIND weight value`, `#` comments.
- **Remembered decisions**: table `calendar_decision(event_uid, verdict, decided_at)`.
- **UI** `/calendar`: read the calendar, filter by result, mark Gig / Not a gig / undo,
  see why, see the rules.

## Step 1b — into the catalog, and tracking changes (implemented)

- **Saved copy**: every read is stored (`calendar_event`) and compared with the previous
  one — new, changed (which fields; "was not sure, now a gig"), removed, returned — until
  the user marks it seen. The first read is the baseline.
- **Add to catalog**: a gig event the catalog doesn't have opens the gig form pre-filled
  (`CalendarGigDrafter`): show time from `SHOWTIME_LABEL` notes labels, venue/city/country
  from the map-style place. The private notes are shown next to it, never copied.
- **Links** (`calendar_link`): an added gig is linked to its event; existing catalog gigs
  are linked by day (one at a time, or in bulk where the day has exactly one catalog gig).
- **Changes to linked upcoming gigs** (`CalendarCatalogMatcher`): another day / show time
  → Update; cancelled in the calendar → Cancel; gone from the calendar → Cancel / Delete /
  Keep. Always the user's click, through the catalog — the platforms follow via the outbox.
- Next: read the calendar on a schedule (server) so changes arrive without opening the page.

## Step 2 — learn the profile from the band's history

1. **Self-labelling** from the catalog's platform-linked gigs: for each platform gig,
   the ONE calendar event that day with the most title words (then location words) in
   common is a gig; the other events that day are not; events with no platform gig within
   ±1 day are not. One-to-one matters: labelling every matching event made travel
   ("cesta") look like a gig. The user's remembered decisions are added as labels and
   win over self-labels.
2. **Candidate features** from labelled events: first word of the title, note labels
   (`word:` at a word start), all-day, repeating, has location, title repeated.
3. **Weights** = log-odds of the feature on gigs vs non-gigs (with +0.5 smoothing),
   scaled and rounded to the integer weights; a feature needs ≥ 4 occurrences. **Title
   words are only learned as evidence AGAINST a gig** — gig titles are unique, so title
   words that look like gig evidence are tour names (`traktor`, `klub`) and overfit.
   Evidence FOR a gig comes from structure (note labels, location, catalog gig that day).
4. Learned rules are stored with origin `LEARNED` (replaced on every re-learn);
   `USER` rules are never changed by learning; a `PRESET` rule the learner contradicts
   is reported, not changed.
5. **Calibrate extraction**: the note label whose time equals the platform start time is
   the band's show-time label (Eufory: `showtime` 10×, `čas predbežne` 4×) → rule kind
   `SHOWTIME_LABEL` (exists since step 1b, preset values; learning would add the band's own).
6. **Backtest** before saving: run the new profile over the history and show "of your N
   known gigs this finds X, misses Y, flags Z others"; refuse silently worse profiles —
   show the difference and let the user choose.
7. Re-learn on demand and after N new decisions. Prototype on Eufory (437 events): the
   learner found `skúška`, every member's name, `porada`, `call`, `reh`, repeating and
   all-day as not-a-gig evidence, and `príjazd:`, `pokec:`, `cena:`, `soundcheck:`,
   `showtime:` … as gig evidence — without any Slovak built in. A title used 4+ times:
   0 of 77 gigs, 121 of 273 others.

New kinds possibly needed: `HAS_LOCATION`, `TITLE_COLOR`/`CALENDAR`
(only with Google sign-in — the iCal export carries no colors).

## Step 3 — setup wizard and presets, for any band

1. **"How do you mark gigs?"** — a separate gig calendar (every event is a gig: one
   rule), a fixed marker (prefix / emoji / color: one rule), or no convention (learning).
2. Connect the calendar (iCal address; Google sign-in only if colors or several
   calendars are needed). Pick a language preset (`sk-cz`, `en`, `de`, …) as the start.
3. Read the platform history (the existing import), run step 2's learning.
4. Ask 10–15 questions — the events the profile is least sure about (score nearest the
   thresholds), plus "these names start many all-day events — band members?".
5. Show the profile as sentences ("Titles starting with *Skúška* are not gigs — seen 64×,
   never a gig") with the backtest; the band can switch off or edit any rule.
6. **Rules editor** (replaces the profile file; the file stays as import/export) and
   "Ignore all like this" from the inbox (adds a `USER` `TITLE_STARTS_WITH` rule).
7. Bands with no platform history: preset + more questions up front, then learning from
   inbox answers.

## Running for several bands

One app instance per band (the current shape): its own database, logins, calendar
address and profile. A shared multi-band service would need a band id on every table,
accounts, and many bands' platform logins and browser sessions on one server (block
risk on Bandsintown) — not planned. Still CZ/SK-specific elsewhere: `CityName`, Bandzone.

## Privacy

Calendar notes (fees, phone numbers, accommodation) are never published and never
copied into gig descriptions; they are shown in the app as private notes only. The
profile file holds members' names and lives in `./data`. Tests use made-up events; the
live test (`CAL_LIVE=true`) reads the real calendar locally only.
