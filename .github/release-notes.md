anautomat 1.1.0 replaces generated Java with **Starlark** and adds concise device-operation APIs.

```python
def main():
    total = 0
    for n in range(1, 101):
        total += n * n
    ui.fill("Result", str(total))
    ui.tap("Verify")
    return total
```

- Programs run entirely on Android in a fresh permissionless isolated process. The APK bundles the interpreter; on-device Java compilation, runtime DEX conversion and dynamic class loading have been removed. Java snippets must be rewritten in Starlark.
- `ui.fill` and `ui.tap` resolve exact labels, hints, descriptions or resource IDs immediately before execution. Missing, hidden, disabled or ambiguous targets stop the remaining batch. Coordinate, text, launch, navigation and wait APIs are also available.
- UI calls queue a macro. anautomat validates the whole plan after the program succeeds, enforces package/text guards immediately before dispatch and preserves the existing pause/manual-resume behavior. No model call is needed between macro operations.
- Readonly input/screen dictionaries, bounded Goal state and logs are available. Computation-step/time limits, an independent Android watchdog and a separate parent deadline contain failed invocations. Scripts have no direct Java/Android, file/network, reflection, module-loading, thread or process APIs.
- New Go/module downloads retain immutable pins, upstream/checksum-database verification, advisory checks and seven complete days of cooldown. Go and C/C++ CodeQL coverage and native ABI/16 KiB/ELF-hardening checks are included.

The real ChatGPT smoke test generated the program above, calculated 338350, executed two device operations and verified PASS on a fresh screenshot in two model steps. The original saved Goal was restored afterward. Existing and new JVM/native/security tests passed locally; the release pipeline independently builds, scans, signs and attests this APK. See docs/automation.md and docs/automation-evidence.md.

Install over your production release to retain sign-in and settings, or use the app's Updates card. Android still requires installation confirmation. Go's memory target is soft and PSS is sampled; completed device effects cannot be rolled back, and already dispatched operations may finish after pause.
