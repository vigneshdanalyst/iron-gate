package com.irongate

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppBlockerAccessibilityService : AccessibilityService() {
    private lateinit var db: IronGateDatabase
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var blockedPackages: Set<String> = emptySet()
    private var lockEndsAtMillis: Long = 0L
    private var isLockActive = false
    private var activeBlockedPackage: String? = null

    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val ensureOverlay = object : Runnable {
        override fun run() {
            if (isLockActive && activeBlockedPackage != null && overlayView?.windowToken == null) {
                showOverlay()
            }
            overlayHandler.postDelayed(this, 500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = IronGateDatabase.getInstance(this)
        windowManager = getSystemService(WindowManager::class.java)
        observeState()
        overlayHandler.post(ensureOverlay)
    }

    private fun observeState() {
        scope.launch {
            db.dao().observeBlockedApps().collectLatest { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }
        scope.launch {
            db.dao().observeActiveLockSession().collectLatest { session ->
                isLockActive = session?.isActive == true
                lockEndsAtMillis = session?.endsAtMillis ?: 0L
                if (!isLockActive) {
                    activeBlockedPackage = null
                    hideOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (!isLockActive) return
        if (foregroundPackage == this.packageName) return

        if (blockedPackages.contains(foregroundPackage)) {
            activeBlockedPackage = foregroundPackage
            showOverlay()
        } else if (activeBlockedPackage != null) {
            activeBlockedPackage = null
            hideOverlay()
        }
    }

    private fun showOverlay() {
        if (overlayView?.windowToken != null) return
        val content = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _: View, _: MotionEvent -> true }
            addView(TextView(this@AppBlockerAccessibilityService).apply {
                setTextColor(Color.WHITE)
                textSize = 21f
                gravity = Gravity.CENTER
                text = "\uD83D\uDD12 BLOCKED by Iron Gate - Lock active until ${formatTime(lockEndsAtMillis)}"
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE
        )
        params.gravity = Gravity.TOP
        params.screenOrientation = -1
        overlayView = content
        windowManager.addView(content, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            if (it.windowToken != null) {
                windowManager.removeViewImmediate(it)
            }
        }
        overlayView = null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlayHandler.removeCallbacks(ensureOverlay)
        hideOverlay()
        super.onDestroy()
    }

    private fun formatTime(epochMillis: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMillis))
    }
}
