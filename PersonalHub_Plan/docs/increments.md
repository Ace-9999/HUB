# Incremental delivery plan

An increment is a verified capability, not a timebox in which unfinished work is automatically accepted. There are fifteen increments (I00–I14) after the planning baseline; Spotify/Music is the final feature increment. This sequence delivers early local usefulness and tackles the riskiest external integrations before large production investment.

No calendar promise is made for AI-generated code. Human portal/device interaction and measured behavior determine throughput. Size labels compare implementation effort: S small, M medium, L substantial. Re-estimate after I02.

## 1. Sequence and gates

| Increment | Result | Depends on | Size | Release gate |
|---|---|---|---|---|
| I00 | Toolchain/source/device audit | Planning package | S | Reproducible baseline, unresolved risks listed |
| I01 | Installable shell/design system | I00 toolchain | M | Navigation, theme, persistence skeleton pass |
| I02 | VTOP and monitoring feasibility probes | I01 | L | Capability matrix with evidence; dependent gates explicit |
| I03 | Daily tasks, manual habits, Mood Log and early trend views | I01 | M | Offline daily workflow and persistence pass |
| I04 | VTOP sign-in, semesters, timetable | I02 VTOP gate | L | Live owner-device read and safe failure tests |
| I05 | Attendance, calendar, reminders | I04 | M | Calculations and calendar provenance pass |
| I06 | Marks, grade history and projections | I04/I05 academic schema | L | Official/history/scenario separation and formula gates |
| I07 | Exercise catalog and routine builder | I01 | M | Cleared seed catalog and repeatable routine creation |
| I08 | Live workout and rest timers | I07 | L | Recovery, timer and completion gates |
| I09 | Gym history, records and muscle map | I08 | M | Independently checked analytics and range controls |
| I10 | Production usage tracking/health | I02 monitoring gate, I03 | L | Correct package sessions and observable coverage gaps |
| I11 | Text/video classification and scroll estimates | I10 | L | Per-surface evaluation gates, unknown behavior |
| I12 | Limits, re-entry redirects, derived habits | I11, I03 | L | Device enforcement and aggregation gates |
| I13 | Regular JSON backups, offline hardening and core release candidate | I00–I12 intended enabled features | L | Offline/JSON restore/privacy/performance/regression pass |
| I14 | Spotify/Music module and final integrated private release | I13 + isolated media-session feasibility gate inside I14 | L | Offline listening capture/stats, updated backup and full regressions pass |

Primary order is I00 → I01 → I02 → I03 → I04 → I05 → I06 → I07 → I08 → I09 → I10 → I11 → I12 → I13 → I14. If an external device gate blocks I04, proceed with I03/I07–09; do not label College connected. If monitoring proves partial, implement supported rows and keep affected rows pending; broader coverage requires another measured adapter increment.

## 2. Common entry / exit rules

**Entry:** agreed requirement IDs, clean/understood working tree, prior dependency gate evidence, current public API/schema map, task scope and rollback strategy. Every task below gets a checkbox row in project_progress.md when activated.

**Exit:** acceptance cases pass; relevant regression tests pass; an installable APK/demo exists when applicable; no acknowledged-save data loss; permissions and failure states work; docs/code map match; migration and undo path reviewed; checkpoint/reference recorded. Critical data corruption, credential leakage, trapping redirects or false official data blocks the gate.

CI after I01 should run formatting/static analysis, applicable JVM tests, debug assembly and an emulator smoke suite. Run broader feature checks when their code or shared dependencies change. A green mock suite is not evidence of live portal/third-party app support. “Device test pending” is a valid status and never “done.”

## I00 — Establish the development baseline

**Tasks**

- I00-T1: Create/import a new local development repository when implementation is authorized; preserve this planning package under docs and root agent files. Do not modify the friend's repository. Choose application ID and provisional app name; no remote repository is required to begin.
- I00-T2: Record JDK, Android SDK/API, Gradle/AGP, Kotlin, Compose and dependency versions. Verify compatibility in official docs and pin them. Record build/test commands and minimum API choice.
- I00-T3: Record owner's phone model, Android/API/OEM, selected browsers/app versions, locale and timezone. If unavailable, leave a clear device gate pending and continue toolchain setup. Confirm sideloaded private-release distribution as baseline.
- I00-T4: Review pinned VTOP source/permissions/license; create ADR-001 stack/modularization and ADR-002 local data/security. Record connector assumptions and define synthetic fixtures.

**Checks / gate:** toolchain can run a minimal Android project/build or the missing SDK is explicitly recorded; no fabricated compile result. Source revision/provenance verified. Actual-phone tasks remain pending if no device is attached. **Rollback:** planning files only; no user data. **Evidence:** environment.md, initial ADRs, progress entry.

## I01 — Foundation and coherent navigation

**Tasks**

- I01-T1: Scaffold app + minimal API/data/feature boundaries; convention plugins/version catalog; DI and clock abstractions; build variants for demo and connected mode.
- I01-T2: Implement shared light/dark tokens and cards, buttons, status chips, input fields, nav bar and placeholder section pages using labelled synthetic data.
- I01-T3: Implement Today/College/Train/Focus navigation and settings; preserve destination state; responsive layouts/font scaling.
- I01-T4: Add Room schema export/migration pattern, DataStore preferences, error/empty/stale UI primitives, CI and dependency-boundary check. Design backup interfaces now; final restore comes in I13.

**Checks:** B01 build/static checks, U01 Compose navigation/theme semantics, D01 schema smoke; install on emulator and owner phone if available. Disable all connectors and confirm app still runs. **Gate:** no permission needed to navigate; a new sample section registers without feature edits. **Rollback:** revert scaffold commits; no real data stored yet. **Demo:** four destinations, theme toggle, settings and consistent empty states.

## I02 — Prove the risky integrations early

This increment uses debug-only probes and controlled data. It does not install a permanent always-on screen recorder or present a partial parser as production.

**Tasks**

- I02-T1: Build a minimal VTOP connection probe from the observed WebView flow. Owner signs in locally, solves challenges and selects a semester. Extract one timetable/attendance sample through the typed validation path; do not commit personal data.
- I02-T2: Audit available marks, current/history grades, credit denominators and calendar source. Record dataset availability and anonymized schema, not actual academic records. Prove source errors and cancellation preserve a fixture snapshot.
- I02-T3: Build an opt-in accessibility/usage probe for foreground context and scroll events. Test all six service/surface combinations: Instagram/X/Reddit native and Chrome web. Include video/text/unknown examples, source versions and any hidden URL issues.
- I02-T4: Test a reversible Home redirect for an explicitly selected test target; verify settings and service disable remain reachable. Explore secondary browser only after primary rows are measured.
- I02-T5: Write capability report and ADR-003 monitoring strategy: supported/partial/unsupported rows, battery observations, data minimization and conditional vision decision. Record missing device tests as pending.

**Checks:** P01 VTOP state transitions + one live read; F01/F02 preliminary observable-source tests; no precision claim from this small probe. **Gate A:** usable authorized session + validated sample required before I04 connected release. **Gate B:** viable events and safe redirect required before corresponding production monitoring/enforcement. Inability to distinguish video is not disguised as a positive result. **Rollback:** disable/remove debug probes, clear temporary local session; no destructive schema. **Demo:** evidence report showing exactly which integration paths work.

## I03 — Tasks and manual habits

**Tasks**

- I03-T1: Implement task CRUD, daily occurrences, recurrence, priority, due times, completion and chosen carry-forward behavior.
- I03-T2: Implement manual count/duration/yes-no habits, increase/reduce/abstain goals, explicit zero and historical editing.
- I03-T3: Add optional sensitive manual habit; editable label, neutral reminders, increment/decrement/undo, hidden summary defaults and Focus lock.
- I03-T4: Add Today cards and day/7d/30d/custom trends using coverage-aware no-entry states. Reserve derived-habit/provider contracts; do not fabricate scrolling or workout data before those modules exist.
- I03-T5: Implement nine-code timestamped MoodEntry records, unrestricted entries per day, optional notes, edit/delete/undo/backdate, categorical day timeline and week/month distributions with drilldown.
- I03-T6: Add private manual-use trend bars and trend metric/coverage contracts; no numeric good/bad mood scoring. Validate missing vs explicit zero and uneven mood logging frequency.

**Checks:** H01 recurrence/date/zero/streak, M01 timestamp/category/distribution tests, D01 save/restart, U01 sensitive visibility and lock fallback; offline mood/task/habit charts. **Gate:** offline task + manual habit journey usable after process death; missing day ≠ zero. **Rollback:** additive migrations and export snapshot before later changes. **Demo:** finish a daily task and log/undo an occurrence privately.

## I04 — College connection and timetable

**Tasks**

- I04-T1: Implement production secret vault and optional credential retention, login/challenge/cancel/expiry UI, audited WebView origins and safe bridge.
- I04-T2: Build sync state machine, semester cache identity, snapshot staging/validation, bounded retry and dataset-level statuses.
- I04-T3: Extract/store course offerings, timetable, available profile/official summary data; show week/day views and next class. No social timetable/friend controls.
- I04-T4: Add fixture parser contracts for normal/changed headings, no records, malformed payload, expiry and account mismatch. Disconnect/clear-cache flow.

**Checks:** P01 parser/state suite; P02 live comparison of selected timetable rows with portal on the owner device; security/log audit; D01 switching semesters/accounts. **Gate:** failed sync preserves valid cache and labels stale state; CAPTCHA never retried indefinitely. **Rollback:** connector switch off + cached mode; forward-compatible migration/revert or protected snapshot restore. **Demo:** connect and browse timetable offline after a successful sync.

## I05 — Attendance and academic calendar

**Tasks**

- I05-T1: Integrate component-level attendance snapshots and data provenance.
- I05-T2: Add attendance calculations/target adjustment with threshold boundary cases.
- I05-T3: Implement calendar month/day detail and instructional/exam/holiday statuses; connect validated source if available. Otherwise implement manual events and bounded ICS import; retain automatic source as pending capability.
- I05-T4: Implement optional class/exam reminders, permission-denied behavior, event resync deduplication and timezone handling.

**Checks:** A01 exact attendance cases, calendar source/override and timezone tests, N01 reminder tests, U01 crowded calendar. **Gate:** imported status cannot be confused with user edits or inferred dates. **Rollback:** disable importer/reminders independently, preserve manual events. **Demo:** see today's attendance risk and calendar details with source/timestamp.

## I06 — Marks, grades and CGPA estimates

**Tasks**

- I06-T1: Import assessment components/weights only where actually exposed; preserve unknowns and incomplete totals.
- I06-T2: Retain grade attempts across semesters, official GPA/CGPA and separately identified GPA/earned credits. Verify regulation policy on the owner's data.
- I06-T3: Build grade-selection scenarios and correct weighted GPA/CGPA including missing denominator and repeats.
- I06-T4: Add deterministic history suggestions using SRS method v1, cohort/sample explanations, manual override and backtest hook with held-out semesters if available.

**Checks:** A02 GPA/credit/repeat cases; A03 suggestion thresholds and no leakage; P02 representative live grade/mark comparison; U01 official/scenario labels. **Gate:** do not show precise projected cumulative GPA if denominator/policy is unresolved. **Rollback:** estimator switch off leaves official history and manual semester scenario usable; scenario tables additive. **Demo:** change a course grade and inspect transparent result.

## I07 — Exercise catalog and routines

**Tasks**

- I07-T1: Create/import ≥40 useful exercises with aliases, metric type, muscles/equipment and original/cleared instructions. Verify per-asset provenance; media may start with static assets.
- I07-T2: Implement search/filter/detail, local lazy media cache, pause/reduced-motion and missing-media fallback. Document animation availability honestly.
- I07-T3: Implement routines, order, target sets/reps/load, notes, exercise-specific rest, duplication/archive and optional folders.
- I07-T4: Seed editable Push/Pull/Legs examples, explicitly labelled templates rather than personal training prescriptions. Add custom exercises.

**Checks:** G01 routine persistence/search, media license completeness, offline operation and routine edit validation. **Gate:** no proprietary Hevy assets bundled; a routine can be saved/duplicated/reopened. **Rollback:** catalog version rollback must preserve IDs used in user data; no hard deletion of referenced exercises. **Demo:** create Push and view exercise instructions.

## I08 — Workout engine and rest timers

**Tasks**

- I08-T1: Start/resume routine snapshots or empty session, one active session, add/edit/reorder exercise/set rows and previous suggestions.
- I08-T2: Autosave commits, idempotent finish, explicit discard, complete/undo set, units and optional RPE.
- I08-T3: Implement deadline-based rest countdown, skip/extend/pause/off, notification controls and correct resume. Test actual OS alarm/service choices before declaring background reliability.
- I08-T4: Test rotation, app switch, process kill, permission revocation, lock/screen-off and reboot semantics; add persistent resume bar.

**Checks:** G02 session lifecycle, D01 crash recovery and duplicated Finish, T01 fake-clock + physical timer checks, U01 keyboard/set-row layout. **Gate:** completed sets survive process death; workout and rest timers do not drift because UI paused. **Rollback:** retain drafts; migration compatibility or prechange backup required. **Demo:** record a short workout and resume it after process recreation.

## I09 — Training analytics and body map

**Tasks**

- I09-T1: History/edit/delete plus day/7d/30d/custom range selector.
- I09-T2: Implement volume, reps, duration, supported e1RM, records and chart data with declared weight conventions.
- I09-T3: Add cleared original front/back body regions, contribution map, numeric distribution, legend and zero/unknown states.
- I09-T5: Expose read-only workout day/week/month/custom metric series (finished count/duration/working sets/volume) via app composition for Focus trends; do not copy workout rows into habits.
- I09-T4: Add optional supersets/measurements only after the above checks pass; keep each a separate task/change set. If deferred, record as remaining SHOULD work, not an invisible omission.

**Checks:** G03 manually calculated dataset and recomputation, U01 accessible map/list and range changes. **Gate:** map values match completed working sets and selected dates; data absence is honest. **Rollback:** derived analytics can be rebuilt; do not remove raw history. **Demo:** one week of seeded workouts with consistent chart and muscle totals, clearly demo data.

## I10 — Production usage sessions and health

**Tasks**

- I10-T1: Implement optional usage-access flow, allowlisted accessibility adapter lifecycle and health UI.
- I10-T2: Resolve foreground sessions, screen-off/lock behavior, multiple event-source reconciliation and bounded persistence checkpoints.
- I10-T3: Store intervals, coverage gaps, daily rollups, retention; show package usage and launches without text/video claims.
- I10-T4: Test OEM battery settings, reboot, user force-stop and re-enable, service disable, foreground-service restrictions if any, and system/keyboard exclusion.

**Checks:** F01 controlled duration/session replay plus device script; D01 restart/dedup; performance baseline. **Gate:** missing tracking is visible and no unrelated/private content is retained. **Rollback:** pause service and keep read-only aggregates; no effect on College/Train. **Demo:** known 5-minute target session and lock interval match observation within recorded tolerance.

## I11 — Surface classification and scrolling

**Tasks**

- I11-T1: Build versioned native source adapters, explicit text/video/other/unknown outcomes, transient UI sanitation and classifications.
- I11-T2: Add Chrome domain/surface adapter for each target, then a validated secondary browser. Test hidden URL, tab switch, cross-site navigation, custom tab and incognito behavior.
- I11-T3: Add nonoverlapping category intervals, scroll-burst deduplication and reliable-delta screen-length estimates; unknown labels/coverage.
- I11-T4: Execute labelled evaluation matrix, tune without leaking evaluation examples into claimed held-out results, publish per-row support and metrics. Consider optional visible vision probe only if needed and authorized on device.

**Checks:** F02/F03 classifier confusion matrices + scroll oracle; privacy/log audit. **Gate:** NFR-07 per supported row or explicitly mark partial/unsupported; unknown isn't omitted from recall. No claim of exact posts/videos watched. **Rollback:** turn off a broken adapter by version; preserve package-level time, reclassify future intervals as unknown. **Demo:** text and video surface switch with visible measurement method.

## I12 — Limits, repeat blocking and automatic habits

**Tasks**

- I12-T1: Add daily/session/category/shared limits, unknown policy, warnings and scope previews.
- I12-T2: Implement pure rule evaluation, restart-safe usage accounting, reset boundaries and override log; strict opt-in friction.
- I12-T3: Implement safe redirects and reopening checks; test native scope vs recognized browser domain; add health failure path and reachable disable route.
- I12-T4: Derive automatic text/video habit values from canonical aggregates and coverage; add correction provenance, day/week/month graphs and Today focus summaries with sensitive defaults. Integrate already-built mood/private-use/workout trend providers without treating all video as Instagram Reels.

**Checks:** F04 budget/timezone/clock/overlap tests, F05 physical reopen/latency/disable tests, H01 derived/manual separation. **Gate:** exhausted limit continues blocking re-entry, unrelated browser tabs/settings stay accessible, no double counted totals. **Rollback:** limit enforcement switch off returns observation-only behavior; manual habits/tasks continue. **Demo:** spend short demo budget, reopen target, see redirect, use explicit temporary override and expiry.

## I13 — Regular JSON backups, offline recovery and core release candidate

**Tasks**

- I13-T1: Implement full local human-readable `.json` snapshots, default 24-hour schedule/seven-snapshot retention, Backup now, versioned schemas/base64 assets, staged restore journal and interruption recovery. Full backups include retained moods/private habits but never secrets; scoped review exports default to omitting sensitive fields. Optional encryption supplements rather than replaces JSON.
- I13-T2: Complete per-module deletion, cache controls, app lock/recovery, backup rules, release log/bridge/WebView hardening and license notices.
- I13-T3: Run every local journey in airplane mode after process restart; verify online-only VTOP refresh preserves cache and optional fetches do not gate use. Test offline scheduled/catch-up backup and local export-folder failures. Profile release build startup, frame time, memory, APK/media footprint and matched monitoring battery usage. Fix regressions within modules before adding scope.
- I13-T4: Run full journeys, migrations from every shipped schema, real-device permissions/lifecycle matrix and privacy audit. Generate a signed sideloadable APK with local signing key handled outside the repository.
- I13-T5: Write installation/user guide, known limitations, support matrix, release notes and final requirement status. Owner uses it for seven days; log defects, fix critical issues, then record the core release-candidate checkpoint. I14 is still required for the requested complete product.

**Checks:** R01 JSON full/review/restore/schedule/corruption/interruption, O01 airplane-mode/no-server-dependency, S01 secret/network audit, B02 performance, all earlier applicable suites and device gates. **Gate:** no critical data/security/correctness defects; conditional capabilities labelled accurately; signed build installs and updates without data loss. **Rollback:** tested forward-compatible code revert or verified local snapshot restore; never downgrade schemas blindly. **Demo:** backup → edit/remove test data → restore, then a complete College/Train/Focus daily walkthrough.

## I14 — Spotify/Music, implemented last, then final integrated release

No Spotify observer/API/UI implementation precedes this increment. Earlier increments reserve only general registration/export/summary contracts; do not bundle a Music permission prompt into Focus setup.

**Tasks**

- I14-T1: Probe opt-in notification/media-session access on the owner phone with current Spotify version. Verify exposed title/credits/IDs/album/artwork, playing/paused/buffering state, screen lock, background, downloaded offline tracks, skips/repeats/seeks, permission loss and service death. Record precise support/gap matrix. A failed probe blocks only claimed capture paths, not prior modules.
- I14-T2: Add isolated Music API/data/UI and Spotify integration modules. Build deduplicated attempts/segments with monotonic timing, nullable metadata, identity resolution/provenance, separate podcasts/ads/unknown and explicit favorites. Implement no screenshot/microphone dependency.
- I14-T3: Retain permitted local album/track artwork, distinct artist portraits if obtainable, missing-art placeholders and optional explicitly enabled online enrichment. Verify current API access/terms before using it; local operation cannot require OAuth/API availability. Do not copy Spotify audio or fabricate artist portraits/history.
- I14-T4: Implement history/search/detail plus day/week/month/custom listening minutes, qualifying plays, unique songs/artists, top song/artist charts, most-played-this-week and manual favorite. Recompute solely from local retained records with labelled rules/coverage.
- I14-T5: Register fifth destination, health/pause/delete controls and Today summary. Add Music data and permitted artwork to full scheduled JSON backup, review selection and cross-module restore. Update schema/version/rollback docs.
- I14-T6: Run S02 local playback scenarios and O01 airplane-mode browsing/capture with Spotify-downloaded content, R01 export/restore, S01 permission/privacy and matched battery checks. Re-run existing College/Train/Focus/Mood journeys and update release candidate without data loss. Deliver signed final private release and complete progress/requirement support matrix.

**Gate:** local capture works for explicitly claimed phone/session paths, no paused/seek time counted as listened, no callback duplicates, missing art/metadata harmless, offline stats and full JSON round-trip pass; unrelated notifications are not retained. If source metadata is insufficient, document the missing capability and do not assert all songs/all devices are captured. **Rollback:** disable only Music observer/route, preserve Music store/export for later repair, revert code with forward-compatible schema or verified JSON snapshot. **Demo:** play downloaded sample tracks offline, pause/seek/repeat, inspect local time/top songs/artists, export JSON, restore and compare; owner-device evidence required.

## 3. Risk register and decision triggers

| Risk | Detection | Response / owner action |
|---|---|---|
| Portal DOM/login changed | Parser validation/state error | Disable affected parser, keep cache; owner performs local login; fix against controlled/redacted fixture |
| Calendar/grade history incomplete | Live dataset audit | Keep manual calendar/scenario; flag exact unavailable dataset; don't invent it |
| Relative grading / repeat policy uncertain | Official-vs-calculated mismatch | Disable cumulative estimate until policy/denominator verified |
| Browser/app UI inaccessible | Unknown classification / bad evaluation | Mark row partial/unsupported, package-level total only; optional explicit later experiment |
| OEM kills service or user revokes access | Health/coverage gap | Recovery guidance; no fabricated usage or tamper resistance |
| Battery overhead too high | Matched profiling | Reduce event work/polling/media, bound context sampling, disable vision by default |
| Media reuse rights unclear | Missing manifest license | Use original/cleared alternatives; don't ship uncertain animation |
| Agent changes unrelated schema/API | Dependency/diff review | Split/revert task, update ADR/contracts before continuing |
| Rollback loses private history | Migration/restore rehearsal fails | Block release; preserve schema compatibility and test JSON restore |
| Mood entries collapsed/scored as ordered | Ten-entry day / categorical-chart review | Preserve timestamped events, frequencies and mixed ties; no invented severity average |
| Spotify metadata/identity/art unavailable | I14 physical-device probe | Nullable records/placeholders, explicit incomplete support; no mandatory API or screen capture |
| Local backup delayed/denied/partial | Scheduler/folder/storage/interruption tests | Show stale/failed state, catch up, keep last-good internal snapshot; never require network |

## 4. Deferred extension backlog

After the baseline: optional announcements, exam/assignment integrations, Moodle as a separate connector, advanced routine programming, body measurements if not completed in I09, more browsers/app versions, widgets, calendar export and optional Health Connect. Each requires its own requirement IDs, scoped increment, migrations and tests. No social or cloud module is implied.
