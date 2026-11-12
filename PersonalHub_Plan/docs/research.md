# Source audit and feasibility notes

Reviewed 12 November 2026. This is source/documentation research, not a live sign-in or device test. External sites and apps can change. Revalidate during I00/I02 and whenever a source adapter changes.

## 1. VTOP source audit

Repository: [therealsujitk/android-vtop-chennai](https://github.com/therealsujitk/android-vtop-chennai). Inspected default branch `master` pinned to **`4d838ae79ef8452a7981d4284a4e319615f7d4b9`**. Main language Java. README declares GPL version 3 or later; preserve applicable license/copyright notices if adapting code. The selected GitHub integration was used for read-only source inspection. No issues/PRs/comments or remote changes were created.

| Source, pinned links | Verified observation | Plan consequence |
|---|---|---|
| [README](https://github.com/therealsujitk/android-vtop-chennai/blob/4d838ae79ef8452a7981d4284a4e319615f7d4b9/README.md) | VTOP/Moodle app, timetable/attendance/marks, light/dark theming, GPL notice | Treat as connector reference; exclude unrelated Moodle features from baseline |
| [VTOPService.java](https://github.com/therealsujitk/android-vtop-chennai/blob/4d838ae79ef8452a7981d4284a4e319615f7d4b9/app/src/main/java/tk/therealsuji/vtopchennai/services/VTOPService.java) | `createWebView`, page state detection, JS extraction and sequential data downloads | Isolate integration; do not copy one large service into new UI |
| [VTOPHelper.java](https://github.com/therealsujitk/android-vtop-chennai/blob/4d838ae79ef8452a7981d4284a4e319615f7d4b9/app/src/main/java/tk/therealsuji/vtopchennai/helpers/VTOPHelper.java) | Callback-driven default CAPTCHA/reCAPTCHA and semester selection | Visible challenge/selection states, cancel/recovery |
| [LoginActivity.java](https://github.com/therealsujitk/android-vtop-chennai/blob/4d838ae79ef8452a7981d4284a4e319615f7d4b9/app/src/main/java/tk/therealsuji/vtopchennai/activities/LoginActivity.java) | Credentials are stored through encrypted preference access; helper starts sync | New vault boundary and remember option; no agent-side password handling |
| Repository tree | Existing Room DAOs/models for courses, attendance, marks, timetable, exams etc. | Typed domain models are feasible; new schema must preserve account/semester/attempt identity |

Observed flow: WebView loads portal → determines landing/login/home → prelogin setup/login → interactive challenge → authenticated content → semester discovery/selection → summary and datasets → local persistence. The connector uses in-page portal requests and DOMParser, not an official documented JSON API. The implementation includes synchronous JS AJAX, a native bridge and remote configuration/user-agent handling. These are source observations to review, not instructions to retain those patterns.

### Observed method and request inventory

Paths are candidates seen in the pinned source. They are relative to the active portal context and may no longer work; do not construct a production client merely by concatenating them with a guessed host.

| Method | Request path observed | Availability interpretation |
|---|---|---|
| `openSignIn` / `signIn` | `/vtop/prelogin/setup`, `/vtop/login` | Auth/session flow in source; live verification required |
| `getSemesters` | `academics/common/StudentTimeTableChn` | Semester discovery |
| `getName` | `studentsRecord/StudentProfileAllView` | Profile extraction exists |
| `getCreditsCGPA` | `examinations/examGradeView/StudentGradeHistory` | CGPA/credit summary, denominator semantics need audit |
| `downloadCourses` / `downloadTimetable` | `processViewTimeTable` | Course and timetable parser exists |
| `downloadAttendance` | `processViewStudentAttendance` | Attendance parser exists |
| `downloadMarks` | `examinations/doStudentMarkView` | Assessment parser exists |
| `downloadGrades` | `examinations/examGradeView/doStudentGradeView` | Selected-semester grade parser exists; complete historical retention is new work |
| `downloadExamSchedule` | Parser method exists | Exam data capability observed; avoid hardcoding an uninspected request path |
| `downloadProctor` / `downloadDeanHOD` | `proctor/viewProctorDetails`, `hrms/viewHodDeanDetails` | Optional later private fields; not baseline automatic import |
| `downloadSpotlight` / `downloadReceipts` | `home`, `p2p/getReceiptsApplno` | Optional announcements/receipts, excluded from baseline |
| `checkDues` | `p2p/Payments` | Optional later feature; not needed for core academics |

The inspected `getCreditsCGPA` uses an earned-credit heading. This does not establish the correct denominator for every prediction/repeat policy. The inspected service has selected-semester grade extraction and GPA handling; it does not by itself prove a complete historical normalized dataset or an academic-calendar feed. I02 must establish those on the owner's portal.

## 2. Android platform sources

| Primary source | Verified boundary | Engineering decision |
|---|---|---|
| [AccessibilityEvent](https://developer.android.com/reference/kotlin/android/view/accessibility/AccessibilityEvent) | Scroll events and scroll-specific properties exist; source UI state matters | Estimate scrolling from exposed signals, validate per adapter; not an exact item counter |
| [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) | Usage history requires special access for most methods | Package totals/reconciliation; not automatic text/video classification |
| [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/views/service) and [API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) | Services can request global Home/Back actions | Prototype safe redirects; availability and behavior need device evidence |
| [ActivityManager.killBackgroundProcesses](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)) | Android 14+ limits third-party callers to their own processes | No cross-app force-stop claim |
| [Media projection](https://developer.android.com/media/grow/media-projection) | Session consent and foreground-service requirements apply; modern tokens are session-bound | Optional visible experiment only; no promise of permanent one-time capture permission |
| [App architecture](https://developer.android.com/topic/architecture) | Lifecycle/process changes require structured state ownership | Single Activity, state holders, repository boundaries |
| [Modularization patterns](https://developer.android.com/topic/modularization/patterns) | Cohesive modules and low coupling; repository/public API patterns | Domain-owned APIs/data and isolated privileged integrations |
| [Android Keystore](https://developer.android.com/privacy-and-security/keystore) | Platform-managed nonexportable cryptographic keys | Encrypt secrets; portable backup requires separate password-based design |
| [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) | Inexact alarms may be delayed; exact alarms have conditions | Separate deadline correctness from alert delivery; test the actual permission/OS path |

Continuous vision, if later selected, needs explicit session UX, a stop control and a separate battery budget. Accessibility is also not a guarantee that arbitrary custom views expose enough information. Category accuracy, hidden browser addresses, third-party app changes, OEM service termination and split-screen behavior remain experimental until measured. Permission does not grant root/device-owner power.

## 3. Training interaction and media research

Official Hevy descriptions reviewed: [routines](https://www.hevyapp.com/features/gym-routines/), [logging](https://www.hevyapp.com/features/track-workouts/), [exercise library](https://www.hevyapp.com/features/exercise-library/), and [performance statistics](https://www.hevyapp.com/features/gym-performance/). They support the intended interaction model: ordered reusable exercises/sets, completion/rest controls and range-based charts/body analytics. This project specifies its own calculations, visual tokens and local data model; it does not import social/paywall structures or imply affiliation.

Candidate open exercise source: [wger documentation](https://wger.readthedocs.io/en/latest/). Its project offers a REST API, with different licensing for application code, initial exercise data and potentially individual images. The live site API page presented a bot challenge during research, so no dataset/animation catalog was audited. Do not claim it supplies the exact Hevy GIFs. I07 must inspect actual per-file metadata/rights, quality and availability; original text/static drawings and custom exercises provide a viable fallback. Other sources require the same per-file audit.

For every shipped asset keep source URL, author, license identifier/full notice reference, retrieval date, content hash, modifications and clearance status. Screenshot anatomy illustrations are reference-only. Create a simple original region-based body map or obtain a clearly licensed one.

## 4. Evidence level and unresolved assumptions

| Claim | Evidence level now |
|---|---|
| Reference repo uses WebView/JS for VTOP data | Confirmed by pinned source inspection |
| Repo's exact flow works with owner's current account/portal | Not tested |
| Calendar can be auto-scraped | Not established |
| Owner's historical GPA denominator/repeat rules known | Not established |
| All six native/web target surfaces can be classified reliably | Not established; device matrix pending |
| A normal app can forcibly terminate other apps | Not a baseline capability; redirect design instead |
| Exact Hevy demonstration media is open source | Not established; do not reuse by assumption |
| Proposed Kotlin architecture/performance targets achieved | Not implemented/measured |

All numerical budgets and algorithms in the SRS are project design decisions. They are not performance measurements or Hevy/VIT official rules. The final owner-facing capability matrix must retain this distinction.

## 5. Version 1.1 — Spotify capture and offline backup sources

Reviewed 12 November 2026; no Spotify/device probe has been run.

| Primary source | Observed documentation | Consequence for this plan |
|---|---|---|
| [Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager) | Active-session/controller access can be available through an enabled notification listener; privilege alone cannot be assumed | I14 probes explicit notification access and Spotify-filtered session callbacks; separate from Focus permissions |
| [Android MediaMetadata](https://developer.android.com/reference/android/media/MediaMetadata) | Title/artist/album/artwork fields are defined, including bitmap or URI art | Exposed fields are nullable and source-dependent; an online URI does not itself supply offline image bytes |
| [Spotify recently played](https://developer.spotify.com/documentation/web-api/reference/get-recently-played) | Official authenticated recent-play API with per-request item limit and cursors | Optional supplement only; no assumed complete lifetime history or exact paused/seeked listening duration |
| [Spotify artist](https://developer.spotify.com/documentation/web-api/reference/get-an-artist) | Artist metadata includes image URLs and applicable visual-content attribution/display conditions | Optional permitted online enrichment; no promise that artist portraits are exposed by local playback or automatically available offline; comply with current rules before caching/exporting |
| [Android periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) | Periodic work timing depends on system optimization/constraints | Default 24-hour local JSON backup is best-effort, no network constraint; display last success and catch-up/failure |

The choice of local media sessions is an engineering inference from Android capabilities, not a confirmed statement about the current Spotify client's exact field exposure. Offline capture means observing available local playback metadata, including downloaded tracks if supported; it does not create offline Spotify streaming, prove audibility or access all-device history. I14 must validate source support and current optional API/asset access and retention rules before claiming those capabilities.

Mood categories, qualifying-play thresholds, ranking/artist-time attribution, trend denominators and JSON cadence/retention are project decisions, not medical scales or Spotify/VIT official statistics. Version 1.1 intentionally follows the owner's plain-JSON/offline requirements rather than version 1.0's encrypted-only backup default.
