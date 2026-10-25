package com.example.pet.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.pet.R
import com.example.pet.data.AssetManifest
import com.example.pet.data.PetRepository
import com.example.pet.model.Pet
import com.example.pet.render.PetRenderers
import com.example.pet.ui.PreviewLoader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Foreground service owning the floating pet window. The overlay is only added when
 * SYSTEM_ALERT_WINDOW is granted; otherwise the service stops instead of crashing.
 *
 * The renderer for the active pet is built off the main thread and swapped into the
 * already-visible view, so the window appears immediately (placeholder first).
 */
class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: PetRepository
    private var pets: List<Pet> = emptyList()
    private var overlay: OverlayView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient()
    private val previewLoader = PreviewLoader(client)

    /** Pet whose renderer is loaded or loading; null means "placeholder shown". */
    private var renderedPetId: String? = null
    private var loadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        pets = AssetManifest.load(assets.open("manifest.json").bufferedReader().readText())
        repository = PetRepository(client, filesDir, pets)
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
        showOverlay()
        loadActivePet()
        return START_STICKY
    }

    /** Loads the renderer for the pet the user activated (or had active at boot). */
    private fun loadActivePet() {
        val wanted = repository.activePetId()
        if (wanted == renderedPetId) return
        renderedPetId = wanted
        loadJob?.cancel()
        loadJob = scope.launch {
            val renderer = withContext(Dispatchers.IO) {
                val pet = pets.firstOrNull { it.id == wanted }
                pet?.let {
                    PetRenderers.create(it, File(filesDir, "pets")) { p ->
                        previewLoader.load(p.previewUrl)
                    }
                }
            }
            if (renderer == null) {
                // Unusable files or failed preview: show the placeholder and let the
                // next start (app resume/boot) retry instead of staying blank forever.
                renderedPetId = null
            }
            overlay?.let { view ->
                view.renderer = renderer
                view.invalidate()
            }
        }
    }

    private fun showOverlay() {
        if (overlay != null) return
        val size = (PET_SIZE_DP * resources.displayMetrics.density).toInt()
        val view = OverlayView(this, null)
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            val screen = screenSize()
            val initial = OverlayView.clampPosition(screen.x - size, screen.y - size, screen.x, screen.y, size, size)
            x = initial.x
            y = initial.y
        }
        view.onDrag = { dx, dy ->
            val screen = screenSize()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val v = overlay ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val screen = screenSize()
        val p = OverlayView.clampPosition(lp.x, lp.y, screen.x, screen.y, v.width, v.height)
        lp.x = p.x
        lp.y = p.y
        try {
            windowManager.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
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
        scope.cancel()
        overlay?.let(windowManager::removeViewImmediate)
        overlay = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.pet.action.STOP"
        private const val PET_SIZE_DP = 160

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PetOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }
}
