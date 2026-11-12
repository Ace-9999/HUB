# Local JSON backup, export and restore — version 1.1

Requirements: SYS-02, SYS-05–06, SYS-10–12, HAB-06–09, MUS-10. This supersedes the version 1.0 encrypted-only backup default. No actual app backup has been produced: the app is still a plan.

## 1. Storage and schedule

The live databases/files are always on device. Automatic snapshots are ordinary UTF-8 `.json` files in app-private storage by default. Default cadence is every 24 hours when data has changed, retaining the latest seven daily snapshots; Backup now is always available and produces a snapshot even when unchanged. Expose daily/weekly cadence, retention, location, file size, last success, last attempted run, dirty revision and failed/catch-up status. This is inexact Android work, not an exact midnight timer. It must run without internet and retry eligible missed work without blocking foreground features.

Offer a user-granted on-device folder for additional copies that can survive uninstall. A file picker can also expose remote providers; label the selected provider and require an actually local destination for automatic offline copies. Loss of folder permission reports failure and preserves internal backups. The app never chooses a cloud provider or sends an export itself. Explain that app-private backups are removed with app data/uninstall, while external exports are owner-managed copies.

Write a consistent versioned snapshot of all included domains while relevant writes are paused or a consistent revision is captured. Avoid long main-thread locks; stream large JSON/base64 safely. Write to a temporary internal file, validate the completed file, then atomic rename and retention rotation. Never delete the last-good snapshot before a new verified one exists. External providers may lack atomic operations: create a new complete copy and verify success before rotating prior copies; keep the internal snapshot as recovery authority.

## 2. Full versus review export

| Property | Full backup | Review export |
|---|---|---|
| Purpose | Restore retained personal app state offline | Let the owner choose data for someone to review |
| Included data | All retained domain records/settings, including mood entries/notes and private manual habits, unless owner deliberately configures a labelled subset | Selected date range, modules and fields; sensitive records/notes and college identifiers omitted by default |
| Artwork/media | Owned/permitted offline assets included as base64 by default for a self-contained full snapshot; data-only option records exclusions | Excluded by default; optional selected images only |
| Secrets/grants | Never passwords, cookies, tokens, signing keys or OS grants | Same exclusion |
| Automatic sharing | None | None; preview, create file, owner decides what to do with it |
| Restore | Yes when schema/selection is complete and supported | Not promised; `restorable: false` for aggregated, pseudonymized or incomplete data |

Full backup contains sensitive personal data because the owner requested all records on device. Keep it private by default; an optional encrypted copy can be added, but a readable `.json` must remain available. Device app lock does not encrypt a separately exported plaintext file. Review preview must show selected fields, date range, number of records, sensitive inclusion flags, pseudonymization and media omissions before writing.

## 3. Envelope and portable records

Illustrative empty envelope (no personal data):

```json
{
  "format": "personalhub-json",
  "schemaVersion": 1,
  "exportKind": "full_backup",
  "restorable": true,
  "createdAt": "2026-11-12T09:00:00Z",
  "reportingZone": "Asia/Kolkata",
  "moduleVersions": {"college": 1, "training": 1, "focus": 1, "music": 1},
  "selection": {"modules": ["college", "training", "focus", "music"], "allRetainedRecords": true, "includeSensitive": true, "includeMedia": true},
  "coverage": {"source": "empty_schema_example", "gaps": [], "omissions": []},
  "data": {"college": {}, "training": {}, "focus": {"moods": []}, "music": {}, "settings": {}},
  "assets": []
}
```

This is a shape example, not a validated restore fixture: each real module must provide its exported schema and tests. Version the envelope separately from module database schemas. `moduleVersions` lists only enabled/exported modules; before I14 it does not fabricate an empty Music database. `selection` must explicitly declare subsets/filters; no `restorable: true` for a review summary that omits necessary records. Preserve original provider IDs where available, stable local UUIDs, relationships, units, timestamps, original/reporting zones, method versions, coverage/unknown status and user edits.

Mood entry example shape: `id`, `moodCode`, `occurredAt`, `originalZone`, `createdAt`, `updatedAt`, optional `note`, revision. Serialize mood code rather than relying on an emoji alone; ten same-day entries remain ten rows. Music exports tracks/artists/albums, listening attempts/playing segments, qualification versions, explicit favorites, gaps and allowed artwork metadata. Keep unresolved credit strings intact rather than guessing artist identities on restore.

Asset entries include stable ID, owner/role, MIME, byte length, hash, origin/license/permission metadata and base64 bytes. Avoid temporary content URIs, absolute host paths and expiring online URLs as the only offline representation. Only include assets whose retention/export is permitted; explicit omissions yield placeholders after restore. Never serialize Spotify audio, unrelated notifications, captured screen content or inaccessible app files.

## 4. Restore rules

1. Read only the owner-selected file; bound file size, record counts, nesting depth and decoded asset sizes. Stream parsing where necessary; reject path traversal or file paths supplied by the JSON.
2. Check format/envelope and each selected module schema, value ranges, known enum values, stable IDs, relationships, duplicate/conflicting IDs, timestamp/zone validity and asset hashes. Checksums detect accidental corruption, not a maliciously rewritten file's authenticity.
3. Preview source date, modules, coverage, selection and any incompatible fields. Default full restore replaces selected domains as one operation after a pre-restore snapshot. No silent merging. Review exports are rejected as restores unless a separately documented import format supports them.
4. Stage all selected domain records/files and validate before taking the maintenance lock. Apply with a recoverable cross-module restore journal. On failure/crash recover to a complete previous or complete new state, never half of each.
5. Rebuild derived graphs from authoritative entries/segments, preserving versions where needed. Do not double count an automatic habit by importing its rollup as a manual event. Restored Music records do not trigger a new observed play.
6. Sign in to VTOP again and re-enable OS permissions separately. Restore cannot grant notification/accessibility access, resume an old MediaProjection token or unlock Spotify API access. Existing local data remains readable without those steps.

## 5. Acceptance gate

Run all of this offline: create multiple moods in one day, explicit-zero and missing habit days, a completed workout, usage intervals with a gap and (after I14) Spotify pause/seek/repeat attempts; export full JSON, restore into clean staging and compare records/units/IDs/timestamps/media bytes and derived totals. Test invalid schema, huge/deep JSON, invalid mood code, duplicate IDs, corrupt assets, insufficient storage, denied folder access, interrupted write/restore and retained last-good backup. Verify scoped review export really omits unselected identifiers/private values. See R01/O01/M01/S02 in verification.md.
