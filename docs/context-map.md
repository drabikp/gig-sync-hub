# The context map — who models what, and what is translated where

This app is already built as bounded contexts: one Maven module each, with its own model of the
same real things. `CLAUDE.md` says what every module *is*; this page says how they stand to one
another, which is the part `ArchitectureTest` enforces without anyone being able to read it. Nothing
here is a plan: it is the shape the code already has, written down so a change can be judged
against it.

## The map

**features/gig — the shared kernel.** `Gig`, `GigId`, the value objects, `Platform`/`PlatformTraits`,
the ports every context writes through (`GigRepository`, `PublishedGigStore`, `Transactions`,
`LiveUpdates`) and the one database schema. Everything below depends on it; it depends on nobody,
knows no feature and names no platform.

What each module depends on, read off the poms (Maven forbids cycles, so these are facts rather
than intentions):

| Module | Depends on |
|---|---|
| `features/gig` (kernel) | nothing of ours |
| `features/accounts` | gig |
| `features/places` | gig |
| `features/sync` | gig |
| `features/catalog` | gig, places, sync |
| `features/importing` | gig, sync, catalog |
| `features/check` | gig, sync, catalog, importing, **accounts** (its nightly run is every band's) |
| `features/calendar` | gig, places, sync, catalog, **accounts** (its address is sealed with the band's key) |
| `web/live` | gig only — server-sent events live here and nowhere else |
| `platforms/portal` | nothing of ours: the one thing both platforms share is the client switch |
| `platforms/bandzone`, `platforms/bandsintown` | gig, sync, catalog, importing, accounts, portal |
| `app` | everything — wiring, security, the row scope, the audit interceptor, the architecture tests |

The platforms are **anticorruption layers**: each plugs into the features' ports (`SyncStep`,
`GigImporter`, `GigExporter`, `PlatformAccountCheck`, `PlatformSignIn`, `PlatformTraits`) and keeps
its site's language behind its own `portal.http`.

## The relationships, by name

| Seam | Pattern | How it is held |
|---|---|---|
| `features/gig` ← everyone | **Shared kernel** | Deliberately small (a gig, its value objects, the ports, the schema) and jointly owned. It knows no feature and no platform; its persistence and wiring are its own. A shared kernel is only legitimate while it stays small — growing it is the thing to resist. |
| feature ← feature | **Customer–supplier, through ports** | A module uses another only through its `domain` and its `application.port.in` / `.out` — never its services, adapters or wiring. The calendar, Import and the check all write gigs through `GigWrites`; the check asks the sync through `SyncStateUseCase`. |
| lower feature ← higher feature | **Dependency inversion** | Where a module lower in the order needs something from a higher one, the lower one owns the port and the higher implements it: `PlatformAccountCheck` and `PlatformSignIn` (the accounts hold a band's platform account; only the platform can say whether it still works). The seam that used to be the clearest example of this, `GigMovedListener`, is gone — see below. |
| platform ↔ its site | **Anticorruption layer** | Bandzone's Nette forms and Bandsintown's portal JSON never travel inward: `NetteForm`/`Answer`/`ConcertForm`, `PortalReply`/`BitEventMapper`, `GigSummaryToGigMapper` translate at the boundary, and `portal.http` is package-private. `PlatformNamesTest` makes the ACL provable — no feature source may even *name* a platform. |
| app ↔ the page | **Published language** | The OpenAPI specs are the contract, both sides generated from them; refusals travel as `ProblemCodeValue` and `ErrorCodesTest` keeps the enums and the spec the same. |
| app ↔ Rauthy | **Conformist** | Identity is Rauthy's model, taken as it comes (`subject`, e-mail, name). The app translates it once at `OidcCurrentLogin` → `CurrentLogin` and models only what is its own: users, memberships, roles. |
| `places` ↔ Photon | **Anticorruption layer** | OpenStreetMap's answer becomes a `Town` at `PhotonPlaceSearch`; its `county` becomes the district in the form the platforms' own town lists use. |

## The same real thing, modelled per context — on purpose

This is the part that looks like duplication and is not. A gig is:

| Context | Its model | Why it differs |
|---|---|---|
| `gig` (kernel) | `Gig` — always valid, immutable, identified by `GigId` (its own) and matched by `GigMatch` (its day and place) | the source of truth |
| the gig list (`catalog`, over the kernel's types) | `GigRow` / `GigSlice` | a page shows columns, not aggregates, and reads no description or lineup |
| the gig form | `GigDraft` + its `Problems` | half-filled and invalid is the normal state while typing |
| `importing` | `ImportedGig` | what a platform *says*, before anyone decides it is ours |
| `calendar` | `CalendarEvent` → `CalendarGigDraft` | an appointment, which may or may not be a gig at all |
| `check` | `Drift` | not a gig but a *difference* between two of them |
| `bandsintown` | a CSV row / `BitEvent` | the shape that site's API accepts |

Translating between these is what the mappers are for. Sharing one class across them would make
every context pay for every other context's fields — which is the usual reason a model rots.

## Where the boundaries are not drawn at the invariants

Honest notes, so they are decisions and not surprises:

- **`GigId` was a natural key** (date + normalised venue) until 2026-10, so editing a gig's day or
  venue *changed who it was*: `CatalogWrites.replace` re-keyed the publications and the queued work
  and told the `GigMovedListener`s. That was the one place where DDD's "identity is independent of
  state" was given up, and V10 ends it: the id is the gig's own, and the natural key lives on as
  `GigMatch` — kept in the row as `match_key` and unique per band, because it is still what Import
  and the nightly check recognise a platform's copy by (a platform's event carries no id of ours).
  What went with the move: `GigMovedListener` and its one implementation, `PublishedGigStore.move`,
  `CalendarLinkStore.move`, `SyncOutbox.move` and the entities' `moveTo`. What is left of it is
  `SyncOutbox.relabel` — the waiting work says how the gig reads now, which is a label, not
  identity.
- **`BandId` is a tenant, not an aggregate root.** Every port that touches a band's data takes it
  explicitly; no thread-local "current band". A band does not *own* its gigs in the model — it would
  be an unbounded collection — and the boundary is held by the database instead (row-level security,
  `RowScope`).
- **`Publication` is its own aggregate**, referenced by id, because a `published_gig` row
  deliberately outlives the deleted catalog gig until the platform's DELETE succeeds.
- **`SyncTask` likewise**: tasks outlive their gig on purpose (`gigLabel` is there so the log still
  reads once the gig is gone), and the outbox is eventually consistent by design.
- **A band's roster IS an aggregate** (`BandMembers`), because its rule — a band keeps an owner —
  spans all of it; the store holds the band's row while a change decides.

## What the tests already enforce

`ArchitectureTest` (ArchUnit) is the executable half of this page: the domain depends only on the
JDK and domain code; application code knows no framework, logger, adapter or web setup; a feature is
reachable from outside only through its domain and ports; the kernel knows no feature; a band's rows
are read and written by band; live updates travel only through their port; how a platform talks to
its site stays behind its portal client; no cycles between modules or packages. `PlatformNamesTest`
holds the platform boundary down to the wording.
