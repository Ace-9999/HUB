# PersonalHub

Private, local-first Android app for college, training and daily focus.
Native Kotlin and Jetpack Compose; Android 10 (API 29) and newer.

The [SRS](PersonalHub_Plan/SRS.md) and [increment plan](PersonalHub_Plan/docs/increments.md)
define the product. [project_progress.md](project_progress.md) records implemented
capabilities, exact checks, pending device gates and the next task. The original
planning package remains intact under `PersonalHub_Plan/`.

## Development

Use JDK 21 (JVM bytecode target 17). Run `python scripts/bootstrap_toolchain.py`
to install checkout-local Gradle/Android CLI, then install API 36, build tools
35.0.0 and platform-tools with the SDK manager. Accept Android SDK terms locally.
Set `sdk.dir` in ignored `local.properties`, or set `ANDROID_HOME`.

Build and test commands are recorded in [environment.md](docs/environment.md).
No personal credentials, cloud service, subscription or OS monitoring permissions
are needed to build or use local features. This repository does not contain signing
keys or personal data. Device-dependent capabilities are not claimed before testing.
