package online.morda.mordatg.updates

object UpdateCheckPolicy {
    const val INTERVAL_MS = 24L * 60 * 60 * 1_000

    fun isDue(lastAttempt: Long, now: Long): Boolean =
        lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= INTERVAL_MS
}
