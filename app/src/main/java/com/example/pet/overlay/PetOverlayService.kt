package com.example.pet.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.pet.R
import com.example.pet.render.Live2DRenderer

/**
 * Foreground service owning the floating pet window. The overlay is only added when
 * SYSTEM_ALERT_WINDOW is granted; otherwise the service stops instead of crashing.
 */
class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlay: OverlayView? = null
    private var activePetId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Must happen before any early return: started with startForegroundService.
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_PET_ID)?.let(::setPet)
        showOverlay()
        return START_STICKY
    }

    /** Switches the active pet. Renderer selection lands with PetManager (Task 12). */
    fun setPet(petId: String) {
        activePetId = petId
    }

    private fun showOverlay() {
        if (overlay != null) return
        val size = (PET_SIZE_DP * resources.displayMetrics.density).toInt()
        val screen = screenSize()
        // TODO(renderers): pick per pet type once SpriteRenderer exists (Task 7).
        val view = OverlayView(this, Live2DRenderer())
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val initial = OverlayView.clampPosition(screen.x - size, screen.y - size, screen.x, screen.y, size, size)
            x = initial.x
            y = initial.y
        }
        view.onDrag = { dx, dy ->
            val p = OverlayView.clampPosition(
                lp.x + dx, lp.y + dy, screen.x, screen.y, view.width, view.height,
            )
            lp.x = p.x
            lp.y = p.y
            windowManager.updateViewLayout(view, lp)
        }
        try {
            windowManager.addView(view, lp)
        } catch (e: WindowManager.BadTokenException) {
            // Permission revoked between the check and addView.
            stopSelf()
            return
        }
        overlay = view
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setOngoing(true)
            .addAction(0, getString(R.string.overlay_notification_stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        overlay?.let(windowManager::removeViewImmediate)
        overlay = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.pet.action.STOP"
        private const val ACTION_SET_PET = "com.example.pet.action.SET_PET"
        private const val EXTRA_PET_ID = "pet_id"
        private const val PET_SIZE_DP = 160

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PetOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }

        fun setPet(context: Context, petId: String) {
            context.startForegroundService(
                Intent(context, PetOverlayService::class.java)
                    .setAction(ACTION_SET_PET)
                    .putExtra(EXTRA_PET_ID, petId),
            )
        }
    }
}
