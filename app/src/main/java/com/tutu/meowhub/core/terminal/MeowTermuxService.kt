package com.tutu.meowhub.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.shared.termux.TermuxConstants
import com.termux.view.TerminalViewClient
import java.io.File

class MeowTermuxService(private val context: Context) {

    private val sessions = mutableListOf<TerminalSession>()
    private var currentSessionIndex = 0
    private var sessionClient: TerminalSessionClient? = null

    val currentSession: TerminalSession?
        get() = sessions.getOrNull(currentSessionIndex)

    val sessionCount: Int
        get() = sessions.size

    fun setSessionClient(client: TerminalSessionClient) {
        sessionClient = client
        sessions.forEach { it.updateTerminalSessionClient(client) }
    }

    fun createSession(
        executable: String? = null,
        args: Array<String>? = null,
        workingDir: String? = null
    ): TerminalSession {
        val client = sessionClient ?: throw IllegalStateException("SessionClient not set")

        val env = buildEnvironment()
        val cwd = workingDir ?: TermuxConstants.TERMUX_HOME_DIR_PATH

        val shell: String
        val shellArgs: Array<String>

        if (executable != null) {
            shell = executable
            shellArgs = args ?: emptyArray()
        } else {
            // Start an interactive /system/bin/sh. This always works because it's an AOSP binary
            // with no DT_RUNPATH dependency on com.termux paths. The environment variables
            // (PATH, LD_LIBRARY_PATH etc.) from buildEnvironment() allow prefix binaries to work.
            // We write an init script to disk and source it on first prompt to attempt upgrade
            // to bash if available.
            shell = "/system/bin/sh"
            shellArgs = emptyArray()
            writeInitRc()
        }

        val session = TerminalSession(shell, cwd, shellArgs, env, 2000, client)
        sessions.add(session)
        currentSessionIndex = sessions.size - 1

        Log.i(TAG, "Created session #${sessions.size}: shell=$shell, cwd=$cwd, args=${shellArgs.toList()}")

        if (executable == null) {
            Log.i(TAG, "Sourcing init.sh to set up environment")
            session.write(". \$HOME/.meowhub/init.sh\n")
        }

        return session
    }

    private fun writeInitRc() {
        val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        val home = TermuxConstants.TERMUX_HOME_DIR_PATH
        val dir = File("$home/.meowhub")
        dir.mkdirs()
        val initFile = File(dir, "init.sh")
        val script = """
            export LD_LIBRARY_PATH='$prefix/lib'
            export PATH='$prefix/bin:$prefix/bin/applets:/system/bin'
            export PREFIX='$prefix'
            export HOME='$home'
            export TMPDIR='$prefix/tmp'
            export LANG='en_US.UTF-8'
            export TERM='xterm-256color'
            export COLORTERM='truecolor'
            cd "${'$'}HOME"
            echo ''
            echo '  =^._.^= MeowHub Terminal'
            echo ''
            if [ -x '$prefix/bin/bash' ]; then
                export SHELL='$prefix/bin/bash'
                exec '$prefix/bin/bash' --norc --noprofile -c 'export PS1="meow\\\$ "; exec bash'
            fi
            export PS1='meow$ '
        """.trimIndent() + "\n"
        initFile.writeText(script)
        Log.i(TAG, "Wrote init.sh to ${initFile.absolutePath}")
    }

    fun switchToSession(index: Int) {
        if (index in sessions.indices) {
            currentSessionIndex = index
        }
    }

    fun findSessionIndex(session: TerminalSession): Int = sessions.indexOf(session)

    fun getSessionLabels(gatewaySession: TerminalSession? = null): List<Pair<Int, String>> {
        return sessions.mapIndexed { index, session ->
            val label = when (session) {
                gatewaySession -> "Gateway"
                else -> "Shell ${index + 1}"
            }
            index to label
        }
    }

    fun getCurrentSessionIndex(): Int = currentSessionIndex

    fun removeSession(session: TerminalSession) {
        val index = sessions.indexOf(session)
        if (index >= 0) {
            sessions.removeAt(index)
            if (currentSessionIndex >= sessions.size) {
                currentSessionIndex = (sessions.size - 1).coerceAtLeast(0)
            }
            Log.i(TAG, "Removed session at index $index, remaining=${sessions.size}")
        }
    }

    fun executeCommand(command: String) {
        val session = currentSession ?: run {
            Log.w(TAG, "executeCommand: no current session")
            return
        }
        Log.d(TAG, "executeCommand: $command")
        session.write("$command\n")
    }

    fun cleanup() {
        Log.i(TAG, "cleanup: finishing ${sessions.size} sessions")
        sessions.forEach { it.finishIfRunning() }
        sessions.clear()
    }

    private fun buildEnvironment(): Array<String> {
        val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        val home = TermuxConstants.TERMUX_HOME_DIR_PATH
        val tmpDir = "$prefix/tmp"

        File(tmpDir).mkdirs()
        File(home).mkdirs()

        val envMap = mutableMapOf(
            "TERM" to "xterm-256color",
            "HOME" to home,
            "PREFIX" to prefix,
            "TMPDIR" to tmpDir,
            "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin",
            "LANG" to "en_US.UTF-8",
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "COLORTERM" to "truecolor",
            "SHELL" to "$prefix/bin/sh",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system"
        )

        return envMap.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    companion object {
        private const val TAG = "MeowTermuxService"
    }
}

class MeowTerminalViewClient : TerminalViewClient {

    var ctrlDown = false
    var altDown = false
    var shiftDown = false
    var fnDown = false

    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && e.action == KeyEvent.ACTION_DOWN) {
            return false
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrlDown
    override fun readAltKey(): Boolean = altDown
    override fun readShiftKey(): Boolean = shiftDown
    override fun readFnKey(): Boolean = fnDown

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}

    override fun logError(tag: String, message: String) = Log.e(tag, message).let {}
    override fun logWarn(tag: String, message: String) = Log.w(tag, message).let {}
    override fun logInfo(tag: String, message: String) = Log.i(tag, message).let {}
    override fun logDebug(tag: String, message: String) = Log.d(tag, message).let {}
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message).let {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Log.e(tag, message, e).let {}
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, "Error", e).let {}
}

class MeowTerminalSessionClient(private val context: Context) : TerminalSessionClient {

    var onSessionUpdate: (() -> Unit)? = null
    var onSessionFinishedCallback: ((TerminalSession) -> Unit)? = null
    var onOutputLineCallback: ((String) -> Unit)? = null

    private var lastCheckedRow = -1

    override fun onTextChanged(changedSession: TerminalSession) {
        onSessionUpdate?.invoke()

        val callback = onOutputLineCallback ?: return
        val emulator = changedSession.emulator ?: return
        val screen = emulator.screen ?: return
        val cursorRow = emulator.cursorRow
        val columns = emulator.mColumns

        if (cursorRow == lastCheckedRow) return
        val startRow = if (lastCheckedRow in 0 until cursorRow) lastCheckedRow else maxOf(0, cursorRow - 2)
        lastCheckedRow = cursorRow

        for (row in startRow..cursorRow) {
            val line = screen.getSelectedText(0, row, columns, row + 1)?.trim()
            if (!line.isNullOrEmpty()) {
                callback(line)
            }
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        try {
            val emulator = finishedSession.emulator
            if (emulator != null) {
                val transcript = emulator.screen?.getTranscriptText() ?: ""
                val lastLines = transcript.lines().takeLast(20).joinToString("\n")
                Log.w("TerminalSession", "Session finished. Last output:\n$lastLines")
            }
        } catch (e: Exception) {
            Log.e("TerminalSession", "Failed to read session output on finish", e)
        }
        onSessionUpdate?.invoke()
        onSessionFinishedCallback?.invoke(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            session?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int = 0

    override fun logError(tag: String, message: String) = Log.e(tag, message).let {}
    override fun logWarn(tag: String, message: String) = Log.w(tag, message).let {}
    override fun logInfo(tag: String, message: String) = Log.i(tag, message).let {}
    override fun logDebug(tag: String, message: String) = Log.d(tag, message).let {}
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message).let {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Log.e(tag, message, e).let {}
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, "Error", e).let {}
}
