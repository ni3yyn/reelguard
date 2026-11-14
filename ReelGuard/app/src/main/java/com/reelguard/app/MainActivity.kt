package com.reelguard.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reelguard.app.data.ProtectionPreferences
import com.reelguard.app.ui.theme.ReelGuardColors
import com.reelguard.app.ui.theme.ReelGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReelGuardTheme {
                ReelGuardHomeScreen()
            }
        }
    }
}

@Composable
fun ReelGuardHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var protectionEnabled by remember { mutableStateOf(ProtectionPreferences.isProtectionEnabled(context)) }
    var disclosureAccepted by remember { mutableStateOf(ProtectionPreferences.hasAccessibilityDisclosureBeenAccepted(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    fun refreshStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        overlayEnabled = Settings.canDrawOverlays(context)
        protectionEnabled = ProtectionPreferences.isProtectionEnabled(context)
        disclosureAccepted = ProtectionPreferences.hasAccessibilityDisclosureBeenAccepted(context)
    }

    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openOverlaySettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openAppInfo() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsReady = accessibilityEnabled && overlayEnabled
    val isProtected = protectionEnabled && permissionsReady
    val isSamsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReelGuardColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = ReelGuardColors.AccentMuted
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = ReelGuardColors.Accent,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ReelGuard",
                    color = ReelGuardColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Choose what deserves your attention.",
                    color = ReelGuardColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (isProtected) ReelGuardColors.EmeraldSurface else ReelGuardColors.Surface,
            border = BorderStroke(
                1.dp,
                if (isProtected) ReelGuardColors.Emerald.copy(alpha = 0.5f) else ReelGuardColors.Border
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber,
                                RoundedCornerShape(50)
                            )
                    )
                    Text(
                        text = if (isProtected) "PROTECTION IS ON" else "PROTECTION IS NOT ACTIVE",
                        color = if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = if (isProtected) "You're ready to use Instagram." else "Set up your protection once.",
                    color = ReelGuardColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = when {
                        isProtected -> "ReelGuard now works automatically when you use Instagram. You do not need to keep this screen open."
                        !accessibilityEnabled -> "Enable ReelGuard's Accessibility Service so it can recognize where a Reel was opened."
                        !disclosureAccepted -> "Review the Accessibility disclosure before activating protection."
                        !overlayEnabled -> "Allow ReelGuard to appear over Instagram so it can show the mindful pause when needed."
                        else -> "Your permissions are ready. Activate protection to start using ReelGuard."
                    },
                    color = ReelGuardColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = {
                        when {
                            !accessibilityEnabled || !disclosureAccepted -> showAccessibilityDisclosure = true
                            !overlayEnabled -> openOverlaySettings()
                            else -> {
                                ProtectionPreferences.setProtectionEnabled(context, !protectionEnabled)
                                protectionEnabled = !protectionEnabled
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProtected) ReelGuardColors.SurfaceElevated else ReelGuardColors.Accent,
                        contentColor = ReelGuardColors.TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isProtected) Icons.Outlined.PowerSettingsNew else Icons.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.size(9.dp))
                    Text(
                        text = when {
                            !accessibilityEnabled || !disclosureAccepted -> "Review access and continue"
                            !overlayEnabled -> "Allow display over other apps"
                            isProtected -> "Pause protection"
                            else -> "Activate protection"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading(title = "One-time setup", subtitle = "ReelGuard needs two Android permissions to work.")
            SetupStepCard(
                number = "01",
                icon = Icons.Outlined.AccessibilityNew,
                title = "Accessibility Service",
                description = if (isSamsung && !accessibilityEnabled) "Recognizes Instagram screens locally. If Android says this is a restricted setting, open App info → ⋮ → Allow restricted settings, then return here." else "Recognizes Instagram screens locally so ReelGuard can distinguish a Reel opened from a direct message from algorithmic Reels.",
                isComplete = accessibilityEnabled,
                actionLabel = if (accessibilityEnabled) "Manage" else "Enable",
                onAction = {
                    if (accessibilityEnabled && disclosureAccepted) openAccessibilitySettings()
                    else showAccessibilityDisclosure = true
                },
                secondaryLabel = if (!accessibilityEnabled) "App info" else null,
                onSecondary = if (!accessibilityEnabled) ({ openAppInfo() }) else null
            )
            SetupStepCard(
                number = "02",
                icon = Icons.Outlined.Layers,
                title = "Display over other apps",
                description = "Allows the ReelGuard pause screen to appear over Instagram when an algorithmic Reel is opened.",
                isComplete = overlayEnabled,
                actionLabel = if (overlayEnabled) "Manage" else "Allow",
                onAction = { openOverlaySettings() }
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ReelGuardColors.AccentMuted.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, ReelGuardColors.Accent.copy(alpha = 0.22f))
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = ReelGuardColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "No button inside Instagram",
                        color = ReelGuardColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "After setup and activation, just open Instagram and use it normally. ReelGuard runs automatically. There is no floating control and nothing to press inside Instagram.",
                        color = ReelGuardColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading(title = "What happens in Instagram", subtitle = "These are the rules ReelGuard applies.")
            HowItWorksCard(
                icon = Icons.Outlined.Home,
                title = "A Reel shared in a DM",
                description = "The shared Reel plays without an interruption. When you swipe to the next Reel, ReelGuard takes you back to that conversation."
            )
            HowItWorksCard(
                icon = Icons.Outlined.Shield,
                title = "Reels, Explore and recommendations",
                description = "A mindful pause appears before algorithmic Reels. Choose a time limit or leave Reels and return to Instagram Home."
            )
            HowItWorksCard(
                icon = Icons.Outlined.Timer,
                title = "When your limit ends",
                description = "The session closes. ReelGuard only navigates within Instagram when Instagram is still showing a Reel; it never sends a Back action to another app."
            )
        }

        if (isSamsung) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ReelGuardColors.Surface,
                border = BorderStroke(1.dp, ReelGuardColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Samsung battery settings",
                        color = ReelGuardColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "If protection stops after some time, set ReelGuard's battery usage to Unrestricted in Android Settings so One UI is less likely to stop its background service.",
                        color = ReelGuardColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { openAppInfo() }) {
                        Text("Open ReelGuard app info")
                    }
                }
            }
        }

        Text(
            text = "ReelGuard reads visible Instagram interface elements locally and observes app-window changes only to dismiss its own pause screen when you leave Instagram. It does not read other apps’ screen content or upload screen content.",
            color = ReelGuardColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 18.dp)
        )
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            containerColor = ReelGuardColors.Surface,
            titleContentColor = ReelGuardColors.TextPrimary,
            textContentColor = ReelGuardColors.TextSecondary,
            title = { Text("Why ReelGuard needs Accessibility") },
            text = {
                Text(
                    "ReelGuard reads visible Instagram interface labels locally to recognize whether a Reel was opened from a direct message or from recommendations. This information is processed on your device and is not uploaded. When protection is active, ReelGuard can show a pause screen and use Back to return from the next Reel in a DM or return to Instagram Home when your chosen session ends."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProtectionPreferences.setAccessibilityDisclosureAccepted(context, true)
                        showAccessibilityDisclosure = false
                        openAccessibilitySettings()
                    }
                ) {
                    Text("Continue to settings", color = ReelGuardColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text("Not now", color = ReelGuardColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = ReelGuardColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Text(
            text = subtitle,
            color = ReelGuardColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SetupStepCard(
    number: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isComplete: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ReelGuardColors.Surface,
        border = BorderStroke(
            1.dp,
            if (isComplete) ReelGuardColors.Emerald.copy(alpha = 0.4f) else ReelGuardColors.Border
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isComplete) ReelGuardColors.EmeraldSurface else ReelGuardColors.SurfaceElevated
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isComplete) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Complete",
                                tint = ReelGuardColors.Emerald,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(number, color = ReelGuardColors.TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isComplete) "Permission enabled" else "Required",
                        color = if (isComplete) ReelGuardColors.Emerald else ReelGuardColors.Amber,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ReelGuardColors.TextSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, if (isComplete) ReelGuardColors.Border else ReelGuardColors.Accent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isComplete) ReelGuardColors.TextPrimary else ReelGuardColors.Accent
                    )
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) {
                        Text(secondaryLabel, color = ReelGuardColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ReelGuardColors.Surface,
        border = BorderStroke(1.dp, ReelGuardColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ReelGuardColors.Accent,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expected = ComponentName(context, com.reelguard.app.service.ReelGuardAccessibilityService::class.java)
    return enabledServices.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
    }
}
