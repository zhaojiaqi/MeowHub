package com.tutu.meowhub.core.terminal

import android.content.Context
import android.system.Os
import android.util.Log
import android.util.Pair
import com.termux.shared.file.FileUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR
import com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH
import com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR
import com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object MeowTermuxInstaller {

    private const val TAG = "MeowTermuxInstaller"

    enum class State {
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        ERROR
    }

    fun isInstalled(): Boolean {
        val dirExists = FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)
        val notEmpty = if (dirExists) !TermuxFileUtils.isTermuxPrefixDirectoryEmpty() else false
        Log.d(TAG, "isInstalled: dirExists=$dirExists, notEmpty=$notEmpty, path=$TERMUX_PREFIX_DIR_PATH")
        return dirExists && notEmpty
    }

    suspend fun install(context: Context, onProgress: (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "install: starting bootstrap installation")
                onProgress("Checking environment...")

                val filesDir = context.filesDir
                Log.d(TAG, "install: filesDir=${filesDir.absolutePath}, exists=${filesDir.exists()}")
                if (!filesDir.exists()) filesDir.mkdirs()

                if (isInstalled()) {
                    Log.i(TAG, "install: already installed, skipping")
                    onProgress("Bootstrap already installed.")
                    return@withContext Result.success(Unit)
                }

                Log.i(TAG, "install: preparing bootstrap environment")
                onProgress("Preparing bootstrap environment...")

                // Clean up
                FileUtils.deleteFile("staging", TERMUX_STAGING_PREFIX_DIR_PATH, true)
                FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true)

                // Create directories
                val homeDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
                if (!homeDir.exists()) homeDir.mkdirs()
                val stagingDir = File(TERMUX_STAGING_PREFIX_DIR_PATH)
                if (!stagingDir.exists()) stagingDir.mkdirs()
                val prefixDir = File(TERMUX_PREFIX_DIR_PATH)
                if (!prefixDir.exists()) prefixDir.mkdirs()

                Log.i(TAG, "install: loading bootstrap zip from native lib")
                onProgress("Loading bootstrap package...")
                val zipBytes = loadZipBytes()
                Log.i(TAG, "install: loaded ${zipBytes.size} bytes (${zipBytes.size / 1024 / 1024}MB)")

                onProgress("Extracting bootstrap (${zipBytes.size / 1024 / 1024}MB)...")
                val buffer = ByteArray(8096)
                val symlinks = mutableListOf<Pair<String, String>>()

                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
                    var zipEntry = zipInput.nextEntry
                    while (zipEntry != null) {
                        if (zipEntry.name == "SYMLINKS.txt") {
                            val reader = BufferedReader(InputStreamReader(zipInput))
                            var line = reader.readLine()
                            while (line != null) {
                                val parts = line.split("←")
                                if (parts.size == 2) {
                                    val oldPath = parts[0]
                                    val newPath = "$TERMUX_STAGING_PREFIX_DIR_PATH/${parts[1]}"
                                    symlinks.add(Pair.create(oldPath, newPath))
                                    File(newPath).parentFile?.let { ensureDir(it) }
                                }
                                line = reader.readLine()
                            }
                        } else {
                            val targetFile = File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntry.name)
                            val isDirectory = zipEntry.isDirectory

                            ensureDir(if (isDirectory) targetFile else targetFile.parentFile!!)

                            if (!isDirectory) {
                                FileOutputStream(targetFile).use { out ->
                                    var readBytes = zipInput.read(buffer)
                                    while (readBytes != -1) {
                                        out.write(buffer, 0, readBytes)
                                        readBytes = zipInput.read(buffer)
                                    }
                                }
                                Os.chmod(targetFile.absolutePath, 448) // 0700
                            }
                        }
                        zipEntry = zipInput.nextEntry
                    }
                }

                if (symlinks.isEmpty()) {
                    Log.e(TAG, "install: no SYMLINKS.txt found in bootstrap zip")
                    return@withContext Result.failure(RuntimeException("No SYMLINKS.txt found in bootstrap"))
                }

                Log.i(TAG, "install: creating ${symlinks.size} symlinks")
                onProgress("Creating symlinks (${symlinks.size})...")
                for (symlink in symlinks) {
                    Os.symlink(symlink.first, symlink.second)
                }

                Log.i(TAG, "install: moving staging to prefix")
                onProgress("Finalizing installation...")
                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    Log.e(TAG, "install: failed to rename staging -> prefix")
                    return@withContext Result.failure(RuntimeException("Failed to move staging to prefix"))
                }

                Log.i(TAG, "install: writing environment file")
                TermuxShellEnvironment.writeEnvironmentToFile(context)

                fixAllPermissions(File(TERMUX_PREFIX_DIR_PATH))

                // Create compat symlink: /data/data/com.termux -> our data dir
                // This is needed because bootstrap binaries hardcode /data/data/com.termux/files/usr
                createCompatSymlink(context)

                onProgress("Bootstrap installed successfully!")
                Log.i(TAG, "Bootstrap installed successfully at $TERMUX_PREFIX_DIR_PATH")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap installation failed", e)
                FileUtils.deleteFile("prefix", TERMUX_PREFIX_DIR_PATH, true)
                Result.failure(e)
            }
        }

    private fun fixBinPermissions(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            try {
                if (file.isDirectory) {
                    Os.chmod(file.absolutePath, 448 or 64 or 8) // 0711
                } else if (file.isFile) {
                    Os.chmod(file.absolutePath, 448) // 0700
                }
            } catch (e: Exception) {
                Log.w(TAG, "chmod failed for ${file.absolutePath}: ${e.message}")
            }
        }
        Log.i(TAG, "Fixed permissions in ${dir.absolutePath}")
    }

    private fun fixAllPermissions(prefixDir: File) {
        val dirs = listOf("bin", "lib", "libexec", "etc", "share", "var", "tmp")
        for (d in dirs) {
            val sub = File(prefixDir, d)
            if (sub.exists()) fixBinPermissions(sub)
        }
    }

    /**
     * Create a symlink from /data/data/com.termux -> /data/data/com.tutu.meowhub
     * to satisfy hardcoded DT_RUNPATH in bootstrap ELF binaries.
     *
     * This requires both paths to be under the same SELinux context, which is generally
     * not possible without root. So we use a fallback: create the symlink inside our own
     * data directory to enable simple path references.
     *
     * The real fix for DT_RUNPATH is handled at session creation time by using /system/bin/sh
     * as the initial process with LD_LIBRARY_PATH set, bypassing the broken DT_RUNPATH.
     */
    private fun createCompatSymlink(context: Context) {
        try {
            val ourDataDir = context.applicationInfo.dataDir
            val termuxDataDir = "/data/data/com.termux"

            // Only try if we actually have a different package name
            if (ourDataDir == termuxDataDir) return

            // Try creating the symlink (will silently fail without root, which is expected)
            val termuxDir = File(termuxDataDir)
            if (!termuxDir.exists()) {
                try {
                    Os.symlink(ourDataDir, termuxDataDir)
                    Log.i(TAG, "Created compat symlink: $termuxDataDir -> $ourDataDir")
                } catch (e: Exception) {
                    // Expected to fail without root; the LD_LIBRARY_PATH workaround handles this
                    Log.d(TAG, "Compat symlink creation skipped (expected): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "createCompatSymlink: ${e.message}")
        }
    }

    private fun ensureDir(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun loadZipBytes(): ByteArray {
        return com.termux.app.TermuxInstaller.loadZipBytes()
    }
}
