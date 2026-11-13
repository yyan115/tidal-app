package io.github.yyan115.opentune.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Turns a short piece of audio into the levels of a few frequency bands, for a visualizer. */
object Spectrum {
    /** The number of samples that one analysis reads. It must be a power of two. At 48 kHz this is 85 ms of audio. */
    const val SIZE = 4096

    /** The level of a band is 0 at this many decibels below a full scale tone, and 1 at [CEILING_DB]. */
    internal const val FLOOR_DB = -70f
    internal const val CEILING_DB = -10f

    /** Music loses about 3 dB per octave toward the treble, so the treble is raised by this much per octave from [TILT_REFERENCE_HZ]. */
    internal const val TILT_DB_PER_OCTAVE = 3f
    internal const val TILT_REFERENCE_HZ = 500f

    internal val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
    private val cosines = FloatArray(SIZE / 2) { cos(2 * PI * it / SIZE).toFloat() }
    private val sines = FloatArray(SIZE / 2) { -sin(2 * PI * it / SIZE).toFloat() }

    /**
     * The level of each of [count] bands between [lowHz] and [highHz], spaced like musical pitch, from 0 for silence to 1 for a loud signal.
     * [samples] holds [SIZE] samples of one channel and [sampleRate] is their rate. This allocates its working space on every call.
     * A screen that calls it many times a second uses a [SpectrumAnalyzer].
     */
    fun bands(samples: FloatArray, sampleRate: Int, count: Int = 32, lowHz: Float = 40f, highHz: Float = 16_000f): FloatArray =
        SpectrumAnalyzer().bands(samples, sampleRate, count, lowHz, highHz)

    internal fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until SIZE) {
            var bit = SIZE shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val a = re[i]; re[i] = re[j]; re[j] = a; val b = im[i]; im[i] = im[j]; im[j] = b }
        }
        var length = 2
        while (length <= SIZE) {
            val step = SIZE / length
            for (start in 0 until SIZE step length) {
                for (k in 0 until length / 2) {
                    val c = cosines[k * step]; val s = sines[k * step]
                    val a = start + k; val b = a + length / 2
                    val tr = re[b] * c - im[b] * s; val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
            }
            length = length shl 1
        }
    }
}

/**
 * The analysis of [Spectrum] with working space that it keeps between calls, so that a screen can run it at every frame without making garbage.
 * A band takes the mean power of all the frequencies in it. A band that is narrower than the spacing of the analysis, which holds for the lowest bands,
 * reads the power between its two nearest frequencies, so that neighbouring bass bands show different levels.
 * One instance serves one thread.
 */
class SpectrumAnalyzer {
    private val re = FloatArray(Spectrum.SIZE)
    private val im = FloatArray(Spectrum.SIZE)
    private val power = FloatArray(Spectrum.SIZE / 2 + 1)

    /** Writes the levels into [into] and returns it. */
    fun bands(samples: FloatArray, sampleRate: Int, count: Int = 32, lowHz: Float = 40f, highHz: Float = 16_000f, into: FloatArray = FloatArray(count)): FloatArray {
        require(samples.size >= Spectrum.SIZE) { "At least ${Spectrum.SIZE} samples are needed" }
        require(into.size >= count) { "The result needs room for $count bands" }
        for (i in 0 until Spectrum.SIZE) { re[i] = samples[i] * Spectrum.window[i]; im[i] = 0f }
        Spectrum.fft(re, im)
        // A full scale sine gives an amplitude of SIZE / 4 with this window, and that is the power of 1.
        val reference = (Spectrum.SIZE / 4f) * (Spectrum.SIZE / 4f)
        for (bin in power.indices) power[bin] = (re[bin] * re[bin] + im[bin] * im[bin]) / reference
        val last = Spectrum.SIZE / 2 - 1f
        val binWidth = sampleRate.toFloat() / Spectrum.SIZE
        val top = minOf(highHz, sampleRate / 2f)
        val ratio = top / lowHz
        for (band in 0 until count) {
            val from = lowHz * ratio.pow(band.toFloat() / count)
            val to = lowHz * ratio.pow((band + 1f) / count)
            val firstBin = from / binWidth
            val lastBin = to / binWidth
            val points = maxOf(3, ceil(lastBin - firstBin).toInt() * 2 + 1)
            var sum = 0f
            for (point in 0 until points) {
                val position = (firstBin + (lastBin - firstBin) * point / (points - 1)).coerceIn(1f, last)
                val index = position.toInt()
                val fraction = position - index
                sum += power[index] * (1f - fraction) + power[index + 1] * fraction
            }
            val db = 10f * log10((sum / points).coerceAtLeast(1e-12f)) + Spectrum.TILT_DB_PER_OCTAVE * log2(sqrt(from * to) / Spectrum.TILT_REFERENCE_HZ)
            into[band] = ((db - Spectrum.FLOOR_DB) / (Spectrum.CEILING_DB - Spectrum.FLOOR_DB)).coerceIn(0f, 1f)
        }
        return into
    }
}

/**
 * The bars of a visualizer and the markers on them. A bar rises at once and sinks slowly.
 * A marker stays at the highest level of its bar for a moment, then sinks, and never lies below the bar.
 */
class SpectrumSmoother(val count: Int = 32) {
    /** The height of each bar, from 0 to 1. */
    val levels = FloatArray(count)
    /** The height of the marker of each bar, from 0 to 1. */
    val peaks = FloatArray(count)
    private val hold = FloatArray(count)

    /** Whether every bar and marker has sunk to the bottom, so that nothing needs to be drawn. */
    val idle: Boolean get() = levels.all { it < IDLE_BELOW } && peaks.all { it < IDLE_BELOW }

    /** Moves the bars toward [fresh], or toward silence when it is null, over [seconds]. */
    fun update(fresh: FloatArray?, seconds: Float) {
        val step = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        val rise = 1f - exp(-step / RISE_SECONDS)
        val fall = exp(-step / FALL_SECONDS)
        for (i in 0 until count) {
            val target = fresh?.get(i) ?: 0f
            val level = levels[i]
            levels[i] = if (target > level) level + (target - level) * rise else level * fall + target * (1f - fall)
            if (levels[i] >= peaks[i]) {
                peaks[i] = levels[i]; hold[i] = HOLD_SECONDS
            } else {
                hold[i] -= step
                if (hold[i] <= 0f) peaks[i] = maxOf(levels[i], peaks[i] - PEAK_FALL_PER_SECOND * step)
            }
        }
    }

    private companion object {
        const val RISE_SECONDS = 0.020f
        const val FALL_SECONDS = 0.220f
        const val HOLD_SECONDS = 0.25f
        const val PEAK_FALL_PER_SECOND = 0.55f
        const val MAX_STEP_SECONDS = 0.1f
        const val IDLE_BELOW = 0.003f
    }
}
