///|
const STATUS_INVALID_ARGUMENT : Int = -1001

///|
const STATUS_CLOSED : Int = -1002

///|
const STATUS_DISCONNECTED : Int = -1003

///|
const STATUS_TIMEOUT : Int = -1004

///|
const STATUS_ACCESS : Int = -1005

///|
const STATUS_NOT_FOUND : Int = -1006

///|
const STATUS_UNSUPPORTED : Int = -1007

///|
const STATUS_RUNTIME_SYMBOL : Int = -1008

///|
const STATUS_RUNTIME_MISSING : Int = -1009

///|
const STATUS_OUT_OF_MEMORY : Int = -1010

///|
const STATUS_FAILURE : Int = -1012

///|
const STATUS_INVALID_ENDPOINT : Int = -1013

///|
const STATUS_INVALID_LENGTH : Int = -1014

///|
const MAX_BRIDGE_BYTES : Int = 64 * 1024

///|
pub fn list_devices_count() -> Int = "madk:aoa/usb-host" "list-devices-count"

///|
pub fn first_device_id() -> Int = "madk:aoa/usb-host" "first-device-id"

///|
pub fn open_device(device_id : Int) -> Int = "madk:aoa/usb-host" "open-device"

///|
fn close_device(handle : Int) -> Int = "madk:aoa/usb-host" "close-device"

///|
fn host_info(handle : Int) -> Int = "madk:aoa/usb-host" "device-info"

///|
fn host_vendor_id(handle : Int) -> Int = "madk:aoa/usb-host" "device-vendor-id"

///|
fn host_product_id(handle : Int) -> Int = "madk:aoa/usb-host" "device-product-id"

///|
fn host_bulk_in_endpoint(handle : Int) -> Int = "madk:aoa/usb-host" "device-bulk-in-endpoint"

///|
fn host_bulk_out_endpoint(handle : Int) -> Int = "madk:aoa/usb-host" "device-bulk-out-endpoint"

///|
fn host_refresh(handle : Int) -> Int = "madk:aoa/usb-host" "refresh-device"

///|
/// Reads a bounded control response into guest linear memory.
#unsafe_skip_stub_check
#borrow(buffer)
fn host_control_in(
  handle : Int,
  request_type : Int,
  request : Int,
  value : Int,
  index : Int,
  buffer : FixedArray[Byte],
  capacity : Int,
  timeout_ms : Int,
) -> Int = "madk:aoa/usb-host" "control-in"

///|
/// Writes a bounded control payload from guest linear memory.
#unsafe_skip_stub_check
#borrow(data)
fn host_control_out(
  handle : Int,
  request_type : Int,
  request : Int,
  value : Int,
  index : Int,
  data : FixedArray[Byte],
  length : Int,
  timeout_ms : Int,
  max_bytes : Int,
) -> Int = "madk:aoa/usb-host" "control-out"

///|
/// Reads a bounded bulk response into guest linear memory.
#unsafe_skip_stub_check
#borrow(buffer)
fn host_bulk_read(
  handle : Int,
  endpoint : Int,
  buffer : FixedArray[Byte],
  capacity : Int,
  timeout_ms : Int,
) -> Int = "madk:aoa/usb-host" "bulk-read"

///|
/// Writes a bounded bulk payload from guest linear memory.
#unsafe_skip_stub_check
#borrow(data)
fn host_bulk_write(
  handle : Int,
  endpoint : Int,
  data : FixedArray[Byte],
  length : Int,
  timeout_ms : Int,
  max_bytes : Int,
) -> Int = "madk:aoa/usb-host" "bulk-write"

///|
pub struct AndroidUsbTransport {
  priv handle : Int
}

///|
pub fn AndroidUsbTransport::new(handle : Int) -> AndroidUsbTransport {
  { handle, }
}

///|
pub fn AndroidUsbTransport::handle(self : AndroidUsbTransport) -> Int {
  self.handle
}

///|
fn endpoint_option(value : Int) -> Int? {
  if value < 0 {
    None
  } else {
    Some(value)
  }
}

///|
fn raise_status(status : Int) -> Unit raise @transport.TransportError {
  match status {
    STATUS_INVALID_ARGUMENT =>
      raise @transport.Failure("invalid Android USB bridge argument")
    STATUS_CLOSED => raise @transport.Closed
    STATUS_DISCONNECTED => raise @transport.Disconnected
    STATUS_TIMEOUT => raise @transport.Timeout
    STATUS_ACCESS => raise @transport.Failure("Android USB permission denied")
    STATUS_NOT_FOUND =>
      raise @transport.Failure("Android USB device or handle not found")
    STATUS_UNSUPPORTED =>
      raise @transport.Failure("Android USB operation is unsupported")
    STATUS_RUNTIME_SYMBOL | STATUS_RUNTIME_MISSING =>
      raise @transport.Failure("Android USB runtime is unavailable")
    STATUS_OUT_OF_MEMORY =>
      raise @transport.Failure("Android USB host ran out of memory")
    STATUS_INVALID_ENDPOINT => raise @transport.InvalidEndpoint(status)
    STATUS_INVALID_LENGTH => raise @transport.InvalidLength(status)
    STATUS_FAILURE => raise @transport.Failure("Android USB transfer failed")
    _ =>
      raise @transport.Failure("Android USB bridge returned status \{status}")
  }
}

///|
fn require_status(status : Int) -> Unit raise @transport.TransportError {
  if status < 0 {
    raise_status(status)
  }
}

///|
fn require_endpoint_status(
  endpoint : Int,
  status : Int,
) -> Unit raise @transport.TransportError {
  if status == STATUS_INVALID_ENDPOINT {
    raise @transport.InvalidEndpoint(endpoint)
  }
  require_status(status)
}

///|
fn require_timeout(timeout_ms : Int) -> Unit raise @transport.TransportError {
  if timeout_ms <= 0 {
    raise @transport.Failure("USB transfer timeout must be positive")
  }
}

///|
fn require_length(length : Int) -> Unit raise @transport.TransportError {
  if length < 0 || length > MAX_BRIDGE_BYTES {
    raise @transport.InvalidLength(length)
  }
}

///|
pub impl @transport.UsbTransport for AndroidUsbTransport with fn info(self) {
  require_status(host_info(self.handle))
  @transport.DeviceInfo::new(
    vendor_id=host_vendor_id(self.handle),
    product_id=host_product_id(self.handle),
    bulk_in_endpoint=endpoint_option(host_bulk_in_endpoint(self.handle)),
    bulk_out_endpoint=endpoint_option(host_bulk_out_endpoint(self.handle)),
  )
}

///|
pub impl @transport.UsbTransport for AndroidUsbTransport with fn control(
  self,
  request,
  timeout_ms,
) {
  require_timeout(timeout_ms)
  let length = request.length()
  require_length(length)
  if request.is_in() {
    let buffer : FixedArray[Byte] = FixedArray::make(length, b'\x00')
    let actual = host_control_in(
      self.handle,
      request.request_type(),
      request.request(),
      request.value(),
      request.index(),
      buffer,
      length,
      timeout_ms,
    )
    require_status(actual)
    if actual > length {
      raise @transport.ShortTransfer(length, actual)
    }
    Bytes::from_array(buffer.view(end=actual))
  } else {
    let payload = request.data()
    let payload_length = payload.length()
    let actual = host_control_out(
      self.handle,
      request.request_type(),
      request.request(),
      request.value(),
      request.index(),
      payload.to_fixedarray(),
      payload_length,
      timeout_ms,
      if payload_length > 0 {
        payload_length
      } else {
        1
      },
    )
    require_status(actual)
    if actual != payload_length {
      raise @transport.ShortTransfer(payload_length, actual)
    }
    b""
  }
}

///|
pub impl @transport.UsbTransport for AndroidUsbTransport with fn bulk_write(
  self,
  endpoint,
  payload,
  timeout_ms,
) {
  require_timeout(timeout_ms)
  require_length(payload.length())
  let length = payload.length()
  let actual = host_bulk_write(
    self.handle,
    endpoint,
    payload.to_fixedarray(),
    length,
    timeout_ms,
    if length > 0 {
      length
    } else {
      1
    },
  )
  require_endpoint_status(endpoint, actual)
  if actual != length {
    raise @transport.ShortTransfer(length, actual)
  }
  actual
}

///|
pub impl @transport.UsbTransport for AndroidUsbTransport with fn bulk_read(
  self,
  endpoint,
  max_length,
  timeout_ms,
) {
  require_timeout(timeout_ms)
  require_length(max_length)
  if max_length <= 0 {
    raise @transport.InvalidLength(max_length)
  }
  let buffer : FixedArray[Byte] = FixedArray::make(max_length, b'\x00')
  let actual = host_bulk_read(
    self.handle,
    endpoint,
    buffer,
    max_length,
    timeout_ms,
  )
  require_endpoint_status(endpoint, actual)
  if actual > max_length {
    raise @transport.ShortTransfer(max_length, actual)
  }
  Bytes::from_array(buffer.view(end=actual))
}

///|
pub impl @transport.UsbTransport for AndroidUsbTransport with fn close(self) {
  require_status(close_device(self.handle))
}

///|
/// Explicitly refreshes an Android handle after an external device switch.
/// AOA start also triggers this refresh lazily from info().
pub fn AndroidUsbTransport::refresh(
  self : AndroidUsbTransport,
) -> Unit raise @transport.TransportError {
  require_status(host_refresh(self.handle))
}
