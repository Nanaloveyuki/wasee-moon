# Contributing

Keep the portable guest contract and Android host implementation separate.
Changes that add Android APIs, runtime dependencies, or APK lifecycle logic
belong under android/. Changes to the WIT contract must include a note in
docs/architecture.md or docs/bridge-abi.md.

Before opening a change, run:

~~~text
moon fmt --check
moon check bridge --target wasm --deny-warn
moon check guest --target wasm --deny-warn
moon check --target all --deny-warn
moon test --target all --deny-warn
moon build guest --target wasm --release
cd android
gradle :app:testDebugUnitTest --no-daemon
gradle :app:assembleDebug --no-daemon
~~~

The Android app requires minSdk 26 because the selected Chicory release uses
method-handle operations that cannot be desugared for older Android releases.
Use a physical device only after the APK build is green. USB permission and
AOA negotiation are runtime checks, not substitutes for the portable build.
The bridge package may import the published Nanaloveyuki/madk module, but must
not copy madk protocol logic into the Android host.
