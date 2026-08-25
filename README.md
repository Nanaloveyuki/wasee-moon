# wasee-moon

wasee-moon is the Android host companion for running MoonBit Wasm guests
with a bounded USB capability. It embeds a WASI Preview 1 runtime in an
Android app and keeps Android USB objects on the host side.

## Current slice

The repository currently provides:

- a MoonBit guest compiled to core Wasm;
- an Android app embedding Chicory WASI Preview 1;
- a reusable android/host library for Orbit and other Android Activities;
- an Android UsbHost implementation backed by UsbManager;
- an explicit USB allowlist and bounded transfer options;
- a reusable bridge package implementing madk's UsbTransport over bounded
  core-Wasm imports;
- guest-side madk.AoaSession negotiation and length-prefixed status/echo
  validation for allowlisted AOA devices;
- a copy of the madk:aoa WIT contract.

The bridge is deliberately not presented as a Component Model binding. It is a
bounded core-Wasm ABI that consumes Nanaloveyuki/madk@0.1.0 and keeps the
portable AOA state machine in madk. Non-AOA devices use the descriptor smoke;
AOA-vendor devices use madk.AoaSession and the fixture frame protocol.
Generated WIT/component bindings remain a later ABI migration.

## Boundary

madk owns the portable AOA protocol state machine and transport contracts.
This repository owns the Android runtime and host capability implementation.
The Android host must not expose UsbDeviceConnection, Java objects, raw
pointers, or unbounded filesystem/network capabilities to a guest.

The host flow is:

1. enumerate only configured USB allowlist entries;
2. request Android permission outside the guest;
3. open an approved device and retain the native Android connection privately;
4. translate bounded control and bulk operations to madk's UsbTransport;
5. after AOA start, wait for accessory re-enumeration and replace the
   connection behind the same guest handle;
6. close every guest-owned handle during teardown.

See docs/architecture.md for the split and docs/bridge-abi.md for the
temporary core-Wasm ABI.
The latest local hardware/runtime evidence is in docs/validation.md.

## Build

Requirements:

- MoonBit 0.10.9+6e6c44045, selected by .moon-version;
- JDK 17;
- Android SDK platform 36 and build tools 37.0.0;
- Android minSdk 26 for Chicory method-handle support;
- Gradle 9.6.1 or a compatible Gradle installation;
- an Android SDK platform-tools installation for device validation.

Build the guest:

~~~text
moon check guest --target wasm --deny-warn
moon build guest --target wasm --release
~~~

Build the Android host. The Gradle buildGuestWasm task compiles and copies
the guest into the app assets before preBuild:

~~~text
cd android
gradle :app:assembleDebug --no-daemon
~~~

The host library is a separate Gradle module:

~~~text
gradle :host:assembleRelease --no-daemon
~~~

Embedding applications use WaseeHostController from android/host. They retain
ownership of Activity lifecycle and permission PendingIntent creation, while
the library owns AndroidUsbHost, WasiGuestRunner, Chicory imports, bounded
transfers, and handle cleanup.

The first build may need network access to resolve Android Gradle Plugin and
Chicory artifacts from Maven Central.

## Runtime choice

The first Android slice uses com.dylibso.chicory:wasi:1.7.5,
runtime:1.7.5, and wasm:1.7.5. Chicory is a pure JVM runtime, so this
prototype does not require an NDK or architecture-specific JNI libraries.
The Android host boundary is kept behind WasiGuestRunner; a future native
runtime can replace that implementation without moving UsbManager logic
into the guest-facing API.

## Validation

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

USB/AOA validation requires a permitted physical data connection. It is
separate from the Android APK build and can be performed after the device is
available again. A mass-storage disk validates the descriptor path. AOA
negotiation and fixture frames require an external AOA-capable device; the
madk Android fixture is a device-side target, not an Android-host test source.

## License

MIT. See LICENSE.
