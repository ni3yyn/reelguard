package com.reelguard.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reelguard.app.data.CapturedLogEntry
import com.reelguard.app.data.DebugLogRepository
import com.reelguard.app.service.ReelGuardAccessibilityService
import com.reelguard.app.ui.theme.ReelGuardColors
import com.reelguard.app.ui.theme.ReelGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugLogRepository.init(this)

        setContent {
            ReelGuardTheme {
                ReelGuardSetupScreen()
            }
        }
    }
}

@Composable
fun ReelGuardSetupScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val debugMode by DebugLogRepository.isDebugMode.collectAsState()
    val capturedLogs by DebugLogRepository.logs.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    accessibilityEnabled = isAccessibilityServiceEnabled(context)
                    overlayEnabled = Settings.canDrawOverlays(context)
                    DebugLogRepository.setReelGuardInForeground(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    DebugLogRepository.setReelGuardInForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            DebugLogRepository.setReelGuardInForeground(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allReady = accessibilityEnabled && overlayEnabled

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ReelGuardColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ReelGuardColors.AccentMuted,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = ReelGuardColors.Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "ReelGuard",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ReelGuardColors.TextPrimary
                    )
                    Text(
                        text = "Mindful Instagram Reels gatekeeper",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReelGuardColors.TextSecondary
                    )
                }
            }
        }

        item {
            ProtectionStatusBanner(allReady = allReady, debugMode = debugMode)
        }

        item {
            PermissionCard(
                icon = Icons.Outlined.AccessibilityNew,
                title = "1. Accessibility Service",
                enabled = accessibilityEnabled,
                description = "Detects DM-shared Reels vs. algorithmic Reels tabs and intercepts swipes out of friend-shared videos.",
                footnote = "Sideloaded on Android 13/14+ (One UI): If the toggle is grayed out, open App Info, tap the top-right ⋮ menu, and tap \"Allow restricted settings\".",
                buttonLabel = if (accessibilityEnabled) "Manage Accessibility" else "Open Accessibility Settings",
                secondaryButtonLabel = "Open App Info",
                onPrimaryClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                onSecondaryClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )
        }

        item {
            PermissionCard(
                icon = Icons.Outlined.Layers,
                title = "2. Display Over Other Apps",
                enabled = overlayEnabled,
                description = "Permits ReelGuard to show full-screen mindful pause overlays, time limit grids, and the floating Debug Snap UI button.",
                footnote = null,
                buttonLabel = if (overlayEnabled) "Manage Overlay Permission" else "Grant Overlay Permission",
                secondaryButtonLabel = null,
                onPrimaryClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                onSecondaryClick = null
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ReelGuardColors.Surface,
                border = BorderStroke(1.dp, ReelGuardColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryChargingFull,
                        contentDescription = null,
                        tint = ReelGuardColors.Amber,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Samsung One UI Battery Tip",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReelGuardColors.TextPrimary
                        )
                        Text(
                            text = "Set Settings > Apps > ReelGuard > Battery to Unrestricted so One UI does not put the Accessibility Service to sleep.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReelGuardColors.TextSecondary
                        )
                    }
                }
            }
        }

        // Debug Inspector Mode Toggle & View Captured Logs Section
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ReelGuardColors.Surface,
                border = BorderStroke(1.dp, ReelGuardColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = ReelGuardColors.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Debug Inspector Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ReelGuardColors.TextPrimary
                                )
                                Text(
                                    text = "Suspends blocking overlays and shows a draggable \"Snap UI\" pill inside Instagram.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ReelGuardColors.TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = debugMode,
                            onCheckedChange = { DebugLogRepository.setDebugMode(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ReelGuardColors.Accent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "View Captured Logs (${capturedLogs.size}/${DebugLogRepository.MAX_LOGS})",
                        style = MaterialTheme.typography.titleMedium,
                        color = ReelGuardColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Grouped by timestamp and event type (TYPE_WINDOW_STATE_CHANGED, TYPE_VIEW_SCROLLED, TYPE_VIEW_CLICKED, UI_SNAPSHOT).",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReelGuardColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val text = DebugLogRepository.formatLogsForClipboard()
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Captured logs copied", Toast.LENGTH_SHORT).show()
                            },
                            enabled = capturedLogs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ReelGuardColors.Accent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(text = "Copy Logs to Clipboard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { DebugLogRepository.clearLogs() },
                            enabled = capturedLogs.isNotEmpty(),
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ReelGuardColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ReelGuardColors.TextPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(text = "Clear Logs", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (capturedLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ReelGuardColors.SurfaceElevated,
                    border = BorderStroke(1.dp, ReelGuardColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No logs captured yet. Turn on Debug Inspector Mode, open Instagram, and tap \"Snap UI\" on any DM Reel, Explore Reel, or Story screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReelGuardColors.TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(capturedLogs, key = { it.id }) { entry ->
                CapturedLogCard(entry = entry)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapturedLogCard(entry: CapturedLogEntry) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ReelGuardColors.SurfaceElevated,
        border = BorderStroke(1.dp, ReelGuardColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${entry.timestamp}  ·  ${entry.eventType}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelGuardColors.Accent
            )
            Text(
                text = entry.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = ReelGuardColors.TextPrimary
            )
            if (entry.nodeTreeText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ReelGuardColors.Background,
                    border = BorderStroke(1.dp, ReelGuardColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.nodeTreeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = ReelGuardColors.TextSecondary,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusBanner(allReady: Boolean, debugMode: Boolean) {
    val containerColor = when {
        debugMode -> ReelGuardColors.AccentMuted
        allReady -> ReelGuardColors.EmeraldSurface
        else -> ReelGuardColors.AmberSurface
    }
    val borderColor = when {
        debugMode -> ReelGuardColors.Accent.copy(alpha = 0.5f)
        allReady -> ReelGuardColors.Emerald.copy(alpha = 0.4f)
        else -> ReelGuardColors.Amber.copy(alpha = 0.4f)
    }
    val iconTint = when {
        debugMode -> ReelGuardColors.Accent
        allReady -> ReelGuardColors.Emerald
        else -> ReelGuardColors.Amber
    }
    val title = when {
        debugMode -> "Debug Inspector Mode Active"
        allReady -> "Protection Active"
        else -> "Setup Required"
    }
    val subtitle = when {
        debugMode -> "Blocking overlays are temporarily suspended. Open Instagram and tap the floating Snap UI button."
        allReady -> "Both permissions granted. ReelGuard is guarding Instagram Reels."
        else -> "Enable both permissions below to activate mindful Reels blocking."
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (debugMode) Icons.Outlined.BugReport else if (allReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ReelGuardColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReelGuardColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    description: String,
    footnote: String?,
    buttonLabel: String,
    secondaryButtonLabel: String?,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ReelGuardColors.Surface,
        border = BorderStroke(1.dp, ReelGuardColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ReelGuardColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ReelGuardColors.TextPrimary
                    )
                }

                StatusBadge(enabled = enabled)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ReelGuardColors.TextSecondary
            )

            if (footnote != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ReelGuardColors.SurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.labelMedium,
                        color = ReelGuardColors.TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (enabled) ReelGuardColors.SurfaceElevated else ReelGuardColors.Accent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                if (secondaryButtonLabel != null && onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ReelGuardColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ReelGuardColors.TextPrimary
                        )
                    ) {
                        Text(text = secondaryButtonLabel, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) ReelGuardColors.EmeraldSurface else ReelGuardColors.CrimsonSurface,
        border = BorderStroke(
            1.dp,
            if (enabled) ReelGuardColors.Emerald.copy(alpha = 0.45f) else ReelGuardColors.Crimson.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = if (enabled) "Enabled" else "Disabled",
            color = if (enabled) ReelGuardColors.Emerald else ReelGuardColors.Crimson,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expectedComponent = ComponentName(context, ReelGuardAccessibilityService::class.java)
    return enabledServices.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        ComponentName(serviceInfo.packageName, serviceInfo.name) == expectedComponent
    }
}
