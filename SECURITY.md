# Security policy

Please report vulnerabilities through this repository's **Security → Report a vulnerability** page. Do not include access tokens, credentials, screenshots containing personal information, or private logs in public issues.

The latest release is supported. This is an experimental device-control application. Android accessibility access is broad: grant it only on a device you intend to automate.

## Application boundaries

- OAuth uses a device-local loopback callback, PKCE S256, state and nonce checks, and RS256 identity verification. Tokens are encrypted by Android Keystore in app-private storage; backups and cleartext HTTP requests are disabled.
- HTTPS requests are restricted to the official authentication and API hosts. Screenshots, visible UI text, installed-app names, the goal and action history are transmitted to OpenAI only during execution.
- Screens are treated as untrusted input in the planner prompt. Sensitive actions require explicit goal authorization. This is a model instruction, not a complete policy enforcement or approval system.
- Pause/stop invalidates outstanding model responses. Gestures already dispatched to Android may finish.
- Local history is encrypted. User-selected exported log files are plain UTF-8 text and can contain goals, visible-state descriptions and public reasoning summaries. Raw private reasoning and authentication tokens are not logged.

### TLS and the certificate-pinning advisory

The app verifies HTTPS certificates and hostnames using Android's system trust store and restricts requests to `auth.openai.com` and `api.openai.com`. It does not replace certificate verification, accept user-installed CAs, or follow redirects. Certificate/public-key pinning is deliberately absent: this project does not control OpenAI's certificate rotation or have provider-managed backup pins. Android advises against routine pinning because certificate changes can strand installed clients ([Android guidance](https://developer.android.com/privacy-and-security/security-ssl#Pinning)). CodeQL consequently reports `java/android/missing-certificate-pinning` as a medium advisory; it remains visible for review. High-severity findings still block release publication.

## CI supply chain

`security/dependencies.lock.json` records exact versions, full action commit SHAs, tool checksums and publication metadata. CI requires **seven complete days** after publication before downloading or executing dependencies. It rechecks upstream release timestamps, Git tag resolution, asset digests and asset modification times, and fails closed when metadata is missing or unavailable. A cached download is still age-checked and hash-checked before use.

Explicit API rate limits and temporary upstream service failures receive bounded retries. Permission denials, missing metadata, young releases and integrity failures do not bypass validation; a persistent failure stops the workflow.

The checkout action receives a separate pre-execution age/tag check before repository code is available. All other actions are checked after checkout and before execution; the attestation action's nested action is checked too. Gitleaks, actionlint, CodeQL and the test-only JSON library are pinned and verified before execution. The CodeQL bundle is supplied explicitly to prevent an implicit latest-version download.

The Android app has no bundled third-party libraries. JVM tests use the locked `org.json:json` artifact. CI checks that artifact against GitHub's advisory database. Gitleaks scans full Git history; actionlint and repository policy checks cover workflow configuration; CodeQL analyzes Java, Python and Actions and blocks high-severity findings. Checks run on pull requests, main-branch pushes, release tags and a weekly schedule. Failures prevent release publication.

Android SDK platform 36/build-tools 36.0.0 and Java 21 are taken from the GitHub-hosted Windows runner image. CI never invokes a package manager to fetch a current SDK or JDK. The runner image and hosted action service remain trusted infrastructure; the seven-day download gate does not control GitHub's own runner-image updates. The cooldown reduces exposure to newly published packages but does not prove that an older dependency is safe.

Release keys are stored in GitHub Actions secrets and are exposed only to the release signing step after checks succeed. Pull-request jobs receive no signing secrets. Tags should be created from reviewed source. Release APKs carry SHA-256 checksums and GitHub provenance attestations.

## In-app release updates

The update checker contacts GitHub's public API without account credentials. Its persisted, network-constrained JobScheduler task needs `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`; only Android may bind the exported job service through `BIND_JOB_SERVICE`. `REQUEST_INSTALL_PACKAGES` enables a user-requested system installer, never silent installation. The APK content provider is non-exported and read-only with a single exact path and temporary URI grants.

Release metadata and downloads are untrusted input: stable semantic versions, exact repository asset URLs, HTTPS redirect host allowlisting, bounded transfers, SHA-256, package identity, monotonic version code and identical signing certificates are checked before installation. The checksum alone is not publisher authentication; the installed signing identity and Android package installer provide that boundary. New third-party libraries were not introduced; existing CI dependency cooldown and pinned-action policies still apply.
