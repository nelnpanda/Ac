package com.nelnpanda.ac

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
            private set

        const val MIN_INTERVAL = 100L
        const val MAX_INTERVAL = 10_000L
        const val INTERVAL_STEP = 100L
    }

    private lateinit var wm: WindowManager
    private var fabView: View? = null
    private var panelView: View? = null
    private var targetView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var clickX = 0f
    private var clickY = 0f
    private var intervalMs = 1000L

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                performClick(clickX, clickY)
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        clickX = dm.widthPixels / 2f
        clickY = dm.heightPixels / 2f
        showFab()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() = stopClicking()

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        safeRemove(fabView)
        safeRemove(panelView)
        safeRemove(targetView)
        instance = null
    }

    // ─── FAB ─────────────────────────────────────────────────────────────────

    private fun showFab() {
        val view = inflate(R.layout.floating_button)
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }

        makeDraggable(view, params) { togglePanel() }
        fabView = view
        wm.addView(view, params)
        refreshFabIcon()
    }

    private fun refreshFabIcon() {
        fabView?.findViewById<TextView>(R.id.tvFabIcon)?.text = if (isRunning) "■" else "▶"
    }

    // ─── Control Panel ────────────────────────────────────────────────────────

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return

        val view = inflate(R.layout.floating_panel)
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        ).apply { gravity = Gravity.CENTER }

        val tvInterval = view.findViewById<TextView>(R.id.tvInterval)
        val tvTarget = view.findViewById<TextView>(R.id.tvTarget)
        val btnStartStop = view.findViewById<Button>(R.id.btnStartStop)

        tvInterval.text = formatMs(intervalMs)
        tvTarget.text = formatTarget()

        view.findViewById<Button>(R.id.btnMinus).setOnClickListener {
            intervalMs = maxOf(MIN_INTERVAL, intervalMs - INTERVAL_STEP)
            tvInterval.text = formatMs(intervalMs)
        }
        view.findViewById<Button>(R.id.btnPlus).setOnClickListener {
            intervalMs = minOf(MAX_INTERVAL, intervalMs + INTERVAL_STEP)
            tvInterval.text = formatMs(intervalMs)
        }
        view.findViewById<Button>(R.id.btnSetTarget).setOnClickListener {
            hidePanel()
            showTargetSelector()
        }

        btnStartStop.text = if (isRunning) "Stop" else "Start"
        btnStartStop.setOnClickListener {
            if (isRunning) {
                stopClicking()
                btnStartStop.text = "Start"
            } else {
                hidePanel()
                startClicking()
            }
            refreshFabIcon()
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener { hidePanel() }

        panelView = view
        wm.addView(view, params)
    }

    private fun hidePanel() {
        safeRemove(panelView)
        panelView = null
    }

    // ─── Target Selector ─────────────────────────────────────────────────────

    private fun showTargetSelector() {
        if (targetView != null) return

        val view = inflate(R.layout.floating_target)
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // No FLAG_NOT_TOUCH_MODAL → modal (captures all touches); FLAG_NOT_FOCUSABLE keeps keyboard hidden
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clickX = event.rawX
                clickY = event.rawY
                dismissTargetSelector()
            }
            true
        }

        view.findViewById<Button>(R.id.btnCancelTarget).setOnClickListener {
            dismissTargetSelector()
        }

        targetView = view
        wm.addView(view, params)
    }

    private fun dismissTargetSelector() {
        safeRemove(targetView)
        targetView = null
    }

    // ─── Click Engine ─────────────────────────────────────────────────────────

    private fun startClicking() {
        isRunning = true
        handler.post(clickRunnable)
    }

    private fun stopClicking() {
        isRunning = false
        handler.removeCallbacks(clickRunnable)
    }

    private fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        var initX = 0; var initY = 0
        var initTouchX = 0f; var initTouchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initTouchX).toInt()
                    val dy = (event.rawY - initTouchY).toInt()
                    if (!moved && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) moved = true
                    params.x = initX + dx
                    params.y = initY + dy
                    wm.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun inflate(layoutId: Int): View =
        LayoutInflater.from(this).inflate(layoutId, null)

    private fun safeRemove(view: View?) {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    private fun formatMs(ms: Long) = "${ms}ms"

    private fun formatTarget() = "Target: (${clickX.toInt()}, ${clickY.toInt()})"
}
