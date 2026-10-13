# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
docker/make-secrets.sh                                 # Once (and after changing GSH_DOMAIN before Rauthy's first start): ./secrets
docker compose up -d --build                           # Run everything: PostgreSQL, the app, Rauthy (the sign-in), Caddy
                                                       #   → https://$GSH_DOMAIN (default https://localhost), daily backups
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
`https://$GSH_DOMAIN/auth/` by Caddy — the same origin as the app. Who may sign in is Rauthy's: the
members of its group `gigsynchub` (the app's client has `restrict_group_prefix`); every `/api` address
needs a sign-in. Which bands a signed-in user sees is the app's: their **memberships** (see **Bands**
below; a user is made at their first sign-in, `OidcCurrentLogin` → `CurrentLogin`). The page sends the browser to
`/oauth2/authorization/rauthy` (code flow with PKCE, confidential client `gigsynchub`); Rauthy sends
it back to `/login/oauth2/code/rauthy`, then to `/` (the page goes back where the user was,
`lib/signIn.ts`). **The app's session: 12 h idle AND 12 h in all** (`SessionLife`) — the first is
`server.servlet.session.timeout`, the second `gigsynchub.auth.session-max-age`, counted from when
the session was made, because an idle timeout alone never fires for a page that keeps asking; and
every `gigsynchub.auth.revalidate-every` (5 min) the first request to arrive asks Rauthy's user
info whether the sign-in still stands (gone, disabled, or out of the group `gigsynchub.auth.group-prefix`
→ the session ends at once; Rauthy unreachable keeps it — an outage must not sign everyone out).
That asking needs the access token the sign-in left, which lasts half an hour, so it is sharp for
the first half hour of a session and the cap carries the rest. Rauthy's own session signs the user
in again without a password. Signing out (`POST /api/auth/logout`) ends both: the answer names
Rauthy's end-session address and the page goes there. `SecurityConfiguration` builds the client from `AuthProperties`
(no discovery at start): the browser goes to the issuer (`gigsynchub.auth.issuer`,
`https://$GSH_DOMAIN/auth/v1/`), the app fetches tokens, keys and user info inside the network
(`gigsynchub.auth.internal-url`, `http://rauthy:8080/auth/v1/`); the ID token's issuer is checked.
Rauthy: `docker/rauthy/config.toml` (not secret; each secret is `'$SECRETS'`, from its secrets file
`secrets/rauthy.toml`), its data in the `rauthy` database on the app's PostgreSQL (made by the
one-shot service `rauthy-db`, backed up with the app's), its cache in `./auth-data`; it runs as
`GSH_UID` (it reads the owner-only secrets). **First start**: Rauthy reads `secrets/rauthy-bootstrap`
(the group and the app's client for `https://$GSH_DOMAIN`, made by `docker/make-secrets.sh`) into its
EMPTY database, and prints the first admin's (`GSH_ADMIN_EMAIL`) password once in its log. Then, in
Rauthy's admin (`https://$GSH_DOMAIN/auth/v1/admin` — admins need MFA: register a passkey first, at
`/auth/v1/account`, which asks for no MFA itself; a machine that cannot make a passkey takes the
password alone with `GSH_ADMIN_MFA=false` in its `.env` — `ADMIN_FORCE_MFA`, which is the whole gate
(`Principal::validate_admin_session`) and is also written onto the `rauthy` client's own `force_mfa`
on every start, so the switch needs a `docker compose up -d rauthy`; never false on a public server.
A passkey is bound to `RP_ID` = `GSH_DOMAIN`, so one made against `localhost` is of no use once the
server has its real address),
add yourself and the members to the group `gigsynchub`. A later change of `GSH_DOMAIN` = change the
client's redirect / post-logout addresses there (the bootstrap is never read again).

**Docker** (`Dockerfile`, `compose.yaml`): the image builds the app from the sources (a Temurin 25 JRE
and the jar — nothing else: no browser runs on this server, so none is installed; a non-root user whose
uid/gid match the host's, `GSH_UID`/`GSH_GID`, default 1000); compose runs `db` (PostgreSQL 18, volume `db-data`, on `127.0.0.1:5432` for development),
`app`, `rauthy-db` + `rauthy` (the sign-in), `proxy` (Caddy) and `backup` (`docker/backup.sh`:
`pg_dump` of the app's and Rauthy's databases on start and daily into `./data/backups`, the newest 14
of each kept). **Secrets are files** under `./secrets` (git-ignored, `600`; `docker/make-secrets.sh` makes
them: random ones — the database passwords (`db_password`: the owner, for Flyway; `db_app_password`: the
app's own login `gigsynchub_app`, which may not change the schema), Rauthy's secrets, the app's client secret, **`data_keys`** (the
key every band's secrets are sealed with in the database: back it up apart from the database dumps, lose it
and they can't be read) — and band #1's old logins from `.bz-creds`, `.bit-test-creds`, `calendar`, which
the app keeps once in the database and doesn't read after),
given to the app as Docker secrets at `/run/secrets/<property name>` and read by
`spring.config.import=optional:configtree:/run/secrets/` — never environment variables (Rauthy gets its
secrets file the same way, `SECRETS_FILE`). Whether this server drives the platforms, band #1's
old band slug and artist name, and this machine's settings (`GSH_DOMAIN`,
`GSH_APP_BIND`, `GSH_SUBNET`, `GSH_UID`/`GSH_GID`, `GSH_ADMIN_MFA`) are in the git-ignored `.env`
(`.env.example`; what weakens a thing is a machine's own business, never git's) —
compose reads it for its own `${…}` and passes it to the app. `./data` is mounted at `/app/data` (the
band's calendar profile, backups); a platform's sign-in is kept in the database with the band's account,
not on disk.
`.dockerignore` is an allowlist: only the sources reach the build. The containers' network has a
fixed subnet (`GSH_SUBNET`, default `10.201.0.0/24`): Docker's own 172.x pick collided with a VPN's
routes, and the published port then reset every connection. The app has its own name server
(`GSH_DNS`, default `1.1.1.1`): Docker's DNS forwarding to a VPN's name servers timed out, so the
platforms, the calendar and the town search couldn't be reached.

**The way in** (compose service `proxy`, always on — signing in needs it): Caddy (`docker/Caddyfile`)
on 80/443 gets and renews the Let's Encrypt certificate for `GSH_DOMAIN` itself (DNS must point at the
server), redirects HTTP to HTTPS, sets HSTS, routes `/auth/*` to Rauthy and the rest to `app:8080`,
compresses everything but the live updates (`/api/bands/*/events`, passed through unbuffered).
`GSH_DOMAIN=localhost` (the default) runs it locally with Caddy's own certificate (the browser warns
once, or trust Caddy's root). The app's own port is published on `127.0.0.1` only (debugging;
`GSH_APP_BIND=0.0.0.0` opens it to the local network). The operator's endpoints (actuator: health, metrics)
answer on the management port 8081, bound to 127.0.0.1 inside the container — never through the proxy or
the published port: `docker compose exec app curl -s localhost:8081/actuator/metrics/gigsynchub.sync.tasks`
(`gigsynchub.sync.tasks` / `.held` per band — `BandMetrics`);
the image's health check uses it. `server.forward-headers-strategy=native`: Tomcat
believes `X-Forwarded-*` only from private addresses, so behind the proxy requests count as HTTPS —
Rauthy is told to send the user back over HTTPS and the session and CSRF cookies are `Secure`
(`ProxyHeadersTest`, a real server).

**Structure: vertical slices.** One Maven module per feature, each holding its own domain,
application (use cases + ports) and adapters (persistence, REST API, workers) in packages
`sk.drabikp.gigsynchub.<feature>.{domain, application, application.port.in|out, adapter.in.*,
adapter.out.*, config}`. `gig` is the shared kernel; platforms plug into the features' ports;
the page is a separate React app (`web/frontend`) over the features' REST APIs.

```
features/gig        shared kernel: Gig + its value objects (GigId = a gig's own identity, kept for
                    life — .key() = how records refer to a gig, .token() = in addresses; GigMatch =
                    which concert it is, the day + the folded venue, how a platform's or the
                    calendar's copy is recognised and the one thing a band can't have twice;
                    Gig.fingerprint() = a revision's text; Address,
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
                    UsersUseCase (the signed-in user, made just in time from CurrentLogin; deleting oneself),
                    BandsUseCase (+ deleting a band, the owner quota), MembersUseCase (roles, removing,
                    leaving — a band keeps an owner), InvitationsUseCase (an owner's link for one address,
                    7 days, once; only the token's SHA-256 kept), AuditUseCase (who changed what — written by
                    the app's AuditInterceptor), BandAccess (a user's role in a band), BandDirectory (every
                    band, by slug);
                    a band's platform accounts (PlatformAccount: settings + secrets by the platform's
                    AccountField names, last sign-in test): PlatformAccounts (the platforms read them; adopt =
                    band #1's from the configuration, once), PlatformAccountsUseCase (the page: owners connect,
                    members test), port.out PlatformAccountCheck (the platform's sign-in test); SecretBox
                    (AesGcmSecretBox: AES-256-GCM, keys gigsynchub.accounts.data-keys, sealed for its row;
                    rotation = a new key first, everything sealed again on start, a page of rows at a time —
                    a half-rotated table is a good one, every row saying which key sealed it); /api/bands,
                    …/platform-accounts; test-jar: AccountsFakes (+ Accounts: in-memory accounts, TEST_KEYS)
features/places     Town (coordinates null when only typed), TownChoice, place search (Photon); /api/places
features/sync       outbox (SyncOutbox = SyncTaskQueries + the writes; SyncTask…, PlatformState: a gig's
                    state on a platform), queueing (QueueSyncWork), read-only state (SyncStateUseCase),
                    workflow engine (WorkflowEngine.run(Workload) walks a workflow; RunIntake answers
                    what a step is to do, OutcomeSettlement what an outcome means, RecreateStep is built
                    from a platform's own steps; a step's unexpected fault → SyncFaults, logged in full)
                    + step ports (SyncStep,
                    OneAtATimeStep), breakers, pause, settings, worker; /api/sync; test-jar: SyncFakes
                    (its outbox gives platforms back as new instances, like the database), SyncOutboxContract
features/catalog    catalog use cases, GigWrites (← CatalogWrites), GigExporter;
                    …/gigs, …/exports, /api/platforms; test-jar: CatalogFakes
features/importing  import planner/merge, GigImporter port (+ PlatformReads); /api/import (reads in the background)
features/check      Reconciler, platform check service + nightly schedule, each band's latest result
                    (CheckResultStore, table band_check — kept across restarts); /api/check
features/calendar   calendar domain (+ domain.event: the calendar's events; + domain.rules: profile
                    and classifier), review service, stores, iCal feed, profile file; /api/calendar;
                    shipped presets (calendar/*.profile)
platforms/portal    what every platform shares: PlatformClient (`gigsynchub.<platform>.client`: off or
                    http) + OnPlatformClient (a client bean exists only for its own setting). That is
                    all it is — the human pacing (HumanPacer) sits in the platform that paces, and
                    Bandzone's own waits are in its web client (this module was `platforms/browser`
                    while a browser was driven here; nothing drives one any more)
platforms/bandzone      bandzone (traits, properties, exception) · scrape · portal (client API, stub,
                        BandzoneTowns, PosterDownloader) · portal.http (NetteForm, Answer, BandzoneWeb,
                        ConcertForm, LineupTab, session, client, sign-in) · step · rest
                        (the public `/gigs/{band_slug}` endpoint — any band, by its slug)
platforms/bandsintown   bandsintown (traits, properties, exception, RemovalReason) · portal (client API,
                        stub, PortalReply + BitEvent/BitPlace/BitEventMapper/BitPlaces — how the portal's
                        own JSON reads) · portal.http (BitConnection: what a band's one browser sign-in
                        left; BitSignIn: a session from it, no browser; BitApi/BitWeb, HttpBitSession)
                        · step · importing · csv
web/live            LiveUpdates as server-sent events (SseLiveUpdates, GET /api/bands/{bandId}/events: a
                    band's changes to its pages, changedEverywhere to all) — the only place that knows
                    SSE; everyone else tells the LiveUpdates port
web/frontend        the page: React + TypeScript + Vite + Mantine 8, TanStack Query, React Router,
                    i18next (English + Slovak), a PWA (vite-plugin-pwa); built into the jar's static/
app/                GigSyncHubApplication; web (security: sign-in through Rauthy (OIDC), session + CSRF
                    for a single-page app; OidcCurrentLogin; BandAccessInterceptor: a band's addresses
                    for its members; ApiErrors; SinglePageApp: the page's own addresses load
                    index.html; BandAccessInterceptor: a band's addresses are its members';
                    AuditInterceptor; RateLimits); bands (BandExport: a band's data as one file, bands.yaml);
                    metrics (BandMetrics, the operator's); application.properties;
                    ArchitectureTest, PlatformNamesTest,
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
higher one, the lower one owns a port and the higher one implements it (`PlatformAccountCheck`
and `PlatformSignIn`: the accounts ask the platform that owns the sign-in). Each feature wires its own services
(`<feature>.config.<Feature>Configuration`, next to its properties record); the implementations
are public only so that wiring can build them — `ArchitectureTest` keeps everyone else on the
ports. Inside a platform, the steps, the importer and the endpoint use the portal client; how it
talks to the platform (`portal.http`, package-private) is used by nothing else.
`ArchitectureTest` (ArchUnit): domain depends only on the JDK and domain code; application code
uses no framework, logger, adapter, config, live module or web setup; a feature is used from
outside only through its domain and ports; the kernel's persistence and wiring are its own; the
kernel knows no feature; a band's rows are read and written by band (no persistence adapter calls a
repository's `findAll()` / `count()` / `deleteAll()` — the accounts' band directory excepted); live updates are used only through their port and the live module knows
only the kernel; nothing uses the app's web setup; the features know no platform, nor a platform
another; how a platform talks to its site stays behind its portal client; every controller implements
the interface generated from its module's spec and has no private methods (the live stream
excepted); no cycles between modules nor between any two packages. `PlatformNamesTest`: no
feature source names a platform — its Java, its OpenAPI spec (and so what is generated from it)
and the schema's own migrations, in code, string or comment alike. Not the generated classes
themselves: they are the spec's, and the spec is read.

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
  schemas once into `sk.drabikp.gigsynchub.gig.api` (plus the shared mappers `GigFormMapper` and `RefMapper` — a gig's token, a platform's id); the
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
  it. A persistence adapter is `@Transactional(readOnly = true)` on the CLASS, and only the methods
  that write carry a plain `@Transactional`: reading is the default everywhere, and a write says so.
  Not mappers, so not MapStruct: the platforms' readers of scraped pages and portal JSON
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
- **The specs' limits are CHECKS**: the generator runs with `useBeanValidation=true`, so a
  `maxLength`, `maxItems` or `maximum` in a spec becomes a jakarta.validation annotation on the
  generated class or parameter and `spring-boot-starter-validation` (app) enforces it — a request
  that breaks one is a 400 `badRequest` (`ApiErrors`, which catches both shapes: Spring's own for a
  body, jakarta's `ConstraintViolationException` for a parameter, since the generator's `@Validated`
  routes those through AOP). The gig form's lengths are its COLUMNS' (V1__schema.sql), so what the
  API refuses and what the database would refuse are the same number. `ApiSecurityTest`'s
  `the_limits_the_specs_carry_are_checked` fails the moment that switch goes back.
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
`BandsintownProperties`; `AccountsProperties`; `AuthProperties` in the app; secrets are hidden in
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
  state on every platform per gig. **Nothing on this page filters, searches, sorts or counts**: the
  period, the words and the chips are what the SERVER is asked for — `GET …/gigs?period=&q=&show=&
  after=&size=` answers `{gigs, total, cursor}`, a page of rows in the list's order, and "Show N more"
  reads on from `cursor` (`useInfiniteQuery`, the search debounced). A band with a thousand gigs
  therefore costs a page of rows on the wire, not a thousand, and the count in the heading is the
  store's), a gig's page (a card per platform with Publish / Retry /
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
  and the client knows `http://localhost:5173` when `GSH_DOMAIN=localhost`.

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
`GigId` is a gig's identity within its band — its own, and kept however the gig is edited (V10);
rows are keyed `(band_id, …)`.
- **Access**: `BandAccessInterceptor` (app) checks every `/api/bands/{bandId}/**` against the
  signed-in user's memberships — not a member = **404** (`notFound`; a band you aren't in doesn't
  exist for you); owner-only actions are checked in the use cases (`ownerOnly`). `BandIsolationTest`
  reads every band path from the specs and checks each is 404 for a non-member, both ways.
- **Band #1** (`BandId.FIRST`, `…0001`) is the band the app ran for before (migration V2 gave it every
  row). Its slug and name come from `gigsynchub.accounts.first-band-slug` / `first-band-name`
  (default: the Bandzone slug and the Bandsintown artist) while it still has V2's `first` / `My band`;
  its owner is the first to sign in with `gigsynchub.accounts.first-owner-email` (unset: whoever signs
  in first).
- **Each band's own connections** (phase 2): its platform accounts (`band_platform_account`) and
  calendar (`band_calendar`), connected on the page; the secrets (the way back in to each platform — a
  Bandzone session, a Bandsintown refresh token — and the iCal address) sealed with AES-256-GCM (`SecretBox`, keys from the Docker secret `data_keys`, the
  row's band — and platform — part of what is sealed: a ciphertext copied to another band's row doesn't
  open), opened only when a platform signs in or the calendar is read, never sent to the page. A
  platform describes its account (`AccountField`s in its `PlatformTraits`: text, secret, yes/no) — the
  page's form and the server's check come from that, no feature names a platform's fields. The portal
  clients read the band's account at `openSession(band, …)` (`BandzoneAccount` / `BandsintownAccount`;
  none = NEEDS_USER "connect it in Settings"), the importers and the CSV too. **No password is kept**: a
  band types its Bandzone one once (the server signs in and writes down only the session, which lasts
  years), and signs in to Bandsintown in its OWN browser, pasting what that leaves — so no authenticator
  secret is kept either. When a platform ends a sign-in the band connects again; it is never retried.
  Band #1's old configuration (`gigsynchub.bandzone.login/…`,
  `gigsynchub.bandsintown.login/…`, `gigsynchub.calendar.ical-url`, the rules file) is kept once on start
  while band #1 has none, then unused. The shipped calendar presets are everyone's
  (`calendar_rule.band_id` null); each band's own rules are edited on the page.
- **Going public** (phase 4): **invitations** — an owner invites an e-mail address as a role and passes
  the link on (`/invite/<token>`; no e-mail is sent yet), whoever is signed in with that address joins
  (once, 7 days); owners change roles and remove members, anyone leaves, **a band keeps an owner**
  (`lastOwner`) — the rule of the band's whole roster, so it is asked of the roster
  (`BandMembers`: `without(user)` / `as(user, role)`, then `hasOwner()`) rather than written out again
  at each of the four places that can break it, and whoever is about to change the roster **holds** it
  first (`MembershipStore.hold`: a lock on the band's row for the rest of the transaction). Without
  that hold, two owners leaving at the same moment each see the other and the band is left with none,
  which nothing on the pages can undo (`BandKeepsAnOwnerTest` fails exactly so when the hold is
  taken away); owners delete the band (its address typed in; the database's cascades take everything)
  and **export** it (`GET …/export`, one JSON file — never a secret); one's own account is deleted
  (`DELETE /api/me`; a band one is alone in goes too; the Rauthy login stays). The **audit log**
  (`audit_event`): every change a member makes through a band's API (`AuditInterceptor`: the operation,
  what it named, how it ended), read by owners. **Tenant isolation is the queries' own** (V12
  dropped the row-level security of V6): every query over a band's table names the band — in a derived
  method's own name (`findByBandId…`) or in a predicate of its `@Query` — and `band_id` is part of the
  primary key of the main tables, so a row cannot even be fetched by id without its band. What keeps it
  that way is enforcement at BUILD time, not a database policy: `BandScopedQueriesTest` (app)
  ENUMERATES the repository interfaces on the classpath, works out which entities keep a band's rows (a
  `bandId` field, or the band inside an `@IdClass`/`@EmbeddedId`) and insists on every method, so a
  query added later is covered the moment it exists; the eight reads that genuinely belong to no band
  carry `@AcrossBands("why")` (the worker's due work and pauses, `FairTurns`, restart recovery, key
  rotation's two re-seals, the shipped presets, and a task's log — whose band is checked on its task in
  `SyncLogService#log`). Two ArchUnit rules close the way round it: nothing is annotated
  `@Query(nativeQuery = true)` and nothing calls `createNativeQuery` — with no exceptions, so no
  Java source of this app writes SQL. (The SQL this app does have is where SQL belongs: the Flyway
  migrations, which ARE the schema, and two connection-init statements in the properties —
  `set role` and Flyway's `lock_timeout`. Neither reads a row.) Each band-scoped store also has a persistence test that writes two bands' rows and
  reads one band's back. **The restricted role stays** and has nothing to do with tenancy: the app's
  connections work as `gigsynchub_app` — in Docker its own login (`SPRING_DATASOURCE_USERNAME`, the
  secret `db_app_password`, given its password after each migration by `AppRoleLogin`), not the
  database's superuser, `set role` on each pooled connection — so the app cannot alter a table and
  cannot read `flyway_schema_history`; Flyway connects as the owner (`spring.flyway.user/password`).
  **Limits**:
  `gigsynchub.limits.requests-per-minute` (600) / `changes-per-minute` (120) per user (per address before
  signing in) on `/api`, `/gigs`, `/oauth2`, `/login` → 429 `tooManyRequests`;
  `gigsynchub.accounts.max-bands-owned` (10) → `tooManyBands` — and what COUNTS the requests is itself
  bounded (`gigsynchub.limits.tracked-callers`, 50 000: the least recently seen caller is forgotten,
  never the one flooding); live streams: 100 per band, 2000 in all
  (the oldest ends), each ends after 30 min (the page reconnects). The background work a request
  starts (Import's read, a check on demand) runs on a bounded pool (`spring.task.execution.*`, 8
  threads and 50 waiting) and a refusal is a 429, not a queue nobody is watching. **Addresses a band gives** (its
  calendar, a gig's poster) are fetched only from the public internet, every redirect checked by hand
  (`PublicAddresses`; a gig's links must be http(s), `links`), at most 10 MB, and a failure says nothing of
  what answered — and the address CHECKED is the address connected to, because the app fixes the
  platform's cache of successful lookups (`PublicAddresses.holdResolvedNamesStill`, from the app
  class's initializer: the JDK reads that policy once, at the first name the process resolves) rather
  than leaving the two lookups free to disagree. An `InetAddressResolverProvider` would say it better
  and does nothing in a Spring Boot jar — the javadoc says why. **The public list** (`/gigs/<slug>`)
  is a band's choice (`band.public_list`, V7, Settings → This band; band #1's on). An e-mail Rauthy hasn't verified is no e-mail to the app (invitations, the
  first owner); a login's e-mail and name are refreshed at each sign-in. Caddy's log never holds a token
  (invitation links, the sign-out's `id_token_hint`).
- **Many bands on one server** (phase 3): the sync runs `gigsynchub.sync.worker.threads` (2) **lanes** at
  once — a lane is a band's work on a platform (`Lane`), run by one worker at a time — and the bands take
  turns (`FairTurns`: round-robin by band, a band's oldest due task first), so one band's hundred tasks
  or slow platform don't keep the others waiting. Circuit breakers are per lane (a band's broken account
  holds back only that band there) and per platform above them (`breaker.platform-bands`, 3, bands held
  back on one platform at once = it seems down: held back for all, told to every band's pages). A session
  is a few requests, so nothing is queued behind a scarce resource: how many bands sync at once is the
  worker threads, nothing more. The nightly platform check runs for every band, one after another; each band has its
  own import session, check result (kept, `band_check`) and pause. The public `/gigs/{band_slug}` finds
  the band by its slug.

## Architecture

**The context map is `docs/context-map.md`**: which module models what, how the seams stand to one
another (shared kernel, anticorruption layer, dependency inversion, conformist), why the same gig is
a different type in seven contexts on purpose, and where the boundaries deliberately do NOT follow
the invariants (a band as a tenant rather than an aggregate root). What
`ArchitectureTest` and `PlatformNamesTest` enforce is that page, in executable form.

**The model says what it is (jMolecules).** Every domain type carries the concept it stands for —
`@AggregateRoot` with its `@Identity` (`Gig`, `Band`, `BandMembers`, `User`, `Invitation`,
`PlatformAccount`, `Publication`, `SyncTask`, `BandCalendar`, `KnownCalendarEvent`), `@Entity`
(`Membership`, the roster's), `@ValueObject` (the rest of the model), `@Service` (the pure policies:
`Reconciler`, `ImportPlanner`, `GigMerge`, `SamePlace`, `CalendarEventClassifier`, …) — and the ports
that keep an aggregate say `@Repository`. The layers say it too: `@PrimaryPort` on each
`application.port.in`, `@SecondaryPort` on each `.port.out`, `@PrimaryAdapter` / `@SecondaryAdapter`
on the `adapter.in.*` / `adapter.out.*` packages (`package-info.java`), `@Application` on the
services themselves — on the classes and not on the package, because a feature's ports live INSIDE
its application package and are not application code. These are annotation-only jars (no runtime),
which is why `ArchitectureTest`'s "the domain knows only the JDK and domain code" admits
`org.jmolecules.ddd.annotation..` and nothing else new. `JMoleculesModelTest` (app) runs jMolecules'
own ArchUnit rules over all of it: an entity belongs to one aggregate, aggregates name each other by
identity, everything with a lifecycle declares what identifies it, and a value object holds values.
Three types carry no stereotype on purpose and say so in their javadoc — `CatalogMatch`,
`ImportProposal`, `ImportedGig` are read models: they HOLD an aggregate so a person can decide about
it, which is not what a value object is. `ensureHexagonal()` is named in that test but not run, with
its one finding written down: `SecretBox` would be a secondary port in a strict hexagon (the
persistence needs it to keep a secret; nobody asks the app to seal anything), and it sits among the
use cases here because another feature CALLS it.

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
or an edit that moves a gig onto another's day and venue → `GigIdentityTakenException`); an edit applies only
to the gig as the writer saw it (`ConcurrentChangeException`); a gig whose platform work is
RUNNING is not changed, deleted, re-synced or unlinked (`GigBusyException`); a gig keeps its
identity through every edit, so its publications, its queued work and the calendar's link to it
stay as they are (only how the waiting work NAMES the gig follows it, `SyncOutbox.relabel`); an
edit queues the update everywhere the gig is published.

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

**The engine orchestrates; it is not coordinated.** `WorkflowEngine.run(Workload)` is given a
**`Workload`** — a band's due work of ONE action on ONE platform, because a single task cannot say
which batch it belongs in — and walks that action's workflow itself, in one call: each step with the
gigs waiting there, and a gig a step passes on tried the next way **in the same pass**, while the
platform's session is still open. `sync_task.step` is written down at each hop
(`SyncOutbox.advance`), so a restart or a pass cut short carries on from where each run stands: it is
a **checkpoint, not a program counter** — nothing has to come round again for a workflow to finish,
and no step is redone because of one. Each step does at most one batch per pass (a batch step its own
size, a one-at-a-time step ≤10 gigs, each result saved on its own), then the lane is given up so the
other bands and platforms get their turn and what is left waits where it stands.
Who decides what: the engine the ORDER; `Workflows` each action's steps; `StepRegistry` which step a
platform has (and, at queue time only, whether any could take the gig); `RunIntake` what a step is to
do for one run — it answers and writes nothing; `OutcomeSettlement` what an outcome means for the
record, written at once — except a refusal, which is the engine's, because what follows it is the
workflow's next step.

A step's `refusal(gig)` passes the gig on (also asked at queue time → "Left out: … by hand",
and a delete then forgets the copy). Outcomes: done (a created event's id recorded, a removed
one's forgotten — in the same transaction) / refused (the platform said no, nothing happened
→ next step, whatever the action) / failed (retry at the same step per `SyncRetryPolicy`; never for creates) /
failed for good (→ user) / postponed (the step never reached the platform — the thread was interrupted,
as when the app stops — again in a minute, no attempt used, not counted by the breaker). The path is
logged ("bulk edit: doesn't take past events → form edit"), and what no step can do ends FAILED with
every reason it collected on the way. Moot work (gig deleted/cancelled/published meanwhile) ends done
with a note.
`SyncWorker` (`gigsynchub.sync.worker.threads` threads, 2; woken on enqueue + every
`gigsynchub.sync.poll-seconds`) calls `SyncDispatcher.runNext()` until nothing it may run is due — it
HANDS WORK OUT and drives no workflow: the next due task names the workload to run (the bands in turn,
`FairTurns`; a lane — a band's work on a platform — never by two workers at once; none of a paused band
or a lane held back).

- Tasks work from the state when they RUN; work that became moot ends DONE with a note.
- Queueing rules (`SyncRequests`): no second UPDATE while one (or the PUBLISH) is PENDING;
  delete discards the gig's PENDING tasks and queues DELETE where it is published (the
  `published_gig` record outlives the catalog row until that succeeds); a pending CANCEL
  and REACTIVATE cancel out into an UPDATE; an edit that moves a gig moves nothing (it is the
  same gig), it only relabels the work still waiting.
- `SyncRetryPolicy`: UPDATE/CANCEL/DELETE (repeatable) retry after 1, 5, 15 min, then
  FAILED; PUBLISH/REACTIVATE never auto-retry (a failure may have created the event);
  what a platform failure means is TYPED where it happens (`FailureKind` on
  `PlatformException` ← `BitPortalException`/`BandzonePortalException`, made only by their named
  factories `temporary`/`refused`/`needsUser`/`busy`): TEMPORARY (retry),
  REFUSED (the platform said no this way — next step, e.g. Bandsintown's row error
  `INVALID_START_TIME` on a past event), NEEDS_USER (switched off or not configured, a town to
  correct, a draft left behind, a platform's sign-in the band must renew — fails at once, so nothing
  is tried with a sign-in that is over), BUSY (postponed; also work
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
- **Circuit breakers per lane** (`PlatformBreakers`, `PlatformBreakerUseCase`, in memory): a batch
  whose results are ALL temporary failures counts against its lane — the band's account on the platform
  (anything else — done, refused, failed for good — shows the platform answering and resets it);
  `gigsynchub.sync.breaker.failures` (3) in a row → the band's work there is held back for
  `.cooldown-minutes` (30): its tasks stay queued, the band's other platforms and every other band go
  on; then one trial batch decides. `.platform-bands` (3) bands held back on one platform at once hold
  the platform back for everyone (`Breaker.everyone`) until fewer are. The Inbox, Activity and Settings
  show a band's with "Resume now" (one held for everyone can't be resumed by a band).
- **Sessions are cheap**: a session is a handful of HTTP requests, so nothing waits for a scarce
  resource — no pool, no lease, and a sync step is never postponed for want of one. A band's sign-in is
  reused across sessions (Bandzone's cookies, Bandsintown's session worked out from the kept refresh
  token and held until it goes stale), so a run signs in once rather than once per gig; when the platform
  has ended it, the band connects again (NEEDS_USER) and it is not retried. Requests are paced like a
  person's — a short random wait between them, a longer one between upload batches — because the
  platforms' terms are about people using their pages, and a burst is what gets an account flagged.
- Page: actions return a `QueueResult` (a toast "Publishing on …" / "Left out: …"); every gig
  shows its state per platform (live / queued / running / retrying / failed / not there) and
  follows it live (`SyncChanges` → `LiveUpdates` → server-sent events); actions on a gig whose
  task is RUNNING are refused by the core (`GigWrites`, 409 `gigBusy`). **Every list of tasks reads as
  a log: latest activity first** (`SyncTask.LATEST_FIRST` — `updatedAt`, the id only settling a tie), so
  the Activity page and a gig's history, which both show each row's `updatedAt`, show it in order. By id
  — the order the work was ASKED for — a retried task sits where it was first queued and the times read
  backwards; `SyncOutboxContract` holds both outboxes to the log's order.
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
upcoming ones and the last `gigsynchub.check.past-days` (60) with its copy there: MISSING (the
event is gone) or DIFFERENT — day, time (Bandsintown: the band's slot), name, venue (a
placeholder "-" is none; a longer platform name is fine), town ("Košice I" = Košice),
country, cancelled state (not on Bandsintown, which has none), and the place: Bandsintown
coordinates (`ImportedGig.latitude/longitude`) > 25 km from a town picked from the place
search. Gigs with sync work still queued are skipped. Fixes, always the user's click:
Re-sync (`ResyncGigUseCase`) or Forget link (a gone event, through `GigWrites.forgetLink`;
Publish creates it again; the gig pages follow). Also
counts platform events no gig is published as (→ Import). Runs nightly
(`PlatformCheckSchedule`, `gigsynchub.check.cron`, default 04:30; `gigsynchub.check.enabled`), every
band one after another **within a budget** (`gigsynchub.check.budget`, 3 h): the next run is
scheduled from the cron before the walk starts, so the cadence never drifts, and a walk that spends
its budget carries on tomorrow at the band it stopped at — so the bands take turns rather than the
first few being checked for ever. Each band's latest result is kept (`CheckResultStore`, table
`band_check`).

## Import (`/import`)

Initial (and repeatable) import of the band's existing gigs, upcoming and past, from
any platform, LINKED to the platform events so they are managed from the catalog
instead of published again. `GigImporter` per platform returns `ImportedGig`s
(gig + platform id): Bandzone scrapes the public band page (year tabs + the "planned"
tab) keeping the concert id and cancelled state; Bandsintown reads the portal.
`ImportPlanner`: skip platform events already linked; group by `GigMatch` (the day and the
venue — a copy read from a platform has an identity of its own that means nothing here) with the
catalog gig and across platforms; else SUGGEST a match on same date + city (only when
the candidate is unique) — the user confirms (default: unconfirmed = import each copy
on its own). Where versions differ (title/time/venue/city) the user picks which to
keep (default catalog > Bandzone > Bandsintown); `GigMerge` fills gaps from the other
versions. `GigImportService.apply` saves + records links in one transaction. Never
deletes.

## Band calendar (`/calendar`)

Read-only: the band's calendar (its private iCal address — a SECRET, set on the page, kept sealed in
`band_calendar` with its zone; band #1's came from `gigsynchub.calendar.ical-url`, its local copy in the
git-ignored `./calendar` file, never in git) mixes gigs
with rehearsals, travel, absences, calls. `CalendarEventClassifier` sorts each event into
GIG / UNSURE / NOT_GIG with the band's `BandProfile`: weighted `ProfileRule`s — the code
knows only rule KINDS, every band's words/labels/weights are DATA. Score ≥ 4 gig, ≤ −1
not a gig (unless there's evidence for a gig and nothing ≤ −4 against), between = the user
decides; `CATALOG_GIG_SAME_DAY` doesn't count after a strong negative. Status
(confirmed/tentative/cancelled) comes from separate role rules. The user's verdict per
event id (`calendar_decision`) always wins. Every matched rule is shown as a reason.

Rules: `classpath:calendar/base.profile` (language-independent) + presets
(`gigsynchub.calendar.presets`, `sk-cz`) are re-synced as PRESET on start (everyone's); each band's
own (USER) rules are edited on the Calendar page (`CalendarSetupUseCase.replaceOwnRules`: replaced
whole, refused with `invalidRule` and the rule's number when one isn't one). Band #1's came from its
file (`gigsynchub.calendar.profile-file`, default `./data/calendar-profile.txt` — members' names, never
in git), read once while it had none. File format: `KIND weight value|alternatives`. Steps 2–3
(learning the profile from platform history, setup wizard for any band) are planned in
`docs/calendar-plan.md`. Calendar notes hold fees/phones — never publish them.

**Saved copy + changes**: each read is saved (`calendar_event`, `CalendarSnapshotStore`, notes
included — local DB only) and compared with the last one (`CalendarChanges`): NEW / CHANGED
(title, time, place, notes, status; plus what the rules said before, when the change made
them say something else) / REMOVED / RETURNED, kept until the user marks them seen. The very
first read is the baseline. The page shows the saved copy; "Read calendar" reads again.

The review reads the gigs **around the calendar's own days** (`GigRepository.findCovering`, a day
either side) and not the band's whole catalog — everything it asks the catalog is about an event's
day, so the answer is the same by construction; a gig an event is LINKED to is fetched by its id
whatever its day, so moving a gig out of the window never makes the page say the link is gone.

**Into the catalog**: `CalendarCatalogUseCase` (`CalendarCatalogService`, through `GigWrites`: a gig
the catalog already has is `gigIdentityTaken`, a link to a gone gig `notFound`).
`CalendarGigDrafter` pre-fills the gig form from an event: show time from the
`SHOWTIME_LABEL` rule kind (the event's own start is the arrival; a show before 06:00 is the
next day), venue/city/country from the map-style place (`Venue, Street, 811 05
City-District, Country`). The user checks and saves → gig + `calendar_link` (event → GigId, which
the gig keeps however it is edited, so the link never has to follow it). Unlinked gig events are matched
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
- **Bandsintown** geocodes the uploaded text — an upload carries no coordinates, its template has
  no such columns — so rows carry street and postal code to tell same-named places apart. After an
  upload (a publish or a bulk edit) the event is read back and its coordinates compared with the
  town's; further than 25 km, the place is **set from the town** by the one-event form edit, which is
  the only way the portal takes a place outright (`BitPlaces`, `HttpBitSession.placedWhereTheGigIs`).

## Bandzone (no browser needed)

`BandzoneGigProvider`/`BandzoneGigImporter` scrape read-only (`BandzoneHtmlFetcher`, at
`gigsynchub.bandzone.base-url`, the year tabs read at once with a 60 s limit). Writes go through the
band-admin pages; off by default (stub). Whether this server drives it is
`gigsynchub.<platform>.client` (`PlatformClient`, `OnPlatformClient`):

```
gigsynchub.bandzone.client=http       # this server drives Bandzone (no browser is involved)
gigsynchub.bandzone.client=off        # the stub (default): it doesn't
```

**The client** (`portal.http`): Bandzone's admin is plain server-rendered forms
whose widgets post the whole form back over AJAX and get JSON, so the client sends exactly what the
pages send — `NetteForm` (a form as a browser would send it: its fields in page order, its buttons
pressed by name), `Answer` (the JSON: a picker's `items`, the re-rendered form's `snippets`, a
finished form's `redirect`, its `flash` messages), `BandzoneWeb` (one account's cookies, the login,
the AJAX posts, the poster's multipart upload), `ConcertForm` (the fields the wizard and the edit
form share; a picker is the form's own search and pick buttons), `LineupTab`, `HttpBandzoneSession`.
A field or button the page hasn't got is an error naming it, never a silent skip. The town is picked by
name + country + district (`BandzoneTowns`), a club matched exactly is linked and anything else kept as
free text, a cancel is checked on the band's gig list and a delete on the concert's public page (404).

**Connecting a band** (`BandzoneAccount`): it types its login, its password and the band's address
(`bandSlug`) on the page once. The server signs in there and then (`HttpBandzoneSignIn`, through the
accounts' `PlatformSignIn`) and writes down only the **session** Bandzone gives back — with "stay signed
in" it lasts about ten years — so the password is never stored. A band that doesn't sign in is not saved,
so an account that doesn't work can't be connected. "Test sign-in" reads the band's page with the kept
session (`BandzoneAccountCheck`); when Bandzone has ended it, the band connects again.
`gigsynchub.bandzone.login / .password / .band-slug` are band #1's old ones (kept once). The real band's
login lives in the git-ignored `.bz-creds` (line 1 login, line 2 password, `chmod 600`);
`.bz-test-creds` is the test band.

**And why this is not Bandsintown's way in** (checked live 2026-10-11, so don't reach for the
bookmarklet here): **Bandzone has no federated sign-in** — its login form carries `login`, `password`,
`permanent`, `doLogin` and nothing else, `/registrace.html` offers no sign-up with anyone else's
account, and the site's own scripts (`js.base`, `js.guest`, `js.js2-common`, ~470 KB) never call
`FB.login` or `getLoginStatus`; the Facebook SDK on every page is there for social plugins
(`FB.init({xfbml:true})`) and a tracking pixel, which is what makes the pages LOOK as though Facebook
could sign you in. Every account therefore has a password: the profile's own settings
(`/fan/<id>/upravit.html`) offer **Změna hesla** and **Trvalá přihlášení** — where a band can clear the
permanent logins, which is how it revokes what we keep — and no "connected account" anywhere. An
account that somehow has no password (one made in some older Facebook era) gets one from
`/zapomenute-udaje.html`, which asks only for the e-mail. **And a bookmarklet could not help even if
Bandzone did offer Facebook**: the way in is cookies, and all three (`SID`, the ten-year `permaLogin`,
`nette-browser`) are **HttpOnly**, so `document.cookie` sees none of them. That is the whole difference
between the two platforms — Bandsintown's token sits in `localStorage`, which the page's own code can
read; Bandzone's sits where only the browser and the server can.

- **Create** — the 2-step wizard: date/time/city → (optional "similar concerts" screen,
  answered "new") → info (name, entry, description, Facebook) → Bandzone sends to
  `/koncert/{id}-…`; the id is taken from that address. **Which step came next is read off the PAGE,
  never off its address**: Bandzone sends you to `zalozit-podobne-koncerty.html?key=…` even when it
  has nothing similar to ask about, and serves the info step at that very address — trusting the
  address cost a publish ("no form for the similar-concerts question", live 2026-10-11). So: answer
  the question if the page holds it, else fill in the info step, wherever we landed. `BandzoneFormCreate` then
  completes the gig via the edit form (end, venue, poster, lineup); if that fails the
  gig is still PUBLISHED with a note to use Re-sync.
- **Update** — `/koncert/{id}/update`: poster upload (`PosterDownloader`: 10 s to connect, 30 s
  for the file), city then venue search (the venue search is scoped to the city; no exact club
  match → free-text venue), dates, info fields; then read back and verified. A picker's pick and
  the poster upload make Bandzone answer with the form again, so what follows works from that
  answer rather than from the page it was sent.
- **Lineup** (`LineupTab`) — full sync: removes performers not in the lineup
  (never the band itself, whose name comes from the profile's `og:title`); adds an
  exact case-insensitive profile match, else a "band without profile" stub — never another
  concert's stub, which the search also offers.
- **Cancel / delete** (the concert's delete tab) — the button's form is submitted, then checked: a
  cancelled concert must be listed `gig--canceled` on the band's gig list (planned tab, else its
  year's); a deleted one's PUBLIC page (`/koncert/<id>`) must answer 404 — the admin pages of a
  deleted concert still open, delete button and all (seen 2026-10-09), so they prove nothing. A
  concert already gone: delete = done ("already removed"), cancel = needs the user.
- Not supported by Bandzone: un-cancel, removing a poster, ticket URL, per-band set times
  (Bandzone gets the whole event; the band's `Slot` goes to Bandsintown only).
- A city Bandzone's search doesn't know fails at once (permanent) with "correct the city".

## Bandsintown (no browser on the server)

Bandsintown has no write API for artists, so the app uses the artist portal's own API — the one its
pages call — over plain HTTP. Off by default (stub):

```
gigsynchub.bandsintown.client=http          # this server drives Bandsintown
gigsynchub.bandsintown.client=off           # the stub (default): it doesn't
gigsynchub.bandsintown.pacing.min-ms / .max-ms   # human pauses between requests (900 / 2600)
```

**Connecting a band, and why it is the one thing a browser does.** The portal's identity provider
(Cognito) allows only the browser's own sign-in exchange — asked for any other, it answers
"USER_PASSWORD_AUTH flow not enabled" — and no Java client implements that exchange. Nor can we put a
sign-in form of our own in its place: bands sign in to Bandsintown **with Facebook** as well as with a
password, and a federated sign-in cannot be reimplemented. So the band signs in **once in its own
browser, on Bandsintown's own form, however it normally does** (Facebook, two-factor, plain password —
all the same to us), clicks the **bookmarklet** Settings gives it to drag to its bookmarks bar
(`AccountField.readAt`; the same line pasted into the browser's console does as much), and pastes **the
refresh token, and nothing else** (`BitConnection`; an older paste carrying the ids is still read).
Framing that form is impossible and always will be: `artists.bandsintown.com` sends
`x-frame-options: SAMEORIGIN`, and a cross-origin frame is opaque anyway — only code running in THAT
origin can read the token, which is what the bookmarklet is for.

The line reads the token at the key its own identity library documents —
`CognitoIdentityServiceProvider.<clientId>.<user>.refreshToken`, reached through that library's
`LastAuthUser` key rather than guessed at with a regex — and then copies it the way **a page** copies:
a selected textarea and `document.execCommand('copy')`, with `navigator.clipboard` tried as well and
the token left selected in a box when the browser allows neither (Ctrl+C). It uses nothing but what a
plain page has, because a bookmarklet IS the page's code: the browser console's own helpers are not
defined there, and `copy(…)` — the obvious way to write this — threw `copy is not defined` on the first
real click (2026-10-11). Nor may the line hold a per cent sign or a newline: the page also turns it into
a `javascript:` address (`DrivingThePlatformsTest` keeps all of this). The server keeps **no password
and no authenticator secret**, and what it holds the band can revoke by signing out there.

**The two numbers are the server's to find, never the band's.** `POST /api/login` answers the manager;
`GET /api/managers/{m}/managed-actors?type=artists` (`BitSignIn.artists()`) answers the artists it looks
after, each row's **`managed_actor_id`** being what the addresses want (its `id`/`artist_id` is the
public artist id — a different number, and the one in the portal's own URLs, so a pasted URL would be
the wrong thing to ask for). **The `type` is not optional**: asked bare, that list answers an empty
array however many artists there are — which is what once had us believing such accounts could not be
asked at all. One artist is taken as it stands; a manager of several is matched on the artist name the
band gave, and told which there are when none matches (`HttpBitSignIn.theArtist`). Both numbers are then
kept with the account, so later sessions cost no extra call.

From the refresh token, `BitSignIn` works out a session whenever one is needed, with no browser: the
refresh token is exchanged for an access token (about 5 h), which the portal's own sign-in
(`POST /api/login`) turns into the session its API wants — recorded against a **fingerprint the server
made up for that band**, nothing of a browser's recomputed. Every request then carries that session and
that fingerprint. When the refresh token is over (the band signed out, or the window the portal allows
has passed), only the band can renew it: NEEDS_USER, never retried.

The rest of the account is the band's too (`BandsintownAccount`): the artist name (required — rows and
the CSV carry it) and notify followers (default: publish silently).

Every address hangs off the manager and the artist together (`BitApi.actorPath`), and `PortalReply`
is the one place the portal's JSON is read.

- **Create** — the Bulk Upload the portal's own page uses: a `BandsintownCsv` (template columns —
  `BandsintownCsv.Column`, in their order —, ≤ `BitSession.MAX_ROWS_PER_UPLOAD` (25) rows) sent as
  a multipart change of the events under `upfile`; the reply maps CSV row → event id. Each new draft
  is then published, quoting the version it was read with. Silent = "Do Not Announce = Y" +
  `announced_at` 2000-01-01. The event lists are read back to confirm PUBLISHED.
- **Update** — the same upload with `Event Id` (+ `Status`) per row edits the events in place
  (`BitSession.EventEdit` per row; results matched by row position).
- **One event at a time** (`BandsintownFormEdit`) — what the upload refuses (past events, and rows it
  said no to): the event is changed as the portal's own edit form changes one — **read it, lay the gig
  over it, `PUT` the whole of it back** at `…/events/{id}` (no `?past=` — the portal's own pages put that
  on their LIST and bulk calls, but one event answers 400 `Unknown query parameter 'past'`). A `PATCH` of an
  event is the portal's way of setting its STATE only (publish, move to draft, remove) — asked to edit
  fields it stalls until the gateway gives up (504), which is what a bare `venue_id` on a PATCH did to
  us before 2026-10-10. A place travels as the portal writes one of its user's own: `venue_name`,
  `venue_city`, `venue_country`, `venue_region`, `venue_address`, `venue_postal_code`,
  `venue_latitude`, `venue_longitude`, `venue_timezone`, with `venue_id` **null** (an id belongs to one
  of Bandsintown's own venue pages). The coordinates are what makes it a place — the portal keeps a
  venue only with a name, town, country AND whereabouts — so a town merely TYPED into the gig's form
  leaves the event's place as it stands, and a gig that has moved somewhere else then needs the user
  ("pick the town from the list"). This is also how a place the UPLOAD guessed wrong is put right: an
  upload can only geocode the row's text (with little more than a village's name it put a gig 71 km
  away, Luková 2026-10-09), so the read-back measures it (`BitPlaces.kilometresOff`) and anything
  further than 25 km from the town is written again this way, in the same session. Without that the
  event simply stayed wrong: a re-sync of an UPCOMING gig goes through the upload again and guesses
  the same, and only a gig already played reaches the form edit at all (the upload refuses the past).
- **When** — rows carry the band's `Slot` when the gig has one (`GigSchedule.showStart/showEnd`),
  else the event's start/end: Bandsintown is about when the artist plays.
- **Past events** — a published past gig is listed under Past, not Upcoming: the create read-back
  checks the past list too. Bulk edits of past events are refused (`INVALID_START_TIME`) → the
  one-at-a-time change does them.
- **List (import)** — the upcoming and past lists (`events?past=false` / `?past=true`) →
  `BitEventMapper` (no entry info on Bandsintown; an event it can't read is left out with a warning
  in the log; an unknown time zone falls back to the country's). The past list comes 20 at a time and
  its `x-next-page` is a DATE, asked for again as `before=…` until there is no more.
- **Cancel/Delete** — Bandsintown has no cancelled state: both remove the event, `RemovalReason`
  CANCELED or OTHER carried with it. Already-gone = success (so reactivate = delete no-op + create).
  Editing a cancelled gig skips Bandsintown. Over the API there is no list-versus-form difference, so
  `deleteEvent` and `deleteEventInForm` are the same removal — which means the DELETE workflow's
  second step (`BandsintownFormRemove`) is never reached, and `BandsintownFormCancel` likewise.
- **Human pacing** (user requirement): a short random wait between requests and a longer one between
  upload batches, and one request at a time for an account — the platforms' terms are about people
  using their pages, and a burst is what gets an account flagged.
- Setup problems (nothing connected, a sign-in the band must renew) need the user (NEEDS_USER), not a
  retry.

## Reading and writing it efficiently

The rules this schema is held to (the ones from *High-Performance Java Persistence* that apply to a
mapping with **no associations at all** — every aggregate is flat, so there is no eager fetch, no
collection and no cascade to get wrong here; the costs are elsewhere):

- **A list is a QUERY, never a filter in the page.** Whatever a page shows, the store decides which
  rows, in which order, and how many: `GigRepository.page(band, GigQuery, today)` → `GigSlice`.
  `findAll(band)` exists for work that really is about every gig and must not answer a page: the
  platform export, Import's planner (which matches every platform event against the catalog) and
  the band's own export file — three callers, and the list of them is short on purpose. The
  calendar review used to be the fourth and is not any more (it reads the days its events cover).
- **Paged by keyset, not by offset.** `(show_at, id) > (the last row's)` — the hundredth page costs
  what the first does, and rows arriving or leaving meanwhile cannot make one slide past unseen. The
  cursor (`GigCursor`) is opaque and one that isn't ours is a `BadRequestException`.
- **Read the columns a view shows, not the entity.** The list reads `GigRowProjection` →
  `GigRow`: no description, no lineup, and nothing managed for the session to dirty-check. Entities
  are for writing, where the dirty checking earns its keep.
- **Nothing per row.** A page's platform states are read for the page's gigs
  (`publicationsOf(band, ids)`, `published_gig_band_gig`), never one query per row and never the
  band's whole catalog; a bulk action reads the gigs it was given with one `findAllByIds` and not a
  `findById` each. `GigListCostsTest` counts the statements and fails if the cost starts to
  follow the rows — the cheapest N+1 alarm there is.
- **A number is counted where the rows are.** The Activity heading's and the operator's four
  numbers are one `group by` (`SyncTaskQueries.openWork`), not every open row counted in Java; "not
  on every platform yet" is a count per gig inside the list's own query (`GigQuery.notOnAllOf`),
  not a `not in` of every gig that already is. `CountingOpenWorkCostsTest` fails if either starts
  loading what it counts.
- **The count that a page shows is the FIRST page's.** `GigSlice.total` is null on every page read
  from a cursor: the page keeps the number it was given and "Show more" adds rows to the same list,
  so counting again is a full pass for an answer nobody reads.
- **A public address is bounded.** `/gigs/{band_slug}` takes no sign-in, so it answers a
  store-ordered `earliestFirst(band, limit)` (2000) rather than however much a catalog has grown to.
- **Ids come from sequences** (V9), because an identity column cannot be batched; with
  `batch_size`, `order_inserts/updates` and `batch_versioned_data` a bulk publish's tasks, a run's
  log and a band's calendar rules go in a few round trips. `in_clause_parameter_padding` keeps the
  plan caches small: an `in (…)` of 3 or 7 ids is padded to 4 or 8, so a handful of shapes serve
  every page. These live in `app/src/main/resources/application.properties` and are copied BY HAND
  into each module's test properties (`features/{gig,accounts,sync,calendar}/src/test/resources/`,
  and the app's own): a module whose tests do not have them is a module where the batching this
  relies on is never exercised, so a change here is a change in six files.
- **A query parameter is a string, not a generated enum**: Spring converts one with `Enum.valueOf`,
  which does not know `upcoming`. MapStruct maps it (`GigMapper.toPeriod/toShow`, as
  `SyncMapper.toShow` already did).
- `spring.jpa.open-in-view=false` (a session must not outlive the use case) and persistence adapters
  are `@Transactional(readOnly = true)` on the class, with only the writing methods saying otherwise.
- Both gig stores — PostgreSQL's and the in-memory one the service tests use — answer
  `GigPagingContract`, so a list cannot read one way in a test and another on the page.

## Database

PostgreSQL 18 (`spring.datasource.*`; in Docker the `db` service, user and database
`gigsynchub`, the password a secret). The schema is owned by **Flyway**
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
address, `key_id`, zone). `V4__band_check.sql`: `band_check (band_id)` — the band's latest platform
check as JSON. `V5__invitations_audit.sql`: `invitation` (the token's hash only), `audit_event`.
`V6__row_level_security.sql`: the role (`bzscraper_app` as it wrote it — see V11), `band_visible(band_id)` and a policy on every
band's table (`calendar_rule`'s shipped rows readable by all, written only by '*'; `sync_log` through its
task); not on `band`, `membership`, `app_user`, `invitation`. **All of the policies are gone again as of
V12** — only the role survives; V6 stays as written because an applied migration is never rewritten. `V7__public_list.sql`: `band.public_list`
(off; band #1's on — it had the list before). `V8__gig_search_and_indexes.sql`: what the LIST reads —
`gig.show_at` (generated: the slot if the event has one, else the start — when the band plays, which is
what `Gig.isPast` reads), the index `gig_band_show (band_id, show_at, id)` the page and its keyset walk,
`gig.search_text` (title + venue + town folded by `TextFold`, written with every save) with a **pg_trgm**
index for the substring search — extension and index both optional, so a database whose user may not
create extensions still answers, by scanning that one band's rows — and `published_gig_band_gig
(band_id, gig_id)`, which the primary key cannot serve (the platform sits in the middle of it).
`V10__gig_identity.sql`: a gig's identity stops being its day and venue — `gig.id` becomes a
`uuid` of the gig's own and the old key becomes `gig.match_key` (`unique (band_id, match_key)`: one
gig per band per day and venue, which is what the primary key used to say), and `published_gig`,
`sync_task` and `calendar_link` name that identity instead of the key (every key any row named got
one identity, including the keys of gigs already gone, whose rows outlive them on purpose). The
band's last platform check is cleared: its JSON named gigs by the old key and the nightly run
writes a new one.
`V11__rename_app_role.sql`: the app's database login follows the app's name, `bzscraper_app` →
`gigsynchub_app` (2026-10-12). It is a migration of its own rather than an edit to V6, which created
the role, because **an applied migration is never rewritten** — Flyway checksums it, so changing one
byte of V6, a comment included, stops the app against every database that already ran it. A
brand-new database therefore makes the old name and renames it a moment later; the grants, the
policies and the default privileges all follow the role itself, so the rename is the whole of it. `V9__sequences_for_batching.sql`: `sync_task`, `sync_log`, `calendar_rule` and `audit_event` take their
ids from sequences instead of identity columns, because an identity column cannot be batched (the
driver must fetch each assigned id back) and all four are written in bulk; drawn 50 at a time
(`pooled-lo`, so a block's ids are above what the database gave), each sequence owned by its column so
`pg_get_serial_sequence` still answers the H2 import, and started well above the rows already there.

`V12__drop_row_level_security.sql`: row-level security goes (2026-10-12) — the thirteen `band_rows`
policies, the RLS flags and `band_visible(uuid)` — and tenant isolation becomes the queries' own, kept
that way at build time (see **Bands** above: `BandScopedQueriesTest`, `@AcrossBands`, the two ArchUnit
rules against native SQL). It was always described as a net under the ports' own band checks, and that
is what it proved to be: the feature modules' persistence tests connect as the user that OWNS the
tables, and a table's owner bypasses RLS (V6 never asked for `force row level security`), so those
tests were already proving the queries' predicates and never the policies. Worth knowing before anyone
proposes putting it back: `app.band` had no privilege check on it, so anyone able to connect as
`gigsynchub_app` could `select set_config('app.band', '*', false)` — it guarded against a mistake in
the app's own queries, never against a leaked database password. The **role stays** for its own sake
(no DDL, no `flyway_schema_history`), as do its grants and default privileges.

`V13__one_open_task_per_gig.sql`: a unique index on `sync_task (band_id, gig_id, platform, action)
where status = 'PENDING'` (2026-10-13). The queueing rules were all advisory — `SyncRequests` reads
a gig's open work and then writes, and under READ COMMITTED two requests for the same gig both see
nothing and both queue, which on a platform whose create makes a new event each time publishes the
gig twice and leaves a live event nothing here has a pointer to. PENDING only, deliberately:
RUNNING too would refuse a legitimate edit while an update runs, and a test says so by name.
`V14__sync_log_knows_its_band.sql`: `sync_log.band_id`, backfilled from its task (2026-10-13) — the
one band-scoped table that had no band of its own, so its reads were exempt from the isolation net
by an `@AcrossBands` rather than covered by it. A column and not a JPA association: the association
would have been the better way to say it and Hibernate refuses it beside the raw `task_id`
("physical column name [task_id] referred to by multiple logical column names"), and replacing the
column with one would put a lazy fetch in the table with the most rows in this schema.

**From H2** (the database until 2026-10): `H2Import` (a Flyway `afterMigrate` callback in the kernel)
copies an H2 SQL backup (`SCRIPT TO`, `gigsynchub.db.import-h2`) into an EMPTY database right after
the schema is built — every table's rows (the columns both have), times without zone shifts, the
id counters moved past the copied ids; one transaction, so a failure leaves nothing and stops the
start; skipped when the database has data; every row becomes band #1's. H2 stays a dependency only for this (`h2.version` pinned).

**Backups**: the compose service `backup` (`docker/backup.sh`: `pg_dump --format=custom` of
`gigsynchub` and `rauthy` plus `pg_dumpall --globals-only` for the ROLES, which live in neither
database and which a restore needs first; daily, `./data/backups`, 14 of each kept, `umask 077`
because a dump holds every band's data). Restoring is `docker/restore.sh`
(`docker compose run --rm backup /restore.sh`, `DAY=` for a given day): it puts the roles back,
refuses a database that already holds tables, and says what is left to do. The whole drill, and
the two things no dump can hold — **`./secrets` (above all `data_keys`, without which every band's
sealed logins and calendar address are gone for good) and `.env`** — are
**`docs/disaster-recovery.md`**. The backup's own healthcheck reads `./data/backups/LAST_OK`, not
whether the container is up: the failure that matters is the silent one.

**The containers**: every log capped (3 × 10 MB — the default is "until the disk is full", and the
first thing a full disk stops is PostgreSQL), every service `no-new-privileges` and `cap_drop: ALL`
except the two that need one capability (`proxy`: `NET_BIND_SERVICE`) or whose image does its own
privileged setup (`db`, deliberately left alone: the one container whose failure to start is not
fixed by starting it again). Memory and CPU per service from `.env` (`GSH_*_MEM` / `GSH_*_CPUS`);
`db` has `PGDATA` pinned (the volume is mounted at `/var/lib/postgresql`, and the image's default
has moved between major versions — a default that changes under a mounted volume is an empty
database on a machine that had one) and `stop_grace_period: 2m` (a database is stopped, never
killed). The app stops gracefully (`server.shutdown=graceful`, 20 s, inside compose's 45 s) so a
sync step talking to a platform is not cut off mid-create. Flyway runs with
`lock_timeout = 15s`: a migration that cannot get its lock fails rather than queueing the whole
database behind it.

**CI** (`.github/workflows/build.yml`): every push and pull request runs `./mvnw verify` on **Java
25** — the version the image runs, not the newest — with Docker for Testcontainers; Dependabot
(`.github/dependabot.yml`) watches Maven, npm, Docker and the actions themselves, weekly and
grouped.

## The operating envelope

What this app is built to carry, and what it does at the edge of it. Every number here is set in
one place and named; none of them is a guess left in the code.

| | How much | Where it is set | What happens at the edge |
|---|---|---|---|
| Requests | 600/min a caller, 120 of them changes | `gigsynchub.limits.*` | 429 `tooManyRequests` with `Retry-After` |
| Callers counted | 50 000 at once | `gigsynchub.limits.tracked-callers` | the quietest is forgotten and counted afresh |
| Request body | 2 MB | `docker/Caddyfile` | the proxy refuses it; the app never sees it |
| A field, a list | the spec's `maxLength` / `maxItems` | the specs | 400 `badRequest` |
| A page of gigs | 200 rows | `GigQuery.MAX_SIZE` | the ask is clamped |
| A bulk action | 1000 gigs | `catalog.yaml` | 400 `badRequest` |
| Bands owned | 10 a user | `gigsynchub.accounts.max-bands-owned` | 409 `tooManyBands` |
| Live streams | 100 a band, 2000 in all, 30 min each | `SseLiveUpdates` | the oldest ends; the page reconnects |
| Background work from a request | 8 at once, 50 waiting | `spring.task.execution.*` | 429 |
| Sync lanes | 2 at once, the bands in turn | `gigsynchub.sync.worker.threads` | the rest wait their turn |
| Any answer from outside | 10 MB | `PublicAddresses.MAX_BODY_BYTES` | the fetch fails; a band's address is told nothing of what answered |
| A session | 12 h idle, 12 h in all, re-asked every 5 min | `server.servlet.session.timeout`, `gigsynchub.auth.*` | 401; the page shows the sign-in |
| The nightly check | 3 h a night | `gigsynchub.check.budget` | it carries on tomorrow where it stopped |
| What is kept | 90 d settled work, 365 d audit, 180 d calendar copy, 30 d invitations | `gigsynchub.housekeeping.*` | the nightly housekeeping forgets it |
| A container | memory and CPU per service, logs 3 × 10 MB | `.env` (`GSH_*_MEM`, `GSH_*_CPUS`) | the kernel refuses the memory; logs roll |

**One app per database.** A second instance would run a second sync worker over the same tasks —
the status changes are guarded (`SyncStatus.canBecome`, `sync_task.version`), so the damage is
duplicated platform calls rather than corrupted rows, but it is not a supported way to run this.
Nothing enforces it: the only way to would be an advisory lock, which is native SQL, and "no Java
source of this app writes SQL" is a rule worth more than this guard.

## Key Dependencies

- Spring Boot 4.1.1, Java 25; Spring Data JPA + PostgreSQL 18 + Flyway (H2 2.4.240 only for the import);
  Spring Security's OAuth2 client (OIDC) with Rauthy 0.37 (Docker)
- Docker: the Temurin 25 JRE (nothing else — no browser is installed); Testcontainers 2 for the tests
- OpenAPI: openapi-generator 7.26 (`spring`, Spring Boot 4 + Jackson 3); openapi-typescript 7 + openapi-fetch
- MapStruct 1.6.3 (every mapper: API ↔ domain and JPA entity ↔ domain)
- jMolecules (BOM 2025.0.2: `jmolecules-ddd` + `jmolecules-hexagonal-architecture` in every module,
  `jmolecules-archunit` in the app's tests) — annotations with no runtime that say what each type is
- The page: React 19 + TypeScript, Vite 6, Mantine 8, TanStack Query 5, React Router 7, i18next,
  vite-plugin-pwa, Mantine dates (+ dayjs); built by Maven (frontend-maven-plugin, Node 22 LTS fetched
  into web/frontend/node — `node.version` in web/frontend/pom.xml)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Nothing for driving a browser: both platforms are reached over plain HTTP (`java.net.http`)

## Testing

`JMoleculesModelTest` (app): jMolecules' own ArchUnit rules over the annotated model — see
**Architecture**. `ApiSecurityTest` (app, MockMvc, signed in with Spring Security's `oidcLogin()` — `SignedIn`; the
test properties' `first-owner-email` makes `owner@band.test` band #1's owner): the API is 401 until
signed in, band #1 is its owner's and 404 for anyone else, signing in goes to Rauthy with PKCE,
signing out names Rauthy's end-session address, changes need the CSRF token, a refusal is a 409 with
its code, the page's own addresses load `index.html`. `BandIsolationTest`: every band path of every
spec (read from the YAML) is 404 for a non-member, both ways. The test properties carry a made-up data
key (`gigsynchub.accounts.data-keys`; `AccountsFakes.TEST_KEYS` for module tests); the JPA tests of the
sealed rows check the secret isn't in the row, that a row copied to another band doesn't open, and key
rotation. The app's tests connect as `gigsynchub_app` like production
(`BandMembershipApiTest` checks the role is the restricted one, and that it can neither alter a table
nor read the migration history); `BandMembershipApiTest`: an invitation joins whom it's for, once; the audit log;
the export without a secret; `RateLimitsTest`. `ManyBandsSyncTest` (sync): ten bands queued at once on three workers all finish, a lane is never
run twice at once, one band's broken account holds back only it, many failing bands hold the platform back.
Each feature's tests run with a band
(`TestBands.BAND`; the fakes key everything by band) and check that another band (`OTHER`) sees none
of it. The page: Vitest (`web/frontend`, run by `./mvnw test`) — both languages have the
same keys and every key the pages use, dates, the calendar's show applied to the form, a band's address from its name.

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). The domain has direct unit tests;
service tests use `SyncFakes` (sync test-jar: in-memory outbox/records/repository/settings,
mutable clock, builders for the registry, engine, dispatcher and requests) and `CatalogFakes`
(catalog test-jar: the write path and catalog use cases) and `TestPlatforms`
(two made-up platforms' traits — the core tests never need the real adapters). The fake
outbox and `JpaSyncOutbox` both pass `SyncOutboxContract` (sync test-jar), so the
fake can't drift from the real one. `gigsynchub.sync.worker.enabled=false` in Spring tests
(tasks queue, never run). Persistence tests run the migrations on PostgreSQL in Docker (Testcontainers'
`jdbc:tc:postgresql:18-alpine` URL in each module's test `application.properties`, one container per
test run — `./mvnw test` needs Docker; `H2ImportTest`: an H2 backup into its own empty database)
(`JpaGigConcurrencyTest`: the optimistic lock across real transactions).

**The platforms are tested against stand-ins that talk as their sites do**, so no browser and no
network are needed. `BandzoneHttpFixture` (bandzone test sources) answers as the band admin's pages
do — the login and its cookie, the pickers' `items`, a pick's re-rendered form, a finished form's
`redirect`, the poster uploader, the wizard in each of the three shapes Bandzone has shown after its
first step (`AfterStepOne`: the question, no question but still at the question's address, straight
to the info step — the middle one is what the real site does, and modelling it as a redirect to the
info ADDRESS is what let a publish fail with green tests), the
performers tab with its remove dialogs (which may answer only "go there" when the concert's address
changed with its name), the delete tab, the band's gig list and a deleted concert's 404 — and keeps
what it is sent, so the client's read-backs see what was stored. `HttpBandzonePortalClientTest` runs
the whole cycle against it, plus a refused login, a town Bandzone knows several of, and a concert
already gone. `BitHttpFixture` does the same for the artist portal's API (its reply envelope, the
past list's date cursor, the upload's row → event id mapping, row errors, and an event's two writes —
a PATCH that sets its state, a PUT that replaces it whole) and `HttpBitSessionTest` runs the six
operations against it.
`PlatformClientTest` covers the client switch and an unknown value stopping the start.
Change a client → run these first; the live tests below then confirm against the real sites.

`HttpBandzonePortalClientLiveTest` creates, edits (every field, incl. lineup and poster), cancels,
deletes and deletes again (already gone) a real Bandzone gig, and checks the login is reused. Skipped
unless `BZ_LIVE=true`; reads `BZ_LOGIN` / `BZ_PASSWORD` / `BZ_SLUG`, `BZ_LINEUP_BAND` (a real band
that gets notified), optional `BZ_KEEP=true`. Run it only against a test band.

`HttpBitPortalClientLiveTest` creates a made-up Bandsintown event (published WITHOUT announcing it),
reads it back, edits it both ways and removes it — the removal runs even when a step fails, and every
step is tried and reported, so one made-up event tells us about all of them. Skipped unless
`BIT_LIVE=true`; reads `BIT_ARTIST_NAME` and `BIT_CONNECTION_FILE` (the connection a band's browser
sign-in leaves, as the bookmarklet in Settings copies it — no browser and no password are involved).
There is no Bandsintown test artist — the event is public for ~1 minute.

`CalendarClassificationLiveTest` prints how the shipped + band rules sort the REAL
calendar (read-only). Skipped unless `CAL_LIVE=true`; reads `CAL_URL`, optional
`CAL_PROFILE` (band rules file) and `CAL_CATALOG_DAYS` (file of ISO dates with gigs).
