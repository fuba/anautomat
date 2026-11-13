# ianautomat for iPhone

ianautomat runs a goal against the ChatGPT Responses API and saves the resulting notes, checklists, and calculators as native tools. The control center supports pause, resume, stop, step limits, local history, and sharing. Checklists and calculators can be used offline after creation. The goal composer uses the system keyboard, including dictation when enabled. In Shortcuts, ianautomat can open the app or return the latest goal log after device authentication.

iOS requires 17.4 or later. This port uses the iOS app sandbox and public frameworks. Other apps' screens, arbitrary taps, floating controls, and Android's Starlark device APIs are unavailable. A goal that needs them stops with a blocked result. To chain ianautomat with actions from other apps, use the Shortcuts app.

## Account and data

Use **Continue with ChatGPT** and approve ChatGPT plan usage. The app opens an iOS authentication session and accepts the OpenAI loopback callback only on the phone. It verifies the ID token and stores each registration in the device Keychain. Goals, generated tool titles, and requests are sent directly to OpenAI. Tool contents, completed checklist items, and local logs stay in the app's protected storage unless shared through iOS. The app uses Apple's operating-system cryptography and networking frameworks and has no third-party runtime dependencies.

## Build and test

Install Xcode and XcodeGen. Run these from the repository root, using an installed simulator from `xcrun simctl list devices`.

```sh
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/ianautomat.xcodeproj -scheme ianautomat \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath build/ios-test -parallel-testing-enabled NO test
```

The tests cover OAuth state and PKCE, a real loopback TCP callback, RS256 signature and OIDC claims, iOS Keychain storage, streaming completion, tool validation, arithmetic, history, the system authentication prompt, and draft recovery after relaunch. A signed-in ChatGPT session is required to verify real model selection and inference end to end.

## Internal TestFlight

The bundle ID is `dev.fuba.ianautomat`. Use the Apple Developer team that owns this ID and an Xcode account with distribution access. The first upload requires an iOS app record in App Store Connect. Suggested values are **ianautomat** for the name, **Japanese** for the primary language, **dev.fuba.ianautomat** for the bundle ID, and **ianautomat-ios** for the SKU.

```sh
ios/testflight.sh prepare TEAM_ID 1
ios/testflight.sh upload TEAM_ID 1
```

The first command creates the archive, exports an internal-only IPA, and verifies its distribution signature. It creates a new directory for the build number. The second command can be retried after creating the app record, and verifies the archive before uploading it. Both commands write logs under `build/ios-distribution/1/`. Use a higher build number for subsequent builds.

After processing, assign the build and the intended Apple Account to an internal TestFlight group. Open the invitation or TestFlight on the iPhone using the invited account, then install and open ianautomat. Smoke test **Continue with ChatGPT**, choose a model, and run `Make a three-item packing checklist`. Check one item, relaunch the app, and confirm the saved tool and checked item appear in the run. See [Apple's internal TestFlight guide](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers).

## Initial delivery

Version 0.1.0, build 1. iPhone simulator tests and signed IPA verification were performed on Xcode 27.2. The first upload attempt reported that the App Store Connect record did not exist. TestFlight processing, tester assignment, installation on a physical iPhone, and authenticated inference are pending.
