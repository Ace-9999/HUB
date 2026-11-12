# Verification, acceptance evidence and traceability

No Android code or live device tests have been run for this planning package. This file specifies future checks. Document-integrity checks are separately recorded in project_progress.md.

## 1. Test strategy

- JVM tests: attendance/GPA/prediction, training analytics, recurrence/streaks, intervals, scroll grouping and limits using injected clocks.
- Repository/Room tests: snapshot transactions, account/semester isolation, migrations, idempotency, deletion and export/restore.
- Adapter contract tests: controlled HTML/JSON and synthetic accessibility observation traces. Include changed/missing structure and malformed data, not only golden happy paths.
- Compose tests: critical navigation, form states, long lists, text scaling and empty/partial/error states.
- Physical device: VTOP login/challenge, third-party app/browser accessibility, background timers, redirects, OS/OEM restrictions and battery profiling. These cannot be substituted with mocks.
- End-to-end: one real user journey per module plus cross-module regression after shared infrastructure changes.

Test names should describe behavior. Each feature task updates tests matching its requirements; do not gate all early work on a universal high coverage percentage. Domain edge cases and failure recovery matter more than line counts. Keep release signing keys and live data outside CI.

## 2. Check families

| Family | Scope | Minimum evidence |
|---|---|---|
| B01 | Build/static/dependency checks | Exact commands, versions, exit status and artifact path |
| B02 | Performance/size/battery | Release build hash, phone/OS, workload, sample count, percentiles/variance |
| U01 | UI/navigation/accessibility | Screen/state list, theme/font/device sizes, screenshots with synthetic data, manual TalkBack outcome |
| D01 | Persistence/migrations/recovery | Old/new schema, fixture, transaction/restart behavior, failed and passed cases |
| P01 | Portal parser and session contracts | Pinned parser version, sanitized fixture source, expected typed result/error |
| P02 | Live VTOP comparison | Owner-run date/phone/source version, fields compared and pass/fail; no credential/record attachment |
| A01 | Attendance | Exact-count table and threshold boundary assertions |
| A02 | Marks/GPA/repeat policy | Independently computed results; official policy/denominator provenance |
| A03 | Historical grade suggestion | Cohort/threshold/rounding/insufficient-history tests and optional held-out backtest |
| G01 | Catalog/routines/media | Search/edit/offline cases and complete license manifest |
| G02 | Workout lifecycle | Single active, snapshot isolation, set/finish idempotency and recovery |
| G03 | Training analytics | Hand-computed weights/reps/muscles/range/edits dataset |
| T01 | Rest and workout timing | Fake-clock deadlines and device background/process/lock outcomes |
| F01 | Foreground sessions and gaps | Labelled switch/lock/pause trace, overlap/duration assertions |
| F02 | Content classification | Per-source confusion matrix, unknown rate, versions and held-out split |
| F03 | Scroll estimation | Burst/delta replay with oracle and unreliable-source fallback |
| F04 | Limit rules | Daily/session/shared/category/unknown/reset/restart cases |
| F05 | Redirect enforcement | Physical reopen/latency/override/escape and unrelated-tab cases |
| H01 | Tasks/habits | Recurrence, explicit zero/unknown, goal direction, derived/manual consistency |
| N01 | Notifications/reminders | Permission denial, duplicates, resync, clock/zone and delivery behavior |
| R01 | JSON schedule/export/restore/deletion | Full/review round-trip, retention/catch-up/folder failure, corruption/unsupported-version/interruption and isolation; wrong-password only for optional encrypted wrapper |
| M01 | Mood Log and shared trends | Multiple timestamped entries/day, exact category vocabulary, distribution/ties, range/zone, privacy and cross-provider totals |
| O01 | Offline operation | All acquired/local journeys, graphs, capture where source supports offline playback, JSON backup/restore after restart with networking disabled |
| S02 | Spotify capture/statistics | Physical media-state trace, pause/seek/repeat/identity/artwork/gaps, known-duration totals, source support and offline ranks |
| S01 | Security/privacy | Release logs/network/storage/backup/bridge/manifest and sensitive UI review |

## 3. Numerical acceptance examples

### A01 attendance

| Attended / held | Target | Required consecutive attended classes | Safe further absences |
|---|---|---|---|
| 27 / 36 | 75% | 0 | 0 |
| 24 / 36 | 75% | 12 | 0 |
| 30 / 36 | 75% | 0 | 4 |
| 7 / 10 | 80% | 5 | 0 |
| 0 / 0 | 75% | No classes yet | No classes yet |
| Missing / 36 | 75% | Unavailable | Unavailable |

Additional cases: `a > h`, negative counts, 100% target with/without an earlier absence, precision exactly at target, component identity, and portal percentage mismatch. Validate both counts and labels.

### A02 GPA / marks

Use synthetic verified-policy points only: 3 credits at 9 plus 4 credits at 8 gives semester GPA `(27+32)/7 = 8.428571…`. With historical quality points 800 and GPA credits 100, cumulative scenario is `(800+59)/(100+7) = 8.028037…`; display precision may be 8.03 but retain full internal calculation. Never compute `(8 + 8.428571)/2`.

If only a rounded CGPA 8.00 and 100 earned credits are known, this does not prove historical quality points =800 or GPA credits=100. Test unknown-denominator unavailability and approximate-mode labels separately. Repeat example under a confirmed replacement rule: 3-credit old 5-point result replaced by 9 removes 15 and adds 27 quality points without adding three credits twice. Under an all-attempts policy results differ; versioned rules must handle this explicitly.

Assessment example: 18/20 with 20% course weight contributes 18 points to a 100-point total; a pending 60%-weight exam must not appear as scored 0/60. Display known contributions plus pending weight. Do not estimate relative-grade rank from that subtotal.

A03 tests: exactly 3 category records vs 2; exactly 5 overall vs 4; valid historical attempts only; deterministic nearest-point ties choose lower; category unavailable fallback; manual override independence. A held-out semester's grades cannot enter its training cohort. If no adequate cohort/backtest exists, report that plainly.

### G03 training

Synthetic bench: three completed working sets 45 kg × 7, 45 × 7, 45 × 6 produce `900 kg·reps`, 20 reps and 3 working sets. An uncompleted suggested set and a 20×10 warm-up contribute zero by default. Chest primary receives 3 contributions; triceps and shoulders secondary each receive 1.5. Sum 6 does not mean 6 actual sets. A 45×7 estimate gives e1RM `55.5 kg` using the declared formula. Do not estimate e1RM for 20-rep, duration-only or assisted sets.

Test pounds conversion, per-hand vs total dumbbell convention, assisted movement exclusion, date-window boundaries, duplicate muscle tags, deletion/edit recomputation and warm-up option labels. Today vs selected range must be the same interval in charts and body map. A custom exercise with no muscle mapping is “unmapped,” not no stimulus.

### F01/F04 usage and budgets

Trace: Instagram native text 120s → Chrome recognized Instagram video 60s → unrelated Chrome site 90s → lock 30s → X native unknown 40s. Expected known service totals: Instagram 180s, X 40s; known text 120s, video surface 60s, unknown target 40s. Unrelated Chrome and locked time do not count as target time. No category interval overlap. Tracking downtime is a coverage gap and not X usage.

Instagram shared daily budget 180s is exhausted exactly at the boundary; opening native Instagram again redirects. If only video budget 60s is set, text can remain allowed on a supported classifier. Unknown obeys the explicitly chosen unknown policy; service-wide budget counts it. Zero budget blocks immediately. No rule means no block.

Test simultaneous applicable rules, updated budgets below already-used time, cross-midnight active intervals, restart, backward wall clock, forward clock jump, reporting-zone change, reboot and daylight saving in a non-India test zone. Session time requires 60s away before reset; 20s away does not create a fresh allowance. A task/day rollover is independent of monitoring availability.

F03 example: same-root scroll events at 0ms, 80ms and 200ms form one burst; 700ms forms the next. Different root/target closes the burst. Valid single-source deltas totalling 2,000 px over a 1,000 px content viewport yield 2.0 estimated screen lengths. Duplicated nested deltas must not produce 4.0. If deltas are unavailable, show bursts and unknown distance. Programmatic scrolling cannot be declared user scrolling unless the source can distinguish it; report the limitation.

## 4. Physical-device scripts

### P02 portal

Owner enters credentials locally. Record OS/WebView version, date and parser revision. Try cancel, invalid credentials once, challenge retry and session expiry safely without triggering lockout. Select two semesters; compare at least five timetable meetings and three attendance components with portal values, then representative marks/grade attempts when available. Record only comparison outcome/field names in shared evidence. Turn off internet and verify stale-cache behavior. Disconnect and verify secret/cookie removal and cache choice.

### F02 content/source matrix

| Source | Required contexts | Result before device testing |
|---|---|---|
| Instagram native | Home feed, Reels, profile, DMs, transition, unknown layout | Pending |
| X native | Text timeline, media/video surface, profile, messages, transition | Pending |
| Reddit native | Text feed/thread, video feed/player, mixed media, transition | Pending |
| Instagram Chrome | Feed/Reels where available, login, hidden URL/tab switch | Pending |
| X Chrome | Timeline, video surface, cross-site navigation | Pending |
| Reddit Chrome | Feed/thread/video surface, full screen, external link | Pending |
| Secondary chosen browser × 3 services | Same cases with independent domain adapter | Pending |

For each row record package version, browser version, OS/OEM, language, at least 30 labelled segments per class that is applicable, varied sessions and duration. If a class/context cannot be accessed or sampled, report it unsupported/pending rather than manufacturing a pass. Separate labelled development data from evaluation sessions. Report per-class precision/recall, confusion with OTHER/UNKNOWN, overall coverage, false redirects and unknown rate. Publish both metrics over classified segments and end-to-end recall over all applicable labelled segments; abstaining as unknown is not free accuracy.

Set the supported row gate at ≥90% precision and recall for each claimed text/video class, no sensitive content retained, and documented unknown cases. With personal-use version-specific adapters this is a target, not a guarantee of future app-version behavior. Re-run affected rows after third-party UI changes.

### F05 enforcement

Set a short demo budget for one app/service. Reach limit during stationary video-surface dwell and during text scroll. Reopen ten times via launcher, recents, deep link and browser tab; measure from observable event to redirect, report p50/p95 and failures. Verify unrelated browser tab, Android Settings, calls, keyboard, notifications and this app's disable screen remain accessible. Test override expiry, process kill, reboot, service revoked and offline use. Unsupported multi-window/PiP paths stay labelled.

### T01 timing

Start a 90s rest timer. Background after 20s, return after 30s; remaining ≈40s. Kill app process after persistence, reopen and compare to deadline. Lock screen through completion with notifications allowed/denied. Change wall clock in same boot: elapsed deadline remains authoritative. Reboot: follow documented approximate recovery. Measure background alert delay separately from correctness of displayed countdown; a correct UI deadline is not proof that the alert fired on time.

## 5. Requirement → increment → verification map

| Requirement IDs | Delivery / dependencies | Check families |
|---|---|---|
| SYS-01, SYS-04, SYS-09 | I01; regress all later increments | U01, B01 |
| SYS-02–03, SYS-07–08 | I01 foundation, per-feature I03–14; complete offline gate I13/I14 | U01, D01, P01, F01, G02, O01 |
| SYS-05–06, SYS-10–11 | I13, extend Music in I14; contract design begins I01 | R01, S01, O01 |
| SYS-12 | All acquisition/local feature increments; final I13/I14 | O01, S01 |
| COL-01–07 | I02 probe, I04 production | P01, P02, D01, S01 |
| COL-08 | I04 | P01, P02, U01 |
| COL-09–10, COL-15 | I05 | A01, P01, P02, U01, N01 |
| COL-11–14 | I06 | A02, A03, P01, P02 |
| COL-16 | I05/I06 when source verified | P01, P02, N01 |
| GYM-01–04 | I07 | G01, D01, S01 |
| GYM-05–09 | I08 | G02, T01, D01, U01 |
| GYM-10–11 | I09 | G03, U01 |
| GYM-12 | I07 folders / I09 optional supersets/measurements | G01, G02, G03 |
| FOC-01–02, FOC-10 | I02 probe, I10 production | F01, S01, D01 |
| FOC-03–05 | I11 | F02, F03 |
| FOC-06–09, FOC-11 | I12 | F04, F05, U01 |
| FOC-12 | Optional I02/I11 experiment; baseline may omit | S01, F02, B02, device session consent tests |
| HAB-01–02, HAB-04–06 | I03, visibility regression I12/I13 | H01, N01, S01, R01 |
| HAB-03 | I12 | H01, F01, F04 |
| HAB-07, HAB-09 | I03, final restore/privacy checks I13 | M01, H01, R01, O01 |
| HAB-08 | I03 mood/manual trends, I09 workout provider, I12 usage trends | M01, H01, G03, F01, U01 |
| MUS-01–10 | I14 only; probe then implementation, final integration | S02, O01, R01, S01, U01, B02 |
| NFR-01, NFR-11–12 | All increments | B01, dependency/documentation review |
| NFR-02 | I04/I10/I13; audit all data changes | S01, R01 |
| NFR-03–06 | Baselines I01/I02, profile I08/I10, core I13 and final I14 | B02 |
| NFR-07 | I11, repeat on adapter changes | F02 |
| NFR-08 | I03/I08 | D01, G02 |
| NFR-09–10 | I01 onward, core I13 and final I14 | U01, device matrix |

## 6. Evidence format and defect policy

Every evidence entry includes date, increment/task/requirement, build/commit, environment, command or manual steps, expected/actual, pass/fail/not-run, artifact path and known limits. Use synthetic UI screenshots in shared documentation. A local private comparison may be summarized without storing academic records.

P0: credential exposure, unrecoverable data corruption, unsafe blocking loop. P1: lost acknowledged set/task, incorrect official data/CGPA, broad false site blocking. P2: isolated adapter gap, delayed alert, recoverable UI defect. P3: cosmetic issue. P0/P1 block release. Adapter gaps must match visible support status and cannot be silently downgraded to cosmetic defects.

Owner-device testing may be requested with a concrete installable build and short script once ready. Do not ask the owner to approve an abstract plan as a substitute for producing the next reviewable increment.

## 7. Version 1.1 acceptance scripts

### M01 mood logging and week/month/day graphs

- Verify all nine stable codes map to the SRS's exact labels/emojis and text remains accessible without emoji support. Log ten moods across one day, including two at the same millisecond; retain ten UUIDs/events/timestamps in UI, database and JSON. Edit one, backdate across midnight and undo deletion without modifying the other nine. Reject future occurrence times and invalid mood codes.
- Example day: Calm/content ×4, Good/energised ×2, Bored ×3, Low/sad ×1. Day view has ten events; daily distribution is 40%, 20%, 30%, 10% respectively. With a second day containing one Low/sad, pooled observation distribution differs from equally weighted daily distributions; label the selected denominator, never silently substitute one for the other. A 2-vs-2 dominant category tie is mixed, not a fabricated numeric average.
- Check local occurrence time versus original timezone, reporting-zone changes, week/month/custom boundaries, empty day, sparse month, uneven logging frequency, notes privacy and exact event-list drilldown. A categorical chart must not label Foggy/unreal as a scalar severity score or produce medical interpretation.
- Private use count: explicit 0, 2, missing, 1 over four days gives 3 logged occurrences and 3 confirmed days; unknown day is visibly missing and cannot become a zero-success streak. Reels/video graphs retain native/web/category semantics and incomplete coverage. Workout 3 finished/1 draft means 3 completed workouts; period duration/set/volume match G03 and ignore the draft.

### S02 Spotify playback, local statistics and missing metadata

Use controlled tracks/metadata for JVM replay; physical Spotify playback verifies which signals are actually exposed. Example trace: Track A playing 20s → pause 10s → play 40s with a forward seek of 60s. Observed A time is 60s, not 130s. Track B playing 15s with known 180s duration is a stored short attempt, but not a qualifying play. A known 20s track played 10s qualifies under the declared 50% rule. Unknown track duration requires 30s; zero/invalid duration cannot cause immediate qualification. Duplicated callbacks produce one attempt/segment, not repeated listens.

Test same-title different albums/IDs, artist credit string containing commas, local files/no provider IDs, repeated track/position reset with reliable vs ambiguous transition evidence, podcasts, ads, absent artist portrait, album art not being an artist portrait, expired URI artwork, image MIME/size errors, app/device restart, notification access revoked, Bluetooth/headphones, muted playback and Spotify Connect. Do not infer audibility or remote-device history from local metadata. A service gap cannot be filled with track length.

Check local rankings independently: qualify by rule, rank plays then observed seconds then stable key; separate manual favorites. For A credited to two known artists and 60 observed seconds, default additive artist-time chart assigns 30s each, while each can receive one credited play. Unresolved combined credit strings remain unresolved. Delete/correct a record and verify graph/rank recomputation. Re-imported history must not duplicate existing local attempts or pretend imported timestamps contain exact playing segments.

Physical gate: observed state/metadata/identity and permission lifecycle cases work for the declared Spotify/OS version, no non-Spotify notification text retained, and missing art/API access does not disable local functions. Every support claim gets a recorded phone/app version; replay passing alone cannot establish device support.

### O01 offline + R01 JSON schedule/restore

After acquiring test academic data and permitted demo assets, disable Wi-Fi/mobile data and restart the app/process. Use every College cached page/scenario/calendar, Train catalog/media/routines/workout/timer/stats, Focus tasks/habits/moods/graphs and supported local monitoring/limits. In I14, play Spotify-downloaded content offline and verify source observation if available; browse Music history/stats/artwork. No online login/token/license dependency may gate local use. New VTOP refresh shows connection unavailable and keeps prior snapshots; missing art stays a placeholder.

Run Backup now, scheduled dirty-data snapshot/catch-up and seven-snapshot retention without internet. Deny external folder access, fill storage and interrupt writes; previous valid backup survives and stale/failure state is visible. Full backup round-trips all selected retained records/settings and permitted media, including ten same-day moods and private counts. Review export honours date/module/field/pseudonym choices and contains no omitted note/private data. Reject incomplete review export as a full restore. Test corrupted/huge/unknown-version JSON, base64 bounds, duplicate IDs, invalid enums/relationships and cross-module interruption; existing state remains or recovers consistently. For an optional encrypted wrapper, add wrong-password tests without replacing the plain JSON gate.
