# I00 evidence ? 12 November 2026

Requirements: NFR-01, NFR-09, NFR-11?12.

PASS: Gradle 8.13 --version with Oracle JDK 21.0.9.
PASS: Gradle wrapper generation, exit 0, 19s.
PASS: API 36/build-tools 35.0.0/platform-tools installation, exit 0.
PASS: published dependency metadata and pinned source hashes (ADR-001).
NOT RUN: Android application assembly/tests; app not created until I01.
PENDING: adb has no attached devices, phone/OS/browser/app versions unavailable.

Rollback: revert this baseline; no user data/schema. Next: I01 foundation.
