# Shipped asset manifest (GYM-03, NFR provenance)

Every bundled visual/text asset and its clearance. Reference screenshots under
`PersonalHub_Plan/assets/references/` are design references only and are NOT shipped.

| Asset | Path | Source / author | Licence | Status |
|---|---|---|---|---|
| Archivo Black font | core/designsystem/src/main/res/font/archivo_black.ttf | google/fonts (Omnibus-Type), sha256 dd9a89a0… | SIL OFL 1.1 (docs/licenses) | Cleared |
| Space Grotesk font | core/designsystem/src/main/res/font/space_grotesk.ttf | google/fonts (Florian Karsten), sha256 acad6de1… | SIL OFL 1.1 (docs/licenses) | Cleared |
| App icon / splash | app/src/main/res/drawable/ic_launcher_*.xml | Original, drawn for PersonalHub (four tiles) | Project | Cleared |
| UI glyphs | core/designsystem/.../HubIcons.kt | Material-style path data | Apache-2.0 (Material icons) | Cleared |
| Exercise catalog text | training/api/.../Catalog.kt | Original cues written for PersonalHub; generic movement names | Project | Cleared |
| Body map v1 | feature/training/.../BodyMap.kt | Original programmatic shapes | Project | Cleared |
| Exercise photos (92 files, 2 frames x 46 movements) | feature/training/src/main/assets/exercises/ | free-exercise-db (github.com/yuhonas/free-exercise-db), restructured from wrkout/exercises.json; per-file source id + sha256 in docs/exercise_media_manifest.json | Unlicense (public-domain dedication; GitHub licence API: Unlicense for both repos, retrieved 14 Nov 2026) | Cleared; original photographer not named by the datasets |

No Hevy, VITals or other proprietary artwork, animations or text are bundled.
