package ai.masaic.platform.api.util

import ai.masaic.platform.api.controller.DownloadRequest
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DownloadPackagingUtil {
    private val logger = LoggerFactory.getLogger(DownloadPackagingUtil::class.java)

    fun buildZip(request: DownloadRequest): ByteArray {
        val functionName = request.functionName?.trim().orEmpty()
        val profileId = request.profile?.trim().orEmpty()
        val type = request.type ?: "client_side"
        logger.info(
            "Building zip for download request: functionName={},type={}",
            functionName, profileId, type
        )
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Include the agc-client-runtime/java-sdk project from platform directory first
            val javaSdkDir: Path = Paths.get("../agc-client-runtime/java-sdk")
            if (Files.exists(javaSdkDir) && Files.isDirectory(javaSdkDir)) {
                logger.info("Including agc-client-runtime directory: {}", javaSdkDir.toAbsolutePath())
                addDirectoryToZip(zos, javaSdkDir, "agc-runtime")
            } else {
                logger.warn("agc-client-runtime directory not found at: {}", javaSdkDir.toAbsolutePath())
            }

            if (type.equals("download_code_snippet", ignoreCase = true)) {
                val className = toPascalCase(functionName).ifBlank { "GeneratedClientTool" }
                val toolName = functionName.ifBlank { "tool" }
                val javaSource = buildJavaClientTool(className, toolName, profileId)
                val entryPath = "agc-runtime/src/main/java/tools/impl/$className.java"
                zos.putNextEntry(ZipEntry(entryPath))
                zos.write(javaSource.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
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
import tools.AgcRuntimeTool;
import tools.ToolRequest;

public class $className implements AgcRuntimeTool {
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
        return "tool $toolName is yet to to be implemented";
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
