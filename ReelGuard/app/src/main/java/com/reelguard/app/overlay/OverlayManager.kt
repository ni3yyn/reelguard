package com.reelguard.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.reelguard.app.ui.theme.ReelGuardColors
import com.reelguard.app.ui.theme.ReelGuardTheme
import kotlin.math.abs

enum class OverlayStep {
    CONFIRMATION,
    TIME_LIMIT
}

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Full-screen blocking overlay state
    private var blockingView: FrameLayout? = null
    private var blockingLifecycleOwner: OverlayLifecycleOwner? = null
    private var currentStep by mutableStateOf(OverlayStep.CONFIRMATION)

    private var onContinueCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onSelectMinutesCallback: ((Int) -> Unit)? = null

    // Floating draggable "Snap UI" debug button state
    private var snapPillView: ComposeView? = null
    private var snapPillLifecycleOwner: OverlayLifecycleOwner? = null
    private var snapPillParams: WindowManager.LayoutParams? = null
    private var onSnapUiCallback: (() -> Unit)? = null

    fun showConfirmationOverlay(
        onContinueAnyway: () -> Unit,
        onCancel: () -> Unit,
        onSelectMinutes: (Int) -> Unit
    ) {
        mainHandler.post {
            onContinueCallback = onContinueAnyway
            onCancelCallback = onCancel
            onSelectMinutesCallback = onSelectMinutes
            currentStep = OverlayStep.CONFIRMATION
            attachBlockingIfNeeded()
        }
    }

    fun switchToTimeLimitOverlay() {
        mainHandler.post {
            currentStep = OverlayStep.TIME_LIMIT
            if (blockingView == null) {
                attachBlockingIfNeeded()
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            detachBlockingInternal()
        }
    }

    fun showSnapUiFloatingButton(onSnapClick: () -> Unit) {
        mainHandler.post {
            onSnapUiCallback = onSnapClick
            attachSnapPillIfNeeded()
        }
    }

    fun hideSnapUiFloatingButton() {
        mainHandler.post {
            detachSnapPillInternal()
        }
    }

    fun dismissAll() {
        mainHandler.post {
            detachBlockingInternal()
            detachSnapPillInternal()
        }
    }

    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun attachBlockingIfNeeded() {
        if (blockingView != null) return

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        blockingLifecycleOwner = owner

        val view = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onCancelCallback?.invoke()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            addView(
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        ReelGuardTheme {
                            BlockingOverlayContent(
                                step = currentStep,
                                onContinueAnyway = { onContinueCallback?.invoke() },
                                onCancel = { onCancelCallback?.invoke() },
                                onSelectMinutes = { minutes -> onSelectMinutesCallback?.invoke(minutes) }
                            )
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            )
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching {
            windowManager.addView(view, layoutParams)
            blockingView = view
        }.onFailure {
            owner.onDestroy()
            blockingLifecycleOwner = null
            blockingView = null
        }
    }

    private fun detachBlockingInternal() {
        val view = blockingView
        val owner = blockingLifecycleOwner

        blockingView = null
        blockingLifecycleOwner = null
        onContinueCallback = null
        onCancelCallback = null
        onSelectMinutesCallback = null

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
            view.setViewTreeLifecycleOwner(null)
            view.setViewTreeViewModelStoreOwner(null)
            view.setViewTreeSavedStateRegistryOwner(null)
        }

        owner?.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSnapPillIfNeeded() {
        if (snapPillView != null) return

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        snapPillLifecycleOwner = owner

        val density = context.resources.displayMetrics.density
        val initialX = (16 * density).toInt()
        val initialY = (96 * density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = initialX
            y = initialY
        }
        snapPillParams = layoutParams

        var startX = 0
        var startY = 0
        var touchDownRawX = 0f
        var touchDownRawY = 0f
        var moved = false
        val dragThresholdPx = 10 * density

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            setContent {
                ReelGuardTheme {
                    SnapUiFloatingPill()
                }
            }

            setOnTouchListener { _, event ->
                val params = snapPillParams ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchDownRawX = event.rawX
                        touchDownRawY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchDownRawX
                        val dy = event.rawY - touchDownRawY
                        if (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx) {
                            moved = true
                        }
                        if (moved) {
                            // Gravity is TOP | END, so dragging left increases x offset from right edge
                            params.x = (startX - dx.toInt()).coerceAtLeast(0)
                            params.y = (startY + dy.toInt()).coerceAtLeast(0)
                            runCatching {
                                if (isAttachedToWindow) {
                                    windowManager.updateViewLayout(this, params)
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            onSnapUiCallback?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        runCatching {
            windowManager.addView(view, layoutParams)
            snapPillView = view
        }.onFailure {
            owner.onDestroy()
            snapPillLifecycleOwner = null
            snapPillView = null
            snapPillParams = null
        }
    }

    private fun detachSnapPillInternal() {
        val view = snapPillView
        val owner = snapPillLifecycleOwner

        snapPillView = null
        snapPillLifecycleOwner = null
        snapPillParams = null
        onSnapUiCallback = null

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
            view.setViewTreeLifecycleOwner(null)
            view.setViewTreeViewModelStoreOwner(null)
            view.setViewTreeSavedStateRegistryOwner(null)
        }

        owner?.onDestroy()
    }
}

@Composable
private fun SnapUiFloatingPill() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xD9131822),
        border = BorderStroke(1.dp, ReelGuardColors.Accent.copy(alpha = 0.85f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BugReport,
                contentDescription = "Snap UI",
                tint = ReelGuardColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Snap UI",
                color = ReelGuardColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun BlockingOverlayContent(
    step: OverlayStep,
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5080B10))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = ReelGuardColors.Surface,
            border = BorderStroke(1.dp, ReelGuardColors.Border)
        ) {
            when (step) {
                OverlayStep.CONFIRMATION -> ConfirmationStep(
                    onContinueAnyway = onContinueAnyway,
                    onCancel = onCancel
                )
                OverlayStep.TIME_LIMIT -> TimeLimitStep(
                    onSelectMinutes = onSelectMinutes,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun ConfirmationStep(
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ReelGuardColors.AccentMuted,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = ReelGuardColors.Accent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Are you sure?",
            style = MaterialTheme.typography.headlineSmall,
            color = ReelGuardColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "This Reel is outside of a message from a friend. Do you really want to watch?",
            style = MaterialTheme.typography.bodyMedium,
            color = ReelGuardColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinueAnyway,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ReelGuardColors.Accent,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue anyway",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ReelGuardColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ReelGuardColors.TextPrimary
            )
        ) {
            Text(
                text = "Cancel",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun TimeLimitStep(
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val durations = (1..10).toList()

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ReelGuardColors.AccentMuted,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.HourglassTop,
                    contentDescription = null,
                    tint = ReelGuardColors.Accent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose your time limit",
            style = MaterialTheme.typography.headlineSmall,
            color = ReelGuardColors.TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reels will automatically lock and return to Home when the timer hits 00:00.",
            style = MaterialTheme.typography.bodyMedium,
            color = ReelGuardColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(296.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(durations) { minutes ->
                Button(
                    onClick = { onSelectMinutes(minutes) },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReelGuardColors.SurfaceElevated,
                        contentColor = ReelGuardColors.TextPrimary
                    ),
                    border = BorderStroke(1.dp, ReelGuardColors.Border)
                ) {
                    Text(
                        text = "$minutes min",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ReelGuardColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ReelGuardColors.TextSecondary
                )
            ) {
                Text(text = "Go back to Home", fontSize = 14.sp)
            }
        }
    }
}
