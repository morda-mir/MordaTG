package online.morda.mordatg.upstream

import kotlin.math.min
import kotlin.random.Random

class ExponentialBackoff(
    private val initialMs: Long = 250,
    private val maximumMs: Long = 4_000,
    private val jitterRatio: Double = 0.25,
    private val random: Random = Random.Default,
) {
    init {
        require(initialMs > 0)
        require(maximumMs >= initialMs)
        require(jitterRatio in 0.0..1.0)
    }

    fun delayMs(attempt: Int): Long {
        require(attempt >= 0)
        var base = initialMs
        repeat(attempt.coerceAtMost(30)) { base = min(maximumMs, base * 2) }
        val spread = (base * jitterRatio).toLong()
        return if (spread == 0L) base else (base + random.nextLong(-spread, spread + 1)).coerceAtLeast(0)
    }
}

