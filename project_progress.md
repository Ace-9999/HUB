# PersonalHub implementation progress

Updated 12 November 2026 (Asia/Kolkata). Owner authorized implementation, package
installation and sequential tested commits/pushes. Original planning history is in
`PersonalHub_Plan/project_progress.md`; SRS baseline 1.1 remains authoritative.

## Current checkpoint

I00 toolchain/source audit verified; phone now attached. I02 debug probes verified locally; live gates pending. Next: I03 local daily tools. Baseline `b354486`, branch
`main`, remote `https://github.com/Ace-9999/personalhub.git`. Clean initial tree.
Gradle wrapper generation passed (19s); SDK API 36, build-tools 35.0.0 and
platform-tools installed. JDK 21.0.9 / Gradle 8.13 verified. `adb devices` returned
no attached devices; physical-phone audit remains pending. No Android app exists yet.

## Increment gates

| Increment | State | Evidence / remaining |
|---|---|---|
| I00 | Complete | Toolchain/source audit passed; OnePlus CPH2661/API 36 recorded |
| I01 | Verification pending | Functional gate passed on API 29 emulator and API 36 phone; manual TalkBack/200%/landscape review pending |
| I02 | Verification pending | Debug probes built/tested; live validated read and monitoring/redirect evidence pending |
| I03 | Not started | Local tasks, habits, moods, trends |
| I04–I06 | Blocked | I02 live VTOP gate needed |
| I07 | Not started | Catalog and routines; independent of VTOP |
| I08 | Not started | Workout engine and timers |
| I09 | Not started | Training history/analytics/body map |
| I10–I12 | Blocked | I02 physical monitoring gate needed |
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

Next exact task: build/test the I01 shell (I00 toolchain gate passed). Physical-device tests remain
pending and independent work proceeds per the increment plan.

### I01 working evidence (not a completed checkpoint)

Requirements: SYS-01?04, SYS-07?09, NFR-01/02/09?12. Four routes, shared
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

### I01 verified checkpoint ? 12 November 2026

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

### I02 bounded probe checkpoint ? 12 November 2026

Base `f3fb80a`. Added debug-only VTOP WebView with strict HTTPS-origin policy,
no credential extraction/native JavaScript bridge and bounded structural counters.
Added opt-in allowlisted accessibility event metadata and reversible Reddit-native
Home probe; no node/content reads, duration/category claims or persistent data.
Capability matrix and ADR-003 record every pending source row. Instagram/X native
packages are unavailable. No personal source content appears in tracked evidence.

PASS: all nine JVM tests (three shared, three origin/shape, three monitoring/privacy);
both debug APK assemblies; lint; boundary check; existing API 29 Compose regression
(34s total Gradle run, exit 0). I02 physical source gates remain pending; structural
page counts do not validate a dataset. I04?I06 and I10?I12 stay blocked. Release
flavors exclude the debug probes. No schema change; disable/close probes and revert
this checkpoint safely. Next: I03 tasks/manual habits/moods, explicitly independent
of live integrations per the plan.
