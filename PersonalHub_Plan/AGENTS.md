# Development rules for AI agents

Applies to this project when it is imported into a development repository. This file does not authorize modifying the reference VTOP repository or publishing private data. The current deliverable is planning only.

## Read before editing

Read README.md, SRS.md, docs/architecture.md, docs/increments.md, docs/verification.md, docs/ui_design.md and project_progress.md. Check repository status and applicable nested AGENTS.md files. Locate the last verified checkpoint and current task. Do not overwrite uncommitted changes or infer that generated code was tested.

## Increment loop

1. Select the next unblocked task in the active increment. State affected requirement IDs and expected behavior.
2. Inspect relevant code and tests; confirm the current baseline. Capture existing failures separately from new failures.
3. Make the smallest cohesive implementation within the assigned module boundaries.
4. Add meaningful tests for calculations, persistence, platform adapters or journeys affected. Use fake clocks, synthetic fixtures and seeded IDs where useful. Do not test trivial implementation mirrors.
5. Run relevant checks and the required existing regression checks. Device-only behavior remains pending until physical-device evidence exists.
6. Update code map, changes, tests, pending work, limitations, migration/rollback and next exact task in project_progress.md in the same change set.
7. Produce an installable increment/demo when applicable. Record a verified Git checkpoint after checks pass. Advance dependent work only after its gate passes.

## Engineering constraints

- Kotlin/Compose is the baseline. Maintain API/data/UI boundaries; no feature-to-feature implementation imports, cross-domain DAO access or giant shared singleton.
- Pure logic must be testable without Android. WebView orchestration and accessibility capabilities stay in integration modules.
- Keep credentials, cookies, tokens, live HTML, private habit values, browser content and personal datasets out of source control, screenshots, CI, logs and agent prompts.
- The owner enters credentials only on the physical device. Request a redacted outcome or controlled fixture when diagnosing; do not ask for a VTOP password in chat.
- Do not hide unknown/unsupported/missing data as zero. Do not silently convert a conditional requirement into a completion claim.
- Do not scrape Hevy's private assets or remove licenses from the VTOP source. Track asset/code provenance.
- No production destructive Room migration, force push, broad history rewrite, automatic OS settings manipulation or unrelated feature expansion.
- Version 1.1 is authoritative: preserve every timestamped mood event, never average nominal mood categories into severity; keep day/week/month graphs offline.
- Plain `.json` backup/export is required; optional encryption must not remove it. Full local backup includes retained sensitive records; review exports default to scoped redaction. Never share a backup automatically.
- Spotify/Music implementation is last, in I14, with independent opt-in permission and no dependency on screen monitoring. All local features remain usable without source connectivity.
- Pin dependencies; do not add libraries for one trivial helper. No telemetry or external AI calls without an explicit later requirement.
- Never mark a test passed if it was skipped, not run, blocked by tooling, or tested only with mocks while claiming live behavior.

## Handoff and collaboration

The default is one active implementation owner per increment. If the owner authorizes parallel agents, assign disjoint modules/tasks with explicit file ownership and one integration owner. Shared API/schema changes require coordination before editing. Avoid concurrent edits to project_progress.md; the integration owner consolidates entries after reviewing evidence.

Each handoff includes exact paths changed, public API/schema changes, commits/base ref if present, commands and outcomes, device/tool versions, failing cases, one next action and safe rollback. A new agent must be able to continue without access to prior chat.

## Completion standard

A task can be complete while its increment remains blocked. The progress file must express both. A release requires the requirement matrix, device capabilities, asset manifest and known limitations to match the actual APK. No placeholder UI/data may be presented as a connected portal or functioning detector.
