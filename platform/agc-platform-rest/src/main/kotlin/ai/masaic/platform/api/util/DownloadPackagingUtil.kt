package ai.masaic.platform.api.util

import ai.masaic.platform.api.controller.DownloadRequest
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DownloadPackagingUtil {
    fun buildZip(request: DownloadRequest): ByteArray {
        val functionName = request.functionName?.trim().orEmpty()
        val profileId = request.profile?.trim().orEmpty()
        val type = request.type ?: "client_side"
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (type.equals("download_code_snippet", ignoreCase = true)) {
                val className = toPascalCase(functionName).ifBlank { "GeneratedClientTool" }
                val toolName = functionName.ifBlank { "tool" }
                val javaSource = buildJavaClientTool(className, toolName, profileId)
                val entryPath = "client_runtime/java-sdk/src/main/java/tools/impl/$className.java"
                zos.putNextEntry(ZipEntry(entryPath))
                zos.write(javaSource.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            // Include the client_runtime/java-sdk project from server resources if present
            val javaSdkDir: Path = Paths.get("../agc-platform-server/src/main/resources/client_runtime/java-sdk")
            if (Files.exists(javaSdkDir) && Files.isDirectory(javaSdkDir)) {
                addDirectoryToZip(zos, javaSdkDir, "client_runtime/java-sdk")
            }
        }
        return baos.toByteArray()
    }

    private fun toPascalCase(input: String): String =
        input
            .split("[^A-Za-z0-9]".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(separator = "") { part ->
                part.substring(0, 1).uppercase() + part.substring(1)
            }

    private fun buildJavaClientTool(className: String, toolName: String, profileId: String): String =
        """
package tools.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.ClientSideTool;
import tools.ToolRequest;

public class $className implements ClientSideTool {
    private static final Logger logger = LoggerFactory.getLogger($className.class);
    private static String TOOL_NAME = "$toolName";
    private static String PROFILE_ID = "$profileId";

    @Override
    public String toolId() {
        return PROFILE_ID + "." + TOOL_NAME;
    }

    @Override
    public String executeTool(ToolRequest request) {
        logger.info("Executing AddNumbersTool: name={}, arguments={}, loopContextInfo={}",
                request != null ? request.getName() : null,
                request != null ? request.getArguments() : null,
                request != null ? request.getLoopContextInfo() : null);
        return "success";
    }
}
        """.trimIndent()

    private fun addDirectoryToZip(zos: ZipOutputStream, baseDir: Path, zipRootPrefix: String) {
        Files.walk(baseDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { filePath ->
                val relative = baseDir.relativize(filePath).toString().replace('\\', '/')
                val zipPath = "$zipRootPrefix/$relative"
                zos.putNextEntry(ZipEntry(zipPath))
                Files.newInputStream(filePath).use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
