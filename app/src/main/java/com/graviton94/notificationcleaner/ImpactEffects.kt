package com.graviton94.notificationcleaner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun impactClock(strike: Strike?, reduced: Boolean): State<Float> {
    val time = remember(strike) { mutableFloatStateOf(0f) }
    LaunchedEffect(strike, reduced) {
        if (strike == null || reduced) return@LaunchedEffect
        val start = withFrameNanos { it }
        do {
            val now = withFrameNanos { it }
            time.floatValue = (now - start) / 1_000_000f
        } while (time.floatValue < strike.durationMs)
    }
    return time
}

internal fun Strike.shake(elapsed: Float): Offset {
    val t = (elapsed / durationMs).coerceIn(0f, 1f)
    val decay = (1f - t) * (1f - t)
    val force = when (kind) {
        ImpactKind.Swipe -> 2f
        ImpactKind.Merge -> 3f
        ImpactKind.Burst -> 8f
        ImpactKind.Overflow -> if (tier == 4) 13f else 9f
        ImpactKind.Finish -> 17f
    }
    return Offset(
        direction * cos(t * 22f) * force * decay,
        sin(t * 28f) * force * .42f * decay,
    )
}

internal fun Strike.tint(): Color = when (tier) {
    0 -> Color(0xFFB3FFE2)
    1 -> Color(0xFF76F4CE)
    2 -> Color(0xFFFFD783)
    3 -> Color(0xFFFF966E)
    else -> Color(0xFFFF66B6)
}

internal fun Strike.hitsRow(id: Long): Boolean =
    kind != ImpactKind.Overflow && kind != ImpactKind.Finish && (id == frame.target.id || id == frame.partnerId)

private data class Chip(val vector: Offset, val speed: Float, val size: Float, val life: Float)

private data class Sprite(val image: ImageBitmap, val cell: IntSize, val pivot: Offset, val frames: Int, val columns: Int)

@Composable
internal fun ImpactEffects(strike: Strike?, anchor: Offset?, elapsed: State<Float>, reduced: Boolean, decor: Int) {
    val spark = ImageBitmap.imageResource(R.drawable.fx_hit_spark)
    val critical = ImageBitmap.imageResource(R.drawable.fx_crit_hit)
    if (strike == null) return
    val tint = when {
        decor >= 80 -> Color(0xFFF9B0FF)
        decor >= 40 -> Color(0xFF8FE7FF)
        else -> strike.tint()
    }
    val chips = remember(strike) {
        val random = Random(strike.frame.target.id xor (strike.frame.combo * 51L))
        List(18 + strike.tier * 12) {
            val angle = random.nextDouble() * PI * 2
            Chip(
                Offset(cos(angle).toFloat(), sin(angle).toFloat()),
                55f + random.nextFloat() * (100 + 42 * strike.tier),
                1.5f + random.nextFloat() * (3 + strike.tier),
                .42f + random.nextFloat() * .58f,
            )
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduced) {
            Canvas(Modifier.fillMaxSize()) {
                val t = (elapsed.value / strike.durationMs).coerceIn(0f, 1f)
                val center = if (strike.kind == ImpactKind.Overflow || strike.kind == ImpactKind.Finish) {
                    Offset(size.width * (.50f + if (strike.frame.combo % 2 == 0) .08f else -.08f), size.height * .47f)
                } else anchor ?: Offset(size.width * .5f, size.height * .48f)
                val fade = 1f - t
                val expansion = 1f - fade * fade * fade
                val radius = (12 + strike.tier * 36).dp.toPx() * expansion
                if (strike.tier >= 1) {
                    drawCircle(tint.copy(alpha = fade * .72f), radius, center, style = Stroke((1.5f + fade * 3).dp.toPx()))
                    if (strike.tier >= 3) {
                        drawCircle(tint.copy(alpha = fade * .34f), radius * .69f, center, style = Stroke(2.dp.toPx()))
                    }
                }
                chips.forEachIndexed { i, chip ->
                    val alpha = ((chip.life - t) / chip.life).coerceAtLeast(0f)
                    val travel = expansion * chip.speed.dp.toPx()
                    val push = if (strike.kind == ImpactKind.Swipe) strike.direction * travel * .55f else 0f
                    val x = center.x + chip.vector.x * travel + push
                    val y = center.y + chip.vector.y * travel + 42.dp.toPx() * t * t
                    val point = Offset((x / 3).roundToInt() * 3f, (y / 3).roundToInt() * 3f)
                    val color = if (i % 5 == 0) Color(0xFFEEFDF5) else if (i % 3 == 0) Color(0xFF62DFE7) else tint
                    val extent = chip.size.dp.toPx() * (.35f + .65f * alpha)
                    if (i % 4 == 0) {
                        drawLine(color.copy(alpha = alpha * .78f), point - chip.vector * extent * 4f, point, extent * .42f)
                    }
                    drawRect(color.copy(alpha = alpha), point, Size(extent, if (i % 3 == 0) extent * .48f else extent))
                }
                if (strike.kind == ImpactKind.Finish) {
                    fracture(center, tint, expansion, fade)
                }
                val sprite = if (strike.tier >= 2) Sprite(critical, IntSize(216, 136), Offset(84f, 106f), 28, 5)
                    else Sprite(spark, IntSize(136, 80), Offset(44f, 64f), 16, 4)
                drawSprite(sprite, center, t, strike.tier)
            }
        }
        if (strike.tier >= 2) {
            val finish = strike.kind == ImpactKind.Finish
            val active = strike.kind == ImpactKind.Overflow || finish
            val face = when {
                finish -> "알림창 붕괴"
                active -> "과부하"
                else -> "연쇄 점화"
            }
            val panel = if (active) Color(0xF3161028) else Color(0xEE1A182A)
            val outline = RoundedCornerShape(if (active) 20.dp else 12.dp)
            Column(
                Modifier.graphicsLayer {
                    if (!reduced) {
                        val t = (elapsed.value / strike.durationMs).coerceIn(0f, 1f)
                        scaleX = 1f + (1f - t) * .17f * sin(t * 14f)
                        scaleY = scaleX
                        translationY = (if (active) -42.dp else -61.dp).toPx() - (t * 10).dp.toPx()
                        alpha = if (finish) ((1f - t) * 6f).coerceIn(0f, 1f) else 1f
                    }
                }.widthIn(max = 320.dp)
                    .background(panel, outline).border(1.dp, tint.copy(alpha = .75f), outline)
                    .padding(horizontal = if (active) 21.dp else 16.dp, vertical = 6.dp)
                    .then(if (finish) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(face, color = tint, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${strike.frame.combo}연쇄", color = Color(0xFFFDF6FF), fontSize = if (active) 43.sp else 25.sp,
                    lineHeight = if (active) 48.sp else 29.sp, fontWeight = FontWeight.Black,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${pretty(strike.frame.cleared)}개 처리", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "+${pretty(strike.frame.earned)}점", color = Color(0xFFFDF6FF), fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun DrawScope.fracture(origin: Offset, tint: Color, expansion: Float, fade: Float) {
    val reach = size.maxDimension * .92f * expansion
    for (spoke in 0 until 10) {
        val angle = (spoke + .2) * 2 * PI / 10
        val ray = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val cross = Offset(-ray.y, ray.x)
        val first = origin + ray * (reach * .17f) + cross * (reach * .036f)
        val second = origin + ray * (reach * .38f) - cross * (reach * .026f)
        val tip = origin + ray * reach
        val path = Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(first.x, first.y)
            lineTo(second.x, second.y)
            lineTo(tip.x, tip.y)
        }
        drawPath(path, Color(0xFF150F24).copy(alpha = fade * .7f), style = Stroke(6.dp.toPx()))
        drawPath(path, tint.copy(alpha = fade * .84f), style = Stroke(2.dp.toPx()))
        drawLine(tint.copy(alpha = fade * .48f), second, second + ray * (reach * .3f) + cross * (reach * .15f), 1.dp.toPx())
    }
}

private fun DrawScope.drawSprite(sprite: Sprite, origin: Offset, time: Float, tier: Int) {
    if (time >= 1f) return
    val frame = (time * (sprite.frames - 1)).roundToInt()
    val scale = (if (tier >= 2) 1.35f + tier * .20f else 1.15f).dp.toPx()
    val position = origin - sprite.pivot * scale
    drawImage(
        image = sprite.image,
        srcOffset = IntOffset(frame % sprite.columns * sprite.cell.width, frame / sprite.columns * sprite.cell.height),
        srcSize = sprite.cell,
        dstOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
        dstSize = IntSize((sprite.cell.width * scale).roundToInt(), (sprite.cell.height * scale).roundToInt()),
        filterQuality = FilterQuality.None,
        alpha = .85f,
    )
}
