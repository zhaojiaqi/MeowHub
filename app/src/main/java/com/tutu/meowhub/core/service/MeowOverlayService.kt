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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private var actionLabelView: ComposeView? = null
    private var actionLabelParams: WindowManager.LayoutParams? = null
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var engineObserverJob: Job? = null
    private var actionLabelJob: Job? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleOwner.onCreate()
        showOverlay()
        showActionLabel()
        observeEngineFinish()
        observeActionLabel()
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
        actionLabelJob?.cancel()
        dismissResult()
        removeActionLabel()
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
        overlayParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                OverlayContent(
                    client = MeowApp.instance.tutuClient,
                    onDragUpdate = { dx, dy ->
                        params.x -= dx.toInt()
                        params.y += dy.toInt()
                        windowManager?.updateViewLayout(this, params)
                        syncActionLabelPosition()
                    },
                    onClose = { stopSelf() },
                    onRequestFocus = {
                        params.flags = params.flags and
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        windowManager?.updateViewLayout(this, params)
                    },
                    onReleaseFocus = {
                        params.flags = params.flags or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        windowManager?.updateViewLayout(this, params)
                    }
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

    private fun showActionLabel() {
        val wm = windowManager ?: return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = overlayParams?.x ?: 0
            y = (overlayParams?.y ?: 200) + 60
        }
        actionLabelParams = lp

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val label by MeowApp.instance.chatActionLabel.collectAsState()
                ActionLabelContent(label)
            }
        }

        actionLabelView = view
        wm.addView(view, lp)
    }

    @Composable
    private fun ActionLabelContent(label: String?) {
        if (label != null) {
            val labelBg = androidx.compose.ui.graphics.Color(0xFF1E1E1E).copy(alpha = 0.8f)
            androidx.compose.material3.Text(
                text = label,
                modifier = androidx.compose.ui.Modifier
                    .widthIn(max = 160.dp)
                    .background(color = labelBg, shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    private fun syncActionLabelPosition() {
        val lp = actionLabelParams ?: return
        val op = overlayParams ?: return
        lp.x = op.x
        lp.y = op.y + 60
        actionLabelView?.let {
            try { windowManager?.updateViewLayout(it, lp) } catch (_: Exception) {}
        }
    }

    private fun removeActionLabel() {
        actionLabelView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        actionLabelView = null
    }

    private fun observeActionLabel() {
        actionLabelJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            MeowApp.instance.chatActionLabel.collect {
                syncActionLabelPosition()
            }
        }
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
