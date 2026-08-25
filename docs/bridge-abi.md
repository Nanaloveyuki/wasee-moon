# Temporary Core-Wasm Bridge ABI

This document describes the executable core-Wasm bridge used by the first
Android slice. It is not the canonical WIT ABI and must not be used as a
stable plugin format.

## Current executable imports

The guest imports:

~~~text
module: madk:aoa/usb-host
name:   list-devices-count
type:   () -> i32

module: madk:aoa/usb-host
name:   first-device-id
type:   () -> i32

module: madk:aoa/usb-host
name:   open-device
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   close-device
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   device-info
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   device-vendor-id
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   device-product-id
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   device-bulk-in-endpoint
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   device-bulk-out-endpoint
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   refresh-device
type:   (i32) -> i32

module: madk:aoa/usb-host
name:   control-in
type:   (i32, i32, i32, i32, i32, i32, i32, i32) -> i32

module: madk:aoa/usb-host
name:   control-out
type:   (i32, i32, i32, i32, i32, i32, i32, i32, i32) -> i32

module: madk:aoa/usb-host
name:   bulk-read
type:   (i32, i32, i32, i32, i32) -> i32

module: madk:aoa/usb-host
name:   bulk-write
type:   (i32, i32, i32, i32, i32, i32) -> i32
~~~

The return value is the count of currently visible devices that match the
Android host allowlist. A negative value represents a host-side failure.
first-device-id returns the first matching Android device ID, or a negative
status. open-device returns an opaque host handle, or a negative status.
device-info refreshes a handle when the host observed an AOA start request.
The four following accessors return the current VID, PID, bulk IN endpoint, and
bulk OUT endpoint; an absent endpoint is -1. close-device and refresh-device
return 0 on success or a negative status.

The current guest uses these imports to enumerate the allowlisted device and
open and close it. It then calls control-in with the standard USB
GET_DESCRIPTOR request. The response is written into a borrowed
FixedArray[Byte] in guest linear memory and checked by the guest.

The byte operation parameters are:

~~~text
control-in:
  handle, requestType, request, value, index, bufferPtr, capacity, timeoutMs
control-out:
  handle, requestType, request, value, index, dataPtr, length, timeoutMs, maxBytes
bulk-read:
  handle, endpoint, bufferPtr, capacity, timeoutMs
bulk-write:
  handle, endpoint, dataPtr, length, timeoutMs, maxBytes
~~~

All pointer values are guest linear-memory offsets, never Android pointers.
The host validates the complete range before reading or writing memory and
limits every operation to 64 KiB. A non-negative result is an actual byte
count. Negative results use the madk/libusb status family, including -1003
for disconnection, -1004 for timeout, -1005 for permission, -1013 for an
invalid endpoint, and -1014 for an invalid length.

## Limits

- no raw Android object or pointer crosses the boundary;
- no guest-provided device selector bypasses the host allowlist;
- no unbounded byte-array transfer operation is exposed by this smoke ABI;
- the guest issues control-out and bulk traffic when an AOA device is selected;
- Android info() waits for AOA re-enumeration after the start request and swaps
  the native connection without changing the guest handle;
- the ABI may change when generated WIT/component bindings land.

The full capability shape is defined in wit/madk-aoa.wit. The Kotlin UsbHost
interface models the same shape plus host lifecycle operations needed to keep
re-enumeration behind the opaque handle.
