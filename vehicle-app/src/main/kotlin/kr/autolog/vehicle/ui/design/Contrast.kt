package kr.autolog.vehicle.ui.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x 상대 휘도. 디자인 언어 5.6 검증용. */
private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val (hi, lo) = if (la >= lb) la to lb else lb to la
    return (hi + 0.05) / (lo + 0.05)
}
