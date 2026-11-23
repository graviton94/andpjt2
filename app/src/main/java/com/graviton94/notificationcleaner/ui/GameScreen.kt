package com.graviton94.notificationcleaner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.graviton94.notificationcleaner.GameViewModel
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.FxPlan
import com.graviton94.notificationcleaner.core.Row as BoardRow
import com.graviton94.notificationcleaner.core.RowKind
import com.graviton94.notificationcleaner.core.short
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Ink = Color(0xFFF6F1EA)
private val Sub = Color(0xFFB3A89B)
private val Gold = Color(0xFFFFD66B)
private val Card = Color(0x1AFFFFFF)

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.state
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    var popText by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    // One celebration per swipe: shake by the ladder, the payout pops and fades.
    LaunchedEffect(vm.fxSeq) {
        val o = vm.lastOutcome ?: return@LaunchedEffect
        if (o.chain <= 0) return@LaunchedEffect
        val step = FxPlan.step(o.chain)
        popText = "+" + short(o.earned) + " P"
        label = if (o.chain == 1) "팡!" else "${o.chain}연쇄 ${step.name}!"
        launch {
            repeat(6) { i -> shake.animateTo(((if (i % 2 == 0) 1 else -1) * step.shakeDp * 2 * (1 - i / 6f)).toFloat(), tween(28)) }
            shake.animateTo(0f, tween(40))
        }
        pop.snapTo(0f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        kotlinx.coroutines.delay(900)
        pop.animateTo(0f, tween(250))
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1A10), Color(0xFF0C0907))))
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Hud(vm)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).offset { IntOffset(shake.value.dp.roundToPx(), 0) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.rows.forEachIndexed { i, row -> key(row.id) { RowCard(row, i) { vm.swipe(row.id) } } }
            }
            Footer(vm)
        }
        if (pop.value > 0.01f) {
            Column(Modifier.align(Alignment.Center).scale(0.6f + 0.4f * pop.value).graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(popText, color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
            }
        }
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

@Composable
private fun key(id: Long, content: @Composable () -> Unit) { androidx.compose.runtime.key(id) { content() } }

@Composable
private fun Hud(vm: GameViewModel) {
    val s = vm.state
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("알림 청소부", color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Lv ${s.level}", color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("배터리 ${s.energy}/${vm.engine.energyCap(s)}", color = Sub, fontSize = 13.sp)
        }
        Text(short(s.wallet) + " P", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "지갑" })
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(s.gauge / 100f).height(8.dp).background(Color(0xFFFF8A1F)))
        }
        Text("과부하 ${s.gauge}%", color = Sub, fontSize = 11.sp)
    }
}

@Composable
private fun RowCard(row: BoardRow, index: Int, onSwipe: () -> Unit) {
    val dx = remember(row.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val limit = with(density) { 110.dp.toPx() }
    val (title, sub, color, letter) = describe(row)
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .offset { IntOffset(dx.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(18.dp)).background(Card).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp))
            .semantics { contentDescription = "알림 줄 ${index + 1}" }
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(dx.value) > limit) { onSwipe() } else dx.animateTo(0f, spring())
                        }
                    },
                    onHorizontalDrag = { _, d -> scope.launch { dx.snapTo(dx.value + d) } },
                )
            }
            .clickable { onSwipe() }
            .padding(horizontal = 12.dp),
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
private fun Footer(vm: GameViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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

