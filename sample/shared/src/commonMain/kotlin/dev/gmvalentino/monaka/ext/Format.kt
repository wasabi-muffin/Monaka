package dev.gmvalentino.monaka.ext

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall-clock milliseconds since the Unix epoch. */
@OptIn(ExperimentalTime::class)
internal fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Format a delta between [ms] and [now] as a short human-friendly relative string. */
internal fun formatRelativeTime(ms: Long, now: Long = nowMs()): String {
    val deltaSec = ((now - ms) / 1000).coerceAtLeast(0)
    return when {
        deltaSec < 60 -> "${deltaSec}s ago"
        deltaSec < 3_600 -> "${deltaSec / 60}m ago"
        deltaSec < 86_400 -> "${deltaSec / 3_600}h ago"
        else -> "${deltaSec / 86_400}d ago"
    }
}

/** Pad an [Int] to at least [width] characters with leading zeroes. */
internal fun Int.padDigits(width: Int = 2): String = toString().padStart(width, '0')

/** Format a [Double] as `whole.dd` with [decimals] digits. Multiplatform replacement for `"%.2f".format(d)`. */
internal fun Double.format(decimals: Int = 2): String {
    require(decimals >= 0) { "decimals must be non-negative, got $decimals" }
    val factor = 10.0.pow(decimals).toLong()
    val rounded = round(abs(this) * factor).toLong()
    val sign = if (this < 0) "-" else ""
    val whole = rounded / factor
    if (decimals == 0) return "$sign$whole"
    val frac = (rounded % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}
