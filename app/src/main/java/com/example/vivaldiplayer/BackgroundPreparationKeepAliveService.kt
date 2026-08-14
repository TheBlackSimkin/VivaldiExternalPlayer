package com.example.vivaldiplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * Foreground process lease used only while BG-share preparation is active.
 *
 * WHY THIS EXISTS
 * ---------------
 * Build #202 proved that normal BG shares begin without opening a dashboard tab,
 * but real-device timing strongly suggested Android was deprioritizing or
 * destroying the stopped share Activity after it moved behind Vivaldi. That
 * silently sent the tab back through the old WorkManager recovery path.
 *
 * This service does NOT resolve media itself and it never creates ExoPlayer.
 * Its only job is to keep the app process in foreground-service importance while
 * the already-created BackgroundShareActivityV2 owns its short WebView/direct
 * preparation. Android therefore gets an honest foreground-service notification
 * while the preparation lifecycle is active.
 *
 * The service also watches the persistent tab store and journals state changes so
 * QA can reconstruct fast technical transitions later from Settings -> Share
 * operations log.
 */
class BackgroundPreparationKeepAliveService : Service(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val ACTION_ACQUIRE = "com.example.vivaldiplayer.BG_PREP_ACQUIRE"
        private const val EXTRA_TOKEN = "bg_prep_token"
        private const val CHANNEL_ID = "background_preparation"
        private const val NOTIFICATION_ID = 2407
        private const val PREFS_NAME = "video_tab_store"
        private const val STOP_GRACE_MS = 1_000L

        private var liveInstance = WeakReference<BackgroundPreparationKeepAliveService>(null)

        /**
         * Called synchronously from the user-launched BG share Activity lifecycle.
         * Starting here is important: Android still sees a user-visible transition.
         */
        fun acquire(context: Context, token: String) {
            val intent = Intent(context, BackgroundPreparationKeepAliveService::class.java)
                .setAction(ACTION_ACQUIRE)
                .putExtra(EXTRA_TOKEN, token)

            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.onFailure { error ->
                OperationLog.record(
                    context,
                    event = "KEEPALIVE_START_FAILED",
                    detail = error.message ?: error.toString()
                )
            }
        }

        /** Release one Activity lease without starting a new background service. */
        fun release(token: String) {
            liveInstance.get()?.releaseLease(token)
        }
    }

    private data class TabSnapshot(
        val state: VideoTabStore.PreparationState,
        val updatedAtMs: Long,
        val tech: String,
        val techAtMs: Long,
        val lastError: String,
        val directStartedAtMs: Long,
        val directFinishedAtMs: Long,
        val browserRequestedAtMs: Long,
        val browserDiscoveryAtMs: Long,
        val readyAtMs: Long
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val leases = linkedSetOf<String>()
    private val previousTabs = mutableMapOf<String, TabSnapshot>()
    private lateinit var preferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        liveInstance = WeakReference(this)
        VideoTabStore.initialize(applicationContext)
        createNotificationChannel()
        promoteToForeground()

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(this)

        OperationLog.record(this, "KEEPALIVE_SERVICE_CREATED")
        captureTabChanges(reason = "service-start")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACQUIRE) {
            val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty().trim()
            if (token.isNotBlank()) {
                synchronized(leases) {
                    leases.add(token)
                }
                OperationLog.record(
                    this,
                    event = "KEEPALIVE_LEASE_ACQUIRED",
                    detail = "token=$token active=${leaseCount()}"
                )
                captureTabChanges(reason = "lease-acquired")
            }
        }

        // Preparation is always tied to explicit share Activities. Do not restart
        // this service after the process is killed with no corresponding Activity.
        return START_NOT_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        captureTabChanges(reason = "tab-store-change")
    }

    private fun releaseLease(token: String) {
        val removed = synchronized(leases) {
            leases.remove(token)
        }

        if (removed) {
            captureTabChanges(reason = "lease-release")
            OperationLog.record(
                this,
                event = "KEEPALIVE_LEASE_RELEASED",
                detail = "token=$token active=${leaseCount()}"
            )
        }

        if (leaseCount() == 0) {
            mainHandler.postDelayed({
                if (leaseCount() == 0) {
                    captureTabChanges(reason = "service-stop")
                    OperationLog.record(this, "KEEPALIVE_SERVICE_STOPPED")
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, STOP_GRACE_MS)
        }
    }

    /**
     * Convert the latest persistent state into a readable chronological line.
     * We intentionally journal lifecycle/timing fields, not media payload JSON.
     */
    private fun captureTabChanges(reason: String) {
        VideoTabStore.allTabs().forEach { tab ->
            val current = TabSnapshot(
                state = tab.preparationState,
                updatedAtMs = tab.updatedAtMs,
                tech = tab.lastTechnicalPreparationStage,
                techAtMs = tab.lastTechnicalStageAtMs,
                lastError = tab.lastError,
                directStartedAtMs = tab.directResolverStartedAtMs,
                directFinishedAtMs = tab.directResolverFinishedAtMs,
                browserRequestedAtMs = tab.browserStageRequestedAtMs,
                browserDiscoveryAtMs = tab.browserDiscoveryStartedAtMs,
                readyAtMs = tab.readyAtMs
            )

            val previous = previousTabs[tab.id]
            if (previous == current) return@forEach
            previousTabs[tab.id] = current

            OperationLog.record(
                this,
                event = "TAB_SNAPSHOT",
                tabId = tab.id,
                detail = buildString {
                    append("reason=$reason")
                    append(" state=${tab.preparationState.name}")
                    append(" tech=${tab.lastTechnicalPreparationStage.ifBlank { "-" }}")
                    append(" created=${tab.createdAtMs}")
                    append(" requested=${tab.preparationRequestedAtMs}")
                    append(" host=${tab.preparationHostCreatedAtMs}")
                    append(" directStart=${tab.directResolverStartedAtMs}")
                    append(" directEnd=${tab.directResolverFinishedAtMs}")
                    append(" browserRequest=${tab.browserStageRequestedAtMs}")
                    append(" webView=${tab.browserWebViewCreatedAtMs}")
                    append(" browserStart=${tab.browserDiscoveryStartedAtMs}")
                    append(" ready=${tab.readyAtMs}")
                    if (tab.lastError.isNotBlank()) {
                        append(" error=${tab.lastError.take(240)}")
                    }
                }
            )
        }
    }

    private fun promoteToForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bg_preparation_notification_title))
            .setContentText(getString(R.string.bg_preparation_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bg_preparation_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.bg_preparation_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun leaseCount(): Int = synchronized(leases) { leases.size }

    override fun onDestroy() {
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        mainHandler.removeCallbacksAndMessages(null)
        captureTabChanges(reason = "service-destroy")
        OperationLog.record(this, "KEEPALIVE_SERVICE_DESTROYED")
        liveInstance = WeakReference(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
