# Architecture

## Ownership

| Layer | Owns | Must not own |
| --- | --- | --- |
| madk | AOA state machine, transport traits, portable protocol errors | Android Context, UsbManager, WASI runtime lifecycle |
| MoonBit guest | Policy and protocol calls | USB permissions, Java objects, raw handles |
| wasee-moon Android host | Chicory runtime, Android permission flow, USB allowlist, transfer limits | Guest policy and unrestricted capabilities |
| android/host library | Reusable Kotlin host facade for Orbit Activities | Orbit Activity lifecycle and application policy |
| bridge package | madk UsbTransport adapter over the bounded core-Wasm imports | Android permissions, Java objects, raw handles |
| UsbHost bridge | A stable capability-shaped API matching wit/madk-aoa.wit | Runtime-specific or Android-specific types in its public methods |

The Android app is the WASI host. It is not the AOA accessory fixture from
madk; it is the side that may enumerate and control an external USB device.
The existing madk fixture remains useful as a device-side integration test.

## Execution flow

~~~text
Activity
  -> UsbPermissionController / AndroidUsbHost
  -> WasiGuestRunner
  -> Chicory WASI Preview 1
  -> MoonBit core-Wasm guest
  -> madk:aoa/usb-host bridge
  -> AndroidUsbHost
  -> UsbManager / UsbDeviceConnection
~~~

Permission requests and lifecycle events stay in Kotlin. The guest only sees
bounded integer and byte-array operations through a capability interface.
Handles are opaque integers scoped to one host instance and are invalid after
close or host teardown.

## Why the runtime is here

The runtime, Android API level, APK packaging, permission UI, and scheduler
are deployment concerns. Keeping them in this companion project lets the
portable madk packages remain usable by native, desktop, and future WASI
hosts without taking an Android or runtime dependency.

## Future component boundary

The current WIT file is the contract source, but the executable guest uses a
temporary core-Wasm scalar/pointer import because this project has not selected
a complete MoonBit Component Model toolchain or a runtime component adapter.
The current guest nevertheless runs madk's portable AoaSession through the
bridge package. The future ABI migration should:

1. version the WIT package and generate guest/host bindings;
2. replace the smoke import with canonical component calls;
3. keep AndroidUsbHost as the implementation of the generated host trait;
4. retain the allowlist and transfer limits at the Android boundary.

## madk source dependency

This repository snapshots the madk WIT contract for review but does not copy
madk's MoonBit source. The module manifest consumes the published
Nanaloveyuki/madk@0.1.0 root and transport packages. The bridge package
implements UsbTransport over the host imports, so AoaSession runs inside the
guest without duplicating protocol code here.

AOA start causes the Android device to disappear and re-enumerate. The host
marks the handle after the standard AOA start request; the next info() call
waits briefly for an allowlisted AOA PID, closes the old connection, and
installs the new connection under the same opaque handle. Permission is still
an Android-host concern and must exist for the re-enumerated device.
