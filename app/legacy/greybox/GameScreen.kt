package com.graviton94.notificationcleaner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.graviton94.notificationcleaner.GameViewModel
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.Row as BoardRow
import com.graviton94.notificationcleaner.core.RowKind
import com.graviton94.notificationcleaner.core.short
import com.graviton94.notificationcleaner.fx.FxDirector
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val Ink = Color(0xFFF6F1EA)
private val Sub = Color(0xFFB3A89B)
private val Gold = Color(0xFFFFD66B)
private val Card = Color(0x1AFFFFFF)
private val Outline = Color(0xFF2A0C02)
private val ChipBlue = Color(0xFF2F7BF5)
private val MultRed = Color(0xFFFF3B30)

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.state
    val fx = vm.fx
    // Frame clock for the FX director (particles, shake, count-up).
    LaunchedEffect(Unit) { while (true) { withFrameNanos { fx.tick() } } }
    @Suppress("UNUSED_VARIABLE") val frame = fx.frame // read: redraw every frame
    val density = LocalDensity.current.density
    val rows = fx.frozenRows ?: s.rows
    val drop = fx.collapse

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1A10), Color(0xFF0C0907))))) {
        // ── world layer: shaken, cracked, flashed ──
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = fx.shakeX * density; translationY = fx.shakeY * density }) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp)) {
                Hud(vm, Modifier.graphicsLayer { translationY = drop * 70f * density; rotationZ = drop * -6f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 0f) })
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEachIndexed { i, row -> key(row.id) { RowCard(row, i, fx) { vm.swipe(row.id) } } }
                }
                Footer(vm, Modifier.graphicsLayer { translationY = drop * 110f * density; rotationZ = drop * 5f })
            }
            WorldFx(fx)
        }
        // ── score layer: never distorted ──
        ScoreLayer(fx, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 96.dp))
        vm.offlinePaid?.let { paid ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { vm.offlinePaid = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF241B14)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("자리 비운 동안 정리했어요", color = Sub, fontSize = 15.sp)
                    Text("+" + short(paid) + " P", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("눌러서 닫기", color = Sub, fontSize = 12.sp)
                }
            }
        }
        if (vm.shopOpen) ShopPanel(vm)
    }
}

/** Shards, sparks, screen glass cracks and the white flash, drawn over the board in window coordinates. */
@Composable
private fun WorldFx(fx: FxDirector) {
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { fx.origin = it.positionInWindow() }) {
        @Suppress("UNUSED_VARIABLE") val f = fx.frame
        val o = fx.origin
        for (p in fx.particles) {
            val fade = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
            translate(p.x - o.x, p.y - o.y) {
                rotate(p.rot, pivot = Offset.Zero) {
                    if (p.shard) {
                        val h = p.size
                        val path = Path().apply { moveTo(-h * 0.5f, -h * 0.35f); lineTo(h * 0.55f, -h * 0.15f); lineTo(h * 0.1f, h * 0.5f); close() }
                        drawPath(path, Outline.copy(alpha = fade), style = Stroke(width = 3f))
                        drawPath(path, p.color.copy(alpha = fade))
                    } else drawCircle(p.color.copy(alpha = fade), radius = p.size * 0.22f, center = Offset.Zero)
                }
            }
        }
        if (fx.crack > 0.01f) {
            val r = Random(fx.crackSeed)
            val c = Offset(size.width * (0.35f + r.nextFloat() * 0.3f), size.height * (0.3f + r.nextFloat() * 0.25f))
            val a = fx.crack.coerceAtMost(1f)
            repeat(11) { k ->
                var ang = k / 11f * 6.283f + r.nextFloat() * 0.4f
                var pt = c
                val path = Path().apply { moveTo(c.x, c.y) }
                repeat(5) {
                    val len = (40f + r.nextFloat() * 120f) * (1f + it * 0.3f)
                    ang += (r.nextFloat() - 0.5f) * 0.7f
                    pt = Offset(pt.x + cos(ang) * len, pt.y + sin(ang) * len)
                    path.lineTo(pt.x, pt.y)
                }
                drawPath(path, Color.Black.copy(alpha = 0.35f * a), style = Stroke(width = 6f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = 0.85f * a), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            }
            drawCircle(Color.White.copy(alpha = 0.5f * a), radius = 26f, center = c, style = Stroke(width = 3f))
        }
        if (fx.flash > 0.01f) drawRect(Color.White.copy(alpha = fx.flash * 0.55f))
    }
}

/** Balatro-style tally: chain label, chips × mult while the chain runs, then the total counts up and slams. */
@Composable
private fun ScoreLayer(fx: FxDirector, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val f = fx.frame
    val t = fx.now()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (fx.labelAt >= 0) {
            val e = (t - fx.labelAt) / 1000f
            val pop = 1f + 0.5f * exp(-e * 9f)
            val alpha = if (e < 1.1f) 1f else (1f - (e - 1.1f) * 3f).coerceIn(0f, 1f)
            Outlined(fx.label, 30.sp, if (fx.labelHot) MultRed else Gold, Modifier.graphicsLayer { scaleX = pop; scaleY = pop; this.alpha = alpha; rotationZ = -4f })
        }
        val total = fx.totalShown
        if (total == null && fx.mult > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tally(short(fx.chips), ChipBlue)
                Outlined("×", 30.sp, Ink)
                Tally(fx.mult.toString(), MultRed)
            }
        }
        if (total != null) {
            val e = if (fx.slamAt >= 0) (t - fx.slamAt) / 1000f else -1f
            val punch = if (e >= 0) 1f + 0.7f * exp(-e * 7f) * cos(e * 26f).coerceAtLeast(-0.3f) else 1f + 0.05f * sin(t / 40f)
            Outlined("+" + short(total), 52.sp, if (e >= 0) Gold else Ink, Modifier.graphicsLayer { scaleX = punch; scaleY = punch; rotationZ = -3f })
        }
    }
}

@Composable
private fun Tally(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Outline).padding(3.dp).clip(RoundedCornerShape(12.dp)).background(color).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

/** Big game text with an ink outline and a hard drop (demo `gtext`). */
@Composable
private fun Outlined(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        val st = TextStyle(fontSize = size, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(text, style = st.copy(color = Outline), modifier = Modifier.offset(y = 4.dp))
        for ((dx, dy) in listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2, -2 to -2, 2 to 2, -2 to 2, 2 to -2)) {
            Text(text, style = st.copy(color = Outline), modifier = Modifier.offset(dx.dp, dy.dp))
        }
        Text(text, style = st.copy(color = color))
    }
}

@Composable
private fun Hud(vm: GameViewModel, modifier: Modifier) {
    val s = vm.state
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("알림 청소부", color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Lv ${s.level}", color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("배터리 ${s.energy}/${vm.engine.energyCap(s)}", color = Sub, fontSize = 13.sp)
        }
        Text(short(vm.fx.walletShown ?: s.wallet) + " P", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "지갑" })
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(s.gauge / 100f).height(8.dp).background(Color(0xFFFF8A1F)))
        }
        Text("과부하 ${s.gauge}%", color = Sub, fontSize = 11.sp)
    }
}

@Composable
private fun RowCard(row: BoardRow, index: Int, fx: FxDirector, onSwipe: () -> Unit) {
    val dx = remember(row.id) { Animatable(0f) }
    val enter = remember(row.id) { Animatable(0f) }
    LaunchedEffect(row.id) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val limit = with(density) { 110.dp.toPx() }
    val (title, sub, color, letter) = describe(row)
    val t = fx.now()
    // burst: swell + white-hot, then gone; merge: a quick bump
    val b = fx.bursting[row.id]?.let { ((t - it) / 220f).coerceIn(0f, 1f) }
    val bump = fx.bumped[row.id]?.let { ((t - it) / 180f).coerceIn(0f, 1f) }
    val scale = when {
        b != null -> 1f + 0.16f * sin(min(b, 1f) * 3.1416f * 0.5f)
        bump != null -> 1f + 0.07f * (1f - bump)
        else -> 0.92f + 0.08f * enter.value
    }
    val alpha = if (b != null) (1f - (b - 0.35f) / 0.65f).coerceIn(0f, 1f) else enter.value.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(60.dp)
            .onGloballyPositioned { fx.bounds[row.id] = it.boundsInWindow() }
            .offset { IntOffset(dx.value.roundToInt(), ((1f - enter.value) * -24f * density.density).roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .semantics { contentDescription = "알림 줄 ${index + 1}" }
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragEnd = { scope.launch { if (abs(dx.value) > limit) onSwipe() else dx.animateTo(0f, spring()) } },
                    onHorizontalDrag = { _, d -> scope.launch { dx.snapTo(dx.value + d) } },
                )
            }
            .clickable { onSwipe() },
    ) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Card).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                Text(letter, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = Sub, fontSize = 12.sp)
            }
            if (row.merges) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(row.threshold.coerceAtLeast(1)) { k ->
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (k < row.count) color else Color(0x33FFFFFF)))
                }
            }
        }
        if (b != null) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = (1f - b) * 0.9f)))
    }
}

private data class Look(val title: String, val sub: String, val color: Color, val letter: String)

private fun describe(r: BoardRow): Look = when (r.kind) {
    RowKind.App -> styleOf(r.app).let { Look(Catalog.app(r.app!!).name, "새 알림 ${r.count}개", it.color, it.letter) }
    RowKind.Wild -> Look("와일드 알림", "어느 앱과도 합쳐져요 · ${r.count}개", Color(0xFF7A3CFF), "★")
    RowKind.Spam -> Look("스팸 문자", "밀어서 치워요 · 합쳐지지 않아요", Color(0xFFFF3B30), "!")
    RowKind.Call -> Look("스팸 전화", "자동 정리가 멈췄어요 · 밀어서 차단", Color(0xFFFF3B30), "☎")
    RowKind.Event -> Look(r.event?.label ?: "이벤트", "밀어서 받기", Color(0xFFEFA03F), "E")
    RowKind.Alert -> Look(r.alert?.label ?: "알림", "밀어서 처리", Color(0xFF8A8A8A), "!")
}

@Composable
private fun Footer(vm: GameViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Pill("상점 · 강화") { vm.shopOpen = true }
        Text("밀어서 정리", color = Sub, fontSize = 12.sp)
        Pill(if (vm.soundOn) "소리 켬" else "소리 끔") { vm.soundOn = !vm.soundOn }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShopPanel(vm: GameViewModel) {
    val s = vm.state
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { vm.shopOpen = false }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1C1510)).clickable(enabled = false) {}.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("상점", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("지갑 " + short(s.wallet) + " P", color = Gold, fontSize = 14.sp)
            Text("새 앱", color = Sub, fontSize = 12.sp)
            vm.buyableApps().forEach { a ->
                ShopLine("${a.name} · ${a.tier}티어", short(vm.engine.appPrice(s, a.id)) + " P") { vm.buy(a.id) }
            }
            Text("강화 (덱)", color = Sub, fontSize = 12.sp)
            s.active.forEach { id ->
                ShopLine("${Catalog.app(id).name} Lv ${s.appLevels[id] ?: 1}", short(vm.engine.upgradePrice(s, id)) + " P") { vm.upgrade(id) }
            }
            if (s.level >= vm.engine.rules.resetLevel) {
                ShopLine("공장 초기화 → 백업파일 +${vm.engine.backupFilesNow(s)}", "초기화") { vm.factoryReset(); vm.shopOpen = false }
            }
            Text("닫기", color = Sub, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { vm.shopOpen = false }.padding(10.dp))
        }
    }
}

@Composable
private fun ShopLine(title: String, price: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).background(Gold).clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(price, color = Color(0xFF1A0E06), fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

