/**
 * The API's shapes under the names the pages use — all generated from the OpenAPI specs
 * (src/api/generated; each module's src/main/openapi/*.yaml, where they are …Request, …Response
 * and …Value). Nothing here is written by hand.
 */
import type { components as AccountsApi } from './generated/accounts';
import type { components as AuthApi } from './generated/auth';
import type { components as CalendarApi } from './generated/calendar';
import type { components as CatalogApi } from './generated/catalog';
import type { components as CheckApi } from './generated/check';
import type { components as GigApi } from './generated/gig';
import type { components as ImportApi } from './generated/import';
import type { components as LiveApi } from './generated/live';
import type { components as PlacesApi } from './generated/places';
import type { components as SyncApi } from './generated/sync';

type Shared = GigApi['schemas'];
type Catalog = CatalogApi['schemas'];
type Sync = SyncApi['schemas'];
type Calendar = CalendarApi['schemas'];
type Check = CheckApi['schemas'];
type Import = ImportApi['schemas'];

// the shared parts (gig.yaml)
export type Country = Shared['CountryValue'];
export type EntryType = Shared['EntryTypeValue'];
/** A gig as the form holds it (sent as GigFormRequest, answered as GigFormResponse — the same fields). Times are "HH:mm". */
export type GigDraft = Shared['GigFormRequest'];
export type Problem = Shared['ProblemResponse'];
/** Every case the API says no with (each has its text, `errors.<code>`). */
export type ProblemCode = Shared['ProblemCodeValue'];

// the catalog (catalog.yaml)
export type Gig = Catalog['GigResponse'];
/** A LIST row — what a row shows; the gig's own page reads the rest. */
export type GigRow = Catalog['GigRowResponse'];
export type GigList = Catalog['GigListResponse'];
export type GigPeriod = Catalog['GigPeriodValue'];
export type GigShow = Catalog['GigShowValue'];
/** What the band asked the list for. The server does the filtering — the page never does. */
export type GigFilter = { period: GigPeriod; q: string; show: GigShow };
export type GigDetail = Catalog['GigDetailResponse'];
export type GigHistory = Catalog['GigHistoryResponse'];
export type Edited = Catalog['GigEditedResponse'];
export type PlatformState = Catalog['PlatformStateResponse'];
export type PlatformStateName = Catalog['PlatformStateValue'];
export type Platform = Catalog['PlatformResponse'];
/** A field of a band's account on a platform, as the platform describes it. */
export type AccountField = Catalog['AccountFieldResponse'];
export type QueueResult = Catalog['QueueResultResponse'];

// the sync (sync.yaml)
export type SyncAction = Sync['SyncActionValue'];
export type SyncStatusName = Sync['SyncTaskStatusValue'];
export type Task = Sync['TaskResponse'];
export type TaskLog = Sync['LogEntryResponse'];
export type Breaker = Sync['BreakerResponse'];
export type SyncStatus = Sync['SyncStatusResponse'];

// the town picker (places.yaml)
export type Town = PlacesApi['schemas']['TownResponse'];

// the band calendar (calendar.yaml)
export type CalendarKind = Calendar['CalendarKindValue'];
export type CalendarFilter = Calendar['CalendarFilterValue'];
export type CalendarRow = Calendar['CalendarRowResponse'];
export type CalendarOverview = Calendar['CalendarOverviewResponse'];
export type CalendarRule = Calendar['RuleResponse'];
export type CalendarRuleDraft = Calendar['RuleRequest'];
export type RuleKind = Calendar['RuleKindResponse'];
export type CalendarConnection = Calendar['CalendarConnectionResponse'];
export type GigRef = Calendar['GigRefResponse'];
export type DifferenceKind = Calendar['MatchDifferenceKindValue'];

// the platform check (check.yaml)
export type Drift = Check['DriftResponse'];
export type DriftField = Check['DriftFieldValue'];
export type DriftRef = Check['DriftRefRequest'];
export type CheckState = Check['CheckStateResponse'];

// import (import.yaml)
export type ImportState = Import['ImportStateResponse'];
export type ImportProposal = Import['ImportProposalResponse'];
export type ImportVersion = Import['ImportVersionResponse'];
export type ImportDecision = Import['ImportDecisionRequest'];
export type ImportResult = Import['ImportResultResponse'];

// the bands and their members (accounts.yaml)
export type MyBand = AccountsApi['schemas']['MyBandResponse'];
export type Member = AccountsApi['schemas']['MemberResponse'];
export type Role = AccountsApi['schemas']['RoleValue'];
export type BandForm = AccountsApi['schemas']['BandRequest'];
/** A band's account on a platform: its settings, which secrets are kept (never their values), its last test. */
export type PlatformAccount = AccountsApi['schemas']['PlatformAccountResponse'];
export type PlatformAccountForm = AccountsApi['schemas']['PlatformAccountRequest'];
export type Invitation = AccountsApi['schemas']['InvitationResponse'];
export type InvitationOffer = AccountsApi['schemas']['InvitationOfferResponse'];
export type AuditEntry = AccountsApi['schemas']['AuditEntryResponse'];

// signing in (auth.yaml) and the live updates (live.yaml)
export type Me = AuthApi['schemas']['MeResponse'];
export type LiveTopic = LiveApi['schemas']['LiveTopicValue'];
