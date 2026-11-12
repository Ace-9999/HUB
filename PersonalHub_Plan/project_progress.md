# Project progress and agent handoff

Last updated: 12 November 2026 (Asia/Kolkata)

## Current state — read first

- Product working name: PersonalHub; personal Android app, Kotlin/Compose, local-first.
- Phase: planning baseline **1.1** delivered. **Android implementation has not started.**
- Active increment: none. Next recommended increment: **I00**.
- Android tests/builds/device checks: **not run**. No SDK, phone capability, live login, classifier accuracy or blocking claim is verified by this planning work.
- Source inspection: reference VTOP repository inspected at `4d838ae79ef8452a7981d4284a4e319615f7d4b9`; WebView/JavaScript/challenge/data flow documented.
- Current branch/commit/remote: not established for this planning artifact. No reference-repository files or remote GitHub state modified.
- Last verified application checkpoint: none. Planning-package integrity checks are recorded at the end of this file after generation.

## Requirement decisions currently in force

| Decision | Rationale / consequence | Status |
|---|---|---|
| Native Kotlin + Compose | Direct Android integration; one UI language | Baseline proposal |
| Four initial destinations + Music fifth in final I14 | Uniform app with local section tabs; independent last module | Baseline 1.1 |
| Mood Log is timestamped and categorical | Nine exact categories; unlimited entries/day, no invented severity average | Required in I03 |
| Every local feature works offline after acquisition | Explicit VTOP refresh and optional media enrichment only online | Baseline 1.1 |
| Regular full local plain JSON snapshots | Includes retained sensitive records; scoped review exports default to omissions; no automatic sharing | Required in I13, extend I14 |
| Dark first + light/system | Combines supplied references under shared tokens | Baseline proposal |
| Separate domain API/data/feature boundaries | Low coupling, testable integrations | Baseline proposal |
| Accessibility/usage first; no default continuous recording | Lower overhead; available context varies by source/version | Requires I02 device probe |
| Redirect on restricted target/re-entry | Normal app has no reliable cross-app force-stop capability | Requires I02/I12 device verification |
| Academic calendar source not assumed | Existing timetable does not establish a calendar feed | I02 audit pending |
| Hevy media not assumed open source | Need per-file cleared source/attribution | I07 pending |
| Personal private APK first | No backend/Play publication required | Implementation default |

## Increment status

| ID | State | Evidence / pending |
|---|---|---|
| Planning | Complete after package integrity validation | Specification, architecture, UI, backlog, handoff files |
| I00 | Not started | Toolchain, source reuse/license decision, actual phone/app/browser matrix |
| I01 | Not started | Installable shell and CI |
| I02 | Not started | Live VTOP + monitoring feasibility gates |
| I03 | Not started | Tasks/manual habits/Mood Log/day-week-month trends |
| I04 | Not started | College auth/cache/timetable |
| I05 | Not started | Attendance/calendar/reminders |
| I06 | Not started | Marks/history/projections |
| I07 | Not started | Catalog/media/routines |
| I08 | Not started | Workout logging/timers |
| I09 | Not started | Training analytics/body map |
| I10 | Not started | Usage sessions/health |
| I11 | Not started | Text/video/scroll adapters |
| I12 | Not started | Limits/redirects/derived habits |
| I13 | Not started | Scheduled JSON/restore/offline/privacy/performance/core candidate |
| I14 | Not started | Spotify capture/history/artwork/stats, updated JSON backup, final integration |

Use states: Not started, In progress, Blocked, Verification pending, Complete, Reverted. A task's status never substitutes for its increment gate.

## Code and file map

There is no application source yet. Proposed module paths and responsibilities are in docs/architecture.md. Replace “proposed” with actual paths as files are created. For **every application source file** add a short file-level purpose comment where useful and a row or a linked generated index entry here; group repetitive resources/tests with an exact inventory link instead of hiding them behind a vague module summary.

| Existing path | Purpose | Notes |
|---|---|---|
| README.md | Entry point and package navigation | Start here |
| SRS.md | Authoritative requirements and formulas | Requirement IDs must remain traceable |
| docs/architecture.md | Boundaries, schema, integration contracts, data lifecycle | Proposed, not implemented |
| docs/increments.md | Task IDs, dependencies, demos, gates and rollback | Execute incrementally |
| docs/ui_design.md | Design tokens, screens, reference adaptation | Includes original references |
| docs/verification.md | Test families, numerical oracles and traceability | Future checks |
| docs/research.md | Pinned source audit and source links | Research only |
| AGENTS.md | Instructions for future developers/agents | Scope applies when imported into repo |
| templates/agent_task.md | Bounded work order template | Fill exact paths/ref |
| assets/references/ | Supplied screenshot reference copies | Full inventory in assets/reference_manifest.json |
| assets/design/proposed_screens.svg | Original conceptual screen board | Synthetic values; no functioning app |
| review.html | Self-contained visual overview | Embedded boards and reference thumbnails |
| docs/data_export.md | Local JSON schedule/full/review/restore contract | Version 1.1 user override of encrypted-only default |
| assets/design/additional_screens.svg / .png | Mood, trends and Music wireframes | Synthetic planning data |

Required future code-map columns: `actual path`, `purpose`, `public symbols/interfaces`, `data owner`, `key callers`, `tests`, `introduced/changed in increment`, `rollback concern`. Every meaningful code block should have a clear function/type responsibility; do not comment every trivial syntax line.

## Change log

### PLAN-001 — 12 November 2026

- Added detailed SRS with scope, functional requirements, calculation rules, failure states, privacy and engineering targets.
- Inspected VTOP WebView scraping/session/challenge mechanism; recorded source paths and endpoints as observations.
- Defined fourteen increments (I00–I13) with tasks, gates, tests, dependencies and rollback strategies.
- Added modular architecture/data contracts, UI reference mapping and unified wireframes, verification matrix and future agent instructions.
- Included all thirteen supplied screenshots with hashes; duplicate Hevy profile reference retained and labelled.
- Pending: all Android implementation and device verification. Do not infer completion from this document.
- Reversal: remove or revise planning files; no personal application data or remote repository state exists to roll back.

### PLAN-002 — 12 November 2026 — baseline 1.1

- Added nine exact emoji mood categories, timestamped unlimited daily entries, categorical timelines and day/week/month/custom distributions.
- Required trend graphs for private manual counts, Reels/video/text usage and workout metrics, with cross-module summary contracts and truthful missing/coverage states.
- Added isolated Music/Spotify module as final increment I14; local notification/media-session probe, playing segments, offline history, top songs/artists, most-played/favorite distinction and conditional pictures.
- Clarified fully offline local operation after acquisition and explicit VTOP refresh; all data remains on device. No app server/runtime AI dependency.
- Replaced mandatory encrypted-only backup with scheduled plain `.json` full backups and scoped review exports per owner request. Full local snapshots include retained sensitive records; secrets/grants remain excluded. Optional encrypted copies supplement JSON.
- Updated SRS, architecture/schema, increments, verification/traceability, UI/research, agent rules, README and visual overview; added data_export.md and three supplementary wireframes.
- Source check: official Android media-session/metadata/periodic-work and Spotify recent-history/artist docs reviewed; source exposure, image access/retention and device capture remain unverified until I14.
- Pending: all application implementation/testing; no live listening history or personal app data captured.
- Rollback: restore the previous document-package version if desired; no live application schema/data exists to migrate or undo.

## Next exact action

Read AGENTS.md, then execute I00-T1/T2: establish the implementation workspace and pin a verified Android toolchain. Record actual file paths and build commands here. Record I00-T3 phone details when available; do not request credentials in chat. Do not implement production scraping or monitoring before the I02 probes have corresponding device evidence.

## Open questions / decisions for the relevant increment

These do not block using this plan now. Ask only when the answer changes active implementation.

1. I00: actual phone model/OS/OEM; preferred browser(s) and installed target versions.
2. I00: permanent app name/application ID; GitHub destination/account only if the owner requests remote creation.
3. I02/I06: academic grading regulation, GPA-denominator availability, repeat-course behavior, actual calendar source.
4. I07: cleared animation/body-map source and whether static alternatives suffice until animations are sourced.
5. I12: owner's specific budgets, category unknown policy and desired override friction. Default budgets remain unset.

## Handoff template — append per task

```text
Date/time/zone:
Agent/task owner:
Increment/task/requirement IDs:
Base and resulting refs (or unavailable):
Scope and expected behavior:
Files changed + responsibility/code-map updates:
Public API/schema/permission changes:
Tests: command/steps, environment, expected/actual, status, evidence paths:
Device/live checks: verified / not run / blocked, with versions:
Known defects and regressions:
Remaining tasks in increment:
Migration/backup required:
Feature switch / code rollback:
Data compatibility / restore approach:
Next exact task and why unblocked:
```

## Rollback log — append when used

```text
Reason and affected requirement:
Bad ref / known-good ref:
Revert change / feature switch:
Schema compatibility validated:
Data backup/restore used (exclude secrets):
Reverification outcome:
Work reopened and next action:
```

## Evidence ledger

Planning integrity checks will be appended here. Application check evidence belongs under `docs/evidence/Ixx/` in the future implementation repository, with redacted/synthetic content only.

### PLAN-QA — 12 November 2026

- Passed: 10 Markdown files, 29 relative Markdown links resolved, all 13 reference hashes verified; duplicate REF-10 confirmed against REF-09.
- Passed: 67 unique SRS requirement IDs; illustrative attendance/GPA/volume arithmetic checked independently. These are document-example checks, not Android application tests.
- Passed: proposed SVG parsed and rendered to PNG with Inkscape; nine-screen image visually inspected. Corrected calendar weekday alignment before final packaging. Inkscape emitted nonfatal font-map/desktop-manager warnings and produced the reviewed image.
- Passed: portable HTML parsed, 14 embedded images decoded (one SVG board + 13 references), all local document links resolved.
- Not run: full browser rendering of review.html. Headless Chromium was blocked by environment socket permissions; no browser-render claim is made. The underlying SVG/PNG board was visually reviewed directly.
- Pending: every Android build, app test, physical-device check, live portal login, licensed production media audit, classifier evaluation, timer/blocking/battery validation.
- Packaged: complete Markdown specification, architecture, increments, UI/source/verification guides, agent rules, progress/work-order templates and reference/design assets.

### PLAN-QA-002 — baseline 1.1, 12 November 2026

- Passed: 11 Markdown files, 34 relative Markdown links, 13 unchanged supplied-reference hashes and two valid SVG boards.
- Passed: 83 unique requirement IDs with all 83 mapped to verification families; fifteen I00–I14 increment headings; nine exact mood labels/codes.
- Passed: documented mood distribution and playback pause/seek/qualification examples checked arithmetically; these are specification-example checks, not application tests.
- Passed: additional three-screen SVG rendered to PNG and visually inspected. Portable HTML contains 15 decodable embedded images (13 references plus two boards) with resolved local document links.
- Not run: full HTML browser rendering, per the existing environment restriction; all Android builds, live Spotify capture, VTOP/device checks and app JSON backup/restore remain pending.
- Saved outputs: updated project ZIP and portable overview, plus SRS.md as a separate readable document. Archive integrity checked before saving.
