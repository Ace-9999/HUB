# Architecture and implementation contracts

Status: proposed baseline 1.1, not generated application code. Related requirements: SYS-01–12, COL-01–16, GYM-01–12, FOC-01–12, HAB-01–09, MUS-01–10, NFR-01–12.

## 1. Technology decision

Use Kotlin for native Android and Jetpack Compose for all new UI. This avoids a cross-platform bridge around accessibility, lifecycle, notifications and WebView integration. Kotlin does not by itself guarantee performance; battery use and main-thread behavior must be profiled. No Python runtime, remote scraping server or cross-platform shell is needed.

Proposed libraries: Compose Material 3 with custom theme tokens, AndroidX lifecycle/ViewModel, Kotlin Coroutines/Flow, Room, DataStore for non-secret preferences, Hilt for composition, AndroidX Navigation Compose behind an app-owned navigator, WorkManager for deferrable noninteractive work, OkHttp where ordinary HTTP is appropriate, and a maintained image loader for cleared GIF/WebP media. Evaluate Media3 only if shipped demonstrations need video. Keep dependencies few and pinned in a version catalog with Gradle wrapper and JDK/toolchain versions recorded. Verify current stable versions/compatibility in Increment 0; these are library choices, not invented version numbers.

Use a single Activity with lifecycle-aware state collection and unidirectional UI state/events. ViewModels call repositories/use cases. Domain calculations depend on pure Kotlin models, not Context, Room entities, WebView or composables. Use immutable UI states; side effects such as a toast, redirect or navigation are explicit events with deduplication/idempotency.

## 2. Module layout and dependency direction

Start with useful boundaries; avoid creating a separate Gradle project per screen or entity. Add a module only for meaningful isolation, platform privilege, ownership or independent contracts.

| Proposed module | Responsibility | Allowed internal dependencies |
|---|---|---|
| `:app` | Manifest composition, DI wiring, root navigation, Today dashboard | All public feature entry points, API and implementation modules at composition time |
| `:core:model` | IDs, clocks/time types, result/status/provenance primitives | Kotlin standard types only where practical |
| `:core:designsystem` | Tokens, theme, reusable cards/fields/charts shells/navigation items | AndroidX UI; no feature implementations |
| `:core:security` | Secret vault, app lock, optional backup encryption adapter | Android platform; core model |
| `:core:notifications` | Reminder scheduling and permission-aware delivery | Android platform; core model |
| `:core:testing` | Fakes, test clocks, fixture loaders and rule helpers | Test libraries; never a production dependency |
| `:college:api` | Academic models, repository/summary interfaces | Core model |
| `:college:data` | Room store, mapping, sync coordinator, schema migrations | College API, connector, core security |
| `:integration:vtop` | WebView/session adapter, parser assets, typed bridge | College API, core security; no feature UI |
| `:feature:college` | College ViewModels/screens and calculation presentation | College API, design system, notifications API |
| `:training:api` | Exercise/routine/workout models and repository interfaces | Core model |
| `:training:data` | Training database, catalog/media metadata, analytics implementations | Training API, core security for backup only if needed |
| `:feature:training` | Routines, logging, history and statistics UI | Training API, design system, notifications API |
| `:focus:api` | Usage/category/limit/task/habit types and interfaces | Core model |
| `:focus:data` | Focus store, pure rules, aggregations, task/habit logic | Focus API |
| `:integration:monitoring` | Accessibility/usage adapters and redirect mechanism | Focus API, Android platform |
| `:feature:focus` | Usage, limits, tasks, habits and health UI | Focus API, design system, security API |
| `:music:api` | Track/artist/album/listening models, Music repository and summaries | Core model |
| `:music:data` | Music Room database, attempt/segment builder, rankings and artwork storage | Music API; core model |
| `:integration:spotify` | Opt-in notification/media-session observer, optional official metadata adapter | Music API; Android platform; core security for optional tokens |
| `:feature:music` | Listening history, day/week/month stats, songs/artists/albums, tracking health | Music API; design system |
| `:core:backup` | Local JSON snapshot scheduler, portable export/import, restore journal and module contracts | Core model/security; module implementations supplied by app composition |
| `:feature:settings` | Settings UI and owner-triggered data controls | Public module administration interfaces and design system |

Feature implementations must not depend on one another, nor may one module query another's Room tables. APIs expose suspend commands and Flow snapshots. Public models differ from persistence entities. `:app` assembles read-only summary providers for Today; it does not calculate attendance, volume or focus budgets. Module configuration includes feature ID, title/icon, route entry, optional summary, enabled state and data export/delete handlers. Navigation requests use typed routes/IDs resolved by `:app`, not references to another feature's composable.

Keep shared core small. Do not build a generic plugin marketplace, global event bus or universal repository abstraction. Add a narrow shared contract when a real integration needs it. Automated dependency checks should reject feature-to-feature and API-to-implementation edges.

## 3. State and persistence ownership

Use one Room database per domain (College, Training, Focus, and Music added in I14) with separate schema exports and migrations. The app orchestrates whole-backup restore; it must stage/validate all selected domain payloads before swapping them into place. To avoid an inconsistent multi-database restore, use a maintenance lock plus recoverable restore journal and either roll forward all staged domains or restore the prior snapshots after a crash. Do not assume separate SQLite transactions form one transaction. Version 1.1 uses regular local JSON snapshots; details are in [data_export.md](data_export.md).

No destructive migration fallback in release builds. IDs are stable UUIDs locally; portal identities are explicit natural/composite keys tied to account and semester. Every editable row carries created/updated instants and optional soft-deletion metadata where undo is provided. Time is stored as UTC instants; recurrence/academic local dates store a ZoneId or declared campus local time. Timers use elapsed realtime and persisted deadlines/boot identity as described below.

### 3.1 College schema, conceptual

| Entity | Key fields / relationship | Important invariants |
|---|---|---|
| PortalAccount | accountId, campus, username reference, credential reference | Secrets live in vault; single active account but explicit IDs prevent cache leakage |
| Semester | accountId + portalSemesterId, label, dates if known | No guessed dates from name |
| CourseOffering | accountId + semesterId + portalCourse/registrationId + component, code, name, credits, faculty, category | Same course can have multiple components/attempts; code alone is not unique |
| TimetableMeeting | offeringId, slot, weekday, start/end local time, venue | Linked to semester/campus zone; overlapping meetings retained |
| AttendanceSnapshot | offeringId, attended, held, portalPercent, capturedAt, syncRunId | Nullable counts distinguish missing data from zero |
| AssessmentResult | offeringId, source assessment key, label, score/max/weight, status | Unique per offering/assessment; unknown weight remains null |
| GradeAttempt | accountId, semesterId, offeringId/source key, grade, numeric point, credits, attempt, status | Raw official grade retained; inclusion/repeat policy version recorded |
| AcademicSummary | officialCgpa, officialGpaCredits, earnedCredits, qualityPoints if known | Independent nullable values; never use earnedCredits as denominator by assumption |
| AcademicEvent | eventId, date/range, type, title, provenance, source key, revision | Imported and manual event sources separate; updates can supersede |
| GradeScenario | scenarioId, semesterId, selected grade per offering, methodVersion | Never updates GradeAttempt |
| SyncRun / DatasetStatus | account, semester, dataset, parserVersion, startedAt, outcome, lastSuccessAt | One serialized sync; success/empty/failed states distinct |

Each valid snapshot uses a staging table or in-memory validated structure then a Room transaction. For a dataset, replace rows only after structural and semantic validation. Retain last valid data after network/auth/markup failure. At most two prior validated snapshots per dataset may be kept for rollback; no indefinite raw HTML history. Official data changes are auditable by dataset revisions, without logging personal rows externally.

### 3.2 Training schema, conceptual

| Entity | Fields / relationship |
|---|---|
| Exercise | id, names/aliases, equipment, instruction revision, metric type, weight convention, custom flag |
| ExerciseMuscle | exerciseId, muscleId, primary/secondary role |
| MediaAsset | exerciseId optional, URI/local path, content hash, MIME, author, source URL, license ID/text reference, clearance status |
| Routine / RoutineExercise / RoutineSet | ordered IDs, exercise reference, targets, set type, rest rule, notes, optional folder/superset |
| Workout | id, sourceRoutineId/revision, startedAt, endedAt, paused duration, status, timezone |
| WorkoutExercise | workoutId, exercise metadata snapshot, order, notes, sourceRoutineExerciseId |
| WorkoutSet | workoutExerciseId, entered quantity/unit, canonical quantity, reps/duration/distance, RPE, type, completeAt |
| ActiveRestTimer | workoutId, duration, elapsed deadline, wall deadline, boot ID, paused remainder |
| BodyMeasurement | timestamp, type, canonical value, display unit; optional |

History references an exercise's stable ID but snapshots names, metric conventions and muscle mapping versions relevant to the session. A later catalog correction requires an explicit versioned recomputation choice rather than silently changing historical semantics. Completed sets count only once. Empty workouts can be discarded; a zero-completed-set finish requires explicit confirmation and is excluded from performance records.

### 3.3 Focus schema, conceptual

| Entity | Fields / relationship |
|---|---|
| TargetService / SourceAdapter | serviceId, package or browser ID, exact hosts, adapter version, supported app/browser versions |
| UsageInterval | id, service, package, native/web, category, start/end, measurement source, confidence, classifier version, reporting zone |
| ScrollAggregate | interval/day link, burst count, estimated screen lengths nullable, reliability |
| CoverageGap | start/end or open-ended, reason, source scope, known/uncertain |
| DailyUsageAggregate | date + zone + service + category, duration, coverage status, aggregation version |
| LimitRule | id, scope, budget, daily/session, unknown policy, reset zone, enabled, rule revision |
| BlockDecision / Override | ruleId, timestamp, source type, reason, expiresAt, redirect outcome; no content text |
| Task / TaskOccurrence | definition, priority, due local time/zone, recurrence; occurrence date, completion |
| HabitDefinition | id, metric, increase/reduce/abstain, target, manual/derived, sensitive, visibility |
| HabitEntry | habitId, local date, value, explicitZero flag, manual source, notes, updatedAt |
| MoodEntry | UUID, moodCode (nine-value vocabulary), occurredAt, originalZone, createdAt, updatedAt, optional note/revision; no unique constraint on date/time |
| HabitDerivedDay | read model from UsageInterval aggregation, coverage, method version; not a manual entry |

Retain fine-grained intervals for 30 days by default, daily aggregates until owner deletion; optional retention changes are explicit. No raw accessibility trees, post text or video frames in production storage. Test fixtures use controlled/dummy UI content; live diagnostics stay ephemeral and redacted. If interval reclassification is added, only retained structured evidence sufficient for safe reclassification may be used; otherwise support manual category/time correction with an audit label rather than pretending the original content can be recovered.

### 3.4 Music schema and offline retention (I14 only)

| Entity | Fields / invariant |
|---|---|
| Track / Album / Artist | Stable local IDs, optional provider IDs, title/names, unresolved credit string, identity provenance; never merge solely by track title |
| TrackArtist | Explicit resolved credits in stable order; no guessing by splitting a combined string |
| ListeningAttempt | UUID, source session/track key, startedAt/endedAt, originalZone, qualification rule version, metadata state, source provenance |
| PlaybackSegment | attemptId, start/end instants, elapsed clock anchors, state, observed duration, uncertainty; only valid playing intervals count |
| ListeningGap | scope, interval, reason; never inferred as listened time |
| MusicArtwork | track/album/artist relation, bytes/path, MIME/hash, origin/permissions, offline retained state; portrait vs cover roles distinct |
| ManualFavorite | trackId, selectedAt; independent from most-played rank |

Spotify audio is never copied. Persist observation attempts and playing segments, not repeated notification snapshots. Artist/album pictures are retained on device only when exposed/obtained with permitted access. Null artwork is a first-class state. No dependency on Focus database or screen recording. Music contributes through the same export/delete/summary contracts.

### 3.5 Cross-module trends

Focus owns MoodEntry and manual habits. Training owns completed workouts and exposes read-only metric series; Music owns listening aggregates. Define narrow `MetricSeriesProvider`/`MoodSeries` models with metric ID, unit, range/zone, points, completeness and method version in the shared model contract. App composition injects available providers into the trends presentation. No cross-module DAO calls or duplicated workout/habit events; chart presentation cannot recalculate domain business rules. Mood series are categorical events, not scalar scores.

## 4. Public contracts

Illustrative contract shapes, to be finalized and tested in Increment 1; not a promise that code already exists:

```kotlin
interface CollegeRepository {
    fun overview(semester: SemesterId): Flow<CollegeOverview>
    fun syncState(): Flow<SyncState>
    suspend fun refresh(request: SyncRequest): SyncRunId
    suspend fun cancel(run: SyncRunId)
}

interface TrainingRepository {
    fun activeWorkout(): Flow<WorkoutDraft?>
    suspend fun startWorkout(source: WorkoutSource): WorkoutId
    suspend fun commitSet(command: CommitSet): SavedSet
    suspend fun finishWorkout(id: WorkoutId, operationId: OperationId)
}

interface FocusRepository {
    fun dailySummary(day: ReportingDay): Flow<FocusSummary>
    fun health(): Flow<TrackingHealth>
    suspend fun ingest(batch: StructuredObservationBatch)
    suspend fun saveLimit(rule: LimitRule)
}

interface ContentClassifier {
    val adapterId: String
    fun classify(context: SanitizedForegroundContext): Classification
}

interface ModuleSummaryProvider {
    val moduleId: String
    fun summary(): Flow<ModuleSummary>
}
```

`SyncState` and result models use sealed types with recoverability and source timestamps. `Classification` contains category, source service, confidence/unknown reason and version. `SanitizedForegroundContext` is internal/transient; it must not be serializable into logs. Use bounded input size, retention and message rates. Avoid a single generic `Map<String, Any>` interface that pushes validation into every screen.

## 5. VTOP integration design

1. Foreground college connection controller creates the authenticated WebView/session and navigates only allowed portal/challenge origins. External navigation is denied or opened without the native bridge. Challenge origins need an audited configuration; cross-origin frames must not access privileged methods.
2. Load the real landing/login flow, discover state, and let the owner solve challenges. Prefer a visible login/challenge surface. Serialize injected data safely using JSON encoding, not string concatenation of credentials.
3. Execute small versioned parser scripts in `integration/vtop/src/main/assets/parsers/`. Separate request/state orchestration from extraction and domain mapping. Use asynchronous operations with timeout/cancellation; do not copy synchronous `async:false` loops into the new UI.
4. Preserve required cookies/CSRF/form fields and semester selection. Treat reference endpoint names as observed candidates, not a public stable API.
5. Send typed bounded JSON through the smallest possible origin-restricted message interface. Revalidate on native side. Restrict bridge methods, disable file access/debugging in release, reject SSL failures; do not expose general native commands to web content.
6. Validate expected headings/keys, account/semester context, counts/ranges and genuine no-record responses before persistence. JSON null/missing fields must survive mapping. Parse errors must never turn missing grades into F or missing attendance into 0%.
7. Destroy the WebView when sync/challenge ends. Local database is the UI source of truth. Show dataset-level progress and partial failure. Session expiry goes to NeedsLogin, not a rapid retry loop.

Do not depend on the reference developer's configuration server or analytics. Pin the studied source revision, keep GPL notices for adapted code and select a compatible license approach before code reuse/distribution. The repo uses GPL-3.0-or-later in its README; personal use is not a reason to remove attribution. Choosing a different app license may require an independent implementation/legal review before distribution.

Academic calendar is a separate importer interface. First prove whether the actual portal offers a usable source; otherwise manual event management and validated ICS import fulfill the calendar UI while source automation remains pending. ICS parsing should use a maintained parser and bound file size/recurrence expansion.

## 6. Monitoring and enforcement pipeline

Events → foreground resolver → allowlisted source adapter → transient classification → interval builder → persisted aggregation → limit engine → redirection executor. Domain rules and replay tests are independent of Android services.

Usage access supports package-level reconciliation and usage history. Accessibility receives selected window/content/scroll events and samples only allowlisted target contexts. Ignore own overlays, keyboard/password inputs, notification panels and unrelated apps. Rate-limit/debounce events, bound tree traversal and do work off the callback thread when safe. Do not retain node references beyond valid lifecycle or build an unrestricted screen scraper.

Browser adapters verify known address bar/UI semantics and parse an exact normalized URI host, including punycode/scheme handling. When the address bar is hidden, retain a domain context only while validated tab/navigation signals permit it, with a short bounded expiry defined and tested per adapter. A new tab, app switch, ambiguity or unsupported UI invalidates it. Web content alone is not a trusted URL source. Cross-site navigation must stop an earlier site's timer.

Interval builder states: Disabled → Ready → TargetActive(category/unknown) → Paused/Gap → Ready. Lock or foreground exit closes an interval. Classification changes close and reopen without overlap. Persist accumulated usage checkpoints while a target is active; bound crash-loss uncertainty and show a gap on restart rather than inventing an end time. WorkManager is not a realtime detector. No unconditional polling loop while the phone is idle.

The pure limit engine uses current intervals + persisted totals + enabled rule revisions. Redirect once per foreground target observation cycle with a cooldown (initial default 750 ms), then re-evaluate on re-entry. Do not create an infinite Home/overlay loop. Browser enforcement affects only recognized restricted sites; native blocking can affect the whole selected package. Category-specific blocking can permit a service's nonrestricted areas only when the classifier can recognize them.

Never use `killBackgroundProcesses` as enforcement. No automation of Force Stop settings. `performGlobalAction(GLOBAL_ACTION_HOME)` is a candidate action, with an accessible explanation overlay/notification if the platform permits it. Verify on physical device, including lock screen and foreground restrictions. If not reliable on a target configuration, report unsupported rather than escalating privileges silently.

On reboot, restore limit state after user unlock and service reconnection where the OS permits; show unobserved time as a gap. Do not assume a foreground service survives force-stop or a special permission is permanent. User can always pause/disable tracking through the app or Android settings.

## 7. Timers, notifications and background work

Workout elapsed time and rest countdown use monotonic elapsed realtime in one boot. Persist duration plus elapsed/wall deadline and boot marker. After process death in the same boot, recover accurately. After reboot, reconcile using wall deadline with an “approximate after restart” label if clock changes prevent certainty. Rest completion is idempotent; repeated notification delivery cannot complete multiple sets.

The UI does not decrement and persist a number every second. Schedule a permission-aware alert where appropriate and recompute the display from the deadline. Evaluate normal notification, justified foreground-service behavior and exact-alarm eligibility against the target OS during timer work. Do not use a fake media service type or declare an unrelated special-use service merely to stay alive. If an exact background alert is unavailable, state the limitation; in-app countdown can remain correct.

Use WorkManager for regular local JSON backups (24-hour default), media cleanup, retention cleanup and periodic local rollups. Backup work has no internet constraint, catches up when eligible, and reports last-success/failure; periodic execution is inexact. After committed changes, a dirty revision allows coalescing redundant snapshots. All feature actions and charts use local repositories; source fetches are separately triggered and never gate opening the app. Portal sync requiring WebView/challenge is user-initiated foreground work until an independently supported session refresh approach is proven. No background attempt may repeatedly ask for CAPTCHA or trigger credential lockouts.

## 8. Security, export and recovery

Credential vault: encrypt secret material with authenticated encryption using keys managed by Android Keystore. Store ciphertext in app-private storage; public DataStore preferences contain only nonsecrets. A device unlock requirement/key invalidation must yield recoverable “sign in again,” not wipe unrelated data. Exclude credentials/cookies/sensitive stores from OS/cloud backups. Owner-controlled full local JSON snapshots intentionally include retained personal mood/habit records, but never secrets or OS permission grants.

Backup format: documented UTF-8 JSON with versioned envelope, module schemas, stable record IDs, local records/settings, coverage and optional owned/permitted media encoded as base64+MIME+hash. Default automatic full snapshots include retained personal data, including sensitive habits/moods, in app-private storage; scheduled copies to an owner-chosen local folder are optional. Review exports have explicit date/module/field selection, default sensitive-field omission and a preview; no sharing is performed by the app. Credentials/cookies/tokens, raw diagnostics and OS grants are never included. An optional authenticated-encrypted wrapper may supplement, but cannot replace, plain `.json` export. If offered, use vetted password-based key derivation/encryption and document parameters; a device-only Keystore key is not a portable backup key. JSON hashes detect accidental corruption, not authenticity against deliberate modification. Restore validates all structure/ranges/IDs/asset bounds and selected module versions before committing with a maintenance lock and recoverable journal. Partial failure must leave or recover the old state. See [data_export.md](data_export.md).

Deletion stops jobs/observers before removing relevant rows/files/keys. Module deletion must not invalidate another module's independent encrypted material. Clear College connection means revoke local credentials/session/cookies; owner chooses whether to retain offline academic cache. App uninstall naturally removes ordinary internal data, subject to Android behavior; do not claim secure deletion against rooted forensic recovery.

## 9. Reversibility and extension

Each increment is a small branch/change set with green baseline, an additive migration when possible, a feature switch for experimental integration, and an evidence-linked checkpoint. Git revert is preferred for code rollback; database downgrades are not assumed safe. Revert code against a forward-compatible schema or restore a tested local JSON snapshot (or an optional encrypted copy). Never suggest reinstalling as a routine rollback for personal history.

To add a module later: define API and data owner → implement isolated repository/UI → register route/summary/export/delete contributions in app → add contract and regression checks → document code paths and migration → ship behind a switch until verified. To remove a module: unregister UI and stop its jobs, preserve/export data unless owner explicitly requests deletion.

## 10. Spotify acquisition boundary — final increment I14

Use `MediaSessionManager` and a user-enabled `NotificationListenerService` to discover permitted Spotify sessions, subscribe to metadata/playback callbacks and discard every non-Spotify notification immediately. The permission declaration does not itself enable notification access. Do not request privileged MEDIA_CONTENT_CONTROL assuming it will be granted. Parse only exposed structured fields; no screenshot OCR, audio capture, hidden database access or token extraction from Spotify.

Track playing intervals with elapsed realtime and source callback timestamps, close on pause/buffer/stop/track change/disconnection, checkpoint periodically while genuinely playing and represent uncertain restart intervals as gaps. Playback position and speed are validation signals, not a sum of listened seconds: seeking cannot add duration. A position reset on the same track is not automatically a new play until a tested repeat rule establishes a new attempt. App/device/OEM callback behavior must be physically validated in I14, including screen lock, Bluetooth, offline downloaded playback, skips, local files and Spotify Connect. Remote-device playback is unsupported unless source evidence explicitly establishes its scope; do not infer audibility.

Optional enrichment uses explicit online mode and official current access with native-app OAuth PKCE if required; no embedded client secret or server is mandatory. Recheck endpoint/access/caching rules then, since offline storage cannot be promised from an online URL alone. Prefer captured local artwork when available; never substitute album cover as artist portrait. Song/artist image fetch failure leaves placeholders and cannot stop local history/stats. An optional official history import is provenance-labelled, bounded and deduplicated against local records; imported play timestamps are not evidence of exact listened intervals.
