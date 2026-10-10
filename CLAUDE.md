# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
docker/make-secrets.sh                                 # Once (and after changing BZ_DOMAIN before Rauthy's first start): ./secrets
docker compose up -d --build                           # Run everything: PostgreSQL, the app, Rauthy (the sign-in), Caddy
                                                       #   → https://$BZ_DOMAIN (default https://localhost), daily backups
docker compose logs -f app                             # The app's log
./mvnw clean install                                   # Build every module (incl. the page: Node is fetched once into web/frontend/node)
docker compose up -d db && SPRING_DATASOURCE_PASSWORD=$(cat secrets/db_password) \
  ./mvnw -pl app -am spring-boot:run                   # Run outside Docker (port 8080; working dir = repo root; the db on localhost:5432)
./mvnw test                                            # Run all tests (Java + the page's Vitest tests) — needs Docker (Testcontainers)
./mvnw test -Dtest=GigPublishingServiceTest -Dsurefire.failIfNoSpecifiedTests=false   # One class
./mvnw -pl platforms/bandzone -am test                 # One module (and what it needs)
cd web/frontend && npm run dev                         # The page with hot reload on :5173 (API proxied to :8080)
cd web/frontend && npm test                            # The page's tests only
```

**Sign-in: Rauthy** (OpenID Connect, `ghcr.io/sebadob/rauthy`, compose service `rauthy`), served at
`https://$BZ_DOMAIN/auth/` by Caddy — the same origin as the app. Who may sign in is Rauthy's: the
members of its group `bzscraper` (the app's client has `restrict_group_prefix`); every `/api` address
needs a sign-in. Which bands a signed-in user sees is the app's: their **memberships** (see **Bands**
below; a user is made at their first sign-in, `OidcCurrentLogin` → `CurrentLogin`). The page sends the browser to
`/oauth2/authorization/rauthy` (code flow with PKCE, confidential client `bzscraper`); Rauthy sends
it back to `/login/oauth2/code/rauthy`, then to `/` (the page goes back where the user was,
`lib/signIn.ts`). The app's session: 12 h; Rauthy's own session signs the user in again without a
password. Signing out (`POST /api/auth/logout`) ends both: the answer names Rauthy's end-session
address and the page goes there. `SecurityConfiguration` builds the client from `AuthProperties`
(no discovery at start): the browser goes to the issuer (`bzscraper.auth.issuer`,
`https://$BZ_DOMAIN/auth/v1/`), the app fetches tokens, keys and user info inside the network
(`bzscraper.auth.internal-url`, `http://rauthy:8080/auth/v1/`); the ID token's issuer is checked.
Rauthy: `docker/rauthy/config.toml` (not secret; each secret is `'$SECRETS'`, from its secrets file
`secrets/rauthy.toml`), its data in the `rauthy` database on the app's PostgreSQL (made by the
one-shot service `rauthy-db`, backed up with the app's), its cache in `./auth-data`; it runs as
`BZ_UID` (it reads the owner-only secrets). **First start**: Rauthy reads `secrets/rauthy-bootstrap`
(the group and the app's client for `https://$BZ_DOMAIN`, made by `docker/make-secrets.sh`) into its
EMPTY database, and prints the first admin's (`BZ_ADMIN_EMAIL`) password once in its log. Then, in
Rauthy's admin (`https://$BZ_DOMAIN/auth/v1/admin` — admins need MFA: register a passkey first),
add yourself and the members to the group `bzscraper`. A later change of `BZ_DOMAIN` = change the
client's redirect / post-logout addresses there (the bootstrap is never read again).

**Docker** (`Dockerfile`, `compose.yaml`): the image builds the app from the sources (Java 21 +
Chromium + chromedriver from Debian, a non-root user whose uid/gid match the host's, `BZ_UID`/`BZ_GID`,
default 1000); compose runs `db` (PostgreSQL 18, volume `db-data`, on `127.0.0.1:5432` for development),
`app`, `rauthy-db` + `rauthy` (the sign-in), `proxy` (Caddy) and `backup` (`docker/backup.sh`:
`pg_dump` of the app's and Rauthy's databases on start and daily into `./data/backups`, the newest 14
of each kept). **Secrets are files** under `./secrets` (git-ignored, `600`; `docker/make-secrets.sh` makes
them: random ones — the database passwords, Rauthy's secrets, the app's client secret, **`data_keys`** (the
key every band's secrets are sealed with in the database: back it up apart from the database dumps, lose it
and they can't be read) — and band #1's old logins from `.bz-creds`, `.bit-test-creds`, `calendar`, which
the app keeps once in the database and doesn't read after),
given to the app as Docker secrets at `/run/secrets/<property name>` and read by
`spring.config.import=optional:configtree:/run/secrets/` — never environment variables (Rauthy gets its
secrets file the same way, `SECRETS_FILE`). Whether this server drives the platforms, band #1's
old band slug and artist name, and this machine's settings (`BZ_DOMAIN`,
`BZ_APP_BIND`, `BZ_SUBNET`, `BZ_UID`/`BZ_GID`) are in the git-ignored `.env` (`.env.example`) —
compose reads it for its own `${…}` and passes it to the app. `./data` is mounted at `/app/data` (the
band's calendar profile, backups), the platforms' saved logins live in the volume `browser-profiles`.
`.dockerignore` is an allowlist: only the sources reach the build. The containers' network has a
fixed subnet (`BZ_SUBNET`, default `10.201.0.0/24`): Docker's own 172.x pick collided with a VPN's
routes, and the published port then reset every connection. The app has its own name server
(`BZ_DNS`, default `1.1.1.1`): Docker's DNS forwarding to a VPN's name servers timed out, so the
platforms, the calendar and the town search couldn't be reached.

**The way in** (compose service `proxy`, always on — signing in needs it): Caddy (`docker/Caddyfile`)
on 80/443 gets and renews the Let's Encrypt certificate for `BZ_DOMAIN` itself (DNS must point at the
server), redirects HTTP to HTTPS, sets HSTS, routes `/auth/*` to Rauthy and the rest to `app:8080`,
compresses everything but the live updates (`/api/bands/*/events`, passed through unbuffered).
`BZ_DOMAIN=localhost` (the default) runs it locally with Caddy's own certificate (the browser warns
once, or trust Caddy's root). The app's own port is published on `127.0.0.1` only (health, debugging;
`BZ_APP_BIND=0.0.0.0` opens it to the local network). `server.forward-headers-strategy=native`: Tomcat
believes `X-Forwarded-*` only from private addresses, so behind the proxy requests count as HTTPS —
Rauthy is told to send the user back over HTTPS and the session and CSRF cookies are `Secure`
(`ProxyHeadersTest`, a real server).

**Structure: vertical slices.** One Maven module per feature, each holding its own domain,
application (use cases + ports) and adapters (persistence, REST API, workers) in packages
`sk.drabikp.bzscraper.<feature>.{domain, application, application.port.in|out, adapter.in.*,
adapter.out.*, config}`. `gig` is the shared kernel; platforms plug into the features' ports;
the page is a separate React app (`web/frontend`) over the features' REST APIs.

```
features/gig        shared kernel: Gig + its value objects (GigId.key() = how records refer to a
                    gig, GigId.token() = in addresses; Gig.fingerprint() = a revision's text; Address,
                    Coordinates), GigDraft (a gig as the form holds it: When / Where / About, its
                    Problems), Platform (compared with equals — never ==)/PlatformTraits (built with
                    PlatformTraits.of(…)…build())/Platforms, Publication, GigRepository/PublishedGigStore/
                    Transactions/LiveUpdates, UserFacingException (ErrorCode + args), ErrorCode/GigErrorCode, InvalidGigException,
                    ConcurrentChangeException (changed()/deleted()), NotFoundException,
                    BadRequestException, UnknownReferenceException (a token or id naming nothing),
                    TextFold, BandId (a band: every port that touches a band's data takes it), AccountField
                    (a field of a band's account on a platform, in its PlatformTraits); their JPA
                    persistence, SpringTransactions, the H2 import, the Clock; THE schema (db/migration,
                    Flyway); test-jar: TestGigs, TestBands (BAND = band #1, OTHER), TestPlatforms, TestLiveUpdates
features/accounts   users, bands, memberships (User, Band — id, slug, name —, Membership, Role OWNER/MANAGER);
                    UsersUseCase (the signed-in user, made just in time from CurrentLogin), BandsUseCase,
                    MembersUseCase, BandAccess (a user's role in a band), BandDirectory (every band, by slug);
                    a band's platform accounts (PlatformAccount: settings + secrets by the platform's
                    AccountField names, last sign-in test): PlatformAccounts (the platforms read them; adopt =
                    band #1's from the configuration, once), PlatformAccountsUseCase (the page: owners connect,
                    members test), port.out PlatformAccountCheck (the platform's sign-in test); SecretBox
                    (AesGcmSecretBox: AES-256-GCM, keys bzscraper.accounts.data-keys, sealed for its row;
                    rotation = a new key first, everything sealed again on start); /api/bands,
                    …/platform-accounts; test-jar: AccountsFakes (+ Accounts: in-memory accounts, TEST_KEYS)
features/places     Town (coordinates null when only typed), TownChoice, place search (Photon); /api/places
features/sync       outbox (SyncOutbox = SyncTaskQueries + the writes; SyncTask…, PlatformState: a gig's
                    state on a platform), queueing (QueueSyncWork), read-only state (SyncStateUseCase),
                    workflow engine (WorkflowEngine → RunPreparation, OutcomeSettlement, RecreateStep;
                    a step's unexpected fault → SyncFaults, logged in full) + step ports (SyncStep,
                    OneAtATimeStep), breakers, pause, settings, worker; /api/sync; test-jar: SyncFakes
                    (its outbox gives platforms back as new instances, like the database), SyncOutboxContract
features/catalog    catalog use cases, GigWrites (← CatalogWrites), GigMovedListener, GigExporter;
                    …/gigs, …/exports, /api/platforms; test-jar: CatalogFakes
features/importing  import planner/merge, GigImporter port (+ PlatformReads); /api/import (reads in the background)
features/check      Reconciler, platform check service + nightly schedule; /api/check
features/calendar   calendar domain (+ domain.event: the calendar's events; + domain.rules: profile
                    and classifier), review service, stores, iCal feed, profile file; /api/calendar;
                    shipped presets (calendar/*.profile)
platforms/browser   PlatformBrowser (every lease names its account — a band's login — and gets that
                    account's profile; a warm browser only for the same account), BrowserProfile,
                    WarmBrowser, properties; test-jar: TestChromium
platforms/bandzone      bandzone (traits, properties, exception) · scrape · portal (client API, stub)
                        · portal.selenium (the Selenium client + page objects) · step · rest
                        (the public `/gigs/{band_slug}` endpoint — any band, by its slug)
platforms/bandsintown   bandsintown (traits, properties, exception, RemovalReason) · portal ·
                        portal.selenium (+ the event list → gig mapping) · step · importing · csv
web/live            LiveUpdates as server-sent events (SseLiveUpdates, GET /api/bands/{bandId}/events: a
                    band's changes to its pages, changedEverywhere to all) — the only place that knows
                    SSE; everyone else tells the LiveUpdates port
web/frontend        the page: React + TypeScript + Vite + Mantine 8, TanStack Query, React Router,
                    i18next (English + Slovak), a PWA (vite-plugin-pwa); built into the jar's static/
app/                BzscraperApplication; web (security: sign-in through Rauthy (OIDC), session + CSRF
                    for a single-page app; OidcCurrentLogin; BandAccessInterceptor: a band's addresses
                    for its members; ApiErrors; SinglePageApp: the page's own addresses load
                    index.html); application.properties; ArchitectureTest, PlatformNamesTest,
                    ApiSecurityTest, BandIsolationTest, ErrorCodesTest
```

Module order (Maven forbids cycles): `gig ← accounts`, `gig ← places ← sync ← catalog ← importing ←
check` (the check also on `accounts`: its nightly run is every band's), `catalog ← calendar` (and
`accounts ← calendar`: its address is sealed by the `SecretBox`); platforms depend on the features whose
ports they implement (and on `accounts`: a band's account, its sign-in test); `web/live`
on the kernel only; only `app` sees everything. Another module uses a feature ONLY through its
`domain` and its ports (`application.port.in` to call, `.out` to implement) — never its services,
adapters or wiring (e.g. the calendar, Import and the check write gigs and links through `GigWrites`, the check asks
the sync through `SyncStateUseCase`). Where a feature lower in the order needs something from a
higher one, the lower one owns a port and the higher one implements it (`GigMovedListener`: the
calendar's links follow a gig's identity move). Each feature wires its own services
(`<feature>.config.<Feature>Configuration`, next to its properties record); the implementations
are public only so that wiring can build them — `ArchitectureTest` keeps everyone else on the
ports. Inside a platform, the steps, the importer and the endpoint use the portal client; its
Selenium implementation and page objects (package-private) are used by nothing else.
`ArchitectureTest` (ArchUnit): domain depends only on the JDK and domain code; application code
uses no framework, logger, adapter, config, live module or web setup; a feature is used from
outside only through its domain and ports; the kernel's persistence and wiring are its own; the
kernel knows no feature; a band's rows are read and written by band (no persistence adapter calls a
repository's `findAll()` / `count()` / `deleteAll()` — the accounts' band directory excepted); live updates are used only through their port and the live module knows
only the kernel; nothing uses the app's web setup; the features know no platform, nor a platform
another; a platform's browser code stays behind its portal client; every controller implements
the interface generated from its module's spec and has no private methods (the live stream
excepted); no cycles between modules nor between any two packages. `PlatformNamesTest`: no
feature source (code, string or comment — the generated code from the specs too) names a platform.

## The API (OpenAPI, contract first)

The API is written as **OpenAPI 3.0 specs**, one per module that serves requests, in
`src/main/openapi/`: `features/gig/…/gig.yaml` (the shared parts: the gig form —
`GigFormRequest` / `GigFormResponse`, one YAML definition under two names — `CountryValue`,
`EntryTypeValue`, `ProblemResponse` and the error responses), `catalog.yaml` (gigs, platforms, exports),
`places.yaml`, `sync.yaml`, `import.yaml`, `check.yaml`, `calendar.yaml`, `accounts.yaml`, `app/…/auth.yaml`
(signing in), `platforms/bandzone/…/bandzone.yaml` (the public `/gigs/{band_slug}`) and
`web/live/…/live.yaml` (the event stream). A spec refers to another's schemas by relative path
(`../../../../gig/src/main/openapi/gig.yaml#/components/schemas/GigFormRequest`). **Names**: a
schema sent to the API is `…Request`, one it answers (and everything nested in it) `…Response`, an
enum `…Value` (inline enums are made named ones) — the names are the generated classes' names.
**Change the spec first**; both sides are generated from it on every build:
- **Server** (openapi-generator `spring`, configured once in the parent pom's pluginManagement;
  each module names its spec and package): an interface per tag (`GigsApi`, `SyncApi`, …) and
  the request / response / value classes (POJOs) in `<feature>.adapter.in.rest.api` (target/generated-sources;
  `Instant` for date-time, Bandzone's keeps `OffsetDateTime`). The kernel generates the shared
  schemas once into `sk.drabikp.bzscraper.gig.api` (plus the shared mappers `GigFormMapper` and `RefMapper` — a gig's token, a platform's id); the
  other modules map them to those classes (`schemaMappings`), never generating them again.
- **Controllers** (`*Controller`, `@RestController`, implement the generated interface) only
  serve requests — no private methods, no logic: they call a use case and a mapper.
  **Mappers are MapStruct** (`*Mapper` interfaces, Spring beans; `-Amapstruct.unmappedTargetPolicy=ERROR`,
  so a response field left unmapped stops the build — a field added to a spec must be mapped):
  `toResponse`, `toValue` (enums), `toDraft`…; computed values are `@Mapping(expression = …)`,
  what is more than one value (a plan's index, a map by platform id, the calendar's views of a
  row via a `@Context`) small default methods. What isn't mapping stays out of them: `GigViews`
  (a gig with its publications and open sync work → `GigView` records, answered through
  `GigMapper`), `GigRevisions` (the edit's revision check, a digest of `Gig.fingerprint()`),
  `GigSummaries` (the public list's band check); the calendar's overview comes ready from the
  domain (`CalendarOverview.of`: rows latest first, counts, each row's views, what link-same-day takes). The processor is configured once in the parent pom (with Spring Boot's configuration
  processor, which naming processors would otherwise switch off). **Persistence maps with MapStruct
  too**: `GigEntityMapper`, `PublicationEntityMapper` (kernel), `SyncEntityMapper`, `CalendarEntityMapper`
  — entities are plain JPA classes with accessors (a public no-arg constructor for JPA and the mapper);
  what a row stores differently is a default method (the zone from the country, the lineup as one
  text, a change's fields by name); an entity's behaviour (the outbox task's status changes) stays on
  it. Not mappers, so not MapStruct: the platforms' readers of scraped pages and portal JSON
  (`GigSummaryToGigMapper`, `BitEventMapper`) — they parse. What used to sit in endpoints is in the
  application: `ListGigsUseCase.gig` (→ `NotFoundException`, 404), `SyncLogUseCase.tasks/counts`,
  `ImportSessionUseCase` (`ImportSession`: the background read and the plan), `StartPlatformCheckUseCase`
  (`BackgroundCheck`), and every use case announces its own changes on the live updates.
  **Error codes are enums**: every refusal or problem has an `ErrorCode` (kernel interface) from its
  feature's enum — `GigErrorCode` (kernel), `CatalogErrorCode`, `SyncErrorCode`, `ImportErrorCode`,
  `CalendarErrorCode`, `AccountsErrorCode` (each in its `application.port.in`), `WebErrorCode` (app); a
  `UserFacingException` can't be made without one. The spec lists them all as `ProblemCodeValue`
  (gig.yaml) and `Problems.of(code, …)` answers with it; `ErrorCodesTest` (app) keeps the enums and
  the spec the same (no code twice), the page's i18n test that every code has its `errors.<code>`
  text in both languages (`npm run generate --enum-values` exports the codes), and the page's
  `ApiError.code` is typed by them. A new refusal = a constant in the feature's enum + the spec + the texts.
  Errors: `ApiErrors` (app) answers with the kernel's `ProblemResponse` (409 refused, 404, 400 only for what the request got wrong — `BadRequestException`,
  `UnknownReferenceException`, an unreadable body or parameter —, 500 logged in full for anything
  else, a stray `IllegalArgumentException` too); `CalendarErrors` 502 `calendarUnavailable`.
- **The page**: `npm run generate` (openapi-typescript, `web/frontend/redocly.yaml` lists the specs;
  runs before dev, build and test) → `src/api/generated/*.ts` (git-ignored); `api/types.ts` only
  names those types; `api/client.ts` is an `openapi-fetch` client over every spec's paths, so a
  path, parameter, body or answer that doesn't match the spec doesn't compile.
- **A band's paths**: everything a band has is under `/api/bands/{bandId}/…` (the shared
  `components/parameters/BandId` of gig.yaml; the generated interfaces take `UUID bandId`, the
  controllers pass `new BandId(…)` / `RefMapper.toBandId` on); `/api/platforms`, `/api/places`,
  `/api/bands` (the user's bands) and `/api/auth` are no band's.
- The live stream (`/api/bands/{bandId}/events`) is the exception on the server: an `SseEmitter` can't be a
  generated interface's return type, so `LiveController` is hand-written; the page still takes its
  event names (`LiveTopic`) from `live.yaml`.

Configuration is typed: a `@ConfigurationProperties` record per module (`<feature>.config` for
the features: `CalendarProperties`, `SyncProperties`, `CheckProperties`,
`PlacesProperties`; the platform's root package for `BandzoneProperties`,
`BandsintownProperties`, `BrowserProperties`; `AccountsProperties`; `AuthProperties` in the app; secrets are hidden in
their `toString`); a band's logins are not configuration but its own (connected on the page).
`spring-boot:run` runs in the repo root (`./data` is relative to it; in Docker `/app/data`).
Spring tests: `app` (whole context) and each module with persistence (`gig`, `accounts`, `sync`, `calendar`:
its own `<Feature>PersistenceTestApplication` over its persistence package). These are marked
`@TestComponent`: a module that uses another's test-jar gets, in a reactor build (`./mvnw test`),
that module's whole test-classes folder (only the packaged jar is filtered to the shared helpers),
and its context scan must skip the other module's test application.

## The page (web/frontend)

Built from a regular user's view, phone first, installable (PWA). **Every band's pages are under
its address**, `/b/<slug>/…` (`band/BandPages` finds the band among the user's — `useMyBands` —
else back to the start; `band/band.tsx`: `useBand`, `useBandId`, `useBandPath('/gig/x')` for links,
the band used last remembered on the device); `/` goes to that band, `/new-band` makes one
("Create your band" — the user its owner; the address follows the name, `band/slug.ts`). A band
switcher sits at the top of the desktop menu and on the phone's More.
- **Places**: Gigs (cards on a phone, a selectable table with bulk actions on a desktop; the
  state on every platform per gig), a gig's page (a card per platform with Publish / Retry /
  Discard / Open, its sync history, its calendar event; ⋯ → edit, update everywhere, publish on
  more, cancel / reactivate, delete — the confirmations say what happens on each platform, from
  the traits), the gig form (When / Where / About; the town picker; the band's time behind
  "Festival?"; after a new gig: "Publish now?"), **Inbox** (everything that needs the user, each
  with its fix: failed sync work, a platform held back, the calendar's events that need a look,
  the nightly check's differences — `lib/attention.ts`), Calendar (suggestions, filters, why),
  More → Activity (sync log, pause), Import (background read → decide → import), Platform
  check, Settings (*You*: account and sign out, language, time format, light/dark; *This band*: its
  name and address — owners —, its members, its **platforms** — each connected with the band's own
  account, the form built from the platform's `accountFields` (`components/PlatformAccounts`; a secret
  is never shown, blank keeps it), "Test sign-in", the breaker —, its **calendar** (the private address,
  the zone — `components/CalendarSetup`), the sync's pause); the calendar's rules are edited on the
  Calendar page (the band's own; the shipped ones listed).
- Phone: a bottom bar (Gigs · Inbox · Calendar · More), sheets slide up from the bottom, toasts
  at the top; desktop: the menu on the left, dialogs centred.
- **Data**: TanStack Query per API resource (`api/hooks.ts`), every call typed by the specs (see
  **The API**); a band's resources are the shown band's (`useBandId`), cached under
  `['band', <id>, …]` (`bandKey`), so another band's data is never shown; what the user tells the sync, the calendar and the check are typed commands
  (`SyncCommand`, `CalendarCommand`, `CheckCommand`); every change reads the affected
  parts again; **live updates**: one `EventSource` on the band's `/api/bands/<id>/events` (`api/live.ts`) — an event
  names what changed (`gigs`, `sync`, `check`, `import`, `calendar`) and that part is read again;
  after the phone woke the app up, everything is.
- **Languages**: `i18n/en.ts`, `i18n/sk.ts` (plural forms per language: Slovak one/few/many/other);
  a test checks both have the same keys and that every key the pages use exists. The API says
  refusals as `{code, args, message}` (409) and the page translates the code (`errors.*`);
  the calendar's reasons (`why`), differences and the check's differences come as codes + values
  too. What the platforms themselves answer (sync messages, "left out" reasons) stays as they
  said it (English).
- **CSRF**: the `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header (`api/client.ts`); a 401 from the
  API shows the sign-in page (a button to Rauthy). The PWA's service worker never answers `/oauth2/`, `/login/`, `/auth/` itself.
  `npm run dev` (:5173) signs in too: the dev server passes `/oauth2` and `/login/oauth2` to the app,
  and the client knows `http://localhost:5173` when `BZ_DOMAIN=localhost`.

## What This Project Does

A Spring Boot **gig sync hub** for bands — begun as one band's tool, now for many (on the way to a
public service, `docs/multi-band-plan.md`). Gigs
are kept in a local catalog (PostgreSQL, the source of truth), can be imported from
Bandzone.cz, and are **published to listing platforms** (Bandzone, Bandsintown). Edits,
cancel, reactivate and delete in the catalog are propagated to every platform the gig
was published to. A React page (phone-first, installable) drives it over a REST API; a per-platform file download (`GigExporter`, the
Bandsintown CSV) and a REST endpoint (`/gigs/{band_slug}`, the catalog's gigs with their
Bandzone concert — in the Bandzone module, where it began) exist too.

## Bands (multi-band, `docs/multi-band-plan.md`)

Everything the app knows is a **band's**: its gigs, publications, sync work, calendar copy,
verdicts and links, settings (the sync's pause). A **user** (made at their first Rauthy sign-in) is
a **member** of bands — one membership per band, `OWNER` (the band itself too) or `MANAGER`, with an
optional display name there; one login can manage many bands. `features/accounts` owns them (tables
`app_user`, `band`, `membership`). **`BandId` is passed explicitly** through every port and use case
that touches a band's data (`GigRepository.findAll(band)`, `GigWrites.add(band, gig)`,
`SyncStep.Item.band()` — every item of one run is one band's —, `LiveUpdates.changed(band, topic)`);
no thread-local "current band": the worker and the nightly check work for every band in one thread.
`GigId` stays a gig's identity within its band; rows are keyed `(band_id, …)`.
- **Access**: `BandAccessInterceptor` (app) checks every `/api/bands/{bandId}/**` against the
  signed-in user's memberships — not a member = **404** (`notFound`; a band you aren't in doesn't
  exist for you); owner-only actions are checked in the use cases (`ownerOnly`). `BandIsolationTest`
  reads every band path from the specs and checks each is 404 for a non-member, both ways.
- **Band #1** (`BandId.FIRST`, `…0001`) is the band the app ran for before (migration V2 gave it every
  row). Its slug and name come from `bzscraper.accounts.first-band-slug` / `first-band-name`
  (default: the Bandzone slug and the Bandsintown artist) while it still has V2's `first` / `My band`;
  its owner is the first to sign in with `bzscraper.accounts.first-owner-email` (unset: whoever signs
  in first).
- **Each band's own connections** (phase 2): its platform accounts (`band_platform_account`) and
  calendar (`band_calendar`), connected on the page; the secrets (passwords, the authenticator secret,
  the iCal address) sealed with AES-256-GCM (`SecretBox`, keys from the Docker secret `data_keys`, the
  row's band — and platform — part of what is sealed: a ciphertext copied to another band's row doesn't
  open), opened only when a platform signs in or the calendar is read, never sent to the page. A
  platform describes its account (`AccountField`s in its `PlatformTraits`: text, secret, yes/no) — the
  page's form and the server's check come from that, no feature names a platform's fields. The portal
  clients read the band's account at `openSession(band, …)` (`BandzoneAccount` / `BandsintownAccount`;
  none = NEEDS_USER "connect it in Settings"), the importers and the CSV too; a platform's browser
  profile is per band and login. Band #1's old configuration (`bzscraper.bandzone.login/…`,
  `bzscraper.bandsintown.login/…`, `bzscraper.calendar.ical-url`, the rules file) is kept once on start
  while band #1 has none, then unused. The shipped calendar presets are everyone's
  (`calendar_rule.band_id` null); each band's own rules are edited on the page.
- The nightly platform check runs for every band (`BandDirectory.all()`), one after another; each
  band has its own import session, check result and pause; the circuit breakers stay per platform
  (one browser per platform), told to every band's pages (`changedEverywhere`). The public
  `/gigs/{band_slug}` finds the band by its slug.

## Architecture

Hexagonal inside every feature module (ports & adapters), see **Structure** above. **The core is
platform-agnostic**: no feature names a platform or branches on one. A `Platform` is a value (an
id string); what a platform IS comes from its module — a `PlatformTraits` bean (display name,
keeps cancelled events, lists the band's slot, carries admission, import precedence, event URL)
and the `SyncStep`s / `GigImporter` / `GigExporter` beans it provides. `Platforms` (the registry,
in import-precedence order) is built from the traits beans. A new platform is a new module under
`platforms/` with those beans — the features, the engine and the pages don't change.

Services are plain POJOs wired in their feature's `config` class; adapters are `@Component`s.
`StepRegistry` collects every `SyncStep` bean by (platform, step type) — two beans for one slot =
startup error — and `GigImportService`/`GigExportService` collect the importers/exporters the
same way.

**Catalog writes** (`GigWrites`, implemented by `CatalogWrites`): add, replace, delete, cancel /
reactivate, link and forget a link — the add page, edits, the calendar, Import and the platform check all
write gigs and their platform links through it, so its rules hold for every writer — a gig never overwrites another (a new gig
or an identity move onto an existing gig → `GigIdentityTakenException`); an edit applies only
to the gig as the writer saw it (`ConcurrentChangeException`); a gig whose platform work is
RUNNING is not changed, deleted, re-synced or unlinked (`GigBusyException`); an identity move
takes the gig's platform records and queued work along and tells the `GigMovedListener`s (the
calendar's links follow); an edit queues the update everywhere the gig is published.

## Platform sync — outbox, eventually consistent

The catalog is the source of truth; the platforms follow it **eventually**. Every change
to a gig that is on a platform, and every Publish, writes its platform work as rows in the
**sync outbox** (`SyncOutbox`, table `sync_task`, history in `sync_log`) IN THE SAME
TRANSACTION as the catalog change (`SyncRequests`, called by `GigCatalogService`,
`GigPublishingService`, `GigResyncService`). The UI never waits for a platform.

**Workflows** (`docs/sync-workflow-plan.md`): every task is a run of its action's workflow
(`Workflows`) in the `WorkflowEngine` — an ordered list of step types; platforms implement
`SyncStep`s, and what a platform can do is which steps it has (no flags, no config):

```
PUBLISH     bulk upload (BIT: BandsintownBulkCreate, 25 rows) → create form (BZ: BandzoneFormCreate)
UPDATE      bulk edit (BIT: BandsintownBulkEdit, no past events) → form edit (BZ: BandzoneFormEdit;
            BIT: BandsintownFormEdit, one at a time — past events and refused rows)
CANCEL      cancel (BZ: BandzoneCancel; BIT: BandsintownCancel = remove "canceled", no past events)
            → cancel in the form (BIT: BandsintownFormCancel — past events)
DELETE      remove (BZ: BandzoneRemove; BIT: BandsintownRemove, no past events)
            → remove in the form (BIT: BandsintownFormRemove — past events) → record forgotten
REACTIVATE  remove and create again — built by the engine from the platform's remove (the
            first of remove / remove in the form that takes the gig) + create
```

A step's `refusal(gig)` passes the gig on (also asked at queue time → "Left out: … by hand",
and a delete then forgets the copy). Outcomes: done (a created event's id recorded, a removed
one's forgotten — in the same transaction) / refused (the platform said no, nothing happened
→ next step) / failed (retry at the same step per `SyncRetryPolicy`; never for creates) /
failed for good (→ user) / postponed (the platform's browser was busy with a read — again in a
minute, no attempt used, not counted by the breaker). The run's step is `sync_task.step`; the path is logged ("bulk edit:
… → form edit"); due runs at one step go together (batch, or ≤10 one by one, each saved on
its own). Moot work (gig deleted/cancelled/published meanwhile) ends done with a note.
`SyncWorker` (one thread, woken on enqueue + every `bzscraper.sync.poll-seconds`) calls
`SyncDispatcher.runNext()` (→ engine) until nothing is due.

- Tasks work from the state when they RUN; work that became moot ends DONE with a note.
- Queueing rules (`SyncRequests`): no second UPDATE while one (or the PUBLISH) is PENDING;
  delete discards the gig's PENDING tasks and queues DELETE where it is published (the
  `published_gig` record outlives the catalog row until that succeeds); a pending CANCEL
  and REACTIVATE cancel out into an UPDATE; an identity change moves records AND tasks.
- `SyncRetryPolicy`: UPDATE/CANCEL/DELETE (repeatable) retry after 1, 5, 15 min, then
  FAILED; PUBLISH/REACTIVATE never auto-retry (a failure may have created the event);
  what a platform failure means is TYPED where it happens (`FailureKind` on
  `PlatformException` ← `BitPortalException`/`BandzonePortalException`, made only by their named
  factories `temporary`/`refused`/`needsUser`/`busy`): TEMPORARY (retry),
  REFUSED (the platform said no this way — next step, e.g. Bandsintown's row error
  `INVALID_START_TIME` on a past event), NEEDS_USER (switched off or not configured, a town to
  correct, a draft left behind, a Bandsintown login or authenticator secret that doesn't work —
  fails at once, so a password isn't typed again and again), BUSY (postponed; also work
  interrupted while the app stops — except a create, which may have happened: TEMPORARY with
  "check there"); a step's unexpected exception fails the run with its type and message and is
  logged in full (`SyncFaults`); steps return
  `e.outcome()`, and per-row replies (`BitSession.Created`/`Edited`) carry their kind too. After a
  restart, RUNNING repeatable tasks run again, others go FAILED ("check the platform").
  FAILED waits for the user: Retry (from the step where it stopped) / Discard on
  the Activity page (`/activity`, `/api/sync`). `markRunning` only starts a still-PENDING task; every status change
  is checked against `SyncStatus.canBecome` (a DONE task stays done whoever writes late) and
  `sync_task.version` (V12) makes two concurrent writers fail instead of overwriting.
- A new task REPLACES the gig's FAILED tasks it redoes on that platform (`replaceFailed`):
  a new publish the failed publish, a new update the failed update, a delete the failed
  updates/cancels (never a failed publish — it may have created the event; the user checks).
- **Pause / Resume** on Activity / Settings (`PauseSyncUseCase`, `SyncPause`): the gig in progress
  finishes, nothing new starts, the waiting work stays queued; a pause survives a restart
  (`SettingsStore`, table `app_setting`).
- **Circuit breaker per platform** (`PlatformBreakers`, `PlatformBreakerUseCase`, in memory):
  a batch whose results are ALL temporary failures counts against its platform (anything
  else — done, refused, failed for good — shows the platform answering and resets it);
  `bzscraper.sync.breaker.failures` (3) in a row → the platform is held back for
  `.cooldown-minutes` (30): its tasks stay queued (`SyncOutbox.nextDue(now, skipping)`), the
  other platforms go on; then one trial batch decides. The Inbox, Activity and Settings show it with "Resume now".
- **One browser per platform** (`PlatformBrowser`; sessions open through `PlatformBrowser.open`:
  lease, log in, screenshot + discard on failure, the platform's exception): a sync step doesn't wait for a browser
  another operation holds (BUSY → postponed); a read the user started (Import, the platform
  check) waits up to 10 min. The browser stays warm ~60 s after a session (`WarmBrowser`), so
  the next step or task takes it over (one start + login check per run, not per gig); closed
  when idle and on shutdown (a browser handed back after that is closed at once; handing back
  never throws and always releases the lease). Failures leave a screenshot,
  `$TMPDIR/bzscraper-<platform>-<step>.png`.
- Page: actions return a `QueueResult` (a toast "Publishing on …" / "Left out: …"); every gig
  shows its state per platform (live / queued / running / retrying / failed / not there) and
  follows it live (`SyncChanges` → `LiveUpdates` → server-sent events); actions on a gig whose
  task is RUNNING are refused by the core (`GigWrites`, 409 `gigBusy`).
- **Optimistic locking**: `UpdateGigUseCase.update(seen, updated)` — `seen` is the gig as the
  user opened it; if the catalog's gig is no longer that (changed in another window, by an
  import or the calendar, or deleted) nothing changes and `ConcurrentChangeException` says
  "changed meanwhile, reload". Two transactions saving one gig at once: `gig.version`
  (`@Version`, V10; `JpaGigRepository.save` carries the version it read) — the later commit
  fails, `SpringTransactions` turns it into the same exception.

## Platform check (`/check`) — reconciliation

The sync only knows what the app did; the platforms can drift by hand or by their own
guessing (a same-named town). `CheckPlatformsUseCase` (`PlatformCheckService`) reads every
platform through its `GigImporter` (read-only, the same reading as Import; a platform held
back by its breaker is skipped) and `Reconciler` (domain) compares each PUBLISHED gig of the
upcoming ones and the last `bzscraper.check.past-days` (60) with its copy there: MISSING (the
event is gone) or DIFFERENT — day, time (Bandsintown: the band's slot), name, venue (a
placeholder "-" is none; a longer platform name is fine), town ("Košice I" = Košice),
country, cancelled state (not on Bandsintown, which has none), and the place: Bandsintown
coordinates (`ImportedGig.latitude/longitude`) > 25 km from a town picked from the place
search. Gigs with sync work still queued are skipped. Fixes, always the user's click:
Re-sync (`ResyncGigUseCase`) or Forget link (a gone event, through `GigWrites.forgetLink`;
Publish creates it again; the gig pages follow). Also
counts platform events no gig is published as (→ Import). Runs nightly
(`PlatformCheckSchedule`, `bzscraper.check.cron`, default 04:30; `bzscraper.check.enabled`);
the result is kept in memory.

## Import (`/import`)

Initial (and repeatable) import of the band's existing gigs, upcoming and past, from
any platform, LINKED to the platform events so they are managed from the catalog
instead of published again. `GigImporter` per platform returns `ImportedGig`s
(gig + platform id): Bandzone scrapes the public band page (year tabs + the "planned"
tab) keeping the concert id and cancelled state; Bandsintown reads the portal.
`ImportPlanner`: skip platform events already linked; group by `GigId` with the
catalog gig and across platforms; else SUGGEST a match on same date + city (only when
the candidate is unique) — the user confirms (default: unconfirmed = import each copy
on its own). Where versions differ (title/time/venue/city) the user picks which to
keep (default catalog > Bandzone > Bandsintown); `GigMerge` fills gaps from the other
versions. `GigImportService.apply` saves + records links in one transaction. Never
deletes.

## Band calendar (`/calendar`)

Read-only: the band's calendar (its private iCal address — a SECRET, set on the page, kept sealed in
`band_calendar` with its zone; band #1's came from `bzscraper.calendar.ical-url`, its local copy in the
git-ignored `./calendar` file, never in git) mixes gigs
with rehearsals, travel, absences, calls. `CalendarEventClassifier` sorts each event into
GIG / UNSURE / NOT_GIG with the band's `BandProfile`: weighted `ProfileRule`s — the code
knows only rule KINDS, every band's words/labels/weights are DATA. Score ≥ 4 gig, ≤ −1
not a gig (unless there's evidence for a gig and nothing ≤ −4 against), between = the user
decides; `CATALOG_GIG_SAME_DAY` doesn't count after a strong negative. Status
(confirmed/tentative/cancelled) comes from separate role rules. The user's verdict per
event id (`calendar_decision`) always wins. Every matched rule is shown as a reason.

Rules: `classpath:calendar/base.profile` (language-independent) + presets
(`bzscraper.calendar.presets`, `sk-cz`) are re-synced as PRESET on start (everyone's); each band's
own (USER) rules are edited on the Calendar page (`CalendarSetupUseCase.replaceOwnRules`: replaced
whole, refused with `invalidRule` and the rule's number when one isn't one). Band #1's came from its
file (`bzscraper.calendar.profile-file`, default `./data/calendar-profile.txt` — members' names, never
in git), read once while it had none. File format: `KIND weight value|alternatives`. Steps 2–3
(learning the profile from platform history, setup wizard for any band) are planned in
`docs/calendar-plan.md`. Calendar notes hold fees/phones — never publish them.

**Saved copy + changes**: each read is saved (`calendar_event`, `CalendarSnapshotStore`, notes
included — local DB only) and compared with the last one (`CalendarChanges`): NEW / CHANGED
(title, time, place, notes, status; plus what the rules said before, when the change made
them say something else) / REMOVED / RETURNED, kept until the user marks them seen. The very
first read is the baseline. The page shows the saved copy; "Read calendar" reads again.

**Into the catalog**: `CalendarCatalogUseCase` (`CalendarCatalogService`, through `GigWrites`: a gig
the catalog already has is `gigIdentityTaken`, a link to a gone gig `notFound`).
`CalendarGigDrafter` pre-fills the gig form from an event: show time from the
`SHOWTIME_LABEL` rule kind (the event's own start is the arrival; a show before 06:00 is the
next day), venue/city/country from the map-style place (`Venue, Street, 811 05
City-District, Country`). The user checks and saves → gig + `calendar_link` (event → GigId,
following the gig's identity moves through `GigMovedListener`). Unlinked gig events are matched
to the catalog's gigs that day (link one, or bulk-link where there is exactly one).
`CalendarCatalogMatcher` compares a LINKED upcoming gig with the calendar: another day,
another show time, cancelled in the calendar, gone from the calendar (a multi-day event
pairs on any of its days; the calendar's show is compared with the band's `Slot`, or offered
as the slot) — the page offers
Update / Cancel / Delete / Keep, always a user click through the catalog use cases (so the
platforms follow via the outbox); nothing is changed automatically.

## Places (towns)

Town names repeat (three Hranice, two Czech villages and a Slovak city called Košice), and
both platforms got it wrong with only a name. So a gig's `Location` carries an optional
`Address` (street, postal code, district "okres Přerov", region, the town's coordinates —
V9), filled when the town is picked from the place search: `PlaceSearch` →
`PhotonPlaceSearch` (OpenStreetMap Photon, free, no key; CZ/SK towns; its `county` is the
district as Bandzone writes it). The gig form's City is a town picker (typed towns still
allowed, flagged "not picked from the list"); the calendar pre-fill picks the town when
exactly one fits name + country + postal area (`TownChoice`).
- **Bandzone** has its own town list: `BandzoneTowns` picks the suggestion with the same name,
  country and district — several fits or none → a clear permanent error, never a guess.
- **Bandsintown** geocodes the uploaded text: rows carry street and postal code; after a
  publish the event's coordinates are compared with the town's (> 25 km → the task's note
  says "check the place there").

## Bandzone (Selenium)

`BandzoneGigProvider`/`BandzoneGigImporter` scrape read-only (`BandzoneHtmlFetcher`, at
`bzscraper.bandzone.base-url`, the year tabs read at once with a 60 s limit). Writes drive the
band-admin pages with Selenium; off by default (stub). Enable + configure:

```
bzscraper.bandzone.selenium.enabled=true                    # this server drives Bandzone
bzscraper.bandzone.selenium.chromium-binary / .chromedriver   # else bzscraper.browser.chromium-binary / .chromedriver
bzscraper.bandzone.selenium.profile-dir              # default ~/.bzscraper/bandzone-browser (a directory per band's account in it)
```

Each band's account (login, password, the band's address `bandSlug`) is connected on the page
(`BandzoneAccount`); "Test sign-in" logs in and reads the band's page (`BandzoneAccountCheck`).
`bzscraper.bandzone.login / .password / .band-slug` are band #1's old ones (kept once). The real
band's login lives in the git-ignored `.bz-creds` (line 1 login, line 2 password, `chmod 600`);
`.bz-test-creds` is the test band.

**Saved logins** (`BrowserProfile`, both platforms): the browser keeps the platform's login
in a profile directory that is private (`700`), one per account — a band's login (a test account can
never act as the real band, nor one band reuse another's saved login), with cookies encrypted by the desktop keyring
(`bzscraper.browser.password-store=auto`, default: the keyring when a desktop session bus
is reachable, else Chromium's own store — on a server the private directory, owned by the
service user on an encrypted volume, is the protection). A still-valid saved login is
reused; the password (and the Bandsintown authenticator code) is used only when the
platform ended it. Deleting the profile directory logs out.

- **Create** — 2-step wizard: date/time/city → (optional "similar concerts" screen,
  answered "new") → info (name, entry, description, Facebook) → redirect to
  `/koncert/{id}-…`; the id is taken from that URL. `BandzoneFormCreate` then
  completes the gig via the edit form (end, venue, poster, lineup); if that fails the
  gig is still PUBLISHED with a note to use Re-sync.
- **Login** — `BandzoneLoginPage`; every field the pages set must be there (`BandzoneBrowser`
  throws "Bandzone field …" instead of silently skipping it).
- **Update** — `/koncert/{id}/update`: poster upload (`PosterDownloader`: 10 s to connect, 30 s
  for the file — the download holds the browser and the sync's one thread), city then venue autocomplete
  (venue search is scoped to the city; no exact club match → free-text venue), dates,
  info fields; then read back and verified. Autocomplete picks and the poster upload
  re-render the form via AJAX, so they run **before** plain fields and are followed
  by a staleness wait.
- **Lineup** (`BandzoneLineupPage`) — full sync: removes performers not in the lineup
  (never the band itself, whose name comes from the profile's `og:title`); adds an
  exact case-insensitive profile match, else a "band without profile" stub.
- **Cancel / delete** (`BandzoneDeleteTab`) — the button's form is submitted, then checked: a
  cancelled concert must be listed `gig--canceled` on the band's gig list (planned tab, else its
  year's); a deleted one's PUBLIC page (`/koncert/<id>`) must answer 404 — the admin pages of a
  deleted concert still open, delete button and all (seen 2026-10-09), so they prove nothing. A
  concert already gone: delete = done ("already removed"), cancel = needs the user.
- Not supported by Bandzone: un-cancel, removing a poster, ticket URL, per-band set times
  (Bandzone gets the whole event; the band's `Slot` goes to Bandsintown only).
- A city Bandzone's search doesn't know fails at once (permanent) with "correct the city".

## Bandsintown (Selenium)

Bandsintown has no write API for artists, and its internal API signs every request, so
the artist portal (artists.bandsintown.com) is driven in Chromium. Off by default (stub):

```
bzscraper.bandsintown.selenium.enabled=true               # this server drives Bandsintown
bzscraper.bandsintown.selenium.profile-dir                # default ~/.bzscraper/bandsintown-browser (a directory per band's account in it)
bzscraper.bandsintown.pacing.min-ms / .max-ms             # human pauses between steps (900 / 2600)
```

Each band's account is connected on the page (`BandsintownAccount`): login, password, authenticator
secret (base32, optional — asked for when Bandsintown wants a code), artist name (required: rows + CSV
carry it), artist id (picks the artist on a multi-artist login), notify followers (default: publish
silently). `bzscraper.bandsintown.login / .password / .totp-secret / .artist-name / .artist-id /
.notify-followers` are band #1's old ones (kept once).

- **Create** — Bulk Upload of a `BandsintownCsv` (template columns — `BandsintownCsv.Column`, in
  their order —, ≤ `BitSession.MAX_ROWS_PER_UPLOAD` (25) rows per upload; logging in: `BitLoginPage`)
  creates drafts; the upload reply maps CSV row → event id. In the success dialog
  "Notify my followers" is set from config, then Publish; the event list is read back
  to confirm PUBLISHED. Silent = "Do Not Announce = Y" + switch off (`announced_at`
  2000-01-01).
- **Update** — Bulk Upload of a row WITH `Event Id` (+ `Status`) edits the event in place
  (`BitSession.EventEdit` per row; results matched by row position).
- **Form edit** (`BandsintownFormEdit`, `editEventInForm`) — the event's single-page form
  (`/artists/<id>/events/<event>?version=single-page`), which opens for past events too:
  "Clear value" on the venue, type "venue town", pick the suggestion in the gig's town
  (`BitPlaces`, by Google `place_id`) and check the field shows it; then dates, times, name,
  description; Save → the reply's place is checked (`placeCheck`, > 25 km off → a note).
- **When** — rows carry the band's `Slot` when the gig has one (`GigSchedule.showStart/showEnd`),
  else the event's start/end: Bandsintown is about when the artist plays.
- **Past events** — a published past gig is listed under Past Events, not Upcoming: the
  create read-back checks the past list too. The list's "⋯ → Delete" doesn't reach PAST
  events, so they are removed through the form (below); an event in neither list counts as
  already removed. Bulk
  edits of past events are refused (`INVALID_START_TIME`) → the form edit does them.
- **List (import)** — Upcoming + Past tabs' event lists (past is paged: `x-next-page`,
  more load on scroll) → `BitEventMapper` (no entry info on Bandsintown; an event it can't read
  is left out with a warning in the log; an unknown time zone falls back to the country's).
- **Cancel/Delete** — Bandsintown has no cancelled state: both remove the event via the
  row's "⋯" → Delete, `RemovalReason` CANCELED or OTHER. Already-gone = success (so reactivate =
  delete no-op + create). Editing a cancelled gig skips Bandsintown.
- **Form cancel/remove** (`BandsintownFormCancel`/`BandsintownFormRemove`, `deleteEventInForm`)
  — past events (and whatever the list refused): the single-page form → Delete opens the same
  "remove this event?" dialog; its reason, details and Delete are looked up INSIDE the dialog
  (the form has dropdowns, text areas and a Delete of its own). A form that doesn't open for
  an event neither list shows = already gone.
- Replies are read by wrapping `window.fetch` in the page. The row to delete is found by
  its index in the captured event list and checked (city, day) before clicking.
- **Human pacing** (`HumanPacer`, user requirement): random pauses, real mouse clicks,
  key-by-key typing, one browser at a time, saved login reused (authenticator code only
  when the session expired), no automation flags / HeadlessChrome UA.
- Setup problems (no login, no authenticator secret, several artists, no artist id) need the
  user (NEEDS_USER), not a retry.

## Database

PostgreSQL 18 (`spring.datasource.*`; in Docker the `db` service, user and database
`bzscraper`, the password a secret). The schema is owned by **Flyway**
(`features/gig/src/main/resources/db/migration/V<n>__*.sql` — one schema history for all features,
in the kernel; an applied migration is never rewritten); Hibernate runs with `ddl-auto=validate`, so
every entity change needs a new migration (the tests run the migrations on a real PostgreSQL and fail
on a mismatch). `V1__schema.sql` is the whole schema as the app had it on H2 (V1–V12 there, squashed
for the new database in 2026-10): `gig` (+ the band's slot, the address, `version` for optimistic
locking), `published_gig`, `sync_task` (+ `step`, `version`) / `sync_log`, `app_setting`,
`calendar_rule`, `calendar_decision`, `calendar_event`, `calendar_link`. `V2__bands.sql` (many bands):
`app_user`, `band`, `membership`, `band_setting` (a band's settings; the pause moved there from
`app_setting`); band #1 (`…0001`, `first` / `My band`) and `band_id` on every band's table, filled
with band #1 — keys `gig (band_id, id)`, `published_gig (band_id, platform, gig_id)`,
`calendar_event|decision|link (band_id, event_uid)`, `sync_task.band_id` (index `(band_id, gig_id)`;
the log goes with its task), `calendar_rule.band_id` null for the shipped presets; deleting a band
deletes all its rows. `V3__band_connections.sql`: `band_platform_account (band_id, platform)` (settings
JSON, `secrets` sealed + `key_id`, the last sign-in test) and `band_calendar (band_id)` (the sealed
address, `key_id`, zone).

**From H2** (the database until 2026-10): `H2Import` (a Flyway `afterMigrate` callback in the kernel)
copies an H2 SQL backup (`SCRIPT TO`, `bzscraper.db.import-h2`) into an EMPTY database right after
the schema is built — every table's rows (the columns both have), times without zone shifts, the
id counters moved past the copied ids; one transaction, so a failure leaves nothing and stops the
start; skipped when the database has data; every row becomes band #1's. H2 stays a dependency only for this (`h2.version` pinned).

**Backups**: the compose service `backup` (`pg_dump --format=custom` of `bzscraper` and `rauthy`, daily,
`./data/backups`, 14 of each kept); restore into an empty database with
`pg_restore -d bzscraper --no-owner bzscraper-<day>.dump` (Rauthy's: `-d rauthy --role=rauthy`).

## Key Dependencies

- Spring Boot 4.0.3, Java 21; Spring Data JPA + PostgreSQL 18 + Flyway (H2 2.4.240 only for the import);
  Spring Security's OAuth2 client (OIDC) with Rauthy 0.37 (Docker)
- Docker: Debian trixie (Chromium + chromedriver) + the Temurin 21 JRE; Testcontainers 2 for the tests
- OpenAPI: openapi-generator 7.26 (`spring`, Spring Boot 4 + Jackson 3); openapi-typescript 7 + openapi-fetch
- MapStruct 1.6.3 (every mapper: API ↔ domain and JPA entity ↔ domain)
- The page: React 19 + TypeScript, Vite 6, Mantine 8, TanStack Query 5, React Router 7, i18next,
  vite-plugin-pwa, Mantine dates (+ dayjs); built by Maven (frontend-maven-plugin, Node 22 fetched into web/frontend/node)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Selenium 4.27.0 (Bandzone + Bandsintown) — needs a Chromium + chromedriver runtime

## Testing

`ApiSecurityTest` (app, MockMvc, signed in with Spring Security's `oidcLogin()` — `SignedIn`; the
test properties' `first-owner-email` makes `owner@band.test` band #1's owner): the API is 401 until
signed in, band #1 is its owner's and 404 for anyone else, signing in goes to Rauthy with PKCE,
signing out names Rauthy's end-session address, changes need the CSRF token, a refusal is a 409 with
its code, the page's own addresses load `index.html`. `BandIsolationTest`: every band path of every
spec (read from the YAML) is 404 for a non-member, both ways. The test properties carry a made-up data
key (`bzscraper.accounts.data-keys`; `AccountsFakes.TEST_KEYS` for module tests); the JPA tests of the
sealed rows check the secret isn't in the row, that a row copied to another band doesn't open, and key
rotation. Each feature's tests run with a band
(`TestBands.BAND`; the fakes key everything by band) and check that another band (`OTHER`) sees none
of it. The page: Vitest (`web/frontend`, run by `./mvnw test`) — both languages have the
same keys and every key the pages use, dates, the calendar's show applied to the form, a band's address from its name.

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). The domain has direct unit tests;
service tests use `SyncFakes` (sync test-jar: in-memory outbox/records/repository/settings,
mutable clock, builders for the registry, engine, dispatcher and requests) and `CatalogFakes`
(catalog test-jar: the write path and catalog use cases) and `TestPlatforms`
(two made-up platforms' traits — the core tests never need the real adapters). The fake
outbox and `JpaSyncOutbox` both pass `SyncOutboxContract` (sync test-jar), so the
fake can't drift from the real one. `bzscraper.sync.worker.enabled=false` in Spring tests
(tasks queue, never run). Persistence tests run the migrations on PostgreSQL in Docker (Testcontainers'
`jdbc:tc:postgresql:18-alpine` URL in each module's test `application.properties`, one container per
test run — `./mvnw test` needs Docker; `H2ImportTest`: an H2 backup into its own empty database)
(`JpaGigConcurrencyTest`: the optimistic lock across real transactions).

**Page objects against copies of the platforms' pages** (headless Chromium from
`TestChromium`, browser test-jar; test-jars carry only these shared helpers; skipped where Chromium + chromedriver are missing, paths
`-Dbzscraper.test.chromium` / `-Dbzscraper.test.chromedriver`, default `/usr/bin/…`;
`PlatformBrowserTest` covers the browser lease itself): `BitPortalPagesTest` runs the Bandsintown
pages against `PortalFixture` — an in-process HTTP server with copies of the portal's list
and event-form pages (`src/test/resources/bandsintown-portal`, built like the real ones as
the probes saw them: the row "⋯" menu, the reason dialog, a form with its own Delete,
dropdowns and text areas, a debounced Google-places venue search) that plays the portal's
API, with the real reply capture. `BandzonePagesTest` does the same with `BandzoneFixture`
(`src/test/resources/bandzone-admin`: the edit form that re-renders after an autocomplete
pick and shows what was stored, the city search with three Hranice, the club search, the
delete tab with `confirm()` and the band's gig list marking a cancelled concert, a deleted
concert's public page answering 404, the two-step wizard, the performers tab with its band search and
"band without profile" stub). Change a page object → run these first;
the live tests below then confirm against the real sites.

`SeleniumBandzonePortalClientLiveTest` creates, edits (every field, incl. lineup and
poster), cancels, deletes and deletes again (already gone) a real Bandzone gig. Skipped unless `BZ_LIVE=true`; reads
`BZ_LOGIN` / `BZ_PASSWORD` / `BZ_SLUG`, `BZ_LINEUP_BAND` (a real band that gets
notified), optional `BZ_KEEP=true`, `BZ_CHROMIUM` / `BZ_CHROMEDRIVER`. Run it only
against a test band.

`SeleniumBitPortalClientLiveTest` creates a made-up Bandsintown event (published
WITHOUT notifying followers), edits it and deletes it. Skipped unless `BIT_LIVE=true`;
reads `BIT_LOGIN` / `BIT_PASSWORD` / `BIT_TOTP` / `BIT_ARTIST_NAME`; `BIT_CLEANUP_ID=<id>` only deletes a
leftover event. There is no Bandsintown test artist — the event is public for ~1 minute.

`CalendarClassificationLiveTest` prints how the shipped + band rules sort the REAL
calendar (read-only). Skipped unless `CAL_LIVE=true`; reads `CAL_URL`, optional
`CAL_PROFILE` (band rules file) and `CAL_CATALOG_DAYS` (file of ISO dates with gigs).
