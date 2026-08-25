package dev.nanaloveyuki.wasee.host

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDevice
import java.io.InputStream

/**
 * Reusable Android-side facade for an Orbit or standalone Activity.
 *
 * Permission UI remains owned by the embedding Activity. The controller owns
 * the bounded USB host and closes all handles when the embedding lifecycle ends.
 */
class WaseeHostController(
  context: Context,
  allowlist: Set<UsbAllowlistEntry>,
  private val onLine: (String) -> Unit,
) : AutoCloseable {
  private val host = AndroidUsbHost(context, allowlist)

  fun firstDeviceWithoutPermission(): UsbDevice? =
    host.firstDeviceWithoutPermission()

  fun requestPermission(device: UsbDevice, permissionIntent: PendingIntent) {
    host.requestPermission(device, permissionIntent)
  }

  fun runGuest(
    wasm: InputStream,
    arguments: List<String> = emptyList(),
  ): GuestRunResult = WasiGuestRunner(host, onLine).run(wasm, arguments)

  override fun close() {
    host.closeAll()
  }
}
