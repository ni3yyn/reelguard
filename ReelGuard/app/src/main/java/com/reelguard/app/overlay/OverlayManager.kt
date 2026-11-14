package com.reelguard.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

private enum class OverlayStep {
    CONFIRMATION,
    TIME_LIMIT
}

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var blockingView: FrameLayout? = null
    private var blockingLifecycleOwner: OverlayLifecycleOwner? = null
    private var currentStep by mutableStateOf(OverlayStep.CONFIRMATION)

    private var onContinueCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onSelectMinutesCallback: ((Int) -> Unit)? = null

    fun showConfirmationOverlay(
        onContinueAnyway: () -> Unit,
        onCancel: () -> Unit,
        onSelectMinutes: (Int) -> Unit,
        onFailure: () -> Unit
    ) {
        mainHandler.post {
            onContinueCallback = onContinueAnyway
            onCancelCallback = onCancel
            onSelectMinutesCallback = onSelectMinutes
            currentStep = OverlayStep.CONFIRMATION
            attachBlockingIfNeeded(onFailure)
        }
    }

    fun switchToTimeLimitOverlay() {
        mainHandler.post {
            currentStep = OverlayStep.TIME_LIMIT
            if (blockingView == null) attachBlockingIfNeeded {}
        }
    }

    fun dismiss() {
        mainHandler.post { detachBlockingInternal() }
    }

    fun dismissAll() {
        mainHandler.post { detachBlockingInternal() }
    }

    fun showMessage(message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun attachBlockingIfNeeded(onFailure: () -> Unit) {
        if (blockingView != null) return

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        blockingLifecycleOwner = owner

        val view = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (currentStep == OverlayStep.TIME_LIMIT) {
                        currentStep = OverlayStep.CONFIRMATION
                    } else {
                        onCancelCallback?.invoke()
                    }
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
                                onGoBack = { currentStep = OverlayStep.CONFIRMATION },
                                onSelectMinutes = { selected -> onSelectMinutesCallback?.invoke(selected) }
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            windowManager.addView(view, params)
            blockingView = view
        }.onFailure {
            view.setViewTreeLifecycleOwner(null)
            view.setViewTreeViewModelStoreOwner(null)
            view.setViewTreeSavedStateRegistryOwner(null)
            owner.onDestroy()
            blockingLifecycleOwner = null
            blockingView = null
            onFailure()
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
                if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            }
            view.setViewTreeLifecycleOwner(null)
            view.setViewTreeViewModelStoreOwner(null)
            view.setViewTreeSavedStateRegistryOwner(null)
        }
        owner?.onDestroy()
    }
}

@Composable
private fun BlockingOverlayContent(
    step: OverlayStep,
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit,
    onGoBack: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9E5E8ED))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(27.dp),
                    ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.75f),
                    spotColor = ReelGuardColors.Shadow
                ),
            shape = RoundedCornerShape(27.dp),
            color = ReelGuardColors.SurfaceRaised,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(listOf(Color.White, ReelGuardColors.Border))
            )
        ) {
            when (step) {
                OverlayStep.CONFIRMATION -> ConfirmationStep(
                    onContinueAnyway = onContinueAnyway,
                    onCancel = onCancel
                )
                OverlayStep.TIME_LIMIT -> TimeLimitStep(
                    onSelectMinutes = onSelectMinutes,
                    onCancel = onGoBack
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
        modifier = Modifier.padding(25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = RoundedCornerShape(16.dp),
            color = ReelGuardColors.SurfacePressed,
            shadowElevation = 7.dp,
            border = BorderStroke(1.dp, Color.White)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = ReelGuardColors.Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(19.dp))
        Text(
            text = "Pause before the scroll",
            color = ReelGuardColors.TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = "This Reel is coming from recommendations, not a message from someone you know. Decide how much time you want to spend here.",
            color = ReelGuardColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(23.dp))

        Button(
            onClick = onContinueAnyway,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = ReelGuardColors.AccentDark,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Outlined.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose a time limit", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ReelGuardColors.SurfacePressed,
                contentColor = ReelGuardColors.TextPrimary
            )
        ) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Text("Leave Reels", fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "Friend-shared Reels in your DMs remain available.",
            color = ReelGuardColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimeLimitStep(
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var selectedMinutes by androidx.compose.runtime.remember { mutableStateOf(10) }
    val durations = listOf(5, 10, 15, 20)

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(15.dp),
            color = ReelGuardColors.AccentMuted
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.HourglassTop,
                    contentDescription = null,
                    tint = ReelGuardColors.Accent,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "How long do you want?",
            style = MaterialTheme.typography.headlineSmall,
            color = ReelGuardColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "Your timer starts when you continue. When it ends, ReelGuard closes the Reels session.",
            style = MaterialTheme.typography.bodyMedium,
            color = ReelGuardColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            durations.chunked(2).forEach { rowDurations ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowDurations.forEach { minutes ->
                        val selected = selectedMinutes == minutes
                        Surface(
                            modifier = Modifier.weight(1f).height(58.dp).clickable { selectedMinutes = minutes },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) ReelGuardColors.AccentMuted else ReelGuardColors.SurfaceRaised,
                            shadowElevation = if (selected) 2.dp else 6.dp,
                            border = BorderStroke(
                                1.dp,
                                if (selected) ReelGuardColors.Accent else Color.White
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$minutes min",
                                    color = if (selected) ReelGuardColors.TextPrimary else ReelGuardColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onSelectMinutes(selectedMinutes) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(11.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = ReelGuardColors.AccentDark,
                contentColor = Color.White
            )
        ) {
            Text("Start $selectedMinutes-minute session", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ReelGuardColors.SurfacePressed,
                contentColor = ReelGuardColors.TextSecondary
            )
        ) {
            Text("Go back", fontWeight = FontWeight.Medium)
        }
    }
}
