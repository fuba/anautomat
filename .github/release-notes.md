anautomat 1.2.0 adds a shared workspace for execution records and reusable Starlark macros.

- The selected save folder now contains `runs/<timestamp + Goal summary>/log.txt` and `steps/` with exact source, JSON input/results, validated macro plans and execution/cancellation records.
- `macros/` holds reusable `.star` programs. Ask anautomat to save a program with a new name, list saved programs, or read one for a later Goal. Existing names cannot be overwritten by library tools. Nothing runs automatically.
- Existing folder settings are retained. Old root-level logs remain intact; the current private log is exported into the new layout and continues to recover across restart.
- Library access stays in the app process under the selected Android folder grant. The isolated Starlark interpreter gains no file/network or Android APIs. Paths/URIs, invalid Unicode, oversized sources and ambiguous documents are rejected. Pause cancels queued library work and stale callbacks cannot continue a Goal.
- English and Japanese folder controls explain the layout. Disconnecting the folder disables shared storage without deleting files.

Workspace files are readable text/JSON and may contain code, input and screen-derived data. Exports require an accessible selected folder; interrupted providers can leave partial files. Already-started writes or dispatched device operations may finish after pause.

Install over the production APK or use the app's Updates card to retain sign-in and settings. The release pipeline runs the existing pinned-dependency/cooldown policy, security scans, tests, signing and build-provenance attestation. See docs/automation.md for the layout and API limits.
