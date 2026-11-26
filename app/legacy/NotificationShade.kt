package com.graviton94.notificationcleaner

import android.animation.ValueAnimator
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal data class ShadeColors(
    val top: Color, val background: Color, val card: Color, val alt: Color, val label: Color,
    val sub: Color, val accent: Color, val onAccent: Color, val line: Color, val gold: Color,
)

private val night = ShadeColors(
    Color(0xFF21263C), Color(0xFF10111D), Color(0xFF232638), Color(0xFF30364E),
    Color(0xFFF6F5FC), Color(0xFFC8C9DD), Color(0xFFB0F4DB), Color(0xFF102F29),
    Color(0xFF4D536F), Color(0xFFFFD792),
)
private val day = ShadeColors(
    Color(0xFFE5F2EE), Color(0xFFD6E4EF), Color(0xFFF8FBFF), Color(0xFFE7EFF4),
    Color(0xFF172C37), Color(0xFF465F6B), Color(0xFF286456), Color.White,
    Color(0xFF8FA6B1), Color(0xFF754F14),
)
private val timeFormat = DateTimeFormatter.ofPattern("H:mm")
private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

private val units = listOf("", "만", "억", "조", "경", "해", "자", "양", "구", "간", "정", "재")

internal fun pretty(value: Long): String = pretty(valueOf(value))

internal fun pretty(value: BigInteger): String {
    val digits = value.toString()
    if (digits.length <= 5) return String.format(Locale.KOREAN, "%,d", value)
    val group = (digits.length - 1) / 4
    if (group >= units.size) return "${digits.first()}.${digits[1]}${digits[2]}e${digits.length - 1}"
    val leading = digits.length - group * 4
    return digits.take(leading) + "." + digits[leading] + units[group]
}

internal fun percent(value: Int): String = percent(valueOf(value.toLong()))

internal fun percent(value: BigInteger): String {
    val (integral, fraction) = value.divideAndRemainder(valueOf(100))
    return if (integral >= valueOf(10_000)) "×${pretty(integral)}"
        else "×$integral.${fraction.toString().padStart(2, '0')}"
}

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
    val reduced = panel.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember(context) { ImpactFeedback(context) }
    val strike = session.strike
    val elapsed = impactClock(strike, reduced)
    val listState = rememberLazyListState()
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    val anchor = remember(strike) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == strike?.frame?.target?.id }
        item?.let {
            Offset(listState.layoutInfo.viewportSize.width * .5f + (strike?.fromX ?: 0f), it.offset + it.size * .5f) +
                listOrigin - viewportOrigin
        }
    }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    LaunchedEffect(session.active, panel.quiet) {
        if (!session.active || panel.quiet) feedback.stopEffects()
    }
    LaunchedEffect(panel.haptics, panel.quiet) {
        if (!panel.haptics || panel.quiet) feedback.stopVibration()
    }

    LaunchedEffect(session) {
        while (true) {
            session.tick(latestBlocked)
            delay(250)
        }
    }
    LaunchedEffect(strike) {
        if (strike != null && session.active) feedback.strike(view, strike, panel)
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
                .windowInsetsPadding(WindowInsets.displayCutout)
                .onGloballyPositioned { viewportOrigin = it.positionInRoot() },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 532.dp).fillMaxHeight().fillMaxWidth().padding(horizontal = 15.dp)
                    .graphicsLayer {
                        if (!reduced && strike != null) {
                            val kick = strike.shake(elapsed.value)
                            translationX = kick.x.dp.toPx()
                            translationY = kick.y.dp.toPx()
                        }
                    }
            ) {
                val clock = Instant.ofEpochMilli(session.now).atZone(ZoneId.systemDefault())
                StatusLine(clock.format(timeFormat), battery, panel, colors)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { listOrigin = it.positionInRoot() },
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    item(key = "settings") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Heading(clock.format(dateFormat), expanded, { expanded = !expanded }, { sheet = PanelSheet.Settings }, colors)
                            QuickControls(panel, expanded, session::updatePanel, { sheet = it }, colors)
                            ProgressLine(session.state, strike, colors)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 3.dp, end = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("알림 · ${session.shownRows.size}", color = colors.label, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { sheet = PanelSheet.Notifications }) { Text("알림 관리", fontSize = 12.sp) }
                            }
                        }
                    }
                    items(session.shownRows, key = { it.id }) { notice ->
                        NotificationRow(
                            notice = notice, now = session.now, tutorial = session.state.swipes == 0 && notice.app == NoticeApp.Delivery,
                            hint = session.state.swipes == 0 && notice.app == NoticeApp.Chat,
                            strike = strike?.takeIf { it.hitsRow(notice.id) }, elapsed = elapsed,
                            blocked = session.busy || session.state.energy == 0, preview = panel.previews, reduced = reduced,
                            onSwipe = { direction, from -> session.swipe(notice.id, direction, from) },
                            onThreshold = { feedback.threshold(view, panel) }, onDrag = { dragging = it }, colors = colors,
                            modifier = if (reduced) Modifier else Modifier.animateItem(
                                fadeInSpec = tween(175), placementSpec = spring(dampingRatio = .72f, stiffness = 590f), fadeOutSpec = null,
                            ),
                        )
                    }
                    item(key = "air") { Spacer(Modifier.height(2.dp)) }
                }
                if (session.now < session.state.eventUntil) {
                    EventStrip(session.state, session.now, {
                        if (session.tapEvent()) feedback.threshold(view, panel)
                    }, colors)
                }
                GameBar(session, { sheet = it }, colors)
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp, bottom = 8.dp)
                        .width(82.dp).height(3.dp).clip(CircleShape).background(colors.sub.copy(alpha = .8f))
                )
            }
            ImpactEffects(strike, anchor, elapsed, reduced)
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
            IconButton(onClick = settings) {
                KenneyIcon(R.drawable.kenney_settings, "설정", colors.label, Modifier.size(21.dp))
            }
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
private fun ProgressLine(state: GameState, strike: Strike?, colors: ShadeColors) {
    val hot = strike?.kind == ImpactKind.Overflow || strike?.kind == ImpactKind.Finish
    val pressureColor = if (hot || state.pressure >= 70) colors.gold else colors.accent
    Surface(
        color = colors.card, shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, if (hot) colors.gold else colors.line.copy(alpha = .65f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("레벨 ${state.level}", color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${pretty(state.xp)}/${pretty(GameEngine.goal(state.level))} XP", Modifier.weight(1f),
                    color = colors.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                KenneyIcon(R.drawable.kenney_power, null, pressureColor, Modifier.size(13.dp))
                Text(
                    if (hot) "  과부하 방출" else "  과부하 ${state.pressure}%", color = pressureColor,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Row(
                Modifier.fillMaxWidth().semantics { stateDescription = "과부하 ${state.pressure}%" },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(20) { index ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(
                            if (hot || state.pressure / 5 > index) pressureColor else colors.alt,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notice: Notice, now: Long, tutorial: Boolean, hint: Boolean,
    strike: Strike?, elapsed: State<Float>, blocked: Boolean, preview: Boolean, reduced: Boolean,
    onSwipe: (Float, Float) -> Unit, onThreshold: () -> Unit, onDrag: (Boolean) -> Unit,
    colors: ShadeColors, modifier: Modifier,
) {
    var drag by remember(notice.id) { mutableFloatStateOf(0f) }
    var width by remember(notice.id) { mutableIntStateOf(0) }
    var active by remember(notice.id) { mutableStateOf(false) }
    var armed by remember(notice.id) { mutableStateOf(false) }
    val doSwipe by rememberUpdatedState(onSwipe)
    val thresholdFeedback by rememberUpdatedState(onThreshold)
    val dragState by rememberUpdatedState(onDrag)
    val animated by animateFloatAsState(
        drag, if (active || reduced) snap() else spring(dampingRatio = .62f, stiffness = 620f), label = "Swipe recoil",
    )
    val hot = strike != null && strike.kind != ImpactKind.Swipe
    val fired = strike?.kind == ImpactKind.Swipe
    val shape = RoundedCornerShape(16.dp)
    val clip = if (fired) Modifier else Modifier.clip(shape)
    val timestamp = if (now - notice.arrivedAt < 60_000) "지금" else "${((now - notice.arrivedAt) / 60_000).coerceAtMost(99)}분 전"
    Box(
        modifier.fillMaxWidth().then(clip).onSizeChanged { width = it.width }
            .background(if (drag == 0f) Color.Transparent else if (armed) colors.accent else colors.alt, shape)
            .semantics {
                customActions = if (blocked) emptyList() else listOf(CustomAccessibilityAction("알림 지우기") {
                    doSwipe(1f, 0f)
                    true
                })
            }
            .pointerInput(notice.id, blocked) {
                if (blocked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { active = true; dragState(true) },
                    onDragEnd = {
                        if (armed) doSwipe(if (drag < 0) -1f else 1f, drag)
                        active = false
                        armed = false
                        drag = 0f
                        dragState(false)
                    },
                    onDragCancel = { active = false; armed = false; dragState(false); drag = 0f },
                ) { change, amount ->
                    change.consume()
                    drag = (drag + amount).coerceIn(-width.toFloat(), width.toFloat())
                    val crosses = width > 0 && abs(drag) >= width * .23f
                    if (crosses && !armed) thresholdFeedback()
                    armed = crosses
                }
            },
    ) {
        if (drag != 0f) {
            val ink = if (armed) colors.onAccent else colors.label
            Row(
                Modifier.align(if (drag > 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                KenneyIcon(R.drawable.kenney_clear, null, ink, Modifier.size(18.dp))
                if (armed) Text("놓기", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        val eventFill = when (notice.app) {
            NoticeApp.Spam -> if (colors == night) Color(0xFF503645) else Color(0xFFF8D9E3)
            NoticeApp.Call -> if (colors == night) Color(0xFF304E46) else Color(0xFFD7F0E7)
            NoticeApp.Drill -> if (colors == night) Color(0xFF584435) else Color(0xFFFBE4C8)
            else -> colors.card
        }
        Surface(
            modifier = Modifier.graphicsLayer {
                translationX = animated
                if (strike != null && !reduced) {
                    if (fired) {
                        val takeoff = ((elapsed.value - strike.freezeMs) / 55f).coerceIn(0f, 1f)
                        translationX = strike.fromX + strike.direction * width * takeoff * takeoff * .9f
                        rotationZ = strike.direction * (1 + takeoff * 14)
                        alpha = 1f - takeoff * .88f
                    } else {
                        val recoil = (1f - elapsed.value / strike.durationMs).coerceIn(0f, 1f)
                        translationY = (if (notice.id == strike.frame.target.id) 10.dp else (-12).dp).toPx() * recoil
                        translationX = strike.direction * (if (strike.kind == ImpactKind.Burst) 20.dp else 5.dp).toPx() * recoil
                        scaleX = 1f + recoil * .025f
                        scaleY = 1f - recoil * .12f
                    }
                }
            },
            color = when {
                hot -> if (colors == night) Color(0xFF58506A) else Color(0xFFFFE5BD)
                tutorial -> colors.alt
                else -> eventFill
            },
            shape = shape,
            border = when {
                hot -> BorderStroke(1.dp, requireNotNull(strike).tint())
                tutorial || hint -> BorderStroke(1.dp, colors.accent.copy(alpha = if (tutorial) 1f else .53f))
                else -> BorderStroke(1.dp, colors.line.copy(alpha = .38f))
            },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 53.dp).padding(vertical = 6.dp, horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(notice.app)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(notice.app.label, color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(timestamp, color = colors.sub, fontSize = 10.sp, maxLines = 1)
                    }
                    val text = when {
                        tutorial -> "여기를 옆으로 밀어 연쇄를 터뜨리세요."
                        !preview && !notice.app.isEvent -> "알림 내용 숨김"
                        notice.app == NoticeApp.Spam && notice.copyAfter == 0 -> "광고 알림이 한 줄 더 도착했어요."
                        else -> notice.app.message
                    }
                    Text(text, color = colors.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    tutorial -> KenneyIcon(R.drawable.kenney_clear, "옆으로 밀기", colors.accent, Modifier.size(20.dp))
                    !notice.app.isEvent -> Column(
                        Modifier.widthIn(min = 37.dp, max = 75.dp).background(colors.background, RoundedCornerShape(7.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            pretty(notice.count), color = if (notice.count >= notice.threshold) colors.gold else colors.accent,
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (notice.threshold > ONE) Text("/ ${pretty(notice.threshold)}", color = colors.sub, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KenneyIcon(@DrawableRes id: Int, label: String?, color: Color, modifier: Modifier = Modifier) {
    Icon(painterResource(id), label, modifier, tint = color)
}

@Composable
internal fun AppIcon(app: NoticeApp, modifier: Modifier = Modifier) {
    val (color, icon) = when (app) {
        NoticeApp.Calendar -> Color(0xFFFFC99F) to R.drawable.kenney_calendar
        NoticeApp.Mail -> Color(0xFFA9D9FB) to R.drawable.kenney_mail
        NoticeApp.Inbox -> Color(0xFFC6CFED) to R.drawable.kenney_inbox
        NoticeApp.Chat -> Color(0xFFCDBDFF) to R.drawable.kenney_chat
        NoticeApp.Delivery -> Color(0xFFF6BBD6) to R.drawable.kenney_delivery
        NoticeApp.Photo -> Color(0xFFF9C5B5) to R.drawable.kenney_photo
        NoticeApp.Music -> Color(0xFFF6DDA7) to R.drawable.kenney_music
        NoticeApp.Payment -> Color(0xFFB4EBC8) to R.drawable.kenney_pay
        NoticeApp.Spam -> Color(0xFFFFB7C5) to R.drawable.kenney_spam
        NoticeApp.Call -> Color(0xFFA9EAC3) to R.drawable.kenney_call
        NoticeApp.Drill -> Color(0xFFFFD494) to R.drawable.kenney_drill
    }
    Box(
        modifier.size(35.dp).clip(RoundedCornerShape(10.dp)).background(
            Brush.linearGradient(listOf(color, color.copy(alpha = .78f))),
        ).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        KenneyIcon(icon, null, Color(0xFF222539), Modifier.size(19.dp))
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
            if (state.rewardOffer > ZERO && !session.busy) TextButton(onClick = session::claim) {
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
                    Text(
                        "${pretty(session.displayedPoints)} P", color = colors.accent, fontSize = 19.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { open(PanelSheet.Store) }) {
                    KenneyIcon(R.drawable.kenney_store, "앱 상점", colors.label, Modifier.size(20.dp))
                }
                IconButton(onClick = { open(PanelSheet.Records) }) {
                    KenneyIcon(R.drawable.kenney_records, "최고점 기록", colors.label, Modifier.size(20.dp))
                }
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
                ) {
                    KenneyIcon(R.drawable.kenney_auto, null, if (state.auto) colors.onAccent else colors.label, Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(autoText, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
