package app.slideswitch.remote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import app.slideswitch.remote.RemoteViewModel
import app.slideswitch.remote.ThemeMode
import app.slideswitch.remote.net.Action
import app.slideswitch.remote.net.Computer
import app.slideswitch.remote.net.ConnectionState
import app.slideswitch.remote.ui.theme.AppTheme
import app.slideswitch.remote.ui.theme.Gradients
import kotlinx.coroutines.delay

private const val PORTFOLIO_URL = "https://harshach.vercel.app"

private enum class Screen { Welcome, Home, Remote }

@Composable
fun SlideSwitchApp(vm: RemoteViewModel, initialTab: Int = 0) {
    val state by vm.state.collectAsState()
    val onboarded by vm.onboarded.collectAsState()
    var showManual by rememberSaveable { mutableStateOf(false) }

    val screen = when {
        state is ConnectionState.Connected -> Screen.Remote
        !onboarded -> Screen.Welcome
        else -> Screen.Home
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Welcome -> WelcomeScreen(onContinue = vm::finishOnboarding)
                Screen.Home -> HomeScreen(vm, state, initialTab, onManual = { showManual = true })
                Screen.Remote -> (state as? ConnectionState.Connected)?.let { RemoteScreen(vm, it) }
            }
        }

        val needsCode = state as? ConnectionState.NeedsCode
        var lastCodeState by remember { mutableStateOf(needsCode) }
        if (needsCode != null) lastCodeState = needsCode
        BottomSheet(visible = needsCode != null, onDismiss = vm::cancel) {
            lastCodeState?.let { CodeSheet(it, onSubmit = vm::submitCode, onCancel = vm::cancel) }
        }

        BottomSheet(visible = showManual && needsCode == null, onDismiss = { showManual = false }) {
            ManualSheet(
                onConnect = { host, port ->
                    showManual = false
                    vm.connectManually(host, port)
                },
                onCancel = { showManual = false },
            )
        }
    }
}

/** Scrim + slide-up sheet, iOS style. */
@Composable
private fun BoxScope.BottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(AppTheme.colors.card)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(AppTheme.colors.separator))
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

// MARK: - Welcome

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("Welcome to", AppTheme.type.title3)
                Spacer(Modifier.height(20.dp))
                Wordmark()
            }
        }
        PillButton("Continue", onClick = onContinue)
    }
}

/** "SlideSwitch" in bold with "For Android" underneath, left-aligned as a block. */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Label("SlideSwitch", AppTheme.type.hero)
        Label("For Android", AppTheme.type.title3)
    }
}

// MARK: - Home (Connect / Settings tabs)

@Composable
private fun HomeScreen(vm: RemoteViewModel, state: ConnectionState, initialTab: Int, onManual: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                slideInHorizontally { dir * it / 4 } + fadeIn() togetherWith slideOutHorizontally { -dir * it / 4 } + fadeOut()
            },
            modifier = Modifier.weight(1f),
            label = "tab",
        ) { selected ->
            if (selected == 0) ConnectTab(vm, state, onManual) else SettingsTab(vm)
        }
        Spacer(Modifier.height(12.dp))
        Segmented(
            options = listOf("Connect", "Settings"),
            selected = tab,
            onSelect = { tab = it },
            height = 72.dp,
            textStyle = AppTheme.type.footnote,
            icons = listOf(Glyphs.Radiowaves, Glyphs.Gear),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ConnectTab(vm: RemoteViewModel, state: ConnectionState, onManual: () -> Unit) {
    val computers by vm.computers.collectAsState()
    val searching by vm.searching.collectAsState()
    val recent = remember(state) { vm.recent }
    val colors = AppTheme.colors
    val connectingTo = (state as? ConnectionState.Connecting)?.target

    // Recent computer first (if it isn't already discovered), then everything found on the network.
    val devices = buildList {
        if (recent != null && computers.none { it.host == recent.host && it.port == recent.port }) add(recent to true)
        computers.forEach { add(it to false) }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Wordmark()
        Spacer(Modifier.height(48.dp))

        Label("Available devices", AppTheme.type.title3, modifier = Modifier.padding(start = 12.dp))
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(colors.well)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            if (state is ConnectionState.Failed) {
                Label(
                    "Couldn't connect to ${state.target.name}. ${state.message}",
                    AppTheme.type.footnote, color = colors.red,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            if (devices.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (searching) Spinner()
                    Spacer(Modifier.height(12.dp))
                    Label(
                        if (searching) "Looking for computers running SlideSwitch…"
                        else "No devices yet. Tap Search for devices.",
                        AppTheme.type.subheadline.copy(textAlign = TextAlign.Center),
                        color = colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(6.dp))
                    Label(
                        "Your phone and computer need to be on the same Wi-Fi.",
                        AppTheme.type.footnote.copy(textAlign = TextAlign.Center),
                        color = colors.secondaryLabel,
                    )
                }
            } else {
                devices.forEach { (computer, isRecent) ->
                    DeviceRow(
                        computer = computer,
                        isRecent = isRecent,
                        connecting = connectingTo?.host == computer.host && connectingTo.port == computer.port,
                        onClick = { vm.connect(computer) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SearchButton(onSearch = vm::refreshDiscovery)
        Label(
            "Enter address manually",
            AppTheme.type.subheadline, color = colors.accent,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onManual)
                .padding(vertical = 14.dp, horizontal = 16.dp),
        )
    }
}

@Composable
private fun DeviceRow(computer: Computer, isRecent: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIcon(
            glyph = when {
                isRecent -> Glyphs.Clock
                computer.platform == "windows" -> Glyphs.Display
                else -> Glyphs.Laptop
            },
            gradient = if (isRecent) Gradients.recent else Gradients.computer,
            size = 40.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Label(computer.name, AppTheme.type.headline, maxLines = 1)
            val detail = listOfNotNull(
                if (isRecent) "Recent" else platformName(computer.platform),
                computer.host,
            ).joinToString(" · ")
            Label(detail, AppTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1)
        }
        if (connecting) Spinner() else {
            androidx.compose.foundation.Image(
                Glyphs.SmallChevron, null, Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryLabel),
            )
        }
    }
}

private fun platformName(platform: String?) = when (platform) {
    "macos" -> "Mac"
    "windows" -> "Windows"
    else -> null
}

/**
 * "Search for devices": on tap the pill springs into a circle at its right edge with a white
 * spinner, scans for a few seconds, then springs back.
 */
@Composable
private fun SearchButton(onSearch: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(scanning) {
        if (scanning) {
            onSearch()
            delay(4000)
            scanning = false
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
        val width by androidx.compose.animation.core.animateDpAsState(
            if (scanning) 60.dp else maxWidth,
            androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 260f),
            label = "searchWidth",
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                .height(60.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(colors.accent)
                .clickable(remember { MutableInteractionSource() }, null, enabled = !scanning) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    scanning = true
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(scanning, label = "searchContent") { busy ->
                if (busy) {
                    Spinner(color = Color.White)
                } else {
                    Label(
                        "Search for devices",
                        AppTheme.type.headline.copy(fontWeight = FontWeight.Medium),
                        color = Color.White, maxLines = 1,
                    )
                }
            }
        }
    }
}

// MARK: - Settings

@Composable
private fun SettingsTab(vm: RemoteViewModel) {
    val mode by vm.themeMode.collectAsState()
    val colors = AppTheme.colors
    val uri = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(40.dp))
        Column {
            Label("Settings", AppTheme.type.hero)
            Label("SlideSwitch for Android", AppTheme.type.title3)
        }
        Spacer(Modifier.height(48.dp))

        SectionTitle("Appearance")
        Segmented(
            options = ThemeMode.entries.map { it.label },
            selected = mode.ordinal,
            onSelect = { vm.setThemeMode(ThemeMode.entries[it]) },
            height = 48.dp,
        )
        Spacer(Modifier.height(36.dp))

        SectionTitle("Buy me a coffee")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(colors.well)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Placeholder for the UPI QR code (to be added later).
            Box(
                Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White),
            )
            Spacer(Modifier.height(14.dp))
            Label(
                "Enjoying SlideSwitch? Scan to buy me a coffee.",
                AppTheme.type.subheadline.copy(textAlign = TextAlign.Center),
                color = colors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(36.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Made with ❤️ by Harsha", AppTheme.type.footnote, color = colors.secondaryLabel)
            Spacer(Modifier.height(4.dp))
            BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.secondaryLabel)) { append("View more of my work at ") }
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) { append("HA.CH") }
                },
                style = AppTheme.type.footnote,
                modifier = Modifier
                    .clickable(remember { MutableInteractionSource() }, null) { uri.openUri(PORTFOLIO_URL) }
                    .padding(vertical = 6.dp, horizontal = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Label(text, AppTheme.type.title3, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
}

// MARK: - Sheets

@Composable
private fun ManualSheet(onConnect: (String, Int) -> Unit, onCancel: () -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("48620") }
    Label("Enter Address", AppTheme.type.title2)
    Spacer(Modifier.height(6.dp))
    Label(
        "Use the address and port shown in SlideSwitch on your computer.",
        AppTheme.type.subheadline.copy(textAlign = TextAlign.Center),
        color = AppTheme.colors.secondaryLabel,
    )
    Spacer(Modifier.height(20.dp))
    Field("IP address, e.g. 192.168.1.20", host, KeyboardType.Uri) { host = it }
    Spacer(Modifier.height(10.dp))
    Field("Port", port, KeyboardType.Number) { port = it.filter(Char::isDigit).take(5) }
    Spacer(Modifier.height(20.dp))
    PillButton(
        "Connect",
        enabled = host.isNotBlank() && port.toIntOrNull() != null,
        onClick = { onConnect(host.trim(), port.toInt()) },
    )
    PillButton("Cancel", color = Color.Transparent, textColor = AppTheme.colors.accent, onClick = onCancel)
}

@Composable
private fun Field(placeholder: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    val colors = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = AppTheme.type.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.fill)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (value.isEmpty()) Label(placeholder, AppTheme.type.body, color = colors.secondaryLabel)
                inner()
            }
        },
    )
}

@Composable
private fun CodeSheet(state: ConnectionState.NeedsCode, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var code by remember(state) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val colors = AppTheme.colors
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }

    Label("Enter Pairing Code", AppTheme.type.title2)
    Spacer(Modifier.height(6.dp))
    Label(
        if (state.wrongCode) "That code didn't match. Check the code shown on ${state.target.name}."
        else "Type the 6-digit code shown in SlideSwitch on ${state.target.name}.",
        AppTheme.type.subheadline.copy(textAlign = TextAlign.Center),
        color = if (state.wrongCode) colors.red else colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(22.dp))

    BasicTextField(
        value = code,
        onValueChange = { new ->
            code = new.filter(Char::isDigit).take(6)
            if (code.length == 6) onSubmit(code)
        },
        modifier = Modifier.focusRequester(focus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val char = code.getOrNull(i)?.toString() ?: ""
                    val active = i == code.length
                    Box(
                        Modifier
                            .width(46.dp)
                            .aspectRatio(0.82f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.fill)
                            .border(2.dp, if (active) colors.accent else Color.Transparent, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(char, AppTheme.type.title1.copy(fontSize = 28.sp, textAlign = TextAlign.Center))
                    }
                }
            }
        },
    )
    Spacer(Modifier.height(24.dp))
    PillButton("Connect", enabled = code.length == 6, onClick = { onSubmit(code) })
    PillButton("Cancel", color = Color.Transparent, textColor = colors.accent, onClick = onCancel)
}

@Composable
private fun Banner(title: String, message: String, action: String, onAction: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.well)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIcon(Glyphs.Close, Gradients.end, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Label(title, AppTheme.type.headline)
            Label(message, AppTheme.type.footnote, color = colors.secondaryLabel, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Label(action, AppTheme.type.headline, color = colors.accent, modifier = Modifier.clickable(onClick = onAction))
    }
}

/** iOS-like activity indicator (a rotating arc). */
@Composable
fun Spinner(color: Color = AppTheme.colors.secondaryLabel) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Canvas(Modifier.size(20.dp)) {
        drawArc(color, angle, 270f, false, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// MARK: - Remote

@Composable
private fun RemoteScreen(vm: RemoteViewModel, state: ConnectionState.Connected) {
    val error by vm.lastError.collectAsState()
    val colors = AppTheme.colors
    val caps = state.capabilities
    fun can(action: Action) = action.wire in caps

    LaunchedEffect(error) {
        if (error != null) {
            delay(4000)
            vm.clearError()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(
                "Disconnect", AppTheme.type.body, color = colors.accent,
                modifier = Modifier
                    .clickable(remember { MutableInteractionSource() }, null) { vm.disconnect() }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            StatusPill("Connected", colors.green)
        }
        Spacer(Modifier.height(16.dp))
        Label(state.serverName, AppTheme.type.largeTitle, maxLines = 1)
        Label(
            "${platformName(state.platform) ?: "Computer"} · keep your presentation app in front",
            AppTheme.type.subheadline, color = colors.secondaryLabel, maxLines = 1,
        )
        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(error != null) {
            Column {
                Banner(
                    title = "Command not delivered",
                    message = error ?: "",
                    action = "OK",
                    onAction = vm::clearError,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        BigButton(
            title = "Next",
            glyph = Glyphs.ChevronRight,
            gradient = Gradients.next,
            glyphSize = 72.dp,
            enabled = can(Action.Next),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onClick = { vm.send(Action.Next) },
        )
        Spacer(Modifier.height(12.dp))
        BigButton(
            title = "Previous",
            glyph = Glyphs.ChevronLeft,
            gradient = Gradients.previous,
            glyphSize = 36.dp,
            enabled = can(Action.Previous),
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            onClick = { vm.send(Action.Previous) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TileButton("Start", Glyphs.Play, Gradients.start, Modifier.weight(1f), can(Action.Start)) { vm.send(Action.Start) }
            TileButton("Black", Glyphs.Display, Gradients.black, Modifier.weight(1f), can(Action.Black)) { vm.send(Action.Black) }
            TileButton("End", Glyphs.Stop, Gradients.end, Modifier.weight(1f), can(Action.End)) { vm.send(Action.End) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
