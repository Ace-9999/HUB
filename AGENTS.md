# PersonalHub implementation instructions

Read `PersonalHub_Plan/AGENTS.md`, `PersonalHub_Plan/SRS.md`, all linked architecture,
increment, UI, export and verification guides, and root `project_progress.md`.
The owner's 12 November 2026 instruction authorizes implementation and sequential
tested commits/pushes to the existing repository; the planning-only statement in
the preserved planning package is historical. No approval per increment is needed.

One implementation owner. Native Kotlin/Compose, local-first, no telemetry or AI
backend. Preserve public API/data/UI boundaries. Never commit credentials, live
portal HTML, personal data, local SDKs, build outputs or signing keys. All sensitive
login and OS grants occur on the owner's device. Maintain truthful gates: a passing
JVM/emulator suite is not physical-device evidence. Continue independent increments
when device gates block dependent work, per the plan. Music implementation stays
last, after I13. Do not alter the reference VTOP repository.

Each cohesive increment: implement, run applicable regressions and build, update
root progress/evidence/code inventory, commit with requirement context, then push.
Record pending acceptance checks instead of claiming a complete increment. Do not
advance dependent integration production work without its feasibility evidence.
