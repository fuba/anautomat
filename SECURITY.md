# Security policy

Please report vulnerabilities through this repository's **Security → Report a vulnerability** page. Do not include access tokens, credentials, screenshots containing personal information, or private logs in public issues.

The latest release is supported. This is an experimental device-control application. Android accessibility access is broad: grant it only on a device you intend to automate.

## Application boundaries

- OAuth uses a device-local loopback callback, PKCE S256, state and nonce checks, and RS256 identity verification. Tokens are encrypted by Android Keystore in app-private storage; backups and cleartext HTTP requests are disabled.
- Authentication and inference requests are restricted to the official authentication and API hosts. Screenshots, visible UI text, installed-app names, the goal and action history are transmitted to OpenAI only during execution.
- Screens are treated as untrusted input in the planner prompt. Sensitive actions require explicit goal authorization. This is a model instruction, not a complete policy enforcement or approval system.
- Pause/stop invalidates outstanding model responses. Gestures already dispatched to Android may finish.
- Local history is encrypted. User-selected workspace files are plain UTF-8 text/JSON and can contain goals, visible-state descriptions, public reasoning summaries, program source, inputs, state, results and macro execution records. Raw private reasoning and authentication tokens are not logged.

### TLS and the certificate-pinning advisory

The app verifies HTTPS certificates and hostnames using Android's system trust store and restricts requests to `auth.openai.com` and `api.openai.com`. It does not replace certificate verification, accept user-installed CAs, or follow redirects. Certificate/public-key pinning is deliberately absent: this project does not control OpenAI's certificate rotation or have provider-managed backup pins. Android advises against routine pinning because certificate changes can strand installed clients ([Android guidance](https://developer.android.com/privacy-and-security/security-ssl#Pinning)). CodeQL consequently reports `java/android/missing-certificate-pinning` as a medium advisory; it remains visible for review. High-severity findings still block release publication.

## CI supply chain

`security/dependencies.lock.json` records exact versions, full action commit SHAs, tool checksums and publication metadata. CI requires **seven complete days** after publication before downloading or executing dependencies. It rechecks upstream release timestamps, Git tag resolution, asset digests and asset modification times, and fails closed when metadata is missing or unavailable. A cached download is still age-checked and hash-checked before use.

Explicit API rate limits and temporary upstream service failures receive bounded retries. Permission denials, missing metadata, young releases and integrity failures do not bypass validation; a persistent failure stops the workflow.

The checkout action receives a separate pre-execution age/tag check before repository code is available. All other actions are checked after checkout and before execution; the attestation action's nested action is checked too. Gitleaks, actionlint, CodeQL and the test-only JSON library are pinned and verified before execution. The CodeQL bundle is supplied explicitly to prevent an implicit latest-version download.

The APK bundles Starlark in Go and its runtime dependencies. `security/go.lock.json` pins module publication times, archive/module hashes and Go checksum-database records; Go SDK pins are in the main dependency lock. Builds use a local file proxy, a local-only toolchain and disabled VCS resolution. JVM tests use the locked `org.json:json` artifact. GitHub advisory audits cover Maven/Go dependencies and the Go standard library/toolchain. Gitleaks scans full Git history; actionlint and repository policy checks cover workflows. CodeQL analyzes Java, Go, the C JNI boundary, Python and Actions and blocks high-severity findings. Checks run on pull requests, main pushes, release tags and a weekly schedule. Failures prevent release publication.

Android SDK platform 36/build-tools 36.0.0, NDK 29.0.14206865 and Java 21 are taken from the GitHub-hosted Windows runner image. CI never invokes a package manager to fetch a current SDK or JDK. The runner image and hosted action service remain trusted infrastructure; the seven-day download gate does not control GitHub's own runner-image updates. The cooldown reduces exposure to newly published packages but does not prove that an older dependency is safe.

Release keys are stored in GitHub Actions secrets and are exposed only to the release signing step after checks succeed. Pull-request jobs receive no signing secrets. Tags should be created from reviewed source. Release APKs carry SHA-256 checksums and GitHub provenance attestations.

## In-app release updates

The update checker contacts GitHub's public API without account credentials. Its persisted, network-constrained JobScheduler task needs `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`; only Android may bind the exported job service through `BIND_JOB_SERVICE`. `REQUEST_INSTALL_PACKAGES` enables a user-requested system installer, never silent installation. The APK content provider is non-exported and read-only with a single exact path and temporary URI grants.

Release metadata and downloads are untrusted input: stable semantic versions, exact repository asset URLs, HTTPS redirect host allowlisting, bounded transfers, SHA-256, package identity, monotonic version code and identical signing certificates are checked before installation. The checksum alone is not publisher authentication; the installed signing identity and Android package installer provide that boundary. The updater uses Android platform APIs; the existing CI dependency cooldown and pinned-action policies still apply.

## Starlark and macros

Starlark runs in a fresh, private Android `isolatedProcess` service. The interpreter receives converted Starlark dictionaries/values and explicitly registered functions, with no Java/Android object references. The JNI boundary accepts and returns bounded UTF-8 JSON byte arrays. Android UID isolation protects app credentials/storage. Scripts cannot import modules, inspect host objects or call direct filesystem, network, clock, thread, process or native APIs. Recursion and `while` remain disabled. Generated Java compilation, runtime DEX conversion and dynamic class loading have been removed.

A 100,000-step computation budget and interpreter cancellation deadline limit normal execution. A separate Android watchdog stays outside the Go interpreter and checks elapsed time and process PSS growth. Go's 48 MiB memory limit is a soft GC target; sampled 80 MiB PSS growth is not a hard RSS cap. A single allocation can exceed the sample threshold. The parent has its own deadline and treats child death, invalid output and cancellation as failures. Processes are disposable and never reused between invocations.

The parent checks output size/nesting before parsing, validates state and validates every macro operation before any starts. Touch/text operations require an expected app package. Guards are rechecked immediately before dispatch, including after hiding overlays. Label selectors must match one visible control; hidden, missing or ambiguous targets fail closed. This does not make device operations transactional: completed operations cannot be rolled back and an in-flight gesture may finish after cancellation. Pause/error never automatically replays a batch.

Interpreter, Go SDK and module downloads retain the same seven-day cooldown, immutable pins, checksum verification and advisory audits. Native libraries are checked for the correct Android ABI, 16 KiB page alignment, a non-executable stack, no writable/executable segments, no text relocations and full RELRO. The Go SDK archive remains unchanged. A repository-pinned source patch corrects two pointer format specifiers in the Android cgo thread local storage diagnostic. The patch manifest and the complete source file before/after application have SHA-256 pins; unexpected source, context or output fails the build. The C/C++ high-severity gate remains enabled. Third-party notices are included in the APK and Settings. See [automation architecture and limits](docs/automation.md).

## Workspace storage

Only the main app process uses the user-selected SAF tree. Starlark has no filesystem access. Library tools accept a bounded logical `.star` name, never a path or URI; they reject traversal, duplicate names, type conflicts, virtual documents and invalid/oversize UTF-8. Saving requires a new name and never deliberately overwrites an existing library file. Directory scans and library counts are bounded. Files are untrusted planner data and are never automatically evaluated. Queued storage checks the Goal generation, execution setting and selected folder before starting; an already-started provider write may finish after pause. Stale callbacks cannot start device operations. A provider failure can leave an incomplete newly created file.

Old root-level log exports are retained. New runs use dedicated directories with random suffixes; current-log migration ignores the old document URI. Workspace exports are not encrypted by anautomat, and should not contain credentials. Users can revoke the folder grant in Android or disconnect it in the app. Storage provider behavior remains part of Android's trust boundary.
