package com.reelguard.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reelguard.app.data.ProtectionPreferences
import com.reelguard.app.service.ReelGuardAccessibilityService
import com.reelguard.app.ui.theme.NeumorphicPanel
import com.reelguard.app.ui.theme.ReelGuardColors
import kotlinx.coroutines.delay

private enum class AppTab(val title: String, val subtitle: String) {
    PROTECT("Protect", "Your protection centre"),
    GUIDE("Guide", "How ReelGuard works"),
    SETTINGS("Settings", "Your system access")
}

@Composable
fun ReelGuardApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1050)
        showSplash = false
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { it / 18 }) togetherWith
                (fadeOut(tween(260)) + slideOutHorizontally(tween(300)) { -it / 28 })
        },
        label = "launch-transition"
    ) { splash ->
        if (splash) ReelGuardSplash() else ReelGuardNavigation()
    }
}

@Composable
private fun ReelGuardSplash() {
    val transition = rememberInfiniteTransition(label = "splash-mark")
    val markScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mark-breathe"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEEF0F4), ReelGuardColors.Background, Color(0xFFDDE2E8))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            NeumorphicMark(
                modifier = Modifier.scale(markScale),
                size = 112.dp,
                iconSize = 54.dp
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "ReelGuard",
                    color = ReelGuardColors.TextPrimary,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    "KEEP THE REELS YOU CHOOSE.",
                    color = ReelGuardColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.1.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(if (index == 1) 7.dp else 5.dp)
                            .background(
                                ReelGuardColors.Accent.copy(alpha = if (index == 1) 1f else 0.42f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ReelGuardNavigation() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.PROTECT) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var protectionEnabled by remember { mutableStateOf(ProtectionPreferences.isProtectionEnabled(context)) }
    var disclosureAccepted by remember { mutableStateOf(ProtectionPreferences.hasAccessibilityDisclosureBeenAccepted(context)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    fun refreshStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        overlayEnabled = Settings.canDrawOverlays(context)
        disclosureAccepted = ProtectionPreferences.hasAccessibilityDisclosureBeenAccepted(context)
        protectionEnabled = ProtectionPreferences.isProtectionEnabled(context)

        if (protectionEnabled && (!accessibilityEnabled || !overlayEnabled || !disclosureAccepted)) {
            ProtectionPreferences.setProtectionEnabled(context, false)
            protectionEnabled = false
        }
    }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
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

    fun handleAccessibilitySetup() {
        if (!disclosureAccepted) showDisclosure = true else openAccessibilitySettings()
    }

    fun handlePrimaryAction() {
        when {
            !disclosureAccepted -> showDisclosure = true
            !accessibilityEnabled -> openAccessibilitySettings()
            !overlayEnabled -> openOverlaySettings()
            else -> {
                ProtectionPreferences.setProtectionEnabled(context, !protectionEnabled)
                protectionEnabled = !protectionEnabled
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (showDisclosure) {
            showDisclosure = false
        } else if (showExitDialog) {
            showExitDialog = false
        } else if (selectedTab != AppTab.PROTECT) {
            selectedTab = AppTab.PROTECT
        } else {
            showExitDialog = true
        }
    }

    val permissionsReady = accessibilityEnabled && overlayEnabled && disclosureAccepted
    val protected = protectionEnabled && permissionsReady
    val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReelGuardColors.Background)
            .navigationBarsPadding()
    ) {
        AppHeader(
            isProtected = protected,
            activeTab = selectedTab,
            onSettings = { selectedTab = AppTab.SETTINGS }
        )

        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(230)) + slideInHorizontally(tween(280)) { it / 16 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(230)) { -it / 24 })
            },
            label = "tab-content"
        ) { destination ->
            when (destination) {
                AppTab.PROTECT -> ProtectScreen(
                    accessibilityEnabled = accessibilityEnabled,
                    overlayEnabled = overlayEnabled,
                    disclosureAccepted = disclosureAccepted,
                    isProtected = protected,
                    onAccessibility = { handleAccessibilitySetup() },
                    onOverlay = { openOverlaySettings() },
                    onDisclosure = { showDisclosure = true },
                    onPrimaryAction = { handlePrimaryAction() },
                    onGuide = { selectedTab = AppTab.GUIDE }
                )
                AppTab.GUIDE -> GuideScreen(onProtection = { selectedTab = AppTab.PROTECT })
                AppTab.SETTINGS -> SettingsScreen(
                    accessibilityEnabled = accessibilityEnabled,
                    overlayEnabled = overlayEnabled,
                    disclosureAccepted = disclosureAccepted,
                    isProtected = protected,
                    isSamsung = isSamsung,
                    onAccessibility = { handleAccessibilitySetup() },
                    onOverlay = { openOverlaySettings() },
                    onDisclosure = { showDisclosure = true },
                    onAppInfo = { openAppInfo() },
                    onProtectionToggle = { handlePrimaryAction() }
                )
            }
        }

        BottomNavigationBar(
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showDisclosure = false },
            onContinue = {
                ProtectionPreferences.setAccessibilityDisclosureAccepted(context, true)
                disclosureAccepted = true
                showDisclosure = false
                openAccessibilitySettings()
            }
        )
    }

    if (showExitDialog) {
        ConfirmExitDialog(
            onStay = { showExitDialog = false },
            onExit = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }
}

@Composable
private fun AppHeader(
    isProtected: Boolean,
    activeTab: AppTab,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeumorphicMark(size = 42.dp, iconSize = 23.dp)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "REELGUARD",
                color = ReelGuardColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.65.sp
            )
            Text(activeTab.subtitle, color = ReelGuardColors.TextSecondary, fontSize = 11.sp)
        }
        if (activeTab != AppTab.SETTINGS) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber,
                            CircleShape
                        )
                )
                Text(
                    if (isProtected) "ON" else "SETUP",
                    color = if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = ReelGuardColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NeumorphicMark(
    modifier: Modifier = Modifier,
    size: Dp,
    iconSize: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 11.dp,
                shape = RoundedCornerShape(size * 0.32f),
                ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.66f),
                spotColor = ReelGuardColors.Shadow.copy(alpha = 0.76f)
            )
            .background(
                Brush.verticalGradient(listOf(ReelGuardColors.SurfaceRaised, ReelGuardColors.SurfacePressed)),
                RoundedCornerShape(size * 0.32f)
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White, ReelGuardColors.Border)),
                RoundedCornerShape(size * 0.32f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = ReelGuardColors.Accent, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ProtectScreen(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    disclosureAccepted: Boolean,
    isProtected: Boolean,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onDisclosure: () -> Unit,
    onPrimaryAction: () -> Unit,
    onGuide: () -> Unit
) {
    val completedSteps = listOf(disclosureAccepted, accessibilityEnabled, overlayEnabled).count { it }
    val setupComplete = completedSteps == 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your attention.", color = ReelGuardColors.TextPrimary, style = MaterialTheme.typography.displaySmall)
            Text("Your rules.", color = ReelGuardColors.Accent, style = MaterialTheme.typography.displaySmall)
            Text(
                "Keep the Reels you choose. Leave the endless scroll behind.",
                color = ReelGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(21.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = 15.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProtectionOrb(isProtected = isProtected, size = 94.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusLabel(isProtected = isProtected)
                    Text(
                        if (isProtected) "You're in control." else "Protection is ready when you are.",
                        color = ReelGuardColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        if (isProtected) "ReelGuard is active for Instagram." else "Complete setup once, then let ReelGuard work quietly.",
                        color = ReelGuardColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(19.dp))
            NeumorphicPrimaryButton(
                label = when {
                    isProtected -> "Pause protection"
                    !disclosureAccepted -> "Review access"
                    !accessibilityEnabled -> "Enable protection access"
                    !overlayEnabled -> "Allow display over apps"
                    else -> "Activate protection"
                },
                icon = if (isProtected) Icons.Outlined.PowerSettingsNew else Icons.Outlined.ArrowForward,
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = isProtected) {
                Text(
                    "Protection runs automatically. You don't need to keep ReelGuard open.",
                    color = ReelGuardColors.Emerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(visible = !setupComplete) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("One-time setup", color = ReelGuardColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Text(
                            completedSteps.toString() + " of 3 requirements complete",
                            color = ReelGuardColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        (completedSteps * 100 / 3).toString() + "%",
                        color = ReelGuardColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                SetupProgressBar(progress = completedSteps / 3f)
                PermissionStep(
                    number = "01",
                    title = "Review the access disclosure",
                    description = "Understand exactly what ReelGuard reads and why.",
                    isComplete = disclosureAccepted,
                    actionLabel = if (disclosureAccepted) "Reviewed" else "Review",
                    onClick = onDisclosure
                )
                PermissionStep(
                    number = "02",
                    title = "Accessibility service",
                    description = "Recognizes whether a Reel came from a direct message or recommendations.",
                    isComplete = accessibilityEnabled,
                    actionLabel = if (accessibilityEnabled) "Enabled" else "Enable",
                    onClick = onAccessibility
                )
                PermissionStep(
                    number = "03",
                    title = "Display over other apps",
                    description = "Shows the pause screen above Instagram when needed.",
                    isComplete = overlayEnabled,
                    actionLabel = if (overlayEnabled) "Allowed" else "Allow",
                    onClick = onOverlay
                )
            }
        }

        AnimatedVisibility(visible = setupComplete) {
            NeumorphicPanel(
                padding = androidx.compose.foundation.layout.PaddingValues(17.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ReelGuardColors.Emerald, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("System access is ready", color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("All setup requirements are complete.", color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ReelGuardColors.TextMuted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("One job. Two clear rules.", "ReelGuard protects the way you enter Instagram Reels.")
            RuleCard(
                icon = Icons.Outlined.MenuBook,
                title = "Friend-shared Reels",
                description = "Open a Reel from a direct message and watch that one. Swiping to another Reel returns you to the conversation.",
                accent = ReelGuardColors.Emerald
            )
            RuleCard(
                icon = Icons.Outlined.Shield,
                title = "Recommended Reels",
                description = "ReelGuard pauses the algorithmic feed. Choose a short session or leave Reels.",
                accent = ReelGuardColors.Accent
            )
        }

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = 9.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onGuide),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeumorphicIconWell(Icons.Outlined.AutoAwesome, tint = ReelGuardColors.Accent, size = 39.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("See how protection works", color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("A quick guide to the experience.", color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = ReelGuardColors.Accent)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GuideScreen(onProtection: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        PageHeading(
            eyebrow = "THE EXPERIENCE",
            title = "Simple by design.",
            description = "You don't control ReelGuard from inside Instagram. Set it up once, then browse normally."
        )

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(19.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            SectionTitle("When a friend shares a Reel", "The conversation stays the starting point.")
            GuideStep("01", "Open the Reel in your DM", "Tap the video shared in a conversation. ReelGuard recognizes the DM entry.")
            GuideStep("02", "Watch that one Reel", "The shared video plays without an interruption.")
            GuideStep("03", "Swipe to the next one", "ReelGuard returns you to the same conversation instead of opening an endless feed.")
        }

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(19.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            SectionTitle("When recommendations take over", "A deliberate pause before the next scroll.")
            GuideStep("01", "Open recommended Reels", "The algorithmic Reels surface is recognized and paused.")
            GuideStep("02", "Choose your intention", "Leave Reels or choose a 5, 10, 15, or 20-minute session.")
            GuideStep("03", "Return when time is up", "The session ends. Navigation is only attempted when Instagram is still showing a Reel.")
        }

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
                NeumorphicIconWell(Icons.Outlined.Security, tint = ReelGuardColors.Emerald, size = 40.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Private by design", color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ReelGuard processes the visible Instagram interface locally. It does not upload screen content or inspect the contents of other apps.",
                        color = ReelGuardColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        NeumorphicPrimaryButton(
            label = "Back to protection",
            icon = Icons.Outlined.Shield,
            onClick = onProtection,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SettingsScreen(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    disclosureAccepted: Boolean,
    isProtected: Boolean,
    isSamsung: Boolean,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onDisclosure: () -> Unit,
    onAppInfo: () -> Unit,
    onProtectionToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeading(
            eyebrow = "PREFERENCES",
            title = "Settings.",
            description = "Manage the access ReelGuard needs and how it runs on your device."
        )

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Protection", color = ReelGuardColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isProtected) "Currently active" else "Currently paused",
                        color = if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                NeumorphicSwitch(
                    checked = isProtected,
                    enabled = accessibilityEnabled && overlayEnabled && disclosureAccepted,
                    onClick = onProtectionToggle
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "When protection is active, ReelGuard applies its rules automatically whenever you use Instagram.",
                color = ReelGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("System permissions", "Android manages these permissions. ReelGuard shows their current state.")
            SettingsRow(
                icon = Icons.Outlined.AccessibilityNew,
                title = "Accessibility service",
                description = "Recognize Instagram's current screen.",
                status = if (accessibilityEnabled) "Enabled" else "Required",
                isComplete = accessibilityEnabled,
                onClick = onAccessibility
            )
            SettingsRow(
                icon = Icons.Outlined.Layers,
                title = "Display over other apps",
                description = "Display the full-screen pause over Instagram.",
                status = if (overlayEnabled) "Allowed" else "Required",
                isComplete = overlayEnabled,
                onClick = onOverlay
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "Access disclosure",
                description = "Review what is accessed and how it is used.",
                status = if (disclosureAccepted) "Reviewed" else "Review",
                isComplete = disclosureAccepted,
                onClick = onDisclosure
            )
        }

        if (isSamsung) {
            NeumorphicPanel(
                padding = androidx.compose.foundation.layout.PaddingValues(17.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    NeumorphicIconWell(Icons.Outlined.PowerSettingsNew, tint = ReelGuardColors.Accent, size = 39.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Samsung background activity", color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Set ReelGuard's battery usage to Unrestricted if One UI keeps stopping its background service.",
                            color = ReelGuardColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onAppInfo, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Open app settings", color = ReelGuardColors.Accent)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("About", "A focused utility. Nothing more.")
            SettingsRow(
                icon = Icons.Outlined.Shield,
                title = "ReelGuard",
                description = "Mindful protection for Instagram Reels.",
                status = "v1.1.0",
                isComplete = true,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "Android app settings",
                description = "Permissions and app information.",
                status = "Open",
                isComplete = false,
                onClick = onAppInfo
            )
        }

        NeumorphicPanel(
            padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp
        ) {
            Text("Your privacy", color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "ReelGuard reads visible Instagram interface elements to identify the current Reel context. This processing stays on your device. It does not collect your Instagram credentials or upload screen content.",
                color = ReelGuardColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PageHeading(
    eyebrow: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            eyebrow,
            color = ReelGuardColors.Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.9.sp
        )
        Text(title, color = ReelGuardColors.TextPrimary, style = MaterialTheme.typography.displaySmall)
        Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = ReelGuardColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProtectionOrb(
    isProtected: Boolean,
    size: Dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isProtected) 1.04f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "protection-orb-scale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .shadow(
                elevation = 13.dp,
                shape = CircleShape,
                ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.72f),
                spotColor = ReelGuardColors.Shadow
            )
            .background(
                Brush.verticalGradient(listOf(Color(0xFFF2F4F7), Color(0xFFDCE1E7))),
                CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.68f)
                .background(
                    if (isProtected) ReelGuardColors.EmeraldSurface else ReelGuardColors.AccentMuted,
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Accent,
                modifier = Modifier.size(size * 0.35f)
            )
        }
    }
}

@Composable
private fun StatusLabel(isProtected: Boolean) {
    val infinite = rememberInfiniteTransition(label = "status-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(
                    (if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber).copy(
                        alpha = if (isProtected) pulse else 1f
                    ),
                    CircleShape
                )
        )
        Text(
            if (isProtected) "PROTECTION ACTIVE" else "NOT ACTIVE",
            color = if (isProtected) ReelGuardColors.Emerald else ReelGuardColors.Amber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
private fun SetupProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .shadow(3.dp, RoundedCornerShape(50.dp), ambientColor = ReelGuardColors.Shadow, spotColor = ReelGuardColors.Shadow)
            .background(ReelGuardColors.SurfacePressed, RoundedCornerShape(50.dp))
            .padding(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(listOf(ReelGuardColors.Accent, ReelGuardColors.Emerald)),
                    RoundedCornerShape(50.dp)
                )
        )
    }
}

@Composable
private fun PermissionStep(
    number: String,
    title: String,
    description: String,
    isComplete: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    NeumorphicPanel(
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        shape = RoundedCornerShape(19.dp),
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .background(
                        if (isComplete) ReelGuardColors.EmeraldSurface else ReelGuardColors.SurfacePressed,
                        CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isComplete) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = ReelGuardColors.Emerald, modifier = Modifier.size(18.dp))
                } else {
                    Text(number, color = ReelGuardColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    actionLabel,
                    color = if (isComplete) ReelGuardColors.Emerald else ReelGuardColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ReelGuardColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color
) {
    NeumorphicPanel(
        padding = androidx.compose.foundation.layout.PaddingValues(15.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 8.dp
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeumorphicIconWell(icon, accent, 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.padding(top = 15.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(33.dp)
                .background(ReelGuardColors.AccentMuted, CircleShape)
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = ReelGuardColors.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    status: String,
    isComplete: Boolean,
    onClick: () -> Unit
) {
    NeumorphicPanel(
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = 7.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeumorphicIconWell(icon, ReelGuardColors.Accent, 38.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ReelGuardColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = ReelGuardColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    status,
                    color = if (isComplete) ReelGuardColors.Emerald else ReelGuardColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ReelGuardColors.TextMuted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun NeumorphicIconWell(
    icon: ImageVector,
    tint: Color,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(size * 0.32f),
                ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.68f),
                spotColor = ReelGuardColors.Shadow.copy(alpha = 0.72f)
            )
            .background(
                Brush.verticalGradient(listOf(ReelGuardColors.SurfaceRaised, ReelGuardColors.SurfacePressed)),
                RoundedCornerShape(size * 0.32f)
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White, ReelGuardColors.Border)),
                RoundedCornerShape(size * 0.32f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
private fun NeumorphicPrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.55f),
                spotColor = ReelGuardColors.AccentDark.copy(alpha = 0.32f)
            )
            .background(Brush.verticalGradient(listOf(Color(0xFF718092), ReelGuardColors.AccentDark)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.32f), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun BottomNavigationBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit
) {
    NeumorphicPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 9.dp)
            .navigationBarsPadding(),
        padding = androidx.compose.foundation.layout.PaddingValues(7.dp),
        shape = RoundedCornerShape(23.dp),
        elevation = 13.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                modifier = Modifier.weight(1f),
                tab = AppTab.PROTECT,
                selected = selected == AppTab.PROTECT,
                icon = Icons.Outlined.Shield,
                onClick = { onSelect(AppTab.PROTECT) }
            )
            NavigationItem(
                modifier = Modifier.weight(1f),
                tab = AppTab.GUIDE,
                selected = selected == AppTab.GUIDE,
                icon = Icons.Outlined.MenuBook,
                onClick = { onSelect(AppTab.GUIDE) }
            )
            NavigationItem(
                modifier = Modifier.weight(1f),
                tab = AppTab.SETTINGS,
                selected = selected == AppTab.SETTINGS,
                icon = Icons.Outlined.Settings,
                onClick = { onSelect(AppTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavigationItem(
    modifier: Modifier,
    tab: AppTab,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.035f else 1f,
        animationSpec = tween(230, easing = FastOutSlowInEasing),
        label = "navigation-scale"
    )
    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (selected) ReelGuardColors.SurfacePressed.copy(alpha = 0.75f) else Color.Transparent,
                RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = tab.title,
            tint = if (selected) ReelGuardColors.AccentDark else ReelGuardColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            tab.title,
            color = if (selected) ReelGuardColors.AccentDark else ReelGuardColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun NeumorphicSwitch(
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 55.dp, height = 32.dp)
            .shadow(5.dp, CircleShape, ambientColor = ReelGuardColors.Shadow, spotColor = ReelGuardColors.Shadow)
            .background(if (checked) ReelGuardColors.EmeraldSurface else ReelGuardColors.SurfacePressed, CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(4.dp, CircleShape, ambientColor = ReelGuardColors.Shadow, spotColor = ReelGuardColors.Shadow)
                .background(if (checked) ReelGuardColors.Emerald else ReelGuardColors.SurfaceRaised, CircleShape)
                .border(1.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ReelGuardColors.SurfaceRaised,
        titleContentColor = ReelGuardColors.TextPrimary,
        textContentColor = ReelGuardColors.TextSecondary,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Why ReelGuard needs access", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "ReelGuard reads visible Instagram interface labels locally to recognize whether a Reel was opened from a direct message or from recommendations. This information is processed on your device and is not uploaded. When protection is active, ReelGuard can show a pause screen and use Back to return from the next Reel in a DM or return to Instagram Home when your chosen session ends."
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Continue to Android settings", color = ReelGuardColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = ReelGuardColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun ConfirmExitDialog(
    onStay: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = ReelGuardColors.SurfaceRaised,
        titleContentColor = ReelGuardColors.TextPrimary,
        textContentColor = ReelGuardColors.TextSecondary,
        shape = RoundedCornerShape(26.dp),
        icon = { NeumorphicIconWell(Icons.Outlined.PowerSettingsNew, ReelGuardColors.Accent, 43.dp) },
        title = { Text("Leave ReelGuard?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Protection keeps running in the background while it is active. You can safely leave the app and return whenever you need to.")
        },
        confirmButton = {
            TextButton(onClick = onExit) {
                Text("Exit app", color = ReelGuardColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onStay) {
                Text("Stay here", color = ReelGuardColors.TextSecondary)
            }
        }
    )
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expected = ComponentName(context, ReelGuardAccessibilityService::class.java)
    return enabledServices.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
    }
}
