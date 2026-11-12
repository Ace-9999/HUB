# PersonalHub — Android development brief

Version 1.1 · 12 November 2026 · Planning baseline

PersonalHub is a working name for one private Android app covering college, training, and intentional phone use. The name is replaceable. This package is a specification and development plan; it contains no implemented Android app and makes no claim that device-dependent integrations have passed testing.

## Start here

1. Read [SRS.md](SRS.md) for scope, requirements, calculations, and acceptance criteria.
2. Read [architecture.md](docs/architecture.md) for module boundaries, contracts, data ownership, and platform behavior.
3. Read [ui_design.md](docs/ui_design.md), then open the [proposed screen board](assets/design/proposed_screens.svg). Original screenshots are included with a reference manifest.
4. Execute [increments.md](docs/increments.md) in order. Each increment produces an installable improvement or an explicitly bounded feasibility result before the next dependent feature begins.
5. Use [verification.md](docs/verification.md) to verify it. Update [project_progress.md](project_progress.md) before handing work over.
6. Follow [AGENTS.md](AGENTS.md). Paste [agent_task.md](templates/agent_task.md) into a new agent session to assign a bounded task.

## Package map

| Path | Purpose |
|---|---|
| `SRS.md` | Product specification; authoritative requirements and acceptance IDs |
| `docs/architecture.md` | Kotlin/Compose architecture, conceptual schema, interfaces, privacy boundaries |
| `docs/increments.md` | Ordered backlog, dependencies, deliverables, tests, release gates |
| `docs/ui_design.md` | Shared visual language, screen behavior, screenshot mapping |
| `docs/verification.md` | Calculation examples, test matrix, device validation, requirement traceability |
| `docs/research.md` | Pinned VTOP source audit, external sources, feasibility limits |
| `project_progress.md` | Current state, code map, change log, blockers, next task, rollback record |
| `AGENTS.md` | Development and handoff rules for all future agents |
| `templates/agent_task.md` | Copyable increment work order |
| `assets/references/` | Thirteen supplied screenshots, including one duplicate reference |
| `assets/design/proposed_screens.svg` / `.png` | Nine original unified dark UI wireframes, with illustrative data; PNG fallback for Markdown viewers |
| `assets/reference_manifest.json` | Original filenames, hashes, reference IDs and intended use |
| `review.html` | Portable visual overview with embedded images; open in a browser |
| `assets/design/additional_screens.svg` / `.png` | Mood Log, shared trends and Music concept screens for version 1.1 |
| `docs/data_export.md` | Scheduled JSON snapshot, review export and restore contract |

## Defaults chosen so work can start

- Native Kotlin, Jetpack Compose, local storage, no backend, no cloud AI dependency.
- Android 10/API 29 minimum, provisional until the owner's actual phone is recorded in Increment 0. Compile/target against the current stable supported SDK chosen and pinned during setup; do not copy old SDK versions from the reference repo.
- Dark theme first, full light theme, one design system. Today, College, Train and Focus ship first; Music becomes the fifth destination in final increment I14. Settings opens from a toolbar action.
- Chennai VTOP integration; the Vellore wording in the UI reference is not a campus requirement.
- Personal sideloaded APK first. Play Store publication, root, device-owner enrollment, social features, nutrition, and wearable integrations are outside the baseline.
- Tracking is opt-in and local. Accessibility signals are the baseline; continuous screenshot recording is not required. Optional screen-analysis experiments must be visible and separately enabled.

## Version 1.1 additions

- Timestamped Mood Log with the nine specified emoji/label pairs, unlimited entries per day, categorical day timeline and week/month distributions.
- Day/week/month/custom trend graphs for moods, private manual use counts, Reels/video/text usage and workouts.
- Independent Spotify capture/history/statistics module, implemented last in I14; optional local notification/media-session observation, cached metadata/artwork and offline rankings.
- All acquired/local records and features work offline, including charts and restore. VTOP refresh and optional metadata/media acquisition are the explicit online boundaries.
- Regular full on-device `.json` backup (24-hour default, seven-snapshot retention), Backup now, restore and field/date-scoped review exports. Full snapshots include retained sensitive records; review exports omit them by default. No automatic sharing. Optional encrypted copies do not replace plain JSON.

## Important implementation boundaries

The VTOP flow is verified in source, not with the owner's live credentials. Academic-calendar extraction and historical grade completeness still need live validation. App/browser content classification is provisional until tested on recorded versions. Unsupported screens remain visible as unknown; they must never be silently counted as zero. App limits redirect away from a blocked target and check again when reopened. Android process termination is not a promised capability.

## Suggested first agent instruction

> Read README.md, AGENTS.md, SRS.md, docs/increments.md and project_progress.md. Execute Increment 0 only: prepare the toolchain/source/phone capability audit and record evidence. Do not ask for portal credentials in chat. Do not begin unrelated increments. Update project_progress.md with exact outcomes, including anything not tested.
