package dev.nanaloveyuki.wasee.host

const val MAX_USB_TRANSFER_BYTES = 64 * 1024

data class DeviceInfo(
  val id: Int,
  val vendorId: Int,
  val productId: Int,
  val bulkInEndpoint: Int? = null,
  val bulkOutEndpoint: Int? = null,
)

data class ControlRequest(
  val requestType: Int,
  val request: Int,
  val value: Int,
  val index: Int,
) {
  init {
    require(requestType in 0..0xFF) { "requestType must fit in u8" }
    require(request in 0..0xFF) { "request must fit in u8" }
    require(value in 0..0xFFFF) { "value must fit in u16" }
    require(index in 0..0xFFFF) { "index must fit in u16" }
  }

  fun isIn(): Boolean = requestType and 0x80 != 0
}

data class TransferOptions(
  val timeoutMs: Int,
  val maxBytes: Int,
) {
  init {
    require(timeoutMs in 1..60_000) { "timeoutMs must be in 1..60000" }
    require(maxBytes in 1..MAX_USB_TRANSFER_BYTES) {
      "maxBytes must be in 1..$MAX_USB_TRANSFER_BYTES"
    }
  }
}

sealed class UsbHostError(message: String) : Exception(message) {
  class NotFound : UsbHostError("USB device or handle was not found")

  class PermissionDenied : UsbHostError("Android USB permission was not granted")

  class Unsupported(detail: String) : UsbHostError(detail)

  class Timeout : UsbHostError("USB transfer timed out")

  class Disconnected : UsbHostError("USB device was disconnected")

  class TransferFailed(detail: String) : UsbHostError(detail)
}

interface UsbHost {
  fun listDevices(): List<DeviceInfo>

  @Throws(UsbHostError::class)
  fun open(deviceId: Int): Int

  @Throws(UsbHostError::class)
  fun info(handle: Int): DeviceInfo

  @Throws(UsbHostError::class)
  fun refresh(handle: Int)

  @Throws(UsbHostError::class)
  fun controlIn(
    handle: Int,
    request: ControlRequest,
    options: TransferOptions,
  ): ByteArray

  @Throws(UsbHostError::class)
  fun controlOut(
    handle: Int,
    request: ControlRequest,
    data: ByteArray,
    options: TransferOptions,
  ): Int

  @Throws(UsbHostError::class)
  fun bulkRead(
    handle: Int,
    endpoint: Int,
    options: TransferOptions,
  ): ByteArray

  @Throws(UsbHostError::class)
  fun bulkWrite(
    handle: Int,
    endpoint: Int,
    data: ByteArray,
    options: TransferOptions,
  ): Int

  @Throws(UsbHostError::class)
  fun close(handle: Int)

  fun closeAll()
}
