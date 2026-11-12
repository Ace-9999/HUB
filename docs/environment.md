# Development environment — I00

Recorded 12 November 2026, Asia/Kolkata. Windows, PowerShell. Existing repository
`Ace-9999/personalhub`, `main`, baseline `b354486`. JDK 21.0.9 installed at
`C:/Program Files/Java/jdk-21`; Python 3.13.1. No Android SDK/Gradle initially present.

Pinned baseline: Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.2.21,
Compose BOM 2025.10.01, compile/target API 36, min API 29, build-tools 35.0.0,
JVM bytecode 17. JDK 21 runs Gradle. These are stable published versions, chosen
for compatibility rather than a claim to be the newest release.

Official compatibility: https://developer.android.com/build/releases/agp-8-13-0-release-notes
documents Gradle 8.13, JDK 17 minimum and API 36.1 maximum. Kotlin 2+ uses the
matching Compose compiler plugin:
https://developer.android.com/jetpack/androidx/releases/compose-kotlin . Publication
of each pinned plugin/BOM was checked in Google Maven/Maven Central metadata.

CLI archive retrieved from official Android Studio page:
`commandlinetools-win-15859902_latest.zip`. Gradle archive SHA-256 verified against
the official distribution checksum. SDK is ignored in `.tools/android-sdk`.
No machine-wide PATH, execution policy or Android settings changed.

Build: `./gradlew.bat test assembleDemoDebug assembleConnectedDebug lintDemoDebug`.
Additional boundary check: `python scripts/check_boundaries.py`.
Reports: each module's `build/reports/tests/`; APKs: `app/build/outputs/apk/`.

Owner phone attached after I00: OnePlus CPH2661, Android 16 / API 36,
security patch 2026-09-01, Asia/Kolkata. USB debugging authorized by owner.
Device identifier is deliberately not included in tracked evidence.
Room revision close/reopen smoke passed on phone during I01. UI smoke failed
while the phone screen was off; rerun pending. Live source comparisons pending. No credentials
requested. Device/SDK installation results are recorded in progress/evidence.
