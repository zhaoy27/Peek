package io.github.liki4.peek.ftms

import kotlin.math.abs

/**
 * Per-resistance-level linear model `watt(rpm) = α(L)·rpm + β(L)` for the
 * Keep CC_23, calibrated from observed (rpm, watt) tuples.
 *
 * Each level holds 5 running accumulators sufficient for a closed-form
 * least-squares fit (no per-sample storage). Calibrated levels feed
 * directly; uncalibrated levels' coefficients are linearly interpolated
 * between the nearest calibrated neighbours (extrapolating at the L=1 and
 * L=[maxLevel] edges).
 *
 * Used by [ErgController] to translate FTMS target-power requests into a
 * resistance level in 1..[maxLevel] (18 on CC_23, 24 on C3). See
 * `memory/project_keep_power_resistance_re.md` for why this model is necessary — the Keep app itself doesn't have one
 * because its courses pre-bake the resistance integer in cloud JSON.
 *
 * Persistence: [toBlob] / [fromBlob] produce a compact delimited string that
 * survives DataStore round-trips. ~600 bytes max for a fully-populated model.
 */
class PowerModel {

    /** Accumulators per level. Indexed [0..MAX_LEVELS-1] for L in [1..maxLevel]. */
    private data class LevelStats(
        var n: Long = 0,
        var sumRpm: Double = 0.0,
        var sumWatt: Double = 0.0,
        var sumRpmWatt: Double = 0.0,
        var sumRpmSq: Double = 0.0,
    )

    private val stats = Array(MAX_LEVELS) { LevelStats() }

    /**
     * Highest usable resistance level for the connected bike: 18 on CC_23,
     * 24 on C3. Set once the bike model is known ([RideRepository] does this on
     * connect); levels above it are never sampled or commanded.
     */
    @Volatile
    var maxLevel: Int = DEFAULT_LEVELS
        set(value) { field = value.coerceIn(1, MAX_LEVELS) }

    /**
     * Add one sample. [level] must be 1..maxLevel — this is the level the bike was
     * commanded to (and is currently echoing), NOT a target. Caller filters
     * coasting (rpm=0) and paused (watt=0) before calling.
     */
    fun feed(level: Int, rpm: Int, watt: Int) {
        require(level in 1..maxLevel) { "level out of range: $level" }
        val s = stats[level - 1]
        s.n++
        s.sumRpm += rpm
        s.sumWatt += watt
        s.sumRpmWatt += rpm.toDouble() * watt
        s.sumRpmSq += rpm.toDouble() * rpm
    }

    /** True iff at least 2 levels have ≥ MIN_SAMPLES samples each — required for predictions. */
    fun isReady(): Boolean = calibratedLevels().size >= 2

    /**
     * Predicted watts for [level] at [rpm]. Returns `NaN` if fewer than 2
     * levels are calibrated (caller must check). Interpolates for L with
     * `n < MIN_SAMPLES`; extrapolates at the edges.
     */
    fun predict(level: Int, rpm: Int): Float {
        val direct = coefs(level)
        if (direct != null) {
            val (a, b) = direct
            return a * rpm + b
        }
        val calibrated = calibratedLevels()
        if (calibrated.size < 2) return Float.NaN

        val below = calibrated.lastOrNull { it < level }
        val above = calibrated.firstOrNull { it > level }
        val (lo, hi) = when {
            below != null && above != null -> below to above
            below == null -> calibrated[0] to calibrated[1]
            else /* above == null */ -> calibrated[calibrated.size - 2] to calibrated.last()
        }
        val (aLo, bLo) = coefs(lo)!!
        val (aHi, bHi) = coefs(hi)!!
        val t = (level - lo).toFloat() / (hi - lo).toFloat()
        val a = aLo + (aHi - aLo) * t
        val b = bLo + (bHi - bLo) * t
        return a * rpm + b
    }

    /**
     * Resistance level whose predicted watts at [rpm] is closest to [targetWatt].
     * Returns `null` if the model isn't ready ([isReady] == false).
     */
    fun pickLevel(targetWatt: Int, rpm: Int): Int? {
        if (!isReady()) return null
        var bestL = 1
        var bestDiff = Float.MAX_VALUE
        for (L in 1..maxLevel) {
            val p = predict(L, rpm)
            if (p.isNaN()) continue
            val d = abs(p - targetWatt)
            if (d < bestDiff) { bestDiff = d; bestL = L }
        }
        return bestL.coerceIn(1, maxLevel)
    }

    /** Return (α, β) for a level if it has enough samples + rpm variance; else null. */
    private fun coefs(level: Int): Pair<Float, Float>? {
        val s = stats[level - 1]
        if (s.n < MIN_SAMPLES) return null
        val denom = s.n * s.sumRpmSq - s.sumRpm * s.sumRpm
        if (denom < 1e-3) return null   // all samples at (nearly) one rpm — α is undefined
        val alpha = (s.n * s.sumRpmWatt - s.sumRpm * s.sumWatt) / denom
        val beta = (s.sumWatt - alpha * s.sumRpm) / s.n
        return alpha.toFloat() to beta.toFloat()
    }

    private fun calibratedLevels(): List<Int> =
        (1..maxLevel).filter { coefs(it) != null }

    /** Inspect sample count for a level — used by UI to show calibration progress. */
    fun sampleCount(level: Int): Long {
        require(level in 1..maxLevel)
        return stats[level - 1].n
    }

    /**
     * Copy all per-level accumulators from [other] into this model, replacing
     * existing state. Useful at startup when loading a persisted blob into
     * the long-lived model instance held by RideRepository.
     */
    fun replaceWith(other: PowerModel) {
        for (i in 0 until MAX_LEVELS) {
            stats[i] = other.stats[i].copy()
        }
    }

    // ===== Persistence =====
    // Plain delimited text: "v1|level:n,sR,sW,sRW,sR2|level:n,sR,sW,sRW,sR2|..."
    // Only levels with n>0 are written. Sticks to JVM primitives so unit
    // tests don't need a JSON dep.

    fun toBlob(): String {
        val sb = StringBuilder(VERSION_PREFIX)
        for (L in 1..maxLevel) {
            val s = stats[L - 1]
            if (s.n == 0L) continue
            sb.append('|')
            sb.append(L).append(':')
                .append(s.n).append(',')
                .append(s.sumRpm).append(',')
                .append(s.sumWatt).append(',')
                .append(s.sumRpmWatt).append(',')
                .append(s.sumRpmSq)
        }
        return sb.toString()
    }

    companion object {
        /** Resistance levels on CC_23 — also the fallback when the model is unknown. */
        const val DEFAULT_LEVELS = 18

        /** Highest resistance level of any supported bike (C3 = 24). */
        const val MAX_LEVELS = 24

        /**
         * Minimum samples per level before we trust its direct regression.
         * Below this, fall back to interpolation from neighbours.
         */
        const val MIN_SAMPLES = 10L
        private const val VERSION_PREFIX = "v1"

        fun fromBlob(blob: String?): PowerModel {
            val m = PowerModel()
            if (blob.isNullOrBlank()) return m
            val parts = blob.split('|')
            if (parts.isEmpty() || parts[0] != VERSION_PREFIX) return m
            for (i in 1 until parts.size) {
                val seg = parts[i]
                val colon = seg.indexOf(':')
                if (colon <= 0) continue
                val level = seg.substring(0, colon).toIntOrNull() ?: continue
                if (level !in 1..MAX_LEVELS) continue
                val fields = seg.substring(colon + 1).split(',')
                if (fields.size < 5) continue
                m.stats[level - 1] = LevelStats(
                    n = fields[0].toLongOrNull() ?: 0,
                    sumRpm = fields[1].toDoubleOrNull() ?: 0.0,
                    sumWatt = fields[2].toDoubleOrNull() ?: 0.0,
                    sumRpmWatt = fields[3].toDoubleOrNull() ?: 0.0,
                    sumRpmSq = fields[4].toDoubleOrNull() ?: 0.0,
                )
            }
            return m
        }
    }
}
