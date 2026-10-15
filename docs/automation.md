# Starlark execution and macros

anautomat writes short Starlark programs on the Android device and executes batches of device operations without a model request between each operation. The APK contains the interpreter; no PC bridge, on-device compiler or runtime download is needed.

## Program

`run_code` takes a Starlark file in `code` and an optional JSON object encoded as a string in `input`. Define `main()` and return a JSON-compatible value. Use ordinary Starlark indentation, functions, loops, comprehensions, strings, lists and dictionaries.

```python
def main():
    total = 0
    for i in range(1, 101):
        total += i * i

    ui.fill("Result", str(total))
    ui.tap("Verify")
    state["sum_of_squares"] = total
    return total
```

The example needs a current screen containing one editable control with the hint `Result` and one visible clickable control with the exact text `Verify`. UI calls queue an operation plan; they do not perform operations while the program is running. The parent validates the entire plan after successful execution and then runs it as a macro. No fresh screen observations are available between operations in that plan. A script failure, invalid result, invalid state or invalid operation record discards the whole plan and state changes. State is committed after program and plan validation, before device execution; it does not attest that the subsequent batch succeeded.

| Value or function | Purpose |
| --- | --- |
| `input` | Read-only input dictionary; defaults to `{}` |
| `screen` | Read-only observation dictionary: Goal, package, screen size, UI nodes and launchable apps; no image or authentication credentials |
| `state` | Mutable dictionary retained within the current Goal; reset for a new Goal |
| `log(value)` | JSON-compatible value or string to the bounded log |
| `print(...)` | Standard Starlark print, routed to the same bounded log |
| `macro([action, ...])` | Queue raw macro action dictionaries; at most 32 operations across all API calls |

Return strings, booleans, `None`, finite numbers, lists/tuples and dictionaries with string keys. Integers returned in JSON must fit in a signed 64-bit value; use `str(value)` for larger integers. Functions, cycles, non-finite floats and oversized/nested output are rejected. Queued raw actions are copied immediately, so mutating a source dictionary cannot change an already queued operation.

## Device API

| Call | Operation |
| --- | --- |
| `ui.fill(target, value)` | Replace text in one editable control, without needing a preliminary tap |
| `ui.tap(target)` | Click one labelled control, or its nearest clickable ancestor |
| `ui.tap_at(x, y)` | Tap screenshot pixel coordinates |
| `ui.long_press_at(x, y)` | Long press screenshot pixel coordinates |
| `ui.swipe(x, y, x2, y2)` | Swipe between screenshot pixel coordinates |
| `ui.text(value)` | Replace the focused editable field |
| `ui.input_text(value)` | Append through the input method; Android 13+ and a valid input connection required |
| `ui.enter()` | Send Enter through the input method |
| `ui.launch(package)` | Launch an installed app |
| `ui.back()`, `ui.home()`, `ui.recents()` | Android navigation |
| `ui.notifications()`, `ui.quick_settings()` | Open the corresponding Android panel |
| `ui.wait(delay_ms)` | Wait before the next operation |

All calls accept `app="package.name"` and `expect_text="literal visible text"`. Calls other than `wait` also accept `delay_ms=0..2000`, the settling time **after** the operation. Defaults are 100 ms, or 700 ms for `launch`.

`app` defaults to `screen["package"]` independently for every call. After an app launch or a cross-app transition, specify the destination package explicitly for subsequent operations. The program cannot assume that a queued launch has already completed.

```python
def main():
    ui.launch("com.example.notes")
    ui.tap("New note", app="com.example.notes", delay_ms=400)
    ui.fill("Title", "Build notes", app="com.example.notes")
    return "note prepared"
```

Only use selectors and app packages from actual observations. Label selectors match exact visible text, accessibility description or input hint. A selector beginning with `id:` matches an accessibility resource ID, such as `id:com.example.notes:id/title`. Lookup occurs immediately before execution, requires a unique match and fails closed on an oversized UI tree. Hidden controls do not match; `fill` requires an enabled editable control. Use `tap_at` when the observed screen does not expose an unambiguous label. Coordinates must come from the current screenshot.

The host independently checks every touch/text operation's expected app package. Guards are checked by the macro executor and again immediately before dispatch, including after hiding the floating menu. Missing targets, multiple matches, disabled controls, rejected Android actions or changed guards stop the remaining batch.

## Macro lifecycle

The planner can also use `type: "macro"` with an `actions` array directly. Items support tap, long press, swipe, text replacement/input, Enter, app launch, navigation, notification/quick-settings panels and wait. The label-based forms are `tap_target` and `fill`, with `target` holding the selector. Tools and nested macros are forbidden inside a macro.

The complete batch is validated before its first operation. Gestures run sequentially and wait for Android's completion callback. Touch and text operations require `expect_package`; `expect_text` optionally checks literal visible text or descriptions. Use settling delays for transitions and small batches when the next screen is uncertain.

Pause, Stop, a failed guard, a failed action or a timeout discards remaining operations. History records the completed count and whether an operation was in flight. Already dispatched Android gestures may finish. Resuming captures a new screen and asks the planner for a fresh action; an old batch is never resumed automatically. Completed operations are not rolled back.

## Limits and isolation

| Resource | Limit |
| --- | --- |
| Starlark source | 32,768 UTF-8 bytes |
| JSON input / observation / state | 16,384 / 131,072 / 8,192 UTF-8 bytes |
| Tool result / logs | 65,536 / 8,192 UTF-8 bytes |
| JSON nesting | 32 levels, also checked before parsing results in the app process |
| Interpreter computation | 100,000 Starlark steps; 2-second cancellation deadline including parsing |
| Android process | 8-second startup deadline; 2.5-second execution watchdog |
| Parent-side deadline | 15 seconds including binding and startup |
| Macro | 32 operations; 15 seconds planned gesture/delay time; 20 seconds overall |
| Per-operation delay | 0–2,000 ms |

Each invocation binds a fresh non-exported Android `isolatedProcess` service. The Go implementation of Starlark receives only converted Starlark values and explicitly registered host functions. The JNI boundary accepts and returns UTF-8 JSON byte arrays; no Java/Android object references or callbacks enter the interpreter. Source is interpreted in memory. Java compilation, bytecode API filtering, runtime DEX conversion and dynamic class loading have been removed.

Imports/module loading, reflection, Java/Android APIs, direct filesystem/network access, the system clock, threads, processes and native calls are unavailable to scripts. Recursion, `while` and top-level control statements remain disabled. These are language/API restrictions, not string-based checks for forbidden words.

A computation-step limit does not bound the cost of one builtin operation. Execution uses at most two Go scheduler processors. Go's 48 MiB runtime memory limit is a **soft GC target**. A separate Android watchdog samples process PSS growth every 50 ms and kills the disposable process above 80 MiB growth. Neither is a strict resident-memory cap; an allocation can exceed a threshold before the next sample. The watchdog stays outside the Go interpreter, and the parent handles process death, deadlines and cancellation. Bindings are released on every completion/cancellation and processes are not reused.

The parent rechecks JSON bounds, state and the complete macro independently. A successful sandbox result cannot bypass the operation validator. Device operations still use the app's accessibility capabilities and require authorization by the user's Goal. Model instructions about sensitive actions are not a complete authorization enforcement system.

Disable Starlark execution in Settings to remove `run_code` and the library tools from the planner's schema. Disabling it during an invocation pauses the Goal and cancels the process. Macro and ordinary screen controls remain available.

Generated source, logs and results appear in bounded encrypted app history and configured workspace exports. Exports can contain source and visible-screen data. Planner context retains up to 40 events / 256 KiB plus current state. Authentication credentials are never passed to the interpreter.

## Workspace and reusable macros

Choose **Workspace folder** in the app. The existing folder selection is retained on upgrade. anautomat uses Android's persisted Storage Access Framework grant; it does not request broad storage permission. The layout is:

```text
<selected folder>/
  runs/
    2026-10-16_12-30-00-123_Check display_a1b2c3d4/
      log.txt
      steps/
        step-0003-9f8e7d6c.star
        step-0003-9f8e7d6c.input.json
        step-0003-9f8e7d6c.result.json
        step-0003-9f8e7d6c.macro.json
        step-0003-9f8e7d6c.execution.json
  macros/
    check_display.star
```

Run names contain the start time in Asia/Tokyo, a sanitized Goal summary and a random suffix. `log.txt` is updated throughout the Goal; pause, resume and continuation keep the same run. Original root-level `.txt` exports remain intact. A pre-upgrade current archive is copied into the new layout when next exported; historical files are not moved or deleted. Encrypted private snapshots continue to recover the current log after app restart.

Source and input are queued for export before sandbox invocation. A successful result records returned data, state, logs and queued actions; failed/cancelled invocations record their status when the callback or cancellation is handled. A generated macro has a separate validated plan and execution record, including partial completion/cancellation. A completed program result does **not** mean the macro completed: check `.execution.json`. Standalone macro batches also produce plan/execution files. Source preserves exact Unicode, indentation and blank lines. These artifacts are written on an ordered background worker. They are exported only while a workspace is selected; unavailable providers or process termination can leave an incomplete export. The private log remains available, but older sidecars are not reconstructed when selecting a folder later.

The planner can use three app-side tools while Starlark execution is enabled:

| Tool | Arguments | Behavior |
| --- | --- | --- |
| `list_macros` | None | Sorted saved `.star` names, at most 256 |
| `read_macro` | `name` | Read one UTF-8 program, at most 32,768 bytes |
| `save_macro` | `name`, `code` | Create a new reusable program; existing names are rejected |

Names match `[a-z0-9][a-z0-9_-]{0,63}.star`, for example `check_display.star`. Paths, parent traversal, URIs and malformed Unicode are rejected. The app lists at most 4,096 documents per directory and rejects duplicate names, document type conflicts and virtual documents when opening a target. Large or invalid externally edited programs fail before execution. Existing macros may be managed with a file manager; anautomat does not overwrite or delete library files.

For example, ask: “Save this as check_display.star for reuse,” then in a later Goal: “Read check_display.star and use it to check the display.” The planner reads and inspects the source against the new Goal/current screen, then calls the ordinary `run_code` tool with that source and suitable JSON input. Saved files never execute automatically, and cannot authorize operations. The isolated interpreter still has **no filesystem access**, URI, Android object or file callback. Reading/saving files happens in the app process under the selected tree. A pause cancels queued storage work; a provider write already in progress may finish, while its stale callback cannot continue the Goal.

**Disconnect folder** disables exports and library access without deleting files. Local history remains encrypted; workspace logs, programs, inputs and results are ordinary readable files. Do not store credentials in reusable programs or input data.

## Build and validation

`security/go.lock.json` records exact module versions, upstream publication times, archive/module SHA-256 hashes and Go checksum-database records. The pinned Go SDK is covered by `security/dependencies.lock.json`. Downloads require the existing seven-day cooldown and advisory checks. Go builds use a local file proxy, a local-only toolchain and disabled VCS resolution. SDK/NDK packages must already be installed. The Go SDK archive remains unchanged. A repository-pinned source patch corrects two pointer format specifiers in the Android cgo thread local storage diagnostic. The patch manifest and the complete source file before/after application have SHA-256 pins; unexpected source, context or output fails the build. The C/C++ high-severity gate remains enabled. Third-party notices are included in the app.

`scripts/test.ps1` runs the protocol/macro tests plus real Starlark programs, rejected host access, quotas, immutable inputs, JSON cycles/alias expansion and Unicode checks. With Starlark enabled and a test workspace selected in the emulator, `scripts/test-device.ps1` checks the isolated process, native UTF-8 boundary, execution/memory pressure, cancellation, real label-based fill/click operations and pause without replay. It also exercises real SAF migration/restart, exact source sidecars, invalid/oversize external files, rejected overwrites, cancelled/disabled storage requests and saved-program reuse across separate Goals. Test-owned runs and library files are removed afterward.

The opt-in live model fixture is an offline test Activity, never packaged in anautomat. `scripts/test-device.ps1 -PrepareGoal` preserves the user's draft and configures a sum-of-squares Goal. Open `dev.androiduse.tests/dev.androiduse.AutomationFixture` and start that Goal through the floating controls. The expected result is `PASS: 338350`. Run `scripts/test-device.ps1 -RestoreGoal` afterward. The task authorizes only the fixture screen; its planner uses the normal ChatGPT connection.

Primary references: [Starlark language](https://github.com/bazelbuild/starlark), [Go implementation and resource-limit caveats](https://github.com/google/starlark-go), [embedding API](https://pkg.go.dev/go.starlark.net/starlark).
