# Platform sync as workflows

The catalog is the source of truth; platforms follow it through the sync outbox (see
CLAUDE.md, "Platform sync"). One adapter method per platform per action (publisher / updater /
withdrawer, as it was until 2026-10-08) could not express what the platforms really need:

- Bandsintown edits upcoming events by bulk upload but refuses edits to past events
  (`INVALID_START_TIME`, `INVALID_EVENT_ID`) — its edit form takes them, one at a time.
- Bandzone has no bulk upload: everything goes one gig at a time through its forms.
- A future platform (Songkick, in the backlog) needs a venue lookup the user may have to
  confirm, and waits days for approval.

So each action is a **workflow**: an ordered list of **steps**, where every step is one way of
doing (part of) the work, and platforms contribute **step implementations**. What a platform can
do follows from which implementations exist — never from configuration.

## Orchestration, not coordination

The engine owns the order. `WorkflowEngine.run(Workload)` is given a **workload** — a band's due
work of ONE action on ONE platform — and walks that action's workflow itself, in one call:

```
run(this band's UPDATE work on Bandsintown)

  bulk edit  ← the due runs waiting here, at most one batch of them
               ├─ done, failed for good, out of retries  → settled; it leaves the pass
               ├─ failed with a retry left, postponed    → it waits here for another turn
               └─ refused, or this step won't take the gig, or the platform hasn't
                  got the step at all                    → on to the next step, below

  form edit  ← what the bulk edit passed on, and the runs that were already waiting here
               └─ nothing left to try  → FAILED: "do it there by hand (every reason)"
```

A gig the bulk step passes on is tried the next way **in the same pass**, while the platform's
session is still open — not when something comes round again.

`sync_task.step` is written down at every hop (`SyncOutbox.advance`), so a restart, or a pass cut
short by a pause, carries on from where each run stands. It is a **checkpoint, not a program
counter**: nothing has to be re-dispatched for a workflow to reach its end, and no step is done
twice because of one.

Who decides what:

| | |
| --- | --- |
| `WorkflowEngine` | the order: which step, which gigs, what follows an outcome, when to stop |
| `Workflows` | each action's steps, in the order they are tried (domain, no dependencies) |
| `StepRegistry` | which step implementation a platform has for a type; and, before work is queued, whether any step could take this gig at all (→ "Left out: …") |
| `RunIntake` | what a step is to do for one run — the gig as it is NOW, the platform's id for its copy, whether the step takes it. It answers; it writes nothing |
| `OutcomeSettlement` | what an outcome means for the record (done / retry / failed / postponed), written down at once. A refusal it leaves to the engine, because what follows a refusal is the workflow's next step |
| `SyncDispatcher` | which workload runs next: the bands in turn, one workload per lane, none of a paused band or a lane held back |
| `SyncWorker` | threads, and asking for another workload until none is due. It hands work out; it does not drive workflows |

## Concepts

- **Run** — one outbox task: gig × platform × action (publish, update, cancel, delete,
  reactivate), created in the same transaction as the catalog change that needs it.
- **Workload** — a band's runs of one action on one platform: what the engine is given. A task is
  never run on its own, because a task cannot say which batch it belongs in.
- **Workflow** — per action, in code (`Workflows`): an ordered list of step types.
  `update = bulk edit → form edit → by hand`.
- **Step type** — one kind of work (bulk edit, form edit, cancel, remove, recreate). Its
  implementations are per platform; a type the platform has no implementation for passes every gig
  straight on, with "none for &lt;platform&gt;" in the log.
- **Step contract** (`SyncStep`)
  - `refusal(gig)` → empty, or the reason this step can't do this gig ("doesn't take past
    events"). Asked when the work is queued, so the user hears "Left out: …" at once, and again
    before it runs — the gig may have changed since.
  - `run(batch)` → one `StepOutcome` per gig, in the same order:
    - **done** (the platform's id for a created event, or a note),
    - **refused** — a definite per-gig "no" and nothing happened there: the next step may do it
      (for any action: a refused bulk create does move on to the create form, because "refused"
      means the platform did nothing),
    - **failed** — technical (timeout, login lost): this step again later, never another way —
      it may have half-happened. Creates are not retried by themselves at all,
    - **failed for good** — nothing else should try; the user decides,
    - **postponed** — it never reached the platform (the app is stopping): again in a minute, no
      attempt used, and the breaker hears nothing.
  - **batch size**: how many gigs one call takes (Bandsintown's upload 25; 1 = one at a time,
    `OneAtATimeStep`, each result written down before the next).
- **A pass's share** — each step does at most one batch: a batch step its own size, a
  one-at-a-time step ten gigs. Then the workload's turn is over and the lane is given up, so the
  other bands and this band's other platforms get theirs.
- **Sessions are the platform's own business**, not the engine's. A session is a handful of HTTP
  requests (no browser has been involved since 2026-10-10), so a step is never postponed for want
  of one; a band's sign-in is reused across sessions by its portal client.
- **State** (DB): `sync_task.step` (the checkpoint), attempts, next attempt, and the path as log
  lines — "bulk edit: INVALID_START_TIME → form edit", then "form edit".

## What happens when the user syncs

Re-sync of 5 gigs — all on Bandzone; on Bandsintown 3 upcoming + 2 past.

1. **Click → queue** (one transaction, immediate answer). One run per gig × platform. A run no
   step of that platform could take is not queued but reported ("Left out: … do it there by
   hand"). Rows show ⏳ and the worker is woken.
2. **A worker takes a workload** — the band's UPDATE work on Bandsintown, say. Another worker can
   take its Bandzone work at the same time; another band's work goes on regardless.
3. **Bulk edit** — the 3 upcoming gigs in one upload (the 2 past ones are passed straight on:
   "bulk edit: doesn't take past events"); each row's result is written down on its own, so the
   done ones show ✓ at once. A row the upload refuses joins the form edit. A failure of the whole
   upload moves nothing on — all 3 wait for the bulk step's own retry.
4. **Form edit**, in the same pass — the leftovers one after another, with human pacing: each
   gig's result written down as it happens, the gig read fresh before its turn, one gig's failure
   not stopping the others.
5. **By hand** — whatever no step could do ends FAILED with every reason it collected on the way,
   and waits for the user on Activity (Retry / Discard).
6. **Bandzone** — its update workflow has no bulk step, so one pass passes all 5 on to its form
   and does up to ten of them there.
7. **Retry** restarts at the step the run stopped at (its intake is asked again first). Pause ends
   the pass after the gig in progress.

## Not doing

No external engine (Temporal is a separate server; Flowable brings BPMN and its own tables;
Spring State Machine models states, not "try these ways in turn") — the needs here are small:
steps, batching, a checkpoint and a log in the database the app already has.

Not yet needed by any step: a **waiting** outcome (check again at a given time — an approval) and
a **needs you** outcome (a choice in the UI, e.g. which venue). Songkick is what would bring them.

## How it got here

1. **2026-10-08:** engine core (`WorkflowEngine`, `SyncStep`, `sync_task.step`) + the **update**
   workflow; Bandzone (form edit) and Bandsintown (bulk edit) on it. Then publish (bulk upload →
   create form), cancel, delete and reactivate (remove and create again, built by the registry
   from the platform's own steps); the old publisher / withdrawer adapters and the capability
   flags went. Verified live end to end on the Bandzone test band.
2. **2026-10-08:** `BandsintownFormEdit` — the single-page form for past events and rows the bulk
   edit refused; then `BandsintownFormCancel` / `BandsintownFormRemove` after the list's cancel /
   remove. Verified live (a made-up past event created silently and removed through its form; a
   removed event's form doesn't open → "already gone").
3. **2026-10-10:** no browser anywhere — both platforms over plain HTTP, so "the engine opens a
   browser session per platform per pass" stopped being true, and with it the one-platform-at-a-
   time rule.
4. **2026-10-11:** many bands — lanes (a band's account on a platform), `FairTurns`, per-lane
   breakers.
5. **2026-10-12:** orchestration — `run(Workload)` walks the workflow in one call; the step column
   became a checkpoint. Before this, a run moved one step per worker round: `runFrom(task)` ran a
   single step for the tasks that happened to be waiting at the same one, and the workflow existed
   only as the sum of a column, two classes that wrote to it and a loop that kept re-dispatching.
   Nothing was wrong with what it did; nobody could see what it was.
6. Backlog: Songkick (venue lookup + "needs you", bulk add for future dates, add / edit forms,
   approval wait, locked events → email).
