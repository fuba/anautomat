# Java execution and macros

anautomat can write and compile Java on the Android device, use the result in a Goal, and execute a batch of device operations without a model request between each operation. No PC bridge or downloaded runtime is required after installation.

## Java tool

`run_code` takes a Java 8 method body in `code` and an optional JSON object encoded as a string in `input`. The compiler supplies `org.json.*` and `java.util.*` imports and a `SandboxApi ctx` parameter. Return a JSON value; a complete class, imports, lambdas, streams and arbitrary JDK/Android APIs are unsupported.

```java
JSONArray values = ctx.input.getJSONArray("values");
long sum = 0;
for (int i = 0; i < values.length(); i++) {
    sum += values.getLong(i);
}
ctx.state.put("sum", sum);
ctx.log("sum=" + sum);
return new JSONObject().put("sum", sum);
```

| API | Purpose |
| --- | --- |
| `ctx.input` | JSON object supplied to this invocation; defaults to `{}` |
| `ctx.observation` | Current Goal, screen size, app package, UI nodes and launchable apps; no image or credentials |
| `ctx.state` | Mutable JSON object retained within the current Goal; resets for a new Goal |
| `ctx.log(value)` | Bounded log output returned to the planner and displayed in local history |
| `ctx.macro(JSONArray)` | Queue device actions for validation and execution after the program succeeds |

An allowed API list covers basic strings, math, boxed primitives, collections, JSON, big numbers, dates, SHA-256 and Base64. The exact list is in `JavaBytecodePolicy.java`. File/network access, reflection, `Class`/`getClass`, `System`, `Runtime`, threads, Android APIs, native methods, finalizers and dynamic bytecode are rejected. This is a restricted Java environment, not a full Android SDK or JDK.

Java source is compiled with Janino, converted to DEX with D8, and loaded from memory with Android's `InMemoryDexClassLoader`. Compilation and execution both happen in a fresh, non-exported `isolatedProcess` service. Generated classes are checked against the API allowlist before DEX conversion. The generated program receives no Android `Context`, Binder connection or app storage handle. No JavaScript engine is included.

## Code-generated device operations

```java
long sum = 0;
for (int i = 1; i <= 100; i++) sum += (long) i * i;
String pkg = ctx.observation.getString("package");
JSONArray actions = new JSONArray()
    .put(new JSONObject()
        .put("type", "tap").put("x", 180).put("y", 240)
        .put("expect_package", pkg).put("expect_text", "Result"))
    .put(new JSONObject()
        .put("type", "text").put("text", String.valueOf(sum))
        .put("expect_package", pkg));
ctx.macro(actions);
return new JSONObject().put("sumOfSquares", sum);
```

Coordinates must come from the current screenshot. This example demonstrates the API; its coordinates are not a reusable target for other screens. A Java exception, invalid result, quota failure or invalid queued action prevents the batch from starting. State is committed only after successful execution and complete batch validation.

## Macro tool

The planner can also return `type: "macro"` with an `actions` array directly. Macro items use existing primitives: tap, long press, swipe, text replacement, text input, Enter, app launch, navigation, notification/quick-settings panels and wait. They cannot contain another macro, `run_code`, `done` or `blocked`.

The entire batch is validated first. Gestures run sequentially, waiting for Android's completion callback. Every touch or text operation must include `expect_package`. An optional `expect_text` checks literal visible text or accessibility descriptions immediately before the action. `delay_ms` controls settling time after an operation; its default is 100 ms. Use a larger delay for app launches and transitions and small batches when the next screen is uncertain. There is no model observation between macro operations.

Pause, Stop, a failed guard, a failed action or a timeout discards remaining operations. Tool history records the completed count and whether an operation was in flight; already dispatched Android gestures may finish. Resuming captures a new screen and asks the planner for a fresh action. It never resumes an old batch automatically. Operations that already completed are not rolled back.

## Limits and isolation

| Resource | Limit |
| --- | --- |
| Source | 32,768 characters |
| JSON input / state | 16,384 / 8,192 characters |
| Tool result / logs | 65,536 / 8,192 characters |
| JSON nesting | 32 levels, checked before parsing in the app process |
| Compilation / execution | 8 seconds / 2 seconds after compilation |
| Parent-side deadline | 15 seconds including binding and startup |
| Macro | 32 operations; 15 seconds planned gesture/delay time; 20 seconds overall |
| Per-operation delay | 0–2,000 ms |

A service watchdog samples Java heap growth every 50 ms: 160 MiB during compilation and 48 MiB during execution. These are growth thresholds, not strict resident-memory caps; Android's process heap limit still applies and allocations can exceed a threshold before the next sample. Failure affects the disposable service, and the parent also enforces its deadline. Bindings are released on completion and cancellation; processes are not reused between invocations. No shell commands, native libraries or generated code files are loaded by the program.

The sandbox's OS boundary protects app credentials and storage; the bytecode allowlist narrows what programs can request inside that boundary. The parent independently validates returned JSON, state and the full operation batch. A sandbox result cannot bypass the device operation validator. Macro actions still have the app's existing accessibility capabilities and require authorization by the user's Goal; the model's sensitive-action instructions are not a complete authorization enforcement system.

Disable code execution in Settings to remove `run_code` from the model's action schema. Disabling it during a code invocation pauses the Goal and cancels the invocation. Macros and existing screen controls remain available.

Generated source, console output and results appear in the bounded encrypted app log and in configured plain-text log exports. Planner context retains up to 40 events / 256 KiB plus current state. Exports can contain code and visible-screen data; authentication credentials are never passed into the sandbox.

## Validation

`scripts/test.ps1` runs real Java compilation/execution, API-denial tests, JSON bounds and deterministic macro callback/cancellation tests. `scripts/test-device.ps1` also checks isolated-process compilation, DEX loading, forbidden APIs, loop/memory termination, code-to-macro integration and pause without replay on Android. Its fixture Activity is a separate offline test app and is not packaged in anautomat.

For the opt-in live model test, install the test APK, run the `AutomationGoalSetup` instrumentation to preserve the original Goal and configure the test, open `dev.androiduse.tests/dev.androiduse.AutomationFixture`, and start the Goal through the floating controls. The task computes the sum of squares from 1 through 100 in Java and uses a generated macro to input and verify `338350`. Run `AutomationGoalSetup` with `-e restore true` afterward to restore the original Goal. No external communications or file operations are part of the fixture task; the planner uses its normal ChatGPT connection.
