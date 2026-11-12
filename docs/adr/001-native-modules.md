# ADR-001: Native Android and cohesive modules

Accepted 12 November 2026. Kotlin/Compose, one Activity, four registered initial
destinations. Pure Kotlin public domain modules, isolated Room data ownership,
feature UI depending on public APIs, app composition providing implementations.
Manual constructor injection initially replaces Hilt to keep early wiring explicit;
introduce Hilt only when graph complexity warrants the extra compiler/plugin.
Pinned Gradle wrapper and version catalog make builds reproducible. Independent
feature modules may progress while physical integration gates remain pending.

The VTOP reference was read only at pinned commit
`4d838ae79ef8452a7981d4284a4e319615f7d4b9`. README SHA-256
`9d36551120f83640203157b2d5fbe04a803634104ee03eaa33473919b58fa1f6`;
VTOPService.java SHA-256
`6b20659642ac704f53c292301f9b83f5c63c17f6b8b90b2ef99ae1761bfe02f8`.
GPL-3.0-or-later notice verified. No reference implementation or proprietary media
copied. New probe code is independently written from Android APIs; preserve full
GPL notices and select compatible licensing before any future adaptation.

Consequences: native privileged integrations stay isolated; more Gradle modules
than a one-file prototype, but domain tests need no Android runtime. No backend.
