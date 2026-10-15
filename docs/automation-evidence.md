# On-device Starlark and macro evidence

A real, signed-in Android emulator run completed the task on **2026-10-16, 00:48 JST** using the v1.1.0 implementation. The agent generated and executed Starlark, filled a native Android field, clicked Verify, inspected a fresh screenshot and finished in **two model steps**. The two device operations had no intervening model request.

The target was a separate offline test Activity, `dev.androiduse.tests/dev.androiduse.AutomationFixture`. The Goal authorized only this screen and requested the sum of squares from 1 through 100. Inference used the app's normal ChatGPT connection. No PC-side program computed the answer or performed a Goal operation after the initial Play click.

## Generated program

This is the exact source recorded by the app:

```python
def main():
    total = 0
    for n in range(1, 101):
        total += n * n
    ui.fill("Result", str(total))
    ui.tap("Verify")
    return total
```

The interpreter returned `338350`. anautomat then executed the queued `fill` and `tap_target` operations under the fixture's package guard. The second model step observed `PASS: 338350` before returning `done`.

## Selected log events

Times below are JST. These are selected, unedited events from this controlled test's configured log export; the source is shown separately above.

```text
10-16 00:48:02  Step 1 • Observing screen
10-16 00:48:10  run_code • Calculate the sum of squares in Starlark and submit it using the exact test-screen labels.
10-16 00:48:10  Running Starlark
10-16 00:48:11  Result:338350
10-16 00:48:11  Macro 1/2 • fill
10-16 00:48:11  Macro 2/2 • tap_target
10-16 00:48:11  Macro completed: 2 actions
10-16 00:48:11  Step 2 • Observing screen
10-16 00:48:16  done • Calculated the sum of squares using Starlark and verified the test screen shows PASS: 338350.
10-16 00:48:16  Completed: Calculated the sum of squares using Starlark and verified the test screen shows PASS: 338350.
```

## Screen after completion

![The Android fixture shows the computed value and PASS: 338350](automation-test.png)

The screenshot contains only the test fixture and the floating control. The test APK is not included in the production APK. The original saved Goal was restored after recording the evidence.

## Additional checks

- 119 JVM protocol/authentication/response/usage checks and 42 host JSON, guarded-target and macro ordering/cancellation checks passed.
- Real Starlark execution tests cover state, readonly inputs, queued operation copies, numeric precision, Unicode, unavailable APIs, recursion/loop restrictions, computation quotas and bounded/cyclic output. A 10-second JSON conversion fuzz run exercised 65,776 inputs.
- 18 security-gate regression tests passed, including Go module/checksum-database pinning and ELF ABI/hardening failures. Publication age, integrity metadata and GitHub advisory checks passed for the new dependencies and Go toolchain.
- Native Android tests passed for the isolated service, UTF-8 JNI boundary including emoji and NUL, unavailable host APIs, computation/memory pressure, child recovery, cancellation, real label-based fill/click operations and pause without replay. Existing Keystore, GOAL synchronization, locale, retry, controls, ticker, SAF export and updater tests also passed.
- All four APK ABIs were built and checked for 16 KiB alignment, NX stack, no writable/executable segments, no text relocations and full RELRO. Device execution was tested on the x86_64 Android 36.1 emulator; the other ABIs were build-validated.

The interpreter runs in a disposable process. Go's memory target is soft and PSS is sampled, so these are not strict resident-memory caps. Device batches are not transactions: already dispatched operations may finish after pause, and completed effects cannot be rolled back. See [APIs, architecture and limits](automation.md).
