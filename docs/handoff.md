# PersonalHub agent handoff - 13 November 2026

Start in `C:/Users/aniru/personalhub`. This is a continuation of authorized work,
not a new scaffold. Do not overwrite or discard the uncommitted I03 files.

## Read first

Read root `AGENTS.md`, `project_progress.md`, `PersonalHub_Plan/AGENTS.md`,
`PersonalHub_Plan/SRS.md`, and its linked architecture, increments, verification,
UI and data-export guides. Root progress is the implementation record; the planning
package's progress file is preserved historical planning evidence.

Owner instructions: implement sequential increments, test each, commit/push passing
changes to the existing repo without repeated approval. Functionality first; the
owner wants a later UI redesign, so keep API/data/ViewModel/presentation boundaries
and shared theme tokens. One implementation owner. No runtime AI/backend/telemetry.
All credentials and OS permissions are handled on the owner's phone. Never copy
credentials, live portal HTML or private records into code, logs or agent prompts.

## Last verified code

- Repo `https://github.com/Ace-9999/personalhub.git`, branch `main`.
- Last verified application code commit: `e548f06`.
- I00 environment/source audit complete. I01 functional shell, theme/navigation,
  DataStore settings and Focus Room schema v1 verified. Manual accessibility/font/
  landscape/performance checks remain pending.
- I02 debug probes exist. White VTOP viewport was caused by incomplete server TLS
  chain plus an error overwritten by page-finished. Exact-host debug NSC supplies
  the independently verified public Sectigo R36 intermediate with R36/R46 SPKI pins.
  SSL errors still cancel, cleartext is off, and release manifests exclude probes.
  See ADR-004, `docs/vtop_certificate_provenance.json`, and `docs/capabilities.md`.
- Actual public portal pixels passed on API 29 emulator and physical OnePlus
  CPH2661/Android 16/API 36. The public viewport was visually reviewed. This proves
  public rendering, NOT authenticated login or validated timetable/attendance data.
- Latest verified connected debug APK was installed on the owner's phone. Nothing
  from the current I03 edits has been compiled or installed.
- Last normal regression: JVM tests, both debug APKs, lint, Compose navigation and
  Room emulator tests passed (43s). Live public paint test also passed separately.
  Remote CI execution has not been confirmed as green.

## Current local WIP - NOT VERIFIED

I03 started, then was interrupted. `git status --short` should show ten I03 files
changed/new, in addition to any later handoff-document changes. They contain:

| Paths | Purpose / status |
|---|---|
| `focus/api/build.gradle.kts` | New pure Kotlin API module |
| `focus/api/src/main/kotlin/dev/personalhub/focus/api/FocusModels.kt` | Tasks, recurrence, habits/goals, nine categorical moods, series and distributions |
| `focus/api/src/main/kotlin/dev/personalhub/focus/api/FocusRepository.kt` | Public snapshot/command contract |
| `focus/api/src/test/kotlin/dev/personalhub/focus/api/FocusRulesTest.kt` | Nine domain tests written, not run |
| `focus/data/build.gradle.kts` | API dependency and schema test-assets setup |
| `focus/data/src/main/kotlin/dev/personalhub/focus/data/FocusDatabase.kt` | Proposed version 2 and additive 1->2 migration; unverified |
| `focus/data/src/main/kotlin/dev/personalhub/focus/data/FocusEntities.kt` | Proposed typed entities/DAO |
| `focus/data/src/main/kotlin/dev/personalhub/focus/data/RoomFocusRepository.kt` | Proposed transaction-backed repository |
| `focus/data/src/androidTest/kotlin/dev/personalhub/focus/data/FocusPersistenceTest.kt` | Migration/reopen/mood/undo/invalid-command tests, not run |
| `settings.gradle.kts` | Adds API and reserved `:feature:focus` |

`feature/focus/` does not yet exist. There is no Focus ViewModel/UI, app wiring,
Today task summary, reminder scheduling, private/device-credential gate or completed
I03 trend journey. Schema v2 JSON has not been generated. The app still shows the
previous placeholders. Inspect the WIP rather than assuming it compiles.

Next exact task: finish the missing module/configuration and app composition;
compile the API/data work; validate schema 1->2 and record-preservation before
finishing the offline task/habit/mood forms and UI journeys. Review nonrecurring
task completion eligibility, historical edits, immutable unit semantics and privacy.
Add meaningful coverage where inspection finds gaps. Preserve every timestamped
mood, including same-millisecond entries; never assign numeric mood severity.
Missing manual days remain unknown; explicit zero is a confirmed observation.

## Transfer to another workspace

The safest handoff is a new agent opened in THIS existing folder: all WIP is present.
Do not reset, clean, stash-drop or apply the patch again here.

For a fresh clone, give the new agent both the repo and
`artifacts/handoff-I03-WIP.patch`. It is local/ignored, not pushed. The patch contains
all ten I03 files, including untracked files, based on `e548f06`. A documentation-only
handoff commit does not alter those source paths, so the patch also applies on top
of that commit. Check first, then apply once:

```powershell
git apply --check path/to/handoff-I03-WIP.patch
git apply path/to/handoff-I03-WIP.patch
```

Patch SHA-256: `4291b9728a1218140ee175a78cbd5e7885cbec27a0ffb2b119033ab4e5989229`.
Verified with a separate temporary Git index; real staging and WIP were preserved.
Do not commit this patch as an application increment. Complete/test the code first.

## Toolchain and checks

Windows PowerShell, JDK 21.0.9 at `C:/Program Files/Java/jdk-21`, Gradle wrapper 8.13,
AGP 8.13.2, Kotlin 2.2.21, compile/target 36, minimum 29. Dependencies pinned in
`gradle/libs.versions.toml`. Checkout-local SDK is ignored in `.tools/android-sdk`;
fresh machines need bootstrap/setup from `docs/environment.md`. Local settings do
not transfer through Git. `scripts/run_gradle.py` discovers the local SDK/JDK.

```powershell
git status --short
python scripts/check_boundaries.py
python scripts/run_gradle.py :focus:api:test :focus:data:assembleDebug
```

After completing the feature/configuration, run the full applicable regression:

```powershell
python scripts/run_gradle.py test assembleDemoDebug assembleConnectedDebug lintDemoDebug
& '.\.tools\android-sdk\platform-tools\adb.exe' devices -l
$env:ANDROID_SERIAL = 'emulator-5554' # only after confirming this emulator is running
python scripts/run_gradle.py connectedDemoDebugAndroidTest :focus:data:connectedDebugAndroidTest
python scripts/code_inventory.py
```

Treat these as commands to run, not passed evidence for I03. Keep tests synthetic
and test databases uniquely named. Do not run destructive lifecycle tests against
the owner's connected app/database. Keep the physical phone unlocked for UI tests.
Do not change the owner's Android settings automatically.

Optional live PUBLIC portal check (separate from offline regression):

```powershell
python scripts/run_gradle.py connectedDemoDebugAndroidTest -PportalProbe '-Pandroid.testInstrumentationRunnerArguments.class=dev.personalhub.app.PublicPortalProbeTest'
```

It never logs in. Do not enable screenshot capture for owner-authenticated sessions.

## Gates, commit and rollback

I04-I06 need an actual authenticated validated VTOP sample. I10-I12 need physical
monitoring/redirect evidence; Instagram and X native packages were unavailable on
the phone during the audit. Do not claim College connected or screen categories
working. Independent I03 and I07-I09 may continue per the plan. I13 is backup/release
hardening; Music must be implemented last in I14, not before those gates.

After checks pass: export the v2 Room schema, update progress/evidence/inventory in
the same cohesive change, commit with increment/requirement context and push.
Record pending device-only acceptance honestly. A documentation handoff checkpoint
is not a green I03 application checkpoint. No production database has been upgraded
to v2 by this WIP. Preserve a WIP copy before any intentional rollback, retain v1
schema, and never destroy owner data or blindly downgrade a later schema.

## Prompt to paste into the next agent

> Continue PersonalHub in this existing workspace. Read AGENTS.md,
> project_progress.md and docs/handoff.md, then the required planning guides.
> Preserve the uncommitted I03 API/data work. Last verified code is e548f06; current
> WIP is untested and the feature module/UI is still missing. Finish I03, run the
> applicable tests and regressions, update progress, then commit/push passing
> increments sequentially without repeated approval. Functionality first; keep the
> UI replaceable. Respect live/device gates and keep Music last. Never use chat
> credentials or claim unrun checks passed.
