package ai.masaic.platform.regression.api.tools

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PlayWrightRunner {
    suspend fun runPlaywrightScript(
        scriptName: String,
        headed: Boolean = true,
        timeout: Long = TimeUnit.MINUTES.toMillis(2), // default 10 min
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null,
    ): PlaywrightRun =
        withTimeout(timeout) {
            val args =
                buildList {
                    addAll(listOf("npx", "playwright", "test", scriptName))
                    if (headed) add("--headed")
                }

            val pb =
                ProcessBuilder(args).apply {
                    directory(File("${System.getProperty("user.dir")}/src/main/resources/playwright"))
                    redirectErrorStream(false)
                }

            val process = withContext(Dispatchers.IO) { pb.start() }

            val outDeferred = async { readStream(process.inputStream, onStdoutLine) }
            val errDeferred = async { readStream(process.errorStream, onStderrLine) }

            val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
            val stdout = outDeferred.await()
            val stderr = errDeferred.await()

            PlaywrightRun.Plain(exitCode, stdout, stderr)
        }

    private suspend fun readStream(
        stream: InputStream,
        onLine: ((String) -> Unit)?,
    ): String =
        withContext(Dispatchers.IO) {
            buildString {
                stream.bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        onLine?.invoke(line)
                        appendLine(line)
                    }
                }
            }.trimEnd()
        }
}

sealed class PlaywrightRun {
    abstract val exitCode: Int
    abstract val stdout: String
    abstract val stderr: String
    val success get() = exitCode == 0

    data class Plain(
        override val exitCode: Int,
        override val stdout: String,
        override val stderr: String,
    ) : PlaywrightRun()

    data class JsonReported(
        override val exitCode: Int,
        override val stdout: String,
        override val stderr: String,
        val report: JsonNode?,
        val summary: String? = null,
    ) : PlaywrightRun()
}
