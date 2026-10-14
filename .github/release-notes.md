Fix APK version stamping and validate the requested version after every build. Version 0.1.3 correctly reports versionCode 1003; the earlier 0.1.2 artifact retained the old manifest version.

This release adds native Android input-method actions for terminals and other custom input fields on Android 13 or later. The regular editable-field replacement action remains available on Android 11+.

The demo shows one goal installing Python in Termux, generating a Mandelbrot explorer, serving it on localhost, and verifying zoom/reset in Chrome. See the README for the exact goal, generated source, edited video and full recording.

Android-only goal execution with ChatGPT sign-in, a floating control menu, voice input, pause/resume, step-budget continuation and automatic log export. English and Japanese interfaces are included.

Download **anautomat.apk** below and install it on Android 11 or later. Enable the app in Accessibility settings, then complete **Continue with ChatGPT** in your browser. No PC bridge or API key is needed after installation.

This release is signed with the project's persistent release key. SHA256SUMS and a GitHub build provenance attestation are provided. Development APKs use a different signing certificate and cannot be upgraded in place to release APKs.

The source, tests, dependency cooldown policy, secret scan, workflow lint and CodeQL checks ran before the release build. Goal execution sends screenshots and visible text directly to OpenAI. Exported logs are plain UTF-8 text in the folder you select.
