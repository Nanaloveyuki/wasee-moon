///|
const MAX_FRAME_BYTES : Int = 64 * 1024

///|
fn fixture_identity() -> @madk.AoaIdentity raise @madk.AoaError {
  @madk.AoaIdentity::new(
    manufacturer="wasee-moon",
    model="wasee-android-host",
    description="wasee Android WASI AOA guest",
    version="0.1.0",
    uri="https://example.invalid/wasee-moon",
    serial="wasee-guest",
  )
}

///|
fn frame(body : String) -> Bytes {
  let payload = @utf8.encode(body, bom=false)
  let output = @buffer.Buffer::Buffer(size_hint=4 + payload.length())
  output.write_int_be(payload.length())
  output.write_bytes(payload[:])
  output.to_bytes()
}

///|
fn read_frame(
  session : @madk.AoaSession,
  timeout_ms : Int,
) -> String raise @madk.AoaError {
  let header = session.read_bulk_exact(4, timeout_ms)
  let length = header[0].to_int() * 16_777_216 +
    header[1].to_int() * 65_536 +
    header[2].to_int() * 256 +
    header[3].to_int()
  if length <= 0 || length > MAX_FRAME_BYTES {
    raise @madk.InvalidState("invalid AOA frame length: \{length}")
  }
  @utf8.decode_lossy(session.read_bulk_exact(length, timeout_ms))
}

///|
fn exchange(
  session : @madk.AoaSession,
  body : String,
  expected : String,
) -> Unit raise @madk.AoaError {
  session.write_bulk(frame(body), 5_000)
  let actual = read_frame(session, 5_000)
  if actual != expected {
    raise @madk.InvalidState(
      "AOA frame mismatch: expected '\{expected}', got '\{actual}'",
    )
  }
  println("wasee guest: frame ok=" + body)
}

///|
fn run_aoa(
  transport : @bridge.AndroidUsbTransport,
) -> Unit raise @madk.AoaError {
  let session = @madk.AoaSession::new(transport as &@transport.UsbTransport)
  if session.state() == @madk.BulkReady {
    println("wasee guest: AOA accessory already enumerated")
  } else {
    let protocol = session.probe()
    println("wasee guest: AOA protocol=\{Repr(protocol)}")
    session.configure(fixture_identity())
    session.start()
    println("wasee guest: AOA start requested; waiting for re-enumeration")
    session.refresh_bulk_endpoints()
  }
  let info = session.device_info()
  println(
    "wasee guest: AOA accessory vid=" +
    info.vendor_id().to_string() +
    " pid=" +
    info.product_id().to_string(),
  )
  exchange(session, "status", "status:connected;protocol=host-negotiated")
  exchange(session, "echo-from-wasee", "echo:echo-from-wasee")
  session.close()
}

///|
fn run_descriptor_probe(
  transport : @bridge.AndroidUsbTransport,
) -> Unit raise @transport.TransportError {
  let request = @transport.ControlTransfer::in_request(
    request_type=0x80,
    request=0x06,
    value=0x0100,
    index=0,
    length=64,
  )
  let response = transport.control(request, 1_000)
  if response.length() >= 12 && response[1] == b'\x01' {
    let vendor_id = response[8].to_int() + response[9].to_int() * 256
    let product_id = response[10].to_int() + response[11].to_int() * 256
    println(
      "wasee guest: usb descriptor length=" + response.length().to_string(),
    )
    println(
      "wasee guest: usb descriptor vid=" +
      vendor_id.to_string() +
      " pid=" +
      product_id.to_string(),
    )
  } else {
    raise @transport.Failure("USB device descriptor response is invalid")
  }
  transport.close()
  println("wasee guest: usb open/close=0")
}

///|
fn main {
  let count = @bridge.list_devices_count()
  println("wasee guest: allowlisted USB devices=" + count.to_string())
  if count <= 0 {
    return
  }
  let device_id = @bridge.first_device_id()
  let handle = @bridge.open_device(device_id)
  if handle < 0 {
    println("wasee guest: usb open failed=" + handle.to_string())
    return
  }
  let transport = @bridge.AndroidUsbTransport::new(handle)
  let info = transport.info() catch {
    error => {
      println("wasee guest: usb info failed=\{Repr(error)}")
      return
    }
  }
  if info.vendor_id() == @madk.aoa_vendor_id {
    run_aoa(transport) catch {
      error => println("wasee guest: AOA failed=\{Repr(error)}")
    }
  } else {
    run_descriptor_probe(transport) catch {
      error => println("wasee guest: descriptor failed=\{Repr(error)}")
    }
  }
}
