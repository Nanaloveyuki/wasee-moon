package dev.nanaloveyuki.wasee.host

import com.dylibso.chicory.runtime.ImportFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasi.WasiExitException
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import com.dylibso.chicory.wasm.Parser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class GuestRunResult(
  val exitCode: Int,
  val output: String,
)

class WasiGuestRunner(
  private val host: UsbHost,
  private val onLine: (String) -> Unit,
) {
  fun run(
    wasm: InputStream,
    arguments: List<String> = emptyList(),
  ): GuestRunResult {
    val output = StringBuilder()
    val stream = LineOutputStream { line ->
      output.append(line).append(System.lineSeparator())
      onLine(line)
    }
    val wasiOptions = WasiOptions.builder()
      .withStdout(stream)
      .withStderr(stream)
      .withArguments(arguments)
      .build()
    val wasi = WasiPreview1.builder().withOptions(wasiOptions).build()
    try {
      val module = Parser.parse(wasm)
      val functions = ArrayList<ImportFunction>()
      functions.addAll(wasi.toHostFunctions())
      functions.addAll(WasiHostFunctions.forHost(host))
      val imports = ImportValues.builder().withFunctions(functions).build()
      Instance.builder(module)
        .withImportValues(imports)
        .withStart(true)
        .build()
      stream.flush()
      return GuestRunResult(0, output.toString())
    } catch (error: WasiExitException) {
      stream.flush()
      return GuestRunResult(error.exitCode(), output.toString())
    } finally {
      wasi.close()
      host.closeAll()
    }
  }

  private class LineOutputStream(
    private val onLine: (String) -> Unit,
  ) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    override fun write(value: Int) {
      buffer.write(value)
      if (value == 10) {
        flushLine()
      }
    }

    override fun flush() {
      flushLine()
    }

    private fun flushLine() {
      if (buffer.size() == 0) {
        return
      }
      val line = buffer.toByteArray().toString(Charsets.UTF_8)
        .trimEnd(13.toChar(), 10.toChar())
      buffer.reset()
      if (line.isNotEmpty()) {
        onLine(line)
      }
    }
  }
}
