# Review follow-up plan (2026-10-08)

Two independent reviews (architecture; code level) found 16 things to change. Plus one
rule from the owner: **domain and application are platform-agnostic** — no Bandzone or
Bandsintown names, branches, formats or traits in them; everything a platform is or can do
comes from its adapter. Done in phases, the build green after each.

> The paths and package names below are those of the layered modules this plan was carried out
> in (`domain/`, `application/`, `adapters/*`). The code has since been reorganized into vertical
> slices (`features/*`, `platforms/*` — see CLAUDE.md, **Structure**); the rules this plan set up
> are now checked by `ArchitectureTest` and `PlatformNamesTest`.

## Phase 1 — a platform-agnostic core (review 6, 7; the rule)

**Problem.** `Platform` is an enum of the two platforms, and the core branches on it:
`Reconciler` (no cancelled state, shows the band's slot), `GigMerge` (no admission info),
`ImportPlanner`/`ImportProposal` (labels, precedence = enum order), names in four places,
event URLs in the UI. Bandzone/Bandsintown session and client interfaces and their two
exception copies sit in `application/port/out` though only their adapters use them. The
legacy scrape (`GigSummary`, `GigSummaryToGigMapper` parsing "Hořice, ČR", `GigProvider`,
`GetGigsForBandUseCase`, `BandNotFoundException`) and the "Bandsintown-compatible CSV" use
case are platform features inside the core.

**Change.**
- `Platform` becomes a value object (`Platform.of("BANDZONE")` — the id stored in the DB, so
  no migration). The core never names one.
- `PlatformTraits` (domain record): id, display name, `keepsCancelledEvents`,
  `listsBandSlot`, `carriesAdmission`, `importPrecedence`, event URL template. Each adapter
  module contributes its traits as a bean; `Platforms` (application) is the registry —
  ordering, names, traits. `Reconciler`, `GigMerge`, `ImportPlanner`, `SyncRequests`, the
  engine's messages and the UI (labels, links, platform pickers) read traits.
- Session/client interfaces and their exceptions move into the adapters
  (`BitSession`, `BitPortalClient`, `BandzoneSession`, `BandzonePortalClient`; the two
  exceptions become adapter subclasses of one `PlatformException`). The application's
  platform contract: `SyncStep`, `GigImporter`, `GigExporter`, `PlatformException` +
  `FailureKind`, `PlatformTraits`.
- The scrape types move into the Bandzone adapter (its importer is the only user). The REST
  endpoint serves the CATALOG (the source of truth) in the same JSON shape, and only for the
  configured band — no more live scraping of any band for anyone.
- CSV: `GigExporter` port (platform, file name, content); the Bandsintown CSV is the
  Bandsintown adapter's exporter; the catalog offers "Download for <platform>" per exporter.
- Test fixtures name platforms only in tests (`TestPlatforms`).

**Check.** `grep -ri 'bandzone\|bandsintown' domain/src/main application/src/main` is empty (now:
`PlatformNamesTest` over `features/*/src/main`).

## Phase 2 — data integrity (review 1, 2, 3)

**1. One write path for gigs.** `SaveGigUseCase.save` (an upsert) overwrites a gig with the
same date and venue without queueing platform work; an edit can move a gig onto another
gig's identity, overwriting it and its platform links; Import repeats the identity move
only partly (no tasks, no calendar links, no update queued, no stale check).
- `CatalogWrites` (application, package-private): `add(gig)` refuses an existing identity
  (`GigIdentityTakenException`); `replace(seen, updated)` = stale check, collision check,
  identity move of gig + records + tasks + calendar links, save, queue update. Used by the
  catalog use cases AND Import apply (with the plan-time gig as `seen`).
- Considered, not done *then*: a surrogate gig key. A deleted gig's platform records must outlive
  its row until the platform delete succeeds, so it needs soft deletes across four tables and a
  migration of the real database; with collisions refused and one write path, the identity
  move is safe and tested.
  **Done in 2026-10 (V10), and the objection turned out not to hold**: records that outlive their
  gig need no soft delete, because they simply go on naming an identity no gig row has — exactly as
  they used to go on naming a key no gig row had. The migration gave one identity to every gig key
  any row named, the gone ones included, so a delete task still names the publication it is
  removing. What the natural key was really needed for is matching, and that is `GigMatch`.

**2. "Not while it syncs" in the application.** Only two views check it. Catalog changes,
re-sync, import apply and "forget link" refuse a gig with a RUNNING task (`GigBusyException`);
the views keep their check only to grey out buttons. The engine re-reads a task's current gig
id before recording a result.

**3. Task status races.** Allowed transitions live in the domain (`SyncStatus.canBecome`); the
outbox applies a change only from an allowed state (a DONE/DISCARDED task stays so) and
`sync_task.version` (V11) makes concurrent writes fail instead of overwriting. H2's
`AUTO_SERVER` (a second process could run a second worker) becomes opt-in
(`bzscraper.db.options`).

## Phase 3 — failure semantics and browsers (review 4, 5, 8, 9)

**4.** `Created`/`Edited` carry a `FailureKind` — no more matching of message text in the
Bandsintown steps; a refused upload is REFUSED.
**5.** Bandsintown setup problems (no login, no authenticator secret, several artists, no
artist id) are NEEDS_USER.
**9.** `PlatformBrowser` (browser module): the one-browser lock, the warm browser, Chromium
options, a profile per account (now also for Bandsintown — one new login), screenshots, and
one `guarded` wrapper. Both Selenium clients keep only their login.
**8.** Reads (Import, the platform check) wait for the browser; sync steps don't: a busy
browser is `FailureKind.BUSY` → a new outcome POSTPONED — tried again in a minute, no attempt
used, not counted by the breaker.

## Phase 4 — wiring and structure (review 10, 11, 13, 14)

**10.** Pause survives a restart (`app_setting` table, V11; `SettingsStore` port).
**11.** `Clock` everywhere: views, stores, backup, the check schedule, the Bandsintown session
and authenticator code.
**13.** Typed configuration: `@ConfigurationProperties` records per module, validated at start
(credentials required when a platform is on); no band name as a code default.
**14.**
- `SyncStep` defaults (`batchSize` 1, no refusal) and a one-by-one base step; Bandsintown
  removal by a reason enum and a way enum instead of two booleans.
- `WorkflowEngine` split: `StepRegistry` (steps, first step taking a gig, admission,
  remove-and-create), moot rules in the domain, the breaker behind `PlatformHealth`.
- No test-only constructors; the `@Lazy` cycle goes: `SyncSignal` (application) is the
  trigger, the worker subscribes to it.

## Phase 5 — errors, UI rules, tests, cleanup (review 12, 15, 16)

**12.** The calendar's filters ("needs a look", recent, upcoming) and counts move into the
domain as `CalendarFilter` with tests; the view only lays out.
**15.**
- User-facing errors: `UserFacingException` (application) for the catalog rules
  (changed meanwhile, busy, identity taken, calendar unavailable); importers throw
  `PlatformException`; the views share one way to show them; programming errors are logged,
  not shown raw.
- `BandzoneHtmlParser` without NPE-as-control-flow.
- Tests: a shared headless-Chromium helper (browser test-jar, one browser per test class,
  paths from properties); `SyncFakes` in the application test-jar and an outbox contract
  test run against the fake and JPA; tests for `CalendarChanges` (RETURNED), `Workflows`,
  removal refusals, `PlaceService`, the lineup page.
**16.** Remove dead code (`GigQueryService` + its use cases, `GigDateFilter`,
`BitSession.updateEvent`, the always-false retry parameter); `notes.txt` leaves the jar (to
`docs/`); stale Javadocs and CLAUDE.md fixed.

## Verification

Full suite after each phase; the app restarted and its pages opened; Bandzone live tests on the
test band; a read-only Bandsintown check. Bandsintown write paths: the live test needs the
owner's approval (real account).

## Status (2026-10-08)

All five phases are done; the full suite is green. Where the work differs from the plan:

- **3.** The task version and `app_setting` are V12 (V11 turned `published_gig.platform` from an
  H2 enum into text — platform ids come from adapters now). A DISCARDED task can still go
  back to PENDING, but only through the user's Retry.
- **8.** Busy = a sync step finds the platform's browser held by another operation; a read
  (Import, the check) waits up to 10 minutes (`BitPortalClient.READ_WAIT`).
- **9.** The Bandsintown profile is per account now (`~/.bzscraper/bandsintown-browser/<hash>`):
  the first run after this logs in once with the password and authenticator code.
- **13.** The legacy REST endpoint keeps reading `bzscraper.bandzone.band-slug` with `@Value`
  (the rest module must not depend on the Bandzone module).
- **15.** The contract test found the fake outbox's `advance` ignoring the status guard; fixed.
  Inherited contract tests run against JPA without a rolled-back transaction, so they empty
  the tables around each test.
- **16.** `PlatformResult`, `BandNotFoundException` and `BitSession.updateEvent` are removed
  as well; the redundant "takes every gig" `refusal` overrides are gone (`SyncStep`'s default).
