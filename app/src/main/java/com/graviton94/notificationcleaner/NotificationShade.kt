package com.graviton94.notificationcleaner

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class ShadeColors(
    val top: Color, val background: Color, val card: Color, val alt: Color, val label: Color,
    val sub: Color, val accent: Color, val onAccent: Color, val line: Color, val gold: Color,
)

private val night = ShadeColors(
    Color(0xFF263C49), Color(0xFF101722), Color(0xFF263342), Color(0xFF344455),
    Color(0xFFF3F8FB), Color(0xFFB9CAD5), Color(0xFFA6ECD5), Color(0xFF143832),
    Color(0xFF4C5D6D), Color(0xFFFFD99A),
)
private val day = ShadeColors(
    Color(0xFFE5F2EE), Color(0xFFD6E4EF), Color(0xFFF8FBFF), Color(0xFFE7EFF4),
    Color(0xFF172C37), Color(0xFF465F6B), Color(0xFF286456), Color.White,
    Color(0xFF8FA6B1), Color(0xFF754F14),
)
private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

internal fun pretty(value: Long): String = when {
    value < 100_000 -> String.format(Locale.KOREAN, "%,d", value)
    value < 100_000_000 -> String.format(Locale.KOREAN, "%.1f만", value / 10_000.0)
    else -> String.format(Locale.KOREAN, "%.1f억", value / 100_000_000.0)
}

internal fun percent(value: Int): String = "×" + String.format(Locale.KOREAN, "%.2f", value / 100.0)

internal enum class PanelSheet { Settings, Networks, Devices, Notifications, Store, Records, Alert }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleanerApp(session: GameSession, battery: Int) {
    var expanded by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PanelSheet?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val latestBlocked by rememberUpdatedState(dragging || sheet != null)
    val panel = session.panel
    val colors = if (panel.dark) night else day
    val haptic = LocalHapticFeedback.current
    val sound = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 40) }.getOrNull() }
    DisposableEffect(sound) { onDispose { sound?.release() } }

    LaunchedEffect(session) {
        while (true) {
            session.tick(latestBlocked)
            delay(250)
        }
    }
    LaunchedEffect(session.burst) {
        if (session.burst > 0 && !panel.quiet) {
            if (panel.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (panel.sound) sound?.startTone(
                if (session.burst >= 2) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 95,
            )
        }
    }

    val colorScheme = if (panel.dark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, surface = colors.card, onSurface = colors.label,
        background = colors.background, onBackground = colors.label, outline = colors.line,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, surface = colors.card, onSurface = colors.label,
        background = colors.background, onBackground = colors.label, outline = colors.line,
    )
    MaterialTheme(colorScheme) {
        val background = if (panel.torch) {
            if (panel.dark) listOf(Color(0xFF405450), Color(0xFF19272C)) else listOf(Color.White, colors.background)
        } else listOf(colors.top, colors.background, colors.background)
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(background))
                .windowInsetsPadding(WindowInsets.displayCutout),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 532.dp).fillMaxHeight().fillMaxWidth().padding(horizontal = 17.dp)
            ) {
                val clock = Instant.ofEpochMilli(session.now).atZone(ZoneId.systemDefault())
                StatusLine(clock.format(timeFormat), battery, panel, colors)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item(key = "settings") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Heading(clock.format(dateFormat), expanded, { expanded = !expanded }, { sheet = PanelSheet.Settings }, colors)
                            QuickControls(panel, expanded, session::updatePanel, { sheet = it }, colors)
                            ProgressLine(session.state, colors)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 3.dp, end = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("알림", color = colors.label, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { sheet = PanelSheet.Notifications }) { Text("알림 관리") }
                            }
                        }
                    }
                    items(session.shownRows, key = { it.id }) { notice ->
                        NotificationRow(
                            notice = notice, now = session.now, tutorial = session.state.swipes == 0 && notice.app == NoticeApp.Delivery,
                            highlight = notice.app == session.merged, blocked = session.busy, preview = panel.previews,
                            onSwipe = { session.swipe(notice.id) }, onDrag = { dragging = it }, colors = colors,
                            modifier = if (panel.reduceMotion) Modifier else Modifier.animateItem(),
                        )
                    }
                    item(key = "air") { Spacer(Modifier.height(2.dp)) }
                }
                AnimatedVisibility(session.now < session.state.eventUntil) {
                    EventStrip(session.state, session.now, session::tapEvent, colors)
                }
                GameBar(session, { sheet = it }, colors)
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp, bottom = 8.dp)
                        .width(82.dp).height(3.dp).clip(CircleShape).background(colors.sub.copy(alpha = .8f))
                )
            }
            if (session.burst >= 2) {
                BurstOverlay(session.burst, session.state.lastScore?.points ?: 0, colors, panel.reduceMotion)
            }
        }
        sheet?.let { open ->
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                containerColor = colors.card,
                contentColor = colors.label,
            ) {
                ShadeSheet(open, session, { sheet = null }, { sheet = it }, colors)
            }
        }
    }
}

@Composable
private fun StatusLine(time: String, battery: Int, panel: PanelSettings, colors: ShadeColors) {
    Row(
        Modifier.fillMaxWidth().height(37.dp).padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (panel.quiet) Icon(Icons.Default.DoNotDisturbOn, "방해 금지", Modifier.size(13.dp), tint = colors.sub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (panel.bluetooth) Icon(Icons.Default.Bluetooth, "블루투스 켜짐", Modifier.size(14.dp), tint = colors.sub)
            if (panel.wifi) Icon(Icons.Default.Wifi, "Wi-Fi 켜짐", Modifier.size(15.dp), tint = colors.label)
            Icon(
                if (panel.airplane) Icons.Default.AirplanemodeActive else Icons.Default.SignalCellularAlt,
                if (panel.airplane) "비행기 모드" else "신호",
                Modifier.size(15.dp), tint = colors.label,
            )
            Surface(color = if (panel.saver) colors.gold else colors.label, shape = RoundedCornerShape(5.dp)) {
                Text(
                    "$battery%", Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = colors.top, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun Heading(date: String, expanded: Boolean, expand: () -> Unit, settings: () -> Unit, colors: ShadeColors) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(date, fontSize = 13.sp, color = colors.sub, lineHeight = 18.sp)
            Text("알림 센터", color = colors.label, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Black)
        }
        Row {
            IconButton(onClick = expand) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "빠른 설정 접기" else "빠른 설정 펼치기",
                    tint = colors.sub,
                )
            }
            IconButton(onClick = settings) { Icon(Icons.Default.Settings, "설정", tint = colors.label) }
        }
    }
}

@Composable
private fun QuickControls(
    panel: PanelSettings,
    expanded: Boolean,
    update: (PanelSettings) -> Unit,
    open: (PanelSheet) -> Unit,
    colors: ShadeColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QuickTile("Wi-Fi", if (panel.wifi) Icons.Default.Wifi else Icons.Default.WifiOff, panel.wifi, colors, Modifier.weight(1f)) {
                update(panel.copy(wifi = !panel.wifi))
            }
            QuickTile("블루투스", Icons.Default.Bluetooth, panel.bluetooth, colors, Modifier.weight(1f)) {
                update(panel.copy(bluetooth = !panel.bluetooth))
            }
            QuickTile("방해 금지", Icons.Default.DoNotDisturbOn, panel.quiet, colors, Modifier.weight(1f)) {
                update(panel.copy(quiet = !panel.quiet))
            }
            QuickTile("손전등", Icons.Default.FlashlightOn, panel.torch, colors, Modifier.weight(1f)) {
                update(panel.copy(torch = !panel.torch))
            }
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickTile("비행기", Icons.Default.AirplanemodeActive, panel.airplane, colors, Modifier.weight(1f)) {
                        update(panel.copy(airplane = !panel.airplane, wifi = if (panel.airplane) panel.wifi else false))
                    }
                    QuickTile("어두운 화면", Icons.Default.DarkMode, panel.dark, colors, Modifier.weight(1f)) {
                        update(panel.copy(dark = !panel.dark))
                    }
                    QuickTile("절전", Icons.Default.BatterySaver, panel.saver, colors, Modifier.weight(1f)) {
                        update(panel.copy(saver = !panel.saver))
                    }
                    QuickTile("알림 소리", if (panel.sound) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        panel.sound, colors, Modifier.weight(1f)) { update(panel.copy(sound = !panel.sound)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = { open(PanelSheet.Networks) }, Modifier.weight(1f)) { Text("네트워크") }
                    TextButton(onClick = { open(PanelSheet.Devices) }, Modifier.weight(1f)) { Text("연결된 기기") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LightMode, "밝기", Modifier.size(19.dp), tint = colors.sub)
            Slider(
                value = panel.brightness, onValueChange = { update(panel.copy(brightness = it, automaticBrightness = false)) },
                modifier = Modifier.weight(1f).height(33.dp).padding(horizontal = 9.dp),
                valueRange = .1f..1f,
                colors = SliderDefaults.colors(activeTrackColor = colors.accent, thumbColor = colors.accent, inactiveTrackColor = colors.alt),
            )
            Text("${(panel.brightness * 100).toInt()}%", color = colors.sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuickTile(label: String, glyph: ImageVector, active: Boolean, colors: ShadeColors, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        color = if (active) colors.accent else colors.alt,
        contentColor = if (active) colors.onAccent else colors.label,
    ) {
        Column(
            Modifier.heightIn(min = 62.dp).padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Icon(glyph, if (active) "켜짐" else "꺼짐", Modifier.size(21.dp))
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProgressLine(state: GameState, colors: ShadeColors) {
    Column(Modifier.padding(horizontal = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("레벨 ${state.level}", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${pretty(state.xp)} / ${pretty(GameEngine.goal(state.level))} XP", color = colors.sub, fontSize = 11.sp)
        }
        LinearProgressIndicator(
            progress = { (state.xp.toFloat() / GameEngine.goal(state.level)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = colors.accent, trackColor = colors.alt,
        )
    }
}

@Composable
private fun NotificationRow(
    notice: Notice, now: Long, tutorial: Boolean, highlight: Boolean, blocked: Boolean, preview: Boolean,
    onSwipe: () -> Unit, onDrag: (Boolean) -> Unit, colors: ShadeColors, modifier: Modifier,
) {
    var drag by remember(notice.id) { mutableFloatStateOf(0f) }
    var width by remember(notice.id) { mutableIntStateOf(0) }
    var active by remember(notice.id) { mutableStateOf(false) }
    val doSwipe by rememberUpdatedState(onSwipe)
    val dragState by rememberUpdatedState(onDrag)
    val animated by animateFloatAsState(drag, if (active) snap() else tween(155), label = "Swipe")
    val shape = RoundedCornerShape(17.dp)
    val timestamp = if (now - notice.arrivedAt < 60_000) "지금" else "${((now - notice.arrivedAt) / 60_000).coerceAtMost(99)}분 전"
    Box(
        modifier.fillMaxWidth().clip(shape).onSizeChanged { width = it.width }
            .background(if (drag == 0f) Color.Transparent else Color(0xFF674453))
            .semantics { customActions = listOf(CustomAccessibilityAction("알림 지우기") { if (blocked) false else { doSwipe(); true } }) }
            .pointerInput(notice.id, blocked) {
                if (blocked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { active = true; dragState(true) },
                    onDragEnd = {
                        val commit = width > 0 && abs(drag) >= width * .25f
                        active = false
                        dragState(false)
                        drag = 0f
                        if (commit) doSwipe()
                    },
                    onDragCancel = { active = false; dragState(false); drag = 0f },
                ) { change, amount ->
                    change.consume()
                    drag = (drag + amount).coerceIn(-width.toFloat(), width.toFloat())
                }
            }
    ) {
        if (drag != 0f) {
            Text("지우기", Modifier.align(if (drag > 0) Alignment.CenterStart else Alignment.CenterEnd).padding(12.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val eventFill = when (notice.app) {
            NoticeApp.Spam -> if (colors == night) Color(0xFF503645) else Color(0xFFF8D9E3)
            NoticeApp.Call -> if (colors == night) Color(0xFF304E46) else Color(0xFFD7F0E7)
            NoticeApp.Drill -> if (colors == night) Color(0xFF584435) else Color(0xFFFBE4C8)
            else -> colors.card
        }
        Surface(
            modifier = Modifier.graphicsLayer { translationX = animated },
            color = if (tutorial || highlight) colors.alt else eventFill,
            shape = shape,
            border = if (tutorial || highlight) androidx.compose.foundation.BorderStroke(1.dp, colors.accent) else null,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 55.dp).padding(vertical = 6.dp, horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(notice.app)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(notice.app.label, color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(timestamp, color = colors.sub, fontSize = 10.sp, maxLines = 1)
                    }
                    val text = when {
                        tutorial -> "옆으로 밀어 같은 앱을 이어 보세요."
                        !preview && !notice.app.isEvent -> "알림 내용 숨김"
                        notice.app == NoticeApp.Spam && notice.copyAfter == 0 -> "광고 알림이 한 줄 더 도착했어요."
                        else -> notice.app.message
                    }
                    Text(text, color = colors.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (tutorial || notice.count > 1) Surface(color = colors.background, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        if (tutorial) "↔" else if (notice.threshold > 4) "${pretty(notice.count.toLong())}/${notice.threshold}" else notice.count.toString(),
                        Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = colors.accent,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(app: NoticeApp, modifier: Modifier = Modifier) {
    val (color, icon) = when (app) {
        NoticeApp.Calendar -> Color(0xFFFFC99F) to Icons.Default.CalendarMonth
        NoticeApp.Mail, NoticeApp.Inbox -> Color(0xFFA9D9FB) to Icons.Default.Mail
        NoticeApp.Chat -> Color(0xFFCDBDFF) to Icons.Default.ChatBubble
        NoticeApp.Delivery -> Color(0xFFF6BBD6) to Icons.Default.LocalShipping
        NoticeApp.Photo -> Color(0xFFF9C5B5) to Icons.Default.Collections
        NoticeApp.Music -> Color(0xFFF6DDA7) to Icons.Default.MusicNote
        NoticeApp.Payment -> Color(0xFFB4EBC8) to Icons.Default.AccountBalanceWallet
        NoticeApp.Spam -> Color(0xFFFFB7C5) to Icons.Default.MarkEmailUnread
        NoticeApp.Call -> Color(0xFFA9EAC3) to Icons.Default.Phone
        NoticeApp.Drill -> Color(0xFFFFD494) to Icons.Default.WarningAmber
    }
    Box(
        modifier.size(33.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color(0xFF23384D))
    }
}

@Composable
private fun EventStrip(state: GameState, now: Long, tap: () -> Unit, colors: ShadeColors) {
    val seconds = ((state.eventUntil - now + 999) / 1_000).coerceIn(0, 60)
    Surface(Modifier.fillMaxWidth().padding(bottom = 5.dp), shape = RoundedCornerShape(15.dp), color = colors.alt) {
        Row(
            Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 3.dp)) {
                Text("훈련 알림 · ${seconds}초", color = colors.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${state.eventTaps}탭 · ${percent(GameEngine.eventPercent(state, now))}", color = colors.label, fontSize = 13.sp)
            }
            Button(onClick = tap, enabled = state.eventTaps < 100) { Text("탭해서 배율 올리기", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun GameBar(session: GameSession, open: (PanelSheet) -> Unit, colors: ShadeColors) {
    val state = session.state
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(session.message, Modifier.weight(1f), color = colors.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.rewardOffer > 0 && !session.busy) TextButton(onClick = session::claim) {
                Text("+${pretty(state.rewardOffer)} P 체험", fontSize = 11.sp)
            }
        }
        Surface(shape = RoundedCornerShape(15.dp), color = colors.card) {
            Row(
                Modifier.fillMaxWidth().padding(start = 9.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("스와이프 ${state.energy}/${GameEngine.ENERGY_CAP}", color = colors.label, fontSize = 12.sp)
                    Text("${pretty(state.points)} P", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { open(PanelSheet.Store) }) { Icon(Icons.Default.Storefront, "앱 상점", tint = colors.sub, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { open(PanelSheet.Records) }) { Icon(Icons.Default.Leaderboard, "최고점 기록", tint = colors.sub, modifier = Modifier.size(20.dp)) }
                val autoText = when {
                    !state.auto -> "자동 꺼짐"
                    session.now < state.pauseUntil -> "통화 대기"
                    else -> "자동 켜짐"
                }
                FilledTonalButton(
                    onClick = session::toggleAuto,
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier.padding(end = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (state.auto) colors.accent else colors.alt,
                        contentColor = if (state.auto) colors.onAccent else colors.label),
                ) { Text(autoText, fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun BurstOverlay(combo: Int, points: Long, colors: ShadeColors, reduceMotion: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shards = remember(combo) {
            val random = Random(combo * 529)
            List(28) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .52f, size.height * .48f)
            val reach = maxOf(size.width, size.height)
            for (index in 0..10) {
                val angle = (index.toDouble() + .3) * 2 * PI / 11
                val dx = cos(angle).toFloat() * reach
                val dy = sin(angle).toFloat() * reach
                val bend = center + Offset(dx * .25f - dy * .05f, dy * .25f + dx * .04f)
                drawLine(colors.accent.copy(alpha = .63f), center, bend, 1.5.dp.toPx())
                drawLine(colors.accent.copy(alpha = .38f), bend, center + Offset(dx, dy), 1.dp.toPx())
            }
            if (!reduceMotion) shards.forEachIndexed { index, (x, y, scale) ->
                val position = Offset(x * size.width, y * size.height)
                val radius = (4 + 13 * scale).dp.toPx()
                val path = Path().apply {
                    moveTo(position.x, position.y - radius)
                    lineTo(position.x + radius * .7f, position.y + radius * .36f)
                    lineTo(position.x - radius * .7f, position.y + radius * .66f)
                    close()
                }
                drawPath(path, (if (index % 3 == 0) colors.gold else colors.accent).copy(alpha = .19f + scale * .34f))
            }
        }
        Surface(shape = RoundedCornerShape(17.dp), color = Color(0xE8182938)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${combo}연쇄!", color = Color(0xFFF2FFA7), fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text("+${pretty(points)} 점", color = Color(0xFFD6FDF0), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
