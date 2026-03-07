package com.tutu.meowhub.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.MainActivity
import com.tutu.meowhub.R
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.feature.overlay.OverlayContent
import com.tutu.meowhub.feature.overlay.ResultOverlayContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MeowOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var resultView: ComposeView? = null
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var engineObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleOwner.onCreate()
        showOverlay()
        observeEngineFinish()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engineObserverJob?.cancel()
        dismissResult()
        removeOverlay()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 200
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                OverlayContent(
                    client = MeowApp.instance.tutuClient,
                    onDragUpdate = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager?.updateViewLayout(this, params)
                    },
                    onClose = { stopSelf() }
                )
            }
        }

        overlayView = composeView
        windowManager?.addView(composeView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun observeEngineFinish() {
        val engine = MeowApp.instance.skillEngine
        engineObserverJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            engine.state
                .map { it }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == SkillEngine.EngineState.FINISHED ||
                        state == SkillEngine.EngineState.STOPPED ||
                        state == SkillEngine.EngineState.ERROR
                    ) {
                        val result = engine.runResult.value ?: return@collect
                        val skill = engine.currentSkill.value
                        showResultOverlay(
                            skillName = skill?.displayName ?: "Skill",
                            result = result
                        )
                    }
                }
        }
    }

    private fun showResultOverlay(skillName: String, result: SkillEngine.RunResult) {
        dismissResult()

        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                CompositionLocalProvider(
                    LocalOnBackPressedDispatcherOwner provides lifecycleOwner
                ) {
                    ResultOverlayContent(
                        skillName = skillName,
                        result = result,
                        onDismiss = { dismissResult() }
                    )
                }
            }
        }

        resultView = view
        wm.addView(view, params)
    }

    private fun dismissResult() {
        resultView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        resultView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MeowOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeowHub")
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "meow_overlay"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.tutu.meowhub.STOP_OVERLAY"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MeowOverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeowOverlayService::class.java))
        }
    }
}
