package dev.nanaloveyuki.wasee.host

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WasiGuestRunnerTest {
  @Test
  fun guestCallsUsbHostAndCleansUp() {
    val host = FakeUsbHost()
    val lines = ArrayList<String>()
    val result = WasiGuestRunner(host, lines::add).run(guestStream())

    assertEquals(0, result.exitCode)
    assertTrue(result.output.contains("allowlisted USB devices=2"))
    assertTrue(result.output.contains("usb descriptor length=18"))
    assertTrue(result.output.contains("usb descriptor vid=6353 pid=11520"))
    assertTrue(result.output.contains("usb open/close=0"))
    assertEquals(
      listOf(
        "wasee guest: allowlisted USB devices=2",
        "wasee guest: usb descriptor length=18",
        "wasee guest: usb descriptor vid=6353 pid=11520",
        "wasee guest: usb open/close=0",
      ),
      lines,
    )
    assertEquals(1, host.openedDeviceId)
    assertEquals(7, host.closedHandle)
    assertTrue(host.closed)
  }

  private fun guestStream(): InputStream =
    checkNotNull(javaClass.getResourceAsStream("/guest/guest.wasm"))

  private class FakeUsbHost : UsbHost {
    var closed = false
    var openedDeviceId: Int? = null
    var closedHandle: Int? = null

    override fun listDevices(): List<DeviceInfo> = listOf(
      DeviceInfo(id = 1, vendorId = 0x18D1, productId = 0x2D00),
      DeviceInfo(id = 2, vendorId = 0x18D1, productId = 0x2D01),
    )

    override fun open(deviceId: Int): Int {
      openedDeviceId = deviceId
      return 7
    }

    override fun info(handle: Int): DeviceInfo =
      DeviceInfo(id = 1, vendorId = 0x346D, productId = 0x5678)

    override fun refresh(handle: Int) = Unit

    override fun controlIn(
      handle: Int,
      request: ControlRequest,
      options: TransferOptions,
    ): ByteArray = byteArrayOf(
      0x12,
      0x01,
      0x00,
      0x02,
      0x00,
      0x00,
      0x00,
      0x40,
      0xD1.toByte(),
      0x18,
      0x00,
      0x2D,
      0x00,
      0x01,
      0x01,
      0x02,
      0x03,
      0x01,
    )

    override fun controlOut(
      handle: Int,
      request: ControlRequest,
      data: ByteArray,
      options: TransferOptions,
    ): Int = data.size

    override fun bulkRead(
      handle: Int,
      endpoint: Int,
      options: TransferOptions,
    ): ByteArray = ByteArray(0)

    override fun bulkWrite(
      handle: Int,
      endpoint: Int,
      data: ByteArray,
      options: TransferOptions,
    ): Int = data.size

    override fun close(handle: Int) {
      closedHandle = handle
    }

    override fun closeAll() {
      closed = true
    }
  }
}
