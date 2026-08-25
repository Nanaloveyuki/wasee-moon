# Validation Record

## Android runtime smoke

Validated on 2026-08-25 with the Android host APK installed over wireless
ADB on a DVD-AN80 device.

The foreground activity stayed resumed and displayed:

~~~text
Starting WASI host...
wasee guest: allowlisted USB devices=0
Guest exited with code 0
~~~

This proves APK installation, Activity startup, Chicory WASI Preview 1
instantiation, guest stdout, and the guest-to-host
madk:aoa/usb-host.list-devices-count call.

The zero count is expected: the Android phone is currently the USB device
connected to the PC. The app itself is an Android USB host and needs a
separate OTG-connected device before AndroidUsbHost can enumerate an
external target.

## Android OTG enumeration

On 2026-08-25, with the same phone connected to an OTG mobile disk, Android
reported the phone in USB host mode:

~~~text
host_connected=true
source_power=true
data_role=host
power_role=source
~~~

The current UsbManager device descriptor was:

~~~text
manufacturer=XJC
product=USB DISK
VID=0x346D
PID=0x5678
interface=mass-storage (class 8, subclass 6, protocol 50)
bulk OUT=0x01
bulk IN=0x81
~~~

This proves the physical OTG link and Android UsbManager enumeration. The
device is now included by exact VID/PID in the demo allowlist. The APK-level
permission and guest open/close probe then passed after reinstalling the APK:

~~~text
Starting WASI host...
wasee guest: allowlisted USB devices=1
wasee guest: usb descriptor length=18
wasee guest: usb descriptor vid=13421 pid=22136
wasee guest: usb open/close=0
Guest exited with code 0
~~~

This covers the WASI guest import path, Android USB permission state,
UsbManager.openDevice, bulk endpoint discovery, interface claiming,
guest-to-host linear-memory response copying, and guest-triggered handle
close. The guest now routes the same host handle through madk's
UsbTransport; because this disk is not an AOA device, the guest selects the
descriptor probe instead of sending AOA control or bulk frames. It deliberately
does not send a mass-storage SCSI or filesystem payload.

## PC-side AOA transport

With the libusb runtime available on Windows:

~~~text
moon run examples/native/aoa_probe --target native -- --vid 0x339B --pid 0x107D
~~~

passed:

~~~text
AOA protocol: V2
AOA start requested; waiting for accessory re-enumeration
accessory device: VID=0x18d1, PID=0x2d01
frame ok: status -> status:connected;protocol=host-negotiated
frame ok: echo-from-host -> echo:echo-from-host
AOA probe completed successfully
~~~

After the device re-enumerated, the direct accessory path also passed:

~~~text
adb kill-server
moon run examples/native/aoa_probe --target native -- --accessory
~~~

The adb server must be stopped for this direct libusb path because adb can
hold the re-enumerated ADB interface.

## Not covered

- live guest AOA control-out/bulk frame validation on an external AOA target;
- mass-storage SCSI command handling or filesystem access;
- canonical WIT Component Model bindings;
- Android permission re-grant after an AOA accessory re-enumerates;
- a production Orbit Android application consuming this host facade.
