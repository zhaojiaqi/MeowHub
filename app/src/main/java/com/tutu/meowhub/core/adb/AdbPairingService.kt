package com.tutu.meowhub.core.adb

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.tutu.meowhub.R
import java.net.ConnectException

@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL = "adb_pairing"
        const val ACTION_PAIRING_RESULT = "com.tutu.meowhub.ADB_PAIRING_RESULT"
        const val EXTRA_SUCCESS = "success"
        private const val TAG = "AdbPairingService"
        private const val NOTIFICATION_ID = 2
        private const val REPLY_REQUEST_ID = 1
        private const val STOP_REQUEST_ID = 2
        private const val START_ACTION = "start"
        private const val STOP_ACTION = "stop"
        private const val REPLY_ACTION = "reply"
        private const val REMOTE_INPUT_KEY = "pairing_code"
        private const val PORT_KEY = "pairing_port"
        private const val ADB_PREFS = "adb_prefs"

        fun startIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(START_ACTION)

        private fun stopIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(STOP_ACTION)

        private fun replyIntent(context: Context, port: Int): Intent =
            Intent(context, AdbPairingService::class.java).setAction(REPLY_ACTION).putExtra(PORT_KEY, port)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var adbMdns: AdbMdns? = null
    private var started = false

    private val observer = Observer<Int> { port ->
        Log.i(TAG, "Pairing service port: $port")
        if (port <= 0) return@Observer
        val notification = createInputNotification(port)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.adb_pairing_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = when (intent?.action) {
            START_ACTION -> onStart()
            REPLY_ACTION -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(REMOTE_INPUT_KEY) ?: ""
                val port = intent.getIntExtra(PORT_KEY, -1)
                if (port != -1) onInput(code.toString(), port) else onStart()
            }
            STOP_ACTION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                null
            }
            else -> return START_NOT_STICKY
        }
        if (notification != null) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            } catch (e: Throwable) {
                Log.e(TAG, "startForeground failed", e)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e is ForegroundServiceStartNotAllowedException
                ) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING, observer).apply { start() }
    }

    private fun stopSearch() {
        if (!started) return
        started = false
        adbMdns?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSearch()
        serviceScope.cancel()
    }

    private fun onStart(): Notification {
        startSearch()
        return searchingNotification
    }

    private fun onInput(code: String, port: Int): Notification {
        serviceScope.launch {
            val host = "127.0.0.1"
            val key = try {
                AdbKey(PreferenceAdbKeyStore(getSharedPreferences(ADB_PREFS, MODE_PRIVATE)), "meowhub")
            } catch (e: Throwable) {
                e.printStackTrace()
                return@launch
            }

            AdbPairingClient(host, port, code, key).runCatching {
                start()
            }.onFailure {
                handleResult(false, it)
            }.onSuccess {
                handleResult(it, null)
            }
        }
        return workingNotification
    }

    private fun handleResult(success: Boolean, exception: Throwable?) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val title: String
        val text: String?

        if (success) {
            Log.i(TAG, "Pairing succeeded")
            title = getString(R.string.adb_pair_succeed_title)
            text = getString(R.string.adb_pair_succeed_text)
            stopSearch()
        } else {
            title = getString(R.string.adb_pair_failed_title)
            text = when (exception) {
                is ConnectException -> getString(R.string.adb_error_connect)
                is AdbInvalidPairingCodeException -> getString(R.string.adb_error_pairing_code)
                is AdbKeyException -> getString(R.string.adb_error_key_store)
                else -> exception?.let { Log.getStackTraceString(it) }
            }
            if (exception != null) Log.w(TAG, "Pairing failed", exception)
        }

        sendBroadcast(Intent(ACTION_PAIRING_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_SUCCESS, success)
        })

        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .build()
        )
        stopSelf()
    }

    private val stopNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this, STOP_REQUEST_ID, stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )
        Notification.Action.Builder(null, getString(R.string.adb_pair_stop_search), pendingIntent).build()
    }

    private val replyNotificationAction by lazy {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(getString(R.string.adb_pair_input_code))
            .build()

        val pendingIntent = PendingIntent.getForegroundService(
            this, REPLY_REQUEST_ID, replyIntent(this, -1),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        Notification.Action.Builder(null, getString(R.string.adb_pair_input_code), pendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        val action = replyNotificationAction
        PendingIntent.getForegroundService(
            this, REPLY_REQUEST_ID, replyIntent(this, port),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return action
    }

    private val searchingNotification by lazy {
        Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.adb_pair_searching))
            .addAction(stopNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification =
        Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.adb_pair_found))
            .addAction(replyNotificationAction(port))
            .build()

    private val workingNotification by lazy {
        Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.adb_pair_working))
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
