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
 * Foreground owner for automatic BG-share preparation.
 *
 * Builds #205/#225/#227 established that Activity ownership is the wrong layer
 * for this phone: a stopped Activity can be destroyed immediately, while even a
 * transparent NOT_TOUCHABLE/NOT_FOCUSABLE Activity on display 0 can still make
 * Vivaldi unresponsive, especially during repeated shares.
 *
 * The service now owns BackgroundPrivateDisplayPreparationSession instances.
 * Each session creates an app-private virtual display plus a Presentation/WebView
 * on that private display. No preparation Activity is launched on the physical
 * display. The service/session code resolves URLs only and never creates
 * PlayerActivity, Media3 or ExoPlayer, so this is not background playback.
 *
 * The service also journals persistent tab-state changes so exported operations
 * logs can reconstruct lifecycle/timing without recording media imagery,
 * credentials or request-header values.
 */
class BackgroundPreparationKeepAliveService : Service(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val ACTION_ACQUIRE = "com.example.vivaldiplayer.BG_PREP_ACQUIRE"
        private const val EXTRA_TOKEN = "bg_prep_token"
        private const val EXTRA_TAB_ID = "bg_prep_tab_id"
        private const val EXTRA_URL = "bg_prep_url"
        private const val CHANNEL_ID = "background_preparation"
        private const val NOTIFICATION_ID = 2407
        private const val PREFS_NAME = "video_tab_store"
        private const val STOP_GRACE_MS = 1_000L

        private var liveInstance = WeakReference<BackgroundPreparationKeepAliveService>(null)

        /**
         * Acquire foreground-service importance and optionally start one real
         * private-display preparation session.
         *
         * tabId/sourceUrl have defaults for source compatibility with older
         * internal callers which used this service only as a process lease.
         */
        fun acquire(
            context: Context,
            token: String,
            tabId: String = "",
            sourceUrl: String = ""
        ) {
            val intent = Intent(context, BackgroundPreparationKeepAliveService::class.java)
                .setAction(ACTION_ACQUIRE)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_TAB_ID, tabId)
                .putExtra(EXTRA_URL, sourceUrl)

            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.onFailure { error ->
                OperationLog.record(
                    context,
                    event = "KEEPALIVE_START_FAILED",
                    tabId = tabId.takeIf { it.isNotBlank() },
                    detail = error.message ?: error.toString()
                )
            }
        }

        /** Release one legacy lease without starting a new background service. */
        fun release(token: String) {
            liveInstance.get()?.releaseLease(token)
        }

        /**
         * Close an already-running revival session when the user starts foreground
         * playback. Normal BG-share sessions are not targeted; the coordinator
         * only supplies tokens it created with the revive-* prefix.
         */
        fun suspendRevivalSession(token: String) {
            liveInstance.get()?.suspendActiveRevivalSession(token)
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
    private val sessions = linkedMapOf<String, BackgroundPrivateDisplayPreparationSession>()
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
            val tabId = intent.getStringExtra(EXTRA_TAB_ID).orEmpty().trim()
            val sourceUrl = intent.getStringExtra(EXTRA_URL).orEmpty().trim()

            if (token.isNotBlank()) {
                synchronized(leases) {
                    leases.add(token)
                }

                OperationLog.record(
                    this,
                    event = "KEEPALIVE_LEASE_ACQUIRED",
                    tabId = tabId.takeIf { it.isNotBlank() },
                    detail = "token=$token active=${leaseCount()}"
                )
                captureTabChanges(reason = "lease-acquired")

                if (tabId.isNotBlank() && isHttpUrl(sourceUrl)) {
                    startPrivateDisplaySession(token, tabId, sourceUrl)
                } else {
                    /*
                     * Old internal callers may still acquire a lease without
                     * asking this service to own preparation. Keep that behavior
                     * compatible while the V2 share path always supplies all
                     * three values.
                     */
                    OperationLog.record(
                        this,
                        event = "KEEPALIVE_LEASE_ONLY",
                        detail = "token=$token"
                    )
                }
            }
        }

        // Preparation is tied to an explicit user share. Never resurrect it
        // automatically after Android kills the process.
        return START_NOT_STICKY
    }

    /**
     * Start exactly one session for a share token.
     *
     * The session object is created on the service/main thread, which is also the
     * correct thread for Presentation and WebView creation.
     */
    private fun startPrivateDisplaySession(
        token: String,
        tabId: String,
        sourceUrl: String
    ) {
        if (sessions.containsKey(token)) {
            OperationLog.record(
                this,
                event = "PRIVATE_PRESENTATION_DUPLICATE_START_IGNORED",
                tabId = tabId,
                detail = "token=$token"
            )
            return
        }

        VideoTabStore.markTechnicalStage(tabId, "PRIVATE_PRESENTATION_SERVICE_STARTING")

        val session = BackgroundPrivateDisplayPreparationSession(
            service = this,
            sourceUrl = sourceUrl,
            tabId = tabId,
            sessionToken = token,
            onFinished = ::onPrivateDisplaySessionFinished
        )

        sessions[token] = session

        OperationLog.record(
            this,
            event = "PRIVATE_PRESENTATION_SERVICE_SESSION_STARTED",
            tabId = tabId,
            detail = "token=$token activeSessions=${sessions.size}"
        )

        runCatching {
            session.start()
        }.onFailure { error ->
            sessions.remove(token)
            VideoTabStore.markError(
                tabId,
                "Private-display preparation session failed to start: ${error.message ?: error}"
            )
            VideoTabStore.markTechnicalStage(tabId, "PRIVATE_PRESENTATION_START_FAILED")
            OperationLog.record(
                this,
                event = "PRIVATE_PRESENTATION_START_FAILED",
                tabId = tabId,
                detail = error.message ?: error.toString()
            )
            releaseLease(token)
        }
    }

    /** Session completion returns its foreground lease to the service. */
    private fun onPrivateDisplaySessionFinished(token: String) {
        /*
         * Session callbacks are currently main-thread callbacks. Posting here
         * makes that ownership explicit and keeps the method safe if a future
         * resolver completion originates on another thread.
         */
        mainHandler.post {
            val removed = sessions.remove(token)
            if (removed != null) {
                OperationLog.record(
                    this,
                    event = "PRIVATE_PRESENTATION_SERVICE_SESSION_FINISHED",
                    detail = "token=$token activeSessions=${sessions.size}"
                )
            }
            releaseLease(token)
        }
    }

    private fun suspendActiveRevivalSession(token: String) {
        if (!token.startsWith("revive-")) return
        val session = sessions[token] ?: return

        OperationLog.record(
            this,
            event = "PRIVATE_PRESENTATION_REVIVAL_SUSPENDED_FOR_PLAYER",
            detail = "token=$token activeSessions=${sessions.size}"
        )

        runCatching { session.closeFromServiceDestroy() }
        sessions.remove(token)
        releaseLease(token)
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
                if (leaseCount() == 0 && sessions.isEmpty()) {
                    captureTabChanges(reason = "service-stop")
                    OperationLog.record(this, "KEEPALIVE_SERVICE_STOPPED")
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, STOP_GRACE_MS)
        }
    }

    /**
     * Convert persistent state into chronological technical diagnostics only.
     * Media payload JSON and credential/header values are intentionally excluded.
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

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    override fun onDestroy() {
        /*
         * If Android destroys the foreground service unexpectedly, destroy all
         * private-display windows/WebViews synchronously and mark unresolved tabs
         * as failed rather than leaving them stuck in RESOLVING forever.
         */
        sessions.values.toList().forEach { session ->
            runCatching { session.closeFromServiceDestroy() }
        }
        sessions.clear()

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
