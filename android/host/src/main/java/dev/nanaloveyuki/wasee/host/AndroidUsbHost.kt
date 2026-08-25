package dev.nanaloveyuki.wasee.host

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

data class UsbAllowlistEntry(
  val vendorId: Int,
  val productId: Int? = null,
) {
  init {
    require(vendorId in 0..0xFFFF) { "vendorId must fit in u16" }
    require(productId == null || productId in 0..0xFFFF) {
      "productId must fit in u16"
    }
  }
}

class AndroidUsbHost(
  context: Context,
  private val allowlist: Set<UsbAllowlistEntry>,
) : UsbHost {
  private val usbManager =
    context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
  private val handles = HashMap<Int, OpenDevice>()
  private var nextHandle = 1

  init {
    require(allowlist.isNotEmpty()) { "USB allowlist must not be empty" }
  }

  override fun listDevices(): List<DeviceInfo> =
    usbManager.deviceList.values
      .filter(::isAllowed)
      .map { device ->
        DeviceInfo(
          id = device.deviceId,
          vendorId = device.vendorId,
          productId = device.productId,
        )
      }
      .sortedBy { it.id }

  fun firstDeviceWithoutPermission(): UsbDevice? =
    usbManager.deviceList.values.firstOrNull { isAllowed(it) && !usbManager.hasPermission(it) }

  fun requestPermission(device: UsbDevice, permissionIntent: PendingIntent) {
    if (!isAllowed(device)) {
      throw UsbHostError.Unsupported("permission request rejected by USB allowlist")
    }
    usbManager.requestPermission(device, permissionIntent)
  }

  @Synchronized
  override fun open(deviceId: Int): Int {
    val device = usbManager.deviceList.values.firstOrNull {
      it.deviceId == deviceId && isAllowed(it)
    } ?: throw UsbHostError.NotFound()
    val handle = allocateHandle()
    handles[handle] = openDevice(device)
    return handle
  }

  @Synchronized
  override fun info(handle: Int): DeviceInfo {
    val open = requireHandle(handle)
    if (open.waitingForAccessory) {
      refreshAccessory(handle)
    }
    return requireHandle(handle).deviceInfo
  }

  @Synchronized
  override fun refresh(handle: Int) {
    refreshAccessory(handle)
  }

  @Synchronized
  override fun controlIn(
    handle: Int,
    request: ControlRequest,
    options: TransferOptions,
  ): ByteArray {
    if (!request.isIn()) {
      throw UsbHostError.Unsupported("controlIn requires an IN request type")
    }
    val open = requireHandle(handle)
    val buffer = ByteArray(options.maxBytes)
    val actual = open.connection.controlTransfer(
      request.requestType,
      request.request,
      request.value,
      request.index,
      buffer,
      buffer.size,
      options.timeoutMs,
    )
    if (actual < 0) {
      throw UsbHostError.TransferFailed("control IN returned status " + actual)
    }
    return buffer.copyOf(actual.coerceAtMost(buffer.size))
  }

  @Synchronized
  override fun controlOut(
    handle: Int,
    request: ControlRequest,
    data: ByteArray,
    options: TransferOptions,
  ): Int {
    if (request.isIn()) {
      throw UsbHostError.Unsupported("controlOut requires an OUT request type")
    }
    if (data.size > options.maxBytes) {
      throw UsbHostError.Unsupported("control OUT payload exceeds maxBytes")
    }
    val open = requireHandle(handle)
    val actual = open.connection.controlTransfer(
      request.requestType,
      request.request,
      request.value,
      request.index,
      data,
      data.size,
      options.timeoutMs,
    )
    if (actual < 0) {
      throw UsbHostError.TransferFailed("control OUT returned status " + actual)
    }
    if (request.requestType == 0x40 &&
      request.request == 53 &&
      request.value == 0 &&
      request.index == 0
    ) {
      open.waitingForAccessory = true
    }
    return actual
  }

  @Synchronized
  override fun bulkRead(
    handle: Int,
    endpoint: Int,
    options: TransferOptions,
  ): ByteArray {
    val open = requireHandle(handle)
    val target = if (endpoint == open.bulkIn.address) {
      open.bulkIn
    } else {
      throw UsbHostError.Unsupported("endpoint is not the opened bulk IN endpoint")
    }
    val buffer = ByteArray(options.maxBytes)
    val actual = open.connection.bulkTransfer(
      target,
      buffer,
      0,
      buffer.size,
      options.timeoutMs,
    )
    if (actual < 0) {
      throw UsbHostError.TransferFailed("bulk IN returned status " + actual)
    }
    return buffer.copyOf(actual.coerceAtMost(buffer.size))
  }

  @Synchronized
  override fun bulkWrite(
    handle: Int,
    endpoint: Int,
    data: ByteArray,
    options: TransferOptions,
  ): Int {
    if (data.size > options.maxBytes) {
      throw UsbHostError.Unsupported("bulk OUT payload exceeds maxBytes")
    }
    val open = requireHandle(handle)
    val target = if (endpoint == open.bulkOut.address) {
      open.bulkOut
    } else {
      throw UsbHostError.Unsupported("endpoint is not the opened bulk OUT endpoint")
    }
    val actual = open.connection.bulkTransfer(
      target,
      data,
      0,
      data.size,
      options.timeoutMs,
    )
    if (actual < 0) {
      throw UsbHostError.TransferFailed("bulk OUT returned status " + actual)
    }
    return actual
  }

  @Synchronized
  override fun close(handle: Int) {
    val open = handles.remove(handle) ?: throw UsbHostError.NotFound()
    closeOpened(open)
  }

  @Synchronized
  override fun closeAll() {
    val openHandles = handles.keys.toList()
    openHandles.forEach { handle ->
      runCatching { close(handle) }
    }
  }

  private fun isAllowed(device: UsbDevice): Boolean =
    allowlist.any { entry ->
      entry.vendorId == device.vendorId &&
        (entry.productId == null || entry.productId == device.productId)
    }

  private fun isAccessoryDevice(device: UsbDevice): Boolean =
    isAllowed(device) &&
      device.vendorId == 0x18D1 &&
      device.productId in 0x2D00..0x2D05

  private fun openDevice(device: UsbDevice): OpenDevice {
    if (!usbManager.hasPermission(device)) {
      throw UsbHostError.PermissionDenied()
    }
    val endpoints = findBulkInterface(device)
      ?: throw UsbHostError.Unsupported("device has no bulk IN/OUT interface")
    val connection = usbManager.openDevice(device)
      ?: throw UsbHostError.TransferFailed("UsbManager.openDevice returned null")
    if (!connection.claimInterface(endpoints.interfaceHandle, true)) {
      connection.close()
      throw UsbHostError.TransferFailed("failed to claim USB interface")
    }
    return OpenDevice(
      deviceInfo = DeviceInfo(
        id = device.deviceId,
        vendorId = device.vendorId,
        productId = device.productId,
        bulkInEndpoint = endpoints.bulkIn.address,
        bulkOutEndpoint = endpoints.bulkOut.address,
      ),
      connection = connection,
      interfaceHandle = endpoints.interfaceHandle,
      bulkIn = endpoints.bulkIn,
      bulkOut = endpoints.bulkOut,
    )
  }

  private fun refreshAccessory(handle: Int) {
    val old = requireHandle(handle)
    closeOpened(old)
    handles.remove(handle)
    var permissionDenied = false
    var lastError: UsbHostError? = null
    for (attempt in 0 until 21) {
      val device = usbManager.deviceList.values.firstOrNull(::isAccessoryDevice)
      if (device != null) {
        if (!usbManager.hasPermission(device)) {
          permissionDenied = true
        } else {
          try {
            handles[handle] = openDevice(device)
            return
          } catch (error: UsbHostError) {
            lastError = error
          }
        }
      }
      if (attempt < 20) {
        Thread.sleep(50)
      }
    }
    if (permissionDenied) {
      throw UsbHostError.PermissionDenied()
    }
    throw lastError ?: UsbHostError.Disconnected()
  }

  private fun closeOpened(open: OpenDevice) {
    open.connection.releaseInterface(open.interfaceHandle)
    open.connection.close()
  }

  private fun allocateHandle(): Int {
    while (handles.containsKey(nextHandle)) {
      nextHandle += 1
      if (nextHandle == Int.MAX_VALUE) {
        nextHandle = 1
      }
    }
    val result = nextHandle
    nextHandle += 1
    return result
  }

  private fun requireHandle(handle: Int): OpenDevice =
    handles[handle] ?: throw UsbHostError.NotFound()

  private fun findBulkInterface(device: UsbDevice): BulkInterface? {
    for (index in 0 until device.interfaceCount) {
      val interfaceHandle = device.getInterface(index)
      var bulkIn: UsbEndpoint? = null
      var bulkOut: UsbEndpoint? = null
      for (endpointIndex in 0 until interfaceHandle.endpointCount) {
        val endpoint = interfaceHandle.getEndpoint(endpointIndex)
        if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) {
          continue
        }
        if (endpoint.direction == UsbConstants.USB_DIR_IN && bulkIn == null) {
          bulkIn = endpoint
        }
        if (endpoint.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) {
          bulkOut = endpoint
        }
      }
      if (bulkIn != null && bulkOut != null) {
        return BulkInterface(interfaceHandle, bulkIn, bulkOut)
      }
    }
    return null
  }

  private data class BulkInterface(
    val interfaceHandle: UsbInterface,
    val bulkIn: UsbEndpoint,
    val bulkOut: UsbEndpoint,
  )

  private data class OpenDevice(
    val deviceInfo: DeviceInfo,
    val connection: UsbDeviceConnection,
    val interfaceHandle: UsbInterface,
    val bulkIn: UsbEndpoint,
    val bulkOut: UsbEndpoint,
    var waitingForAccessory: Boolean = false,
  )
}
