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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
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
import com.reelguard.app.ui.theme.NeumorphicPanel
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
    onSelectMinutes: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9E5E8ED))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        NeumorphicPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            shape = RoundedCornerShape(30.dp),
            elevation = 23.dp
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
private fun TactileButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean,
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth by animateDpAsState(
        targetValue = if (pressed) 1.dp else if (primary) 11.dp else 7.dp,
        animationSpec = tween(125),
        label = "overlay-button-depth"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(125),
        label = "overlay-button-press"
    )
    val shape = RoundedCornerShape(15.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(pressScale)
            .shadow(
                elevation = depth,
                shape = shape,
                ambientColor = if (primary) ReelGuardColors.AccentDark.copy(alpha = 0.38f) else ReelGuardColors.Shadow.copy(alpha = 0.72f),
                spotColor = if (primary) ReelGuardColors.AccentDark.copy(alpha = 0.5f) else ReelGuardColors.Shadow
            )
            .background(
                if (primary) {
                    Brush.verticalGradient(listOf(Color(0xFF657B91), Color(0xFF354657), Color(0xFF2F3E4D)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFF6F8FB), Color(0xFFE3E8EF), Color(0xFFDCE2E9)))
                },
                shape
            )
            .border(
                1.dp,
                if (primary) {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.08f)))
                } else {
                    Brush.linearGradient(listOf(Color.White, ReelGuardColors.Border))
                },
                shape
            )
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) Color.White else ReelGuardColors.AccentDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (primary) Color.White else ReelGuardColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun DurationChoice(
    modifier: Modifier,
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth by animateDpAsState(
        targetValue = if (selected || pressed) 1.dp else 7.dp,
        animationSpec = tween(130),
        label = "duration-choice-depth"
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .height(58.dp)
            .shadow(
                elevation = depth,
                shape = shape,
                ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.72f),
                spotColor = ReelGuardColors.Shadow.copy(alpha = 0.82f)
            )
            .background(
                Brush.verticalGradient(
                    if (selected) {
                        listOf(Color(0xFFD7E1EA), ReelGuardColors.AccentMuted, Color(0xFFD6DFE8))
                    } else {
                        listOf(Color(0xFFF7F9FB), ReelGuardColors.SurfaceRaised, Color(0xFFE1E6ED))
                    }
                ),
                shape
            )
            .border(
                1.dp,
                if (selected) {
                    Brush.linearGradient(listOf(ReelGuardColors.Accent.copy(alpha = 0.82f), Color.White.copy(alpha = 0.6f)))
                } else {
                    Brush.linearGradient(listOf(Color.White, ReelGuardColors.Border))
                },
                shape
            )
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$minutes min",
            color = if (selected) ReelGuardColors.AccentDark else ReelGuardColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 16.sp
        )
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

        TactileButton(
            text = "Choose a time limit",
            icon = Icons.Outlined.HourglassTop,
            primary = true,
            onClick = onContinueAnyway
        )

        Spacer(modifier = Modifier.height(11.dp))
        TactileButton(
            text = "Leave Reels",
            icon = Icons.Outlined.ArrowBack,
            primary = false,
            onClick = onCancel,
            height = 50.dp
        )
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
                        DurationChoice(
                            modifier = Modifier.weight(1f),
                            minutes = minutes,
                            selected = selected,
                            onClick = { selectedMinutes = minutes }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        TactileButton(
            text = "Start $selectedMinutes-minute session",
            primary = true,
            onClick = { onSelectMinutes(selectedMinutes) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        TactileButton(
            text = "Leave Reels",
            icon = Icons.Outlined.ArrowBack,
            primary = false,
            onClick = onCancel,
            height = 48.dp
        )
    }
}
