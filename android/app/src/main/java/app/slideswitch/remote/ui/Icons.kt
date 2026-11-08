package app.slideswitch.remote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small SF-Symbols-like glyph set (24×24, white; tinted at draw time). */
object Glyphs {
    private fun stroke(name: String, width: Float = 2.4f, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    private fun fill(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White), pathBuilder = block)
        }.build()

    val ChevronRight = stroke("chevron.right", 3f) { moveTo(9f, 4.5f); lineTo(16.5f, 12f); lineTo(9f, 19.5f) }
    val ChevronLeft = stroke("chevron.left", 3f) { moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f) }
    val SmallChevron = stroke("chevron.forward", 2f) { moveTo(10f, 6f); lineTo(16f, 12f); lineTo(10f, 18f) }

    val Play = fill("play.fill") {
        moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
    }
    val Stop = fill("stop.fill") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close()
    }
    val Display = stroke("display", 2f) {
        moveTo(3.5f, 5f); lineTo(20.5f, 5f); lineTo(20.5f, 16f); lineTo(3.5f, 16f); close()
        moveTo(9f, 19.5f); lineTo(15f, 19.5f)
        moveTo(12f, 16f); lineTo(12f, 19.5f)
    }
    val Laptop = stroke("laptopcomputer", 2f) {
        moveTo(5f, 6f); lineTo(19f, 6f); lineTo(19f, 15.5f); lineTo(5f, 15.5f); close()
        moveTo(2.5f, 18.5f); lineTo(21.5f, 18.5f)
    }
    val Keypad = fill("number") {
        for (row in 0..2) for (col in 0..2) {
            val cx = 6f + col * 6f
            val cy = 6f + row * 6f
            moveTo(cx - 1.8f, cy); arcTo(1.8f, 1.8f, 0f, true, true, cx + 1.8f, cy); arcTo(1.8f, 1.8f, 0f, true, true, cx - 1.8f, cy); close()
        }
    }
    val Clock = stroke("clock", 2f) {
        moveTo(3.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f); arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
    }
    val Refresh = stroke("arrow.clockwise", 2.2f) {
        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.5f, 6.6f)
        moveTo(17f, 3f); lineTo(17f, 7f); lineTo(13f, 7f)
    }
    val Close = stroke("xmark", 2.6f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }

    /** antenna.radiowaves.left.and.right */
    val Radiowaves = stroke("radiowaves", 2f) {
        moveTo(12f, 10.5f); lineTo(12f, 20f)
        moveTo(8.2f, 7.2f); arcTo(5f, 5f, 0f, false, false, 8.2f, 14.8f)
        moveTo(15.8f, 7.2f); arcTo(5f, 5f, 0f, false, true, 15.8f, 14.8f)
        moveTo(5.2f, 4.4f); arcTo(9f, 9f, 0f, false, false, 5.2f, 17.6f)
        moveTo(18.8f, 4.4f); arcTo(9f, 9f, 0f, false, true, 18.8f, 17.6f)
        moveTo(12f, 9.6f); lineTo(12f, 9.7f)
    }

    /** gearshape.fill: 8 teeth, punched centre (even-odd fill). */
    val Gear = ImageVector.Builder("gearshape", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            val teeth = 8
            val outer = 10.5f
            val inner = 8f
            val steps = teeth * 4
            for (i in 0 until steps) {
                val angle = Math.toRadians(i * 360.0 / steps - 90.0 + 360.0 / steps / 2)
                val r = if ((i % 4) < 2) outer else inner
                val x = 12f + (r * kotlin.math.cos(angle)).toFloat()
                val y = 12f + (r * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
            moveTo(15.2f, 12f); arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f); arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f); close()
        }
    }.build()
}
