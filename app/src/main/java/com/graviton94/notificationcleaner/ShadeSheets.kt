package com.graviton94.notificationcleaner

import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ShadeSheet(
    sheet: PanelSheet, session: GameSession, close: () -> Unit,
    navigate: (PanelSheet) -> Unit, colors: ShadeColors,
) {
    val title = when (sheet) {
        PanelSheet.Settings -> "빠른 설정"
        PanelSheet.Networks -> "네트워크"
        PanelSheet.Devices -> "연결된 기기"
        PanelSheet.Notifications -> "알림 관리"
        PanelSheet.Store -> "앱 상점"
        PanelSheet.Records -> "정리 기록"
        PanelSheet.Alert -> "알림 센터 정보"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.label)
            IconButton(onClick = close) { Icon(Icons.Default.Close, "닫기", tint = colors.label) }
        }
        when (sheet) {
            PanelSheet.Settings -> SettingsContent(session, navigate, colors)
            PanelSheet.Networks -> ConnectionContent(session, true, colors)
            PanelSheet.Devices -> ConnectionContent(session, false, colors)
            PanelSheet.Notifications -> NotificationSettings(session, colors)
            PanelSheet.Store -> StoreContent(session, colors)
            PanelSheet.Records -> RecordContent(session.state, colors)
            PanelSheet.Alert -> {
                Text("알림 청소부", color = colors.accent, fontWeight = FontWeight.Bold)
                Text("알림을 가로로 밀어 같은 앱을 연결하세요. 빠른 설정과 연결 목록은 이 게임 화면에서 체험할 수 있어요.",
                    Modifier.padding(top = 8.dp), color = colors.sub)
            }
        }
        Spacer(Modifier.height(23.dp))
    }
}

@Composable
private fun SettingsContent(session: GameSession, navigate: (PanelSheet) -> Unit, colors: ShadeColors) {
    val panel = session.panel
    LazyColumn(Modifier.heightIn(max = 565.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        item { Text("게임 내 알림 센터", color = colors.accent, fontSize = 12.sp) }
        item {
            SettingEntry("네트워크", if (panel.wifi) panel.network else "Wi-Fi 꺼짐", colors) { navigate(PanelSheet.Networks) }
        }
        item {
            SettingEntry("연결된 기기", if (panel.bluetooth) panel.device else "블루투스 꺼짐", colors) { navigate(PanelSheet.Devices) }
        }
        item { SettingEntry("알림 관리", "미리보기, 소리와 진동", colors) { navigate(PanelSheet.Notifications) } }
        item {
            ToggleEntry("시스템 밝기 따라가기", "끄면 화면의 슬라이더로 직접 조절", panel.automaticBrightness, colors) {
                session.updatePanel(panel.copy(automaticBrightness = it))
            }
        }
        item { ToggleEntry("어두운 화면", "알림창 색상", panel.dark, colors) { session.updatePanel(panel.copy(dark = it)) } }
        item { ToggleEntry("알림 소리", "연쇄 효과음", panel.sound, colors) { session.updatePanel(panel.copy(sound = it)) } }
        item { ToggleEntry("진동", "연쇄 햅틱", panel.haptics, colors) { session.updatePanel(panel.copy(haptics = it)) } }
        item { ToggleEntry("움직임 줄이기", "화면 흔들림, 섬광과 파티클 끄기", panel.reduceMotion, colors) { session.updatePanel(panel.copy(reduceMotion = it)) } }
        item { SettingEntry("알림 센터 정보", "게임용 빠른 설정과 조작 방법", colors) { navigate(PanelSheet.Alert) } }
    }
}

@Composable
private fun NotificationSettings(session: GameSession, colors: ShadeColors) {
    val panel = session.panel
    Text("게임 알림의 표시 방식을 설정해요.", color = colors.sub, fontSize = 12.sp)
    ToggleEntry("내용 미리보기", "알림창에 가상 앱에서 보낸 내용을 표시", panel.previews, colors) {
        session.updatePanel(panel.copy(previews = it))
    }
    ToggleEntry("알림 소리", "연쇄 효과음", panel.sound, colors) { session.updatePanel(panel.copy(sound = it)) }
    ToggleEntry("진동", "연쇄 햅틱", panel.haptics, colors) { session.updatePanel(panel.copy(haptics = it)) }
}

@Composable
private fun ConnectionContent(session: GameSession, wifi: Boolean, colors: ShadeColors) {
    val panel = session.panel
    val enabled = if (wifi) panel.wifi else panel.bluetooth
    val selected = if (wifi) panel.network else panel.device
    val names = if (wifi) listOf("Cleanroom Wi-Fi", "도서관 공용망", "게스트 네트워크") else {
        listOf("청소부 이어버드", "책상 스피커", "휴대용 키보드")
    }
    val icon = if (wifi) Icons.Default.Wifi else Icons.Default.Bluetooth
    ToggleEntry(if (wifi) "Wi-Fi" else "블루투스", "게임 화면의 연결 표시", enabled, colors) {
        session.updatePanel(if (wifi) panel.copy(wifi = it) else panel.copy(bluetooth = it))
    }
    if (enabled) {
        Text(if (wifi) "사용 가능한 네트워크" else "사용 가능한 기기", Modifier.padding(top = 6.dp),
            color = colors.accent, fontSize = 12.sp)
        names.forEach { name ->
            ConnectionRow(name, name == selected, icon, colors) {
                session.updatePanel(if (wifi) panel.copy(network = name) else panel.copy(device = name))
            }
        }
    }
}

@Composable
private fun ConnectionRow(name: String, checked: Boolean, icon: ImageVector, colors: ShadeColors, select: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = select).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.accent)
        Text(name, Modifier.weight(1f), color = colors.label, fontSize = 14.sp)
        if (checked) Icon(Icons.Default.Check, "연결됨", Modifier.size(18.dp), tint = colors.accent)
    }
}

@Composable
private fun SettingEntry(label: String, detail: String, colors: ShadeColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.sub, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.sub)
    }
}

@Composable
private fun ToggleEntry(label: String, detail: String, enabled: Boolean, colors: ShadeColors, update: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.sub, fontSize = 11.sp)
        }
        Switch(checked = enabled, onCheckedChange = update)
    }
}

@Composable
private fun StoreContent(session: GameSession, colors: ShadeColors) {
    val state = session.state
    val apps = NoticeApp.entries.filter { !it.isEvent }.sortedBy { it in state.owned }
    Text("보유 ${state.owned.size}/${apps.size} · ${pretty(state.points)} P", color = colors.accent, fontWeight = FontWeight.Bold)
    Text("구매한 앱은 새 알림으로 들어와요.", color = colors.sub, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.heightIn(max = 472.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { AccountStore(session, colors) }
        items(apps, key = { it.name }) { app ->
            val owned = app in state.owned
            val upgrade = if (owned) GameEngine.upgradePrice(state, app) else null
            val meetsLevel = state.level >= app.requiredLevel
            Surface(color = colors.alt, shape = RoundedCornerShape(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(app)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${app.category?.label} · ${app.region?.label}", color = colors.sub, fontSize = 11.sp)
                        if (owned) Text("Lv.${state.appLevels.getOrDefault(app, 1)} · ${percent(GameEngine.appPercent(state, app))}" +
                            " → ${percent(GameEngine.appPercent(state.copy(appLevels = state.appLevels + (app to (state.appLevels.getOrDefault(app, 1) + 1))), app))}",
                            color = colors.accent, fontSize = 11.sp)
                    }
                    when {
                        !owned && app.price > 0 && meetsLevel -> FilledTonalButton(
                            onClick = { session.buy(app) }, enabled = !session.busy && state.points >= valueOf(app.price.toLong()),
                        ) { Text("${pretty(app.price.toLong())} P 구매", fontSize = 11.sp) }
                        !owned -> Text("레벨 ${app.requiredLevel}", color = colors.sub, fontSize = 11.sp)
                        upgrade != null -> FilledTonalButton(
                            onClick = { session.upgrade(app) }, enabled = !session.busy && state.points >= upgrade,
                        ) { Text("${pretty(upgrade)} P 강화", fontSize = 11.sp) }
                        else -> Text("강화 완료", color = colors.accent, fontSize = 11.sp)
                    }
                }
            }
            if (owned) {
                Row {
                    if (app in state.account.masteries) TextButton(onClick = { session.equip(app, false) }) { Text("마스터리 장착") }
                    else {
                        if (state.account.trials > 0) TextButton(onClick = { session.equip(app, true) }) { Text("3회 체험") }
                        if (BuildConfig.DEBUG) TextButton(onClick = { session.offer(Product.MASTERY, app) }) { Text("마스터리 2,900원") }
                        if (BuildConfig.DEBUG && !state.account.starter) TextButton(onClick = { session.offer(Product.STARTER, app) }) { Text("스타터 3,900원") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStore(session: GameSession, colors: ShadeColors) {
    val state = session.state
    val a = state.account
    Text("출석 ${a.attendance}/20 · 환생 토큰 ${a.tokens} · 유료재화 ${a.credits}", color = colors.label)
    Text("증폭기 ${a.boosts} · 마스터리 체험권 ${a.trials} · 남은 사용 ${a.trialUses}", color = colors.sub)
    Text("패스 보상 ${a.passClaims}/3 · 과부하 ${a.overloads}회", color = colors.sub)
    if (session.now < a.passUntil) {
        Text("패스 만료 · " + java.time.Instant.ofEpochMilli(a.passUntil)
            .atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(), color = colors.sub)
        Row {
            TextButton(onClick = { session.preset(true) }) { Text("장비 조합 저장") }
            TextButton(onClick = { session.preset(false) }) { Text("장비 조합 불러오기") }
        }
    }
    Row {
        TextButton(onClick = session::arm, enabled = a.boosts > 0) { Text(if (a.armed) "증폭기 해제" else "증폭기 준비") }
        TextButton(onClick = session::dress, enabled = a.decor > 0) { Text(if (a.equipped) "꾸미기 해제" else "꾸미기 장착") }
    }
    Row {
        TextButton(onClick = { session.redeem(false) }, enabled = a.credits >= 100) { Text("증폭기 · 100") }
        TextButton(onClick = { session.redeem(true) }, enabled = a.credits >= 200) { Text("체험권 · 200") }
    }
    if (a.trials > 0 && a.masteries.isNotEmpty()) TextButton(onClick = session::exchange) { Text("체험권을 증폭기 2개로 교환") }
    if (BuildConfig.DEBUG) {
        for (product in listOf(Product.SMALL, Product.LARGE, Product.THEME, Product.PASS)) {
            TextButton(onClick = { session.offer(product) }) { Text("${product.label} · ${product.won}원 테스트") }
        }
        TextButton(onClick = session::restore) { Text("테스트 거래 복원") }
    }
    Text("야간 관제실 최고 ${pretty(a.challengeBest)}점 · 미리보기 ${a.previews}회", color = colors.sub)
    if (a.challengeOpen) {
        Text("고정 보드에서 알림 한 줄을 골라 연쇄를 만드세요.", color = colors.label)
        EconomyRules.challengeBoard(session.now).rows.forEach { row ->
            TextButton(onClick = { session.challenge(row.id) }) {
                Text("${row.id}. ${row.app.label} ×${row.count}" + if (row.id == 5L) " · 중계 알림" else "")
            }
        }
    } else if (session.now < a.passUntil || a.previews > 0) {
        TextButton(onClick = session::enterChallenge) { Text("야간 관제실 입장") }
    }
    Text("환생 레벨 70 · 레벨 90에서 토큰 3개 · 복귀 레벨 45", color = colors.sub)
    if (a.recovery > ZERO) Text("복구 목표 ${pretty(a.recovery)}점", color = colors.sub)
    if (EconomyRules.canRebirth(state)) {
        var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        TextButton(onClick = { confirm = true }) { Text("환생") }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("레벨 45로 환생") },
            text = { Text("이번 회차의 P와 일반 강화를 정산하고 토큰 ${1 + (state.level - 70) / 10}개를 받습니다.") },
            confirmButton = { TextButton(onClick = { confirm = false; session.rebirth() }) { Text("환생 확정") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
    }
    session.pendingProduct?.let { (product, app) ->
        AlertDialog(onDismissRequest = { session.confirmPurchase(false) }, title = { Text(product.label) },
            text = { Text("${app?.label.orEmpty()} ${product.won}원 · 검증용 거래") },
            confirmButton = { TextButton(onClick = { session.confirmPurchase(true) }) { Text("테스트 승인") } },
            dismissButton = { TextButton(onClick = { session.confirmPurchase(false) }) { Text("취소") } })
    }
}

@Composable
private fun RecordContent(state: GameState, colors: ShadeColors) {
    LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("기본 최고점 ${pretty(state.bestScore)} · 이전 기록 ${pretty(state.account.legacyBest)}", fontSize = 12.sp, color = colors.sub)
            Text("${pretty(state.account.personalBest)}점", fontSize = 36.sp, fontWeight = FontWeight.Black, color = colors.gold)
            Text("최고 ${state.bestCombo}연쇄 · 최대 ${pretty(state.bestCleared)}개 처리", color = colors.sub, fontSize = 12.sp)
        }
        state.lastScore?.let { score ->
            item {
                Text("최근 정리", color = colors.accent, fontWeight = FontWeight.Bold)
                Text("기본 ${pretty(score.points)} · 개인 ${pretty(score.personal)}점 · ${pretty(score.xp)} XP", color = colors.sub)
                score.bonuses.forEach { (source, amount) -> Text("$source +${pretty(amount)}점", color = colors.accent) }
            }
            items(score.appScores) { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.app.label, color = colors.label)
                    Text("+${pretty(entry.points)}점", color = colors.accent)
                }
            }
        }
        item { Text("사용권역 배율", fontWeight = FontWeight.Bold, color = colors.label) }
        items(Region.entries) { region ->
            val count = state.regions[region] ?: ZERO
            RecordLine(region.label, count, GameEngine.regionPercent(count), colors)
        }
        item { Text("카테고리 배율", fontWeight = FontWeight.Bold, color = colors.label) }
        items(Category.entries) { category ->
            val count = state.categories[category] ?: ZERO
            RecordLine(category.label, count, GameEngine.categoryPercent(count), colors)
        }
    }
}

@Composable
private fun RecordLine(label: String, count: BigInteger, multiplier: Int, colors: ShadeColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label · ${pretty(count)}개", color = colors.sub, fontSize = 13.sp)
        Text(percent(multiplier), color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
