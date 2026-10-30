# Messenger platform runtime gate (#23): verification handoff

Verdict: **OPEN / UNVERIFIED**, 2026-10-27. The user elected to perform this
verification. No target runtime result is accepted as passing. Successful desktop
prerequisites: [#21](../messenger-gate-21/README.md) and
[#22](../messenger-gate-22/README.md). This experimental harness does not unblock
production adoption by itself and does not deliver #6, #7 or #14.

## Prepared harness and verification status

`-PmessengerGate` opts into `messengerGateMain` and alternate web/iOS entrypoints.
Android additionally uses a separate gate manifest, launcher and application ID
`dev.phillipslabs.comcord.gate`; local HTTP permission is confined to that opt-in
manifest. Ordinary builds keep their original entrypoints. The shared experiment
uses AccountStore selection and actual Messenger timeline/input/outbox/decryption
VMs in Compose, with the same manual viewport mode as the desktop fixture.

The in-app scenario asserts initial/paged/incoming bodies, a real pending outbox
and exact-one confirmed body, A→B disposal/late-event isolation, actual encrypted
history missing its key, actual encrypted readable content, decryption cleanup,
and destruction after a real sync barrier. It prints `PLATFORM_GATE` observations
and PASS/FAIL lines and leaves the encrypted Channel visible for manual review.
These assertions are **prepared test procedures**, not observed target results.
They do not replace inspecting actual rendered sender/avatar fallback/time/body,
Sending state, encrypted placeholder or any host lifecycle checks. After the
scenario finishes, the A/B/Encrypted, Older, SDK composer/Send and Hold next send
controls allow manual inspection of those same real VM paths. Scroll the timeline
to inspect older/newest bodies; the journal is not a rendering assertion.

Before verification was handed off, JS/Wasm shared source compilation, iOS
simulator source compilation and the Android debug APK task completed during
iterative builds. A development-artifact build was canceled at the user's request
while JS/Wasm executable linking was in progress. No browser artifact completion,
Swift wrapper build, runtime success, `buildDev`, or final lint/static-analysis
pass is claimed. Final handoff edits (including browser listener removal, manual controls, journal
layout and explicit imports) have only been reviewed with `git diff --check`.
Source-compile observations therefore do not validate the final tree.

Static review then made host destruction observable to Compose: closing a host
removes its rendered room and UI-owned paging effects. Paging now makes at most
eight older-history requests, waiting for each response, rather than assuming one
SDK page contains the oldest seed. These are static robustness fixes; they were
not run on any platform and do not change the UNVERIFIED verdict.

| Target | Source/build observation before handoff | Runtime verdict |
| --- | --- | --- |
| Android | Debug APK task completed | UNVERIFIED |
| JS | Kotlin source compilation completed; dev executable build canceled | UNVERIFIED |
| Wasm | Kotlin source compilation completed; dev executable build canceled | UNVERIFIED |
| iOS simulator | Kotlin source compilation completed; Swift app not built | UNVERIFIED |

## Start the disposable homeserver

Use Java 17, Docker and the installed Android/Xcode/browser toolchains. From the
experimental branch, in a dedicated terminal:

```sh
rm -f /tmp/comcord-platform-stop /tmp/comcord-platform-ready
COMCORD_GATE_HOST=1 ./gradlew :composeApp:matrixSmokeTest \
  --tests '*PlatformGateHostSmokeTest'
```

Wait for `/tmp/comcord-platform-ready`. The host starts its own fresh Synapse
container/database and proxy at `http://127.0.0.1:18723`, with disposable
`alice` / `alicepass` and `bob` / `bobpass`. The host is skipped in ordinary smoke
runs. It shuts down when `/tmp/comcord-platform-stop` appears, or fails after a
two-hour safety deadline. Keep port 18723 free; never point this experiment at a
real homeserver or production Account. The browser-only proxy enables CORS and
`POST /_gate/hold-next-send` delays one real send for two seconds. It supplies no
outbox or encryption state.

Each app launch creates a random SDK storage namespace and new devices, logs the
namespace, creates fresh Channels, and uses actual SDK encryption. It deliberately
uses the non-keychain secret provider from the desktop experiment. Remove only
the disposable app/browser profile after testing; do not clear unrelated data.
The host remains alive across target runs; restart it for a completely fresh
server dataset.

## Run one representative target of each kind

Android (one emulator/device is sufficient):

```sh
./gradlew -PmessengerGate :androidApp:assembleDebug
adb reverse tcp:18723 tcp:18723
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb logcat -c
adb shell am start -n dev.phillipslabs.comcord.gate/dev.phillipslabs.comcord.PlatformGateActivity
adb logcat | grep PLATFORM_GATE
```

Use the Android SDK's full `platform-tools/adb` path if it is not on PATH. After
`WORKFLOW PASS`, press Home, reopen the gate app, then use **Close host**. Record
actual onStop/onResume/onDestroy logs and stopped/started sync. Close host calls
Activity.finish; force-stopping a process alone does not prove SDK destruction.

JS and Wasm, in separate runs (stop one dev server before starting the other):

```sh
./gradlew -PmessengerGate :composeApp:jsBrowserDevelopmentRun
# Then separately:
./gradlew -PmessengerGate :composeApp:wasmJsBrowserDevelopmentRun
```

Open the printed dev-server URL in a fresh disposable browser profile, inspect the
browser console and rendered scene, and identify which target is running. After
`WORKFLOW PASS`, change tabs/windows to trigger real visibility changes, return,
and use **Close host**. The host binds visibilitychange and pagehide and removes
its listeners on explicit close. Observe stopped/started sync and cleanup logs;
page-unload async completion and back/forward-cache restoration are unverified.
A close button is the deterministic cleanup check. Browser persistence, origin
storage and eviction behavior require separate observations.

iOS, one simulator/device is sufficient. Build the Swift wrapper as well as the
framework, with a distinct disposable bundle identifier. To allow the local HTTP
fixture without changing production transport policy, make a temporary Info.plist:

```sh
python3 - <<'PY'
import plistlib
from pathlib import Path
info = plistlib.loads(Path('iosApp/iosApp/Info.plist').read_bytes())
info['NSAppTransportSecurity'] = {'NSAllowsLocalNetworking': True}
Path('/tmp/comcord-gate-Info.plist').write_bytes(plistlib.dumps(info))
PY
ORG_GRADLE_PROJECT_messengerGate=true xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/comcord-gate-ios \
  CODE_SIGNING_ALLOWED=NO PRODUCT_BUNDLE_IDENTIFIER=dev.phillipslabs.comcord.gate \
  INFOPLIST_FILE=/tmp/comcord-gate-Info.plist build
xcrun simctl install booted /tmp/comcord-gate-ios/Build/Products/Debug-iphonesimulator/Comcord.app
xcrun simctl launch --console booted dev.phillipslabs.comcord.gate
```

The app output filename follows the Xcode product name; adjust `Comcord.app` to
that build's actual product if necessary. Inspect console/rendering, background
and resume the app after `WORKFLOW PASS`, then use **Close host**. UIKit active /
background notifications drive foreground sync; closing removes the experimental
child controller, observers and Messenger lifetime. Terminating the simulator
process alone is not evidence that those callbacks completed. The launchers and
commands still require the user's runtime validation.

## Acceptance checklist and result recording

For **each** Android, JS, Wasm and iOS run, preserve the exact revision, tool and
OS/browser version, commands, screenshots, console lines and any failure:

- Initial, paged and incoming messages actually render sender, initials/avatar
  fallback, time and body from the real VM.
- A→B has one AccountStore selection authority; old room work is disposed and
  late A events cannot replace B. Destroyed-room observers/paging stay stopped.
- A pending SDK outbox message is visible, then reconciles to one confirmed
  timeline event. Real encrypted content is readable with keys; missing-key
  history stays visible with an explicit placeholder. Inspect rendering while
  the scenario runs, not only its final journal.
- Actual host stop/resume changes all-account foreground sync; actual disposal
  reaches cleanup. Observe journal and `HOST`/`SYNC`/cleanup logs. Background/push
  delivery is outside scope.
- Record platform storage/crypto limits. This non-keychain disposable fixture
  proves neither native keychain/keystore protection nor production storage
  restoration, cross-signing or backup recovery. Web secret protection must not
  be inferred from iOS/JVM capabilities.

Run the repository-required development checks separately after reviewing the
final tree:

```sh
./gradlew buildDev
./gradlew -PmessengerGate buildDev
```

The ordinary run checks production-target compatibility; the opt-in run checks
the experimental shared fixture. Neither is runtime evidence. No production web
bundling or iOS release link is required. Record failures/unavailable checks and
keep this gate open until every material constraint and every target verdict is
resolved. An in-app `WORKFLOW PASS` alone does not establish host lifecycle,
rendering, storage protection or an overall #23 pass.

Cleanup after the checks:

```sh
touch /tmp/comcord-platform-stop
adb reverse --remove tcp:18723
adb uninstall dev.phillipslabs.comcord.gate
xcrun simctl uninstall booted dev.phillipslabs.comcord.gate
```

Stop dev servers, close the disposable browser profile and remove only its test
storage. The host runner removes its owned proxy/container/database on exit.
