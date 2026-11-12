# I01 evidence ? 12 November 2026

Requirements: SYS-01?04, SYS-07?09, NFR-01/02/09?12.

PASS: `python scripts/check_boundaries.py`.
PASS: `python scripts/run_gradle.py test assembleDemoDebug assembleConnectedDebug lintDemoDebug connectedDemoDebugAndroidTest` (API 29 emulator, final palette, 19s, exit 0).
PASS: `connectedDemoDebugAndroidTest :focus:data:connectedDebugAndroidTest` (OnePlus CPH2661, Android 16/API 36, 27s, exit 0).
Three JVM tests, one Compose smoke, one Room persistence test; Compose/Room run on API 29 and API 36.
Earlier failures: JVM bytecode target mismatch, local SDK property lint/cache, Room test close syntax, phone asleep. Fixed/rerun; no failed case relabelled as a pass without rerun.
PENDING: manual TalkBack/200% fonts/landscape, broader themes and performance. CI execution not yet verified.

| APK | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/androidTest/demo/debug/app-demo-debug-androidTest.apk` | 2370930 | `325b7a6d20b8ddc7f973d684031ff9f0f612f734d701e4f6314a132be916a48a` |
| `app/build/outputs/apk/connected/debug/app-connected-debug.apk` | 30706925 | `dbf52367338caf777fe7e5e065ccbf035666e076396803a668a1768edf099c32` |
| `app/build/outputs/apk/demo/debug/app-demo-debug.apk` | 30706893 | `350219ede47fc8bb6f6be5eeb4e5cdf750c4550b14c5f429b0dbbe853714d836` |
