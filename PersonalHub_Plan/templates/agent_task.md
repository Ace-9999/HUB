# Agent work order

Copy and fill this for one bounded task. Attach the repository, not credentials.

```text
Project: PersonalHub Android
Read: AGENTS.md, README.md, SRS.md, docs/architecture.md,
      docs/increments.md, docs/verification.md, docs/ui_design.md,
      project_progress.md
Active increment: <Ixx>
Task: <Ixx-Tn and concrete outcome>
Requirement IDs: <IDs>
Base branch/commit: <verified ref or unavailable>
Last verified checkpoint: <ref/evidence>
Allowed modules/files: <paths>
Dependencies already verified: <evidence>
Do not change: <boundaries>
Acceptance: <observable cases from plan>
Required checks: <commands/device script>
Device-only checks: <owner action and evidence needed>
Expected deliverables: <code/tests/APK/doc updates>
Rollback: <code/schema/data approach>

Implement only this task, verify it, and update project_progress.md.
Record all not-run or blocked checks truthfully. Do not begin the next
dependent task if this gate fails. End with changed paths, test evidence,
remaining risks, and the exact next action.
```

## Example, once the foundation exists

```text
Active increment: I05
Task: I05-T2 implement attendance target calculations and view state
Requirement IDs: COL-09, SYS-03
Allowed modules/files: college API/domain calculation code,
                      feature college attendance screen and related tests
Acceptance: 27/36 at 75% => 0 safe absences and 0 required classes;
            24/36 => 12 consecutive classes required;
            missing counts => unavailable, not 0%; 100% target handled
Required checks: pure calculation suite, attendance UI tests, existing
                 timetable/auth smoke checks
Do not change: VTOP login flow, database unrelated to attendance, gym/focus
Rollback: revert this task's code commit; no schema migration expected
```
