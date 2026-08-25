package dev.nanaloveyuki.wasee.host

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

object WasiHostFunctions {
  private const val MODULE_NAME = "madk:aoa/usb-host"
  private const val MAX_BRIDGE_BYTES = MAX_USB_TRANSFER_BYTES
  private const val STATUS_INVALID_ARGUMENT = -1001
  private const val STATUS_CLOSED = -1002
  private const val STATUS_DISCONNECTED = -1003
  private const val STATUS_TIMEOUT = -1004
  private const val STATUS_ACCESS = -1005
  private const val STATUS_NOT_FOUND = -1006
  private const val STATUS_UNSUPPORTED = -1007
  private const val STATUS_RUNTIME_SYMBOL = -1008
  private const val STATUS_RUNTIME_MISSING = -1009
  private const val STATUS_OUT_OF_MEMORY = -1010
  private const val STATUS_FAILURE = -1012
  private const val STATUS_INVALID_ENDPOINT = -1013
  private const val STATUS_INVALID_LENGTH = -1014

  fun forHost(host: UsbHost): Array<HostFunction> {
    val listDevicesCount = WasmFunctionHandle { _, _ ->
      safeInt { host.listDevices().size }
    }
    val firstDeviceId = WasmFunctionHandle { _, _ ->
      safeInt { host.listDevices().firstOrNull()?.id ?: -1 }
    }
    val openDevice = WasmFunctionHandle { _, args ->
      safeInt { host.open(args[0].toInt()) }
    }
    val closeDevice = WasmFunctionHandle { _, args ->
      safeInt {
        host.close(args[0].toInt())
        0
      }
    }
    val deviceInfo = WasmFunctionHandle { _, args ->
      safeInt {
        host.info(args[0].toInt())
        0
      }
    }
    val deviceVendorId = WasmFunctionHandle { _, args ->
      safeInt { host.info(args[0].toInt()).vendorId }
    }
    val deviceProductId = WasmFunctionHandle { _, args ->
      safeInt { host.info(args[0].toInt()).productId }
    }
    val deviceBulkInEndpoint = WasmFunctionHandle { _, args ->
      safeInt { host.info(args[0].toInt()).bulkInEndpoint ?: -1 }
    }
    val deviceBulkOutEndpoint = WasmFunctionHandle { _, args ->
      safeInt { host.info(args[0].toInt()).bulkOutEndpoint ?: -1 }
    }
    val refreshDevice = WasmFunctionHandle { _, args ->
      safeInt {
        host.refresh(args[0].toInt())
        0
      }
    }
    val controlIn = WasmFunctionHandle { instance, args ->
      safeInt {
        val request = ControlRequest(
          requestType = args[1].toInt(),
          request = args[2].toInt(),
          value = args[3].toInt(),
          index = args[4].toInt(),
        )
        val capacity = args[6].toInt()
        val data = host.controlIn(
          args[0].toInt(),
          request,
          TransferOptions(args[7].toInt(), capacity),
        )
        writeGuestBytes(instance, args[5], capacity, data)
        data.size
      }
    }
    val controlOut = WasmFunctionHandle { instance, args ->
      safeInt {
        val request = ControlRequest(
          requestType = args[1].toInt(),
          request = args[2].toInt(),
          value = args[3].toInt(),
          index = args[4].toInt(),
        )
        val length = args[6].toInt()
        val data = readGuestBytes(instance, args[5], length)
        host.controlOut(
          args[0].toInt(),
          request,
          data,
          TransferOptions(args[7].toInt(), args[8].toInt()),
        )
      }
    }
    val bulkRead = WasmFunctionHandle { instance, args ->
      safeInt {
        val capacity = args[3].toInt()
        val data = host.bulkRead(
          args[0].toInt(),
          args[1].toInt(),
          TransferOptions(args[4].toInt(), capacity),
        )
        writeGuestBytes(instance, args[2], capacity, data)
        data.size
      }
    }
    val bulkWrite = WasmFunctionHandle { instance, args ->
      safeInt {
        val length = args[3].toInt()
        val data = readGuestBytes(instance, args[2], length)
        host.bulkWrite(
          args[0].toInt(),
          args[1].toInt(),
          data,
          TransferOptions(args[4].toInt(), args[5].toInt()),
        )
      }
    }
    return arrayOf(
      HostFunction(
        MODULE_NAME,
        "list-devices-count",
        FunctionType.of(emptyList<ValType>(), listOf(ValType.I32)),
        listDevicesCount,
      ),
      HostFunction(
        MODULE_NAME,
        "first-device-id",
        FunctionType.of(emptyList<ValType>(), listOf(ValType.I32)),
        firstDeviceId,
      ),
      HostFunction(
        MODULE_NAME,
        "open-device",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        openDevice,
      ),
      HostFunction(
        MODULE_NAME,
        "close-device",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        closeDevice,
      ),
      HostFunction(
        MODULE_NAME,
        "device-info",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        deviceInfo,
      ),
      HostFunction(
        MODULE_NAME,
        "device-vendor-id",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        deviceVendorId,
      ),
      HostFunction(
        MODULE_NAME,
        "device-product-id",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        deviceProductId,
      ),
      HostFunction(
        MODULE_NAME,
        "device-bulk-in-endpoint",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        deviceBulkInEndpoint,
      ),
      HostFunction(
        MODULE_NAME,
        "device-bulk-out-endpoint",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        deviceBulkOutEndpoint,
      ),
      HostFunction(
        MODULE_NAME,
        "refresh-device",
        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
        refreshDevice,
      ),
      HostFunction(
        MODULE_NAME,
        "control-in",
        FunctionType.of(
          List(8) { ValType.I32 },
          listOf(ValType.I32),
        ),
        controlIn,
      ),
      HostFunction(
        MODULE_NAME,
        "control-out",
        FunctionType.of(
          List(9) { ValType.I32 },
          listOf(ValType.I32),
        ),
        controlOut,
      ),
      HostFunction(
        MODULE_NAME,
        "bulk-read",
        FunctionType.of(
          List(5) { ValType.I32 },
          listOf(ValType.I32),
        ),
        bulkRead,
      ),
      HostFunction(
        MODULE_NAME,
        "bulk-write",
        FunctionType.of(
          List(6) { ValType.I32 },
          listOf(ValType.I32),
        ),
        bulkWrite,
      ),
    )
  }

  private fun safeInt(block: () -> Int): LongArray =
    try {
      longArrayOf(block().toLong())
    } catch (error: UsbHostError) {
      longArrayOf(errorStatus(error).toLong())
    } catch (_: IllegalArgumentException) {
      longArrayOf(STATUS_INVALID_ARGUMENT.toLong())
    }

  private fun errorStatus(error: UsbHostError): Int =
    when (error) {
      is UsbHostError.PermissionDenied -> STATUS_ACCESS
      is UsbHostError.NotFound -> STATUS_NOT_FOUND
      is UsbHostError.Timeout -> STATUS_TIMEOUT
      is UsbHostError.Disconnected -> STATUS_DISCONNECTED
      is UsbHostError.Unsupported -> STATUS_UNSUPPORTED
      is UsbHostError.TransferFailed -> STATUS_FAILURE
    }

  private fun readGuestBytes(
    instance: Instance,
    pointer: Long,
    length: Int,
  ): ByteArray {
    val range = checkedMemoryRange(instance, pointer, length.toLong())
    return instance.memory().readBytes(range.offset, range.length)
  }

  private fun writeGuestBytes(
    instance: Instance,
    pointer: Long,
    capacity: Int,
    data: ByteArray,
  ) {
    val range = checkedMemoryRange(instance, pointer, capacity.toLong())
    if (data.size > range.length) {
      throw UsbHostError.Unsupported("host response exceeds guest buffer")
    }
    instance.memory().write(range.offset, data, 0, data.size)
  }

  private fun checkedMemoryRange(
    instance: Instance,
    pointer: Long,
    length: Long,
  ): GuestMemoryRange {
    if (pointer < 0 ||
      length < 0 ||
      length > MAX_BRIDGE_BYTES.toLong() ||
      pointer > Int.MAX_VALUE.toLong()
    ) {
      throw UsbHostError.Unsupported("guest memory range is invalid")
    }
    val memoryBytes =
      instance.memory().pages().toLong() * Memory.PAGE_SIZE.toLong()
    if (pointer > memoryBytes || length > memoryBytes - pointer) {
      throw UsbHostError.Unsupported("guest memory range is outside linear memory")
    }
    return GuestMemoryRange(pointer.toInt(), length.toInt())
  }

  private data class GuestMemoryRange(
    val offset: Int,
    val length: Int,
  )
}
