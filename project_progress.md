# PersonalHub implementation progress

Updated 13 November 2026 (Asia/Kolkata). Owner authorized implementation, package
installation and sequential tested commits/pushes. Original planning history is in
`PersonalHub_Plan/project_progress.md`; SRS baseline 1.1 remains authoritative.

## Current checkpoint

I00 toolchain/source audit complete. Native app exists, with four destinations,
themes/settings and a Focus Room schema skeleton. Last verified code checkpoint:
`e548f06` (committed and pushed). Its I02 repair passed actual public portal painting
on API 29 and API 36. I03 API/data work is now partially written, UNCOMMITTED and
UNTESTED. No I03 functionality is installed or wired into the app yet.
Repository: `Ace-9999/personalhub`, branch `main`. JDK 21.0.9 / Gradle 8.13,
compile/target API 36, minimum API 29. OnePlus CPH2661/API 36 and an API 29 emulator
were used on 12 November; recheck attachment/running state when resuming.
Live authenticated dataset and monitoring gates remain pending.
UI redesigned 13 Nov to the owner-selected REF-01-05 style (see docs/ui_architecture.md).
Implementation is being handed off at the owner's request. Read
[docs/handoff.md](docs/handoff.md) before continuing I03. Preserve all local WIP.

## Increment gates

| Increment | State | Evidence / remaining |
|---|---|---|
| I00 | Complete | Toolchain/source audit passed; OnePlus CPH2661/API 36 recorded |
| I01 | Verification pending | Functional gate passed on API 29 emulator and API 36 phone; manual TalkBack/200%/landscape review pending |
| I02 | Verification pending | Debug probes built/tested; live validated read and monitoring/redirect evidence pending |
| I03 | Verification pending | Tasks/habits/moods/trends UI wired and journey-tested on emulator; Focus lock, reminders and phone acceptance pending |
| I04-I06 | Implemented, live gate pending | Owner-requested connector: sign-in WebView, sync, parsers, Room cache, College screens; first authenticated owner sync + portal comparison pending |
| I07 | Not started | Catalog and routines; owner moved Train after Focus (13 Nov) |
| I08 | Not started | Workout engine and timers |
| I09 | Not started | Training history/analytics/body map |
| I10-I12 | Blocked | I02 physical monitoring gate needed |
| I13 | Not started | Backups/hardening and release gates |
| I14 | Blocked | Final module, after I13 |

## Code map

- `scripts/bootstrap_toolchain.py`: pinned local CLI/Gradle installation, SHA check.
- `AGENTS.md`, `README.md`: implementation entry points and authorization context.
- `docs/environment.md`: exact baseline, versions, commands and device limitations.
- `docs/adr/001-native-modules.md`: architecture and reference-source provenance.
- `docs/adr/002-local-data.md`: local persistence/privacy/backup decisions.
- Original planning/design/reference assets: unchanged under `PersonalHub_Plan/`.

## Changes and evidence

I00: read planning requirements and all linked guides. Rechecked the pinned VTOP
README and service hashes directly from the reference source; GPL notice, WebView
and in-page request flow verified in source only. No copied reference code/assets.
Verified published plugin/BOM versions and official AGP compatibility. Downloaded
Gradle 8.13 and Android CLI, verified Gradle checksum. SDK installation exited 0. Gradle wrapper generation exited 0; wrapper SHA pinned.
`adb devices`: empty list. Android build/test not yet applicable (no app).

Migration/rollback: no user database exists. Revert implementation commits; ignored
SDK/build files may be removed separately. Never rewrite remote history.

Next exact task: I03 offline daily tools. I04-I06 and I10-I12 remain gated by live source/device evidence; independent Training work follows I03.

### I01 working evidence (not a completed checkpoint)

Requirements: SYS-01-04, SYS-07-09, NFR-01/02/09-12. Four routes, shared
light/dark/system tokens, DataStore theme, Room focus dirty revision schema v1,
module boundaries, demo/connected flavors and CI added. Exact source inventory:
[docs/code_inventory.md](docs/code_inventory.md).

Initial build found and fixed JVM 21/17 mismatch. Lint caught local.properties
escaping; corrected and forced lint rerun (input cache retained old diagnostic).
APKs assembled; three JVM tests passed; lint exit 0 (warnings remain). Room phone
close/reopen test passed. Compose smoke failed with no hierarchy while phone
reported SCREEN_STATE_OFF; no UI pass claimed. Added assertion before synthetic
screenshot. Emulator API 29 installed; UI rerun next. No production data exists;
rollback is code revert, preserve Room schema v1 for the next additive migration.

### I01 verified checkpoint - 12 November 2026

Base: `26abe61`. PASS: three core JVM tests; demo/connected debug assembly;
lint (0 errors, warnings retained); boundary check. PASS: Compose navigation and
activity recreation plus real SQLite close/reopen on API 29 emulator and OnePlus
CPH2661/API 36. Final palette adjustment reran build/lint/JVM/Compose on API 29
(19s, exit 0). Initial screen-off UI failure reran successfully on awake phone
(27s, exit 0). Synthetic emulator screenshot visually reviewed; no personal UI
was captured for evidence. All source/test/resource files are inventoried.

Remaining I01 acceptance: manual TalkBack, large-font/landscape matrix, full
performance baselines. These are not claimed passed. Functional shell/persistence
gate allows I02 and independent local increments. CI defined but remote execution
is not yet verified. Debug APKs are not a signed production release.

Migration: Focus schema v1 exported; no destructive fallback. Revert code using
forward-compatible schema; retain local data. Next: debug-only I02 origin-restricted
VTOP and opt-in monitoring probes, then I03 while live source gates are pending.

### I02 bounded probe checkpoint - 12 November 2026

Base `f3fb80a`. Added debug-only VTOP WebView with strict HTTPS-origin policy,
no credential extraction/native JavaScript bridge and bounded structural counters.
Added opt-in allowlisted accessibility event metadata and reversible Reddit-native
Home probe; no node/content reads, duration/category claims or persistent data.
Capability matrix and ADR-003 record every pending source row. Instagram/X native
packages are unavailable. No personal source content appears in tracked evidence.

PASS: all nine JVM tests (three shared, three origin/shape, three monitoring/privacy);
both debug APK assemblies; lint; boundary check; existing API 29 Compose regression
(34s total Gradle run, exit 0). I02 physical source gates remain pending; structural
page counts do not validate a dataset. I04-I06 and I10-I12 stay blocked. Release
flavors exclude the debug probes. No schema change; disable/close probes and revert
this checkpoint safely. Next: I03 tasks/manual habits/moods, explicitly independent
of live integrations per the plan.

### I02 repair checkpoint

Owner reported blank portal. Added explicit full-height layout, progress/retry and
redacted error codes; added separately opt-in public-page instrumentation test.
PASS: real public page load plus bounded structural inspection on API 29 emulator
(17s) and OnePlus API 36 (18s). No login or private table read occurred.
PASS: normal JVM/build/lint/Compose regression after repair (22s).
No TLS bypass. Authenticated dataset gate remains pending. No schema changes.
Rollback: revert repair; production/offline modules unaffected. Next: I03.

### I02 verified white-screen repair - 12 November 2026

The earlier callback/count-only test was insufficient: a failed TLS page could
finish and overwrite its error. Reproduced a genuinely all-white WebView viewport
(0/23552 ink samples). Native host alone did not solve it. The portal serves only
the leaf certificate; adding the official R36 intermediate validates it against
existing trusted roots. Added exact-host debug NSC, verified public intermediate,
R36/R46 CA key pins, retained TLS/HTTP failures, full-height native debug host and
FLAG_SECURE for owner login. ADR-004 documents provenance/rotation/rollback.

PASS: actual public page painting on API 29 (9866/23552 nonwhite samples, visual
callback responded) and physical API 36 (25s). Public-page image visually inspected
in ignored tooling output; no authenticated screenshot or HTML committed.
PASS: final public visual run (32s), then JVM tests, both APKs, lint, Compose and
Room emulator regression (43s, exit 0), boundaries and release-manifest isolation.
Owner authenticated login/semester/validated read remains PENDING. Installed repair
successfully on the phone after the passing checks. No database schema changes. Revert this repair only with offline modules
preserved; failed TLS must remain visible. UI replacement map: docs/ui_architecture.md.

### I03 interrupted WIP / handoff - 13 November 2026

Base and last verified code ref: `e548f06`. I03 has NOT been committed or pushed.
The exact source inventory now includes the WIP, with verification status governed
by this entry rather than inferred from the existence of files.

Written locally:

- `focus/api/build.gradle.kts` and `focus/api/src/main/kotlin/dev/personalhub/focus/api/FocusModels.kt`:
  pure models, daily/weekly/nonrecurring task semantics, count/minutes/yes-no habits,
  nine exact mood categories, categorical distributions, range/zone and missing/zero series.
- `focus/api/src/main/kotlin/dev/personalhub/focus/api/FocusRepository.kt`:
  Flow snapshot and suspend CRUD/completion/soft-delete/restore commands.
- `focus/api/src/test/kotlin/dev/personalhub/focus/api/FocusRulesTest.kt`:
  nine proposed domain tests, NOT RUN.
- `focus/data/src/main/kotlin/dev/personalhub/focus/data/FocusEntities.kt`:
  typed Room task/occurrence/habit/entry/mood rows and DAO.
- `focus/data/src/main/kotlin/dev/personalhub/focus/data/RoomFocusRepository.kt`:
  proposed transactional repository with injected clocks/IDs, validation and reopen support.
- Modified `focus/data/src/main/kotlin/dev/personalhub/focus/data/FocusDatabase.kt`:
  proposed additive migration 1->2. Only schema v1 is exported; v2 has NOT been
  generated, tested or installed. No destructive fallback was added.
- `focus/data/src/androidTest/kotlin/dev/personalhub/focus/data/FocusPersistenceTest.kt`:
  proposed migration, close/reopen, ten same-millisecond mood, undo and invalid-command
  tests, NOT RUN.
- Modified `focus/data/build.gradle.kts` and `settings.gradle.kts`: new API edge,
  migration-test schema assets and reserved `:feature:focus` include.

Still missing: `feature/focus/` itself (currently no directory/build file), Focus
ViewModel/screens/forms, app repository wiring, Today task summary, reminders,
private reveal/device-credential lock, full trend interactions and I03 UI journeys.
The reserved module include must be completed or deliberately deferred before
claiming a working project configuration. Review command validation and historical
editing semantics; generated source is not evidence of correctness.

Tests/build for I03: NOT RUN. The last passing application regression applies to
`e548f06`, not this dirty tree. No new APK was produced from the I03 edits.

Transfer safety: local `artifacts/handoff-I03-WIP.patch` captures all ten changed/new
I03 files, including untracked source. SHA-256:
`4291b9728a1218140ee175a78cbd5e7885cbec27a0ffb2b119033ab4e5989229`.
It was verified with `git apply --cached --check` against a separate temporary index
at `e548f06`; the real index/worktree was not modified by that verification. The
patch is intentionally ignored and must be supplied separately for a new checkout.
Do NOT apply it again in this working folder. Handoff documentation can be committed
separately; it does not turn the untested code into a verified checkpoint.

Next exact action: the receiving agent reads the requirements/handoff, inspects and
preserves the WIP, creates the missing feature module and app composition, then
compiles/tests the proposed API/repository/migration before advancing dependent UI
or claiming completion. Run the existing regressions as well. Commit/push a cohesive
I03 checkpoint only after applicable tests pass; record pending physical acceptance.

Rollback: no device database has been upgraded to v2 by this work. Preserve a WIP
patch before any intentional code rollback. Keep schema v1; do not reset/clean this
folder or downgrade/delete owner data. Later applied migrations require tested
forward-compatible rollback or verified backup restoration.

### I03 data checkpoint - 13 November 2026

Base `caab4ba`. Preserved WIP compiled unchanged. Created `feature/focus` module
(build file + package stub) so the reserved include configures. Room generated
`focus/data/schemas/.../2.json`.
PASS: `:focus:api:test` 9/9 domain tests; `:focus:data:connectedDebugAndroidTest`
4/4 on API 29 emulator (v1->v2 additive migration validated against exported v2
schema with revision preserved, ten same-millisecond moods survive reopen, undo,
explicit-zero private entry, invalid commands leave records untouched).
PASS: boundaries; `test assembleDemoDebug assembleConnectedDebug lintDemoDebug` (37s).
Not yet: Focus ViewModel/screens, Today summary, reminders, Focus lock. No owner
database upgraded yet (app does not open the repository until wiring lands).
Rollback: revert commit; schema v1 retained. Next: shared UI redesign + Focus UI.

### Redesign + I03 UI + I04-I06 connector - 13 November 2026

Owner instruction (13 Nov): replace the UI with the REF-01-REF-05 style (light mode,
distinct fonts, hard shadows) and make VTOP data actually import after login. The owner
explicitly requested the connector, so I04-I06 production code was built ahead of the
authenticated-sample gate; it stays labelled unverified until the owner's first sync is
compared with the portal. Requirements: SYS-01-04, FOC tasks/habits/mood/trends, COL-01-07.

Code map additions:
- `core/designsystem/.../Design.kt`, `HubIcons.kt`, `res/font/*`, `assets/licenses/*`: tokens,
  neo-brutalist components, OFL fonts (Archivo Black, Space Grotesk; hashes dd9a89a0…, acad6de1…).
- `feature/focus/`: `FocusViewModel` (undo, validation messages) and `FocusScreens` (tasks with
  day picker/recurrence/carry-forward, habits with explicit zero vs unknown and private reveal,
  nine-category mood log with backdate/edit/delete, trends with distributions and unknown days).
- `college/api/`: academic models, attendance target plan, VIT 10-point GPA and scenario CGPA
  (null when official denominator is unknown), repository contract; 8 JVM tests.
- `college/data/`: Room `college.db` v1 (schema exported), per-dataset replace-on-success,
  semester-scoped rows, last-success timestamps, clear; 2 device tests.
- `integration/vtop/VtopScripts.kt`, `VtopParser.kt`, `VtopSync.kt`: in-page bounded table
  extraction using the page's own session/CSRF (paths from the pinned source audit), span-expanded
  grids, header-label parsers, `VtopImport`, structural-only `VtopDiagnostics`; 11 JVM tests.
- `feature/college/`: overview (CGPA/credits/low attendance, next class, sync status, semester
  switch, disconnect), REF-03 timetable grid + day view, attendance with target planner, REF-04
  calendar (weekly class days/weekends only), marks (pending not counted as zero), REF-05 CGPA.
- `app/`: new shell (dotted canvas, display headers, REF-04 bottom bar, back handling, undo bar,
  light default), `CollegeConnectActivity` (FLAG_SECURE, native WebView host, auto-sync once
  signed in), demo-only labelled sample data, repository composition. NSC moved to main (ADR-004).

Checks (13 Nov, API 29 emulator unless stated):
PASS boundaries; `test assembleDemoDebug assembleConnectedDebug lintDemoDebug` (lint 0 errors,
23 warnings); JVM 37/37 (core 3, focus 9, college 8, vtop 3+11, monitoring 3); Room focus 4/4 and
college 2/2; Compose `ShellTest` 2/2 (navigation/recreation/settings back, task add-complete-undo-
delete). Visual review of Today, College, Schedule, Calendar, CGPA, Focus task/mood screens with
synthetic demo data. Known: 🫥 renders as a box on API 29's emoji font.

Pending (not claimed): owner authenticated sync and P02 comparison; parser fixes from structural
diagnostics; TalkBack/200%/landscape review of new screens; Focus device-credential lock and
reminders (I03-T3/T4); academic calendar source (I05-T3); class/exam reminders (I05-T4); history
suggestions (I06-T4). No credential storage: the owner signs in each time the portal session expires.

Migration/rollback: Focus 1->2 additive (tested). New `college.db` v1. Disconnect clears college
data and portal cookies. Reverting this commit leaves both databases readable by later versions;
do not downgrade Focus below v2 on a device that opened this build.
Next: install on the phone, owner signs in and syncs, read structural diagnostics, fix parsers.

### I04-I06 first owner sync - 13 November 2026

Owner signed in on OnePlus API 36 and the connector ran end to end. Structural log only
(no values): 6 semesters offered; timetable page 2 tables (course list 13x16, grid 18x15)
-> TIMETABLE OK, 11 courses; attendance 1 table -> OK, 11 components; marks endpoint
returned 0 tables -> EMPTY (endpoint/params or no marks yet: unresolved); grade history
7 tables incl. CGPA summary -> OK, 48 rows. Two nested attempt tables could duplicate
history rows, so nested tables are now skipped when a top-level history exists (+1 test,
vtop parser 12/12). Debug log now also reports meeting count and whether CGPA was found.
Fix installed on the phone. PENDING: owner comparison of timetable/attendance/CGPA with
the portal (P02), weekly meeting count, marks endpoint investigation.
Second owner sync with the fix: GRADES 46 rows (2 nested duplicates removed), 23 weekly
timetable meetings parsed, CGPA found; timetable/attendance unchanged (11/11); marks still EMPTY.

### Marks import fixed - 13 November 2026

Diagnosis (structure only): the default semester was the first portal option (Winter,
no data yet). Owner confirmed the active term is Fall 2026-27. Sync now defaults to the
in-session semester by date (Fall Jul-Nov, Winter Dec-Apr, Summer May-Jun); explicit
selection still wins. Marks/exam pages carry their own semester lists and are submitted
as multipart FormData like the portal's script. Owner Fall sync: TIMETABLE 12 courses /
25 weekly meetings, ATTENDANCE 11, MARKS 61 rows (13 tables: course list + 12 nested
component tables), GRADES 46, CGPA found. Exam schedule and academic calendar
(`processViewCalendar`, status 233 with first parameter guess) are still in discovery;
debug-only structural probes remain in `VtopScripts` until they're implemented.
Owner portal comparison (P02) still pending.

### Exam schedule + academic calendar import - 13 November 2026

Owner asked for Examination -> Exam Schedule and Academics -> Academic Calendar (holidays).
Discovery (structure only) found: exam schedule = multipart `examinations/doSearchExamScheduleForStudent`
(17x13 table, full-width rows name the exam type); calendar = `academics/common/CalendarPreview`
(class group, e.g. COMB) -> `getDateForSemesterPreview` (month list) -> `processViewCalendar`
per month (6x7 grid). Implemented `ExamSlot`/`CalendarDay` models, `VtopParser.exams/calendar`
(+3 JVM tests, vtop 15/15, college api 9/9), college Room v2 with additive 1->2 migration
(+1 device test; college data 3/3), Exams page, Today next-exam/holiday cards, calendar
cells coloured by portal label (holiday/instructional/exam/other); weekly classes hidden on
portal holidays. Discovery probes removed; only counts/shapes remain in the debug log.
PASS: boundaries; test/assemble/lint; Compose 2/2, focus 4/4, college 3/3 on API 29.
PENDING: owner sync of exams/calendar and comparison with the portal.

Priority change (owner, 13 Nov): after VTOP, do Focus before Train. Order now
I04-I06 finish -> Focus (I03 lock/reminders, then I10-I12 monitoring under its device gates)
-> I07-I09 Train -> I13 -> I14 Music.
Owner sync with 60805c3 (OnePlus API 36): TIMETABLE 12, ATTENDANCE 11, MARKS 61, GRADES 46,
EXAMS 14, CALENDAR 129 labelled days, 25 meetings, CGPA found. All six datasets import.
Remaining I04-I06 acceptance: owner comparison of values with the portal (P02).

### Grade History section (I06) - 13 November 2026

Owner confirmed timetable, attendance, marks, CGPA, exams and calendar match the portal
(P02 PASS for those datasets). Owner requested a Grade History section with selectable
semesters, CAT1/CAT2/DA/FAT, total, grade and grade ranges. Discovery (structure only) found
Grade View = multipart `examinations/examGradeView/doStudentGradeView` (Course Code/Title/
Type/Credits/Grading Type/Grand Total/Grade/View Mark) and per-course
`getGradeViewDetails(courseId)` with authorizedID/semesterSubId/courseId/x/_csrf.
Implemented `SemesterResult`/`GradedCourse`/`GradeRange`, `VtopParser.gradeView/gradeDetail`
(+1 test, vtop 16/16), college Room v3 additive migration (+1 test, college data 4/4),
Grade History page (semester pills, computed GPA, components, total, grade, range chips;
all-attempts list moved here from CGPA). Schedule day view: room moved under the course
pills (owner report); pills never wrap. PASS: boundaries, test/assemble/lint, Compose 2/2,
focus 4/4 on API 29. PENDING: owner sync of results and check of the detail layout
(components/ranges are parsed generically; structural log reports the detail tables).

### Motion pass - 13 November 2026

Owner: the UI felt mechanical. Added shared motion in the design system and shell only (no
data/ViewModel changes): directional page transitions (deeper pages push from the right,
tabs slide by bar order, fade throughout), Focus tab crossfade/slide, springy press-into-
shadow on every surface, animated fills for pills/grade cells/checkboxes/bottom-bar capsule
(with bounce), animated progress bars and card size changes.
PASS: boundaries, JVM tests, connected APK, lint, Compose 2/2 on API 29. Owner feel check pending.
Grade History: owner sync imported 4 semesters (11/14/10/11 courses); detail tables match the
parser's expected layout. Owner check of components/ranges against the portal still PENDING.
Next: Focus (lock, reminders) once Grade History is confirmed.

### Schedule grid labels + app icon - 13 November 2026

Owner confirmed Grade History matches the portal: I04-I06 owner data gate PASSES (all
datasets). Schedule grid now uses a fixed 72dp hour width (horizontal scroll, pinned day
column, hour labels, faint hour guides) with the course code inside each block.
Owner asked for an app logo: original adaptive icon (four outlined rounded tiles in the app's
lime/cyan/peach/mint with a hard ink shadow on the ivory dotted canvas = the four sections),
themed monochrome layer, round icon, light window background and Android 12+ splash icon.
No third-party/brand artwork. PASS: JVM tests, connected APK, lint, Compose 2/2; launcher
rendering checked on API 29. VTOP work is complete; next is Focus (lock, reminders).

### Focus usage tracking, limits and curfew (I10-I12 implementation) - 13 November 2026

Owner: Focus before Train; requires reel tracking, text-scroll tracking, daily limits that
redirect on every reopen, and a night cut-off (e.g. after 23:00). Targets chosen by owner:
Instagram app (owner will install), Instagram/X/Reddit in Chrome or Brave, Reddit app,
YouTube (Shorts + regular). Instagram/X native packages were absent on the phone at start.
Requirements: FOC-01..08, FOC-10, FOC-11, HAB-03 (derived totals), SRS §7.2; the cut-off is
an owner addition implemented as a scheduled block window under FOC-06/07.

Code map additions:
- `usage/api`: targets/hosts (exact host match), surfaces (VIDEO/TEXT_FEED/OTHER/UNKNOWN),
  intervals, `SessionTracker` (monotonic timing, 300 ms/1 s scroll bursts, reliable-delta
  screen estimates, midnight split), limit rules, curfew, overrides, `evaluate` (curfew
  first, most-exhausted budget, override suspends, unknown counts service-wide only).
- `usage/data`: Room `usage.db` v1 (intervals, rules, settings, overrides, coverage gaps).
- `integration/monitoring`: `UsageTrackerService` (opt-in accessibility; window package,
  resource-id names and browser address bar only; screen-off stop; 15 s ticks with 60 s
  checkpoints; Home redirect + local explanation), `UsageEngine`, `SurfaceClassifier` v1
  (initial heuristics, pending calibration), debug-only `Calibration` id-name log.
- `app`: service registration/config, `BlockedActivity` (redirect explanation, 10 s friction,
  logged 5-minute override unless strict, settings always reachable), engine composition.
- `feature/focus/FocusUsage.kt`: Usage tab (disclosure/enable flow, health, today's reels/video
  vs feed minutes, bursts/screens, per-app split, 7-day chart, limits editor with scope preview,
  night cut-off, strict mode).
Checks: usage api 7/7 JVM; classifier 3/3 JVM; usage data 1/1 device (API 29); full
test/assemble/lint; Compose 2/2. NOT yet verified on the phone: service enable, timing vs
stopwatch, surface classification per app, redirect on reopen, curfew, override expiry,
battery. Classification rules v1 are unvalidated heuristics until calibration.
Rollback: pause tracking (Focus -> Usage) or disable the accessibility service; usage.db is
separate from all other data.

### Usage owner feedback round 1 - 13 November 2026

Owner reports tracking works on the phone (owner observation; stopwatch comparison and
classification accuracy still not formally measured). Changes: second-precise durations
everywhere (`formatDuration`: 20s / 3m 28s / 1h 28m); limit modes - per-app switch (off:
global reels/video + global feed limits; on: each app's own reels/video + feed limits),
fixed grid rule ids, inactive-mode rules kept not deleted, legacy free-form limits folded
into the grid or listed under "Other limits". Usage DB v2 additive migration (perApp
column, default global). PASS: usage api 8/8, usage data 2/2 (incl. 1->2 migration),
full test/assemble/lint, Compose 2/2. Installed on the phone.

### Usage owner feedback round 2 - 13 November 2026

Owner: Instagram home was counted as reels. Cause (inferred; no calibration log captured):
the bottom-bar `clips_tab` button exists on every screen and v1 matched any `clips_` id.
Classifier v2: reels only for the full-screen reel viewer or the *selected* Reels tab
(selected state now collected as "selected:<id>"); selected Home tab/feed rows = feed, inline
feed reels stay feed; other selected tabs = other. +1 JVM test (classifier 4/4). Limit fields
now drop focus, hide the keyboard and grey out after Set (HubField `committed`), re-activating
on tap. Installed on the phone; owner re-check with calibration log ON pending.

### Usage owner feedback round 3 - 13 November 2026

Owner: stats overflowed; X video counted as feed; Instagram home still counted as reels
under v2. Changes: equal-width `StatRow` tiles; X web routes from owner observation
(`/status/` and `/i/videos` = video, `/home` and others = feed, +2 JVM asserts); classifier v3
collects only `isVisibleToUser` node ids (hypothesis: Instagram keeps the reels viewer loaded
behind Home); debug calibration now writes id names/masked URL shapes to a private capped file
(this OnePlus rotates logcat within minutes) and is on by default in debug builds. Instagram
fix NOT yet confirmed on the phone.

### Performance, swipe navigation and next-class strip - 13 November 2026

Owner: Instagram/X classification now correct on the phone (classifier v3, owner-confirmed).
Owner reported stuttery transitions (Habits -> Usage, opening Grade History, changing semester).
Causes addressed: debug (unoptimised) build on the phone -> new `fast` build type (R8 minify +
resource shrink, non-debuggable, signed with the local debug key so it upgrades in place);
dotted background redrew ~700 circles per frame -> cached tiled shader (one draw); global
`animateContentSize` on every surface removed; whole-page AnimatedContent inside one scroll
container -> `HorizontalPager` (each tab its own scroll state, beyondViewportPageCount 0).
Owner requests: swipe between Today/College/Train/Focus (pager; disabled inside College
sub-pages and Settings); College next-class card is a swipeable strip of upcoming classes over
the next week (skips portal holidays), fixed card size, tap opens Schedule; separate Schedule
tile removed, tiles regrouped. PASS: boundaries, JVM tests, lint, Compose 2/2 (API 29),
`assembleConnectedFast`; fast build installed on the phone in place, tracker still enabled,
cold start 338 ms, no crash. Install: `python scripts/run_gradle.py installConnectedFast`.
Debug-only probes/calibration are absent from the fast build by design. Owner smoothness check pending.

### Google Tasks two-way sync (owner-requested) - 13 November 2026

Owner chose the Google Tasks REST API over accessibility scraping. New online boundary,
owner-approved: Google authorization on device (play-services-auth 21.2.0, scope `tasks`),
access tokens in memory only for one pass, never stored or logged; only title, due date,
completion and deletion are exchanged. Code map: `focus/api` sync contract + `remoteWins`
(last-writer-wins) + `GoogleSyncState`; `focus/data` schema v3 (additive: googleId,
googleList, dirty on tasks; `sync_state` table) and `TaskSyncStore` implementation (local
one-off edits mark dirty; imports are carry-forward one-off tasks); `integration/googletasks`
(`TasksApi`, REST client, JSON mapping, `GoogleTasksSync`: pull all lists incrementally,
then push inserts/patches/deletes, 404-on-patch re-creates); app `GoogleTasksHost`
(mutex, silent auth, consent via IntentSender, WorkManager periodic 15 min + debounced 5 s
after edits/app start, network constraint) and Tasks-page card with Google pill on linked tasks.
Repeating tasks stay local (API has no recurrence). PASS: focus api sync rules 4/4, Google
Tasks JVM 3/3 (real sync loop against a fake API), focus data 5/5 incl. v2->v3 migration,
full test/assemble (debug + R8 fast)/lint, Compose 2/2.

## PARKED FOR OWNER (needs you; nothing else blocked)

1. Google Cloud setup for Tasks (one time): create project -> enable "Google Tasks API" ->
   OAuth consent screen (External, Testing) and add your Google account as a test user ->
   Credentials -> Create OAuth client ID -> Android, package `dev.personalhub`,
   SHA-1 `51:64:C1:CA:C0:4C:0D:29:87:64:C3:68:52:2C:91:4A:7D:73:B6:62`. No key or secret is
   copied into the app. Then Focus -> Tasks -> Connect Google Tasks and approve.
   Live sync is UNVERIFIED until this is done.
2. Focus lock (Settings -> Privacy lock): check the fingerprint/PIN prompt appears when opening
   Focus, cancelling keeps it locked, and leaving the app re-locks.
3. Reminders (Settings -> Reminders): allow notifications when asked; add a task due in ~3
   minutes and confirm the notification; enable the daily check-in and confirm it never names habits.
4. Phone checks of later increments (listed per increment below as they land).

### Focus lock and reminders (HAB-02, HAB-06) - 13 November 2026

Built while owner away. Focus lock: platform BiometricPrompt with biometric-or-device-credential
(no new library), unlock per foreground session, re-locks on app stop; phones without a secure
lock screen are not locked out. Today check-in card no longer shows mood values (HAB-06).
Reminders: WorkManager one-time work per task at its next not-completed due occurrence
(`Reminders.nextFire`), rescheduled after every task change; optional neutral daily habit
check-in (count only, never names). POST_NOTIFICATIONS requested on enabling; USE_BIOMETRIC
declared (lint caught it). PASS: reminder rules 3/3 JVM, full test, R8 build, lint (0 errors after
fresh rerun), Compose 2/2. PENDING (owner): prompt/notification behaviour on the phone.
