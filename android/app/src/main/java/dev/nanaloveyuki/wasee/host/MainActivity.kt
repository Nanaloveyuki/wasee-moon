package dev.nanaloveyuki.wasee.host

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
  private lateinit var logView: TextView
  private lateinit var usbHost: AndroidUsbHost
  private val permissionAction = "dev.nanaloveyuki.wasee.host.USB_PERMISSION"
  private var guestStarted = false

  private val permissionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != permissionAction) {
        return
      }
      val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
      appendLog("USB permission result: " + granted)
      if (granted) {
        runGuest()
      } else {
        appendLog("Guest not started: USB permission was denied")
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    logView = TextView(this).apply {
      textSize = 14f
      setPadding(32, 32, 32, 32)
      text = "Starting WASI host..."
    }
    setContentView(logView)

    usbHost = AndroidUsbHost(
      context = this,
      allowlist = setOf(
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D00),
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D01),
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D02),
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D03),
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D04),
        UsbAllowlistEntry(vendorId = 0x18D1, productId = 0x2D05),
        UsbAllowlistEntry(vendorId = 0x346D, productId = 0x5678),
      ),
    )
    registerPermissionReceiver()
    if (!requestUsbPermissionIfNeeded()) {
      runGuest()
    }
  }

  override fun onDestroy() {
    usbHost.closeAll()
    unregisterReceiver(permissionReceiver)
    super.onDestroy()
  }

  private fun registerPermissionReceiver() {
    val filter = IntentFilter(permissionAction)
    if (Build.VERSION.SDK_INT >= 33) {
      registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      registerReceiver(permissionReceiver, filter)
    }
  }

  private fun requestUsbPermissionIfNeeded(): Boolean {
    val device = usbHost.firstDeviceWithoutPermission() ?: return false
    val pendingIntent = PendingIntent.getBroadcast(
      this,
      0,
      Intent(permissionAction).setPackage(packageName),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    usbHost.requestPermission(device, pendingIntent)
    appendLog("Requested permission for USB device " + device.deviceId)
    return true
  }

  private fun runGuest() {
    if (guestStarted) {
      return
    }
    guestStarted = true
    Thread {
      try {
        val runner = WasiGuestRunner(usbHost, ::appendLog)
        val result = assets.open("guest/guest.wasm").use { input ->
          runner.run(input)
        }
        appendLog("Guest exited with code " + result.exitCode)
      } catch (error: Throwable) {
        appendLog("Guest failed: " + (error.message ?: error.javaClass.simpleName))
      }
    }.start()
  }

  private fun appendLog(line: String) {
    runOnUiThread {
      logView.append(System.lineSeparator() + line)
    }
  }
}
