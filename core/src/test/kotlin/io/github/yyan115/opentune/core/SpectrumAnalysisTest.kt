package io.github.yyan115.opentune.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class SpectrumAnalysisTest {
    private fun tone(hz: Double, rate: Int, amplitude: Double) = FloatArray(Spectrum.SIZE) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    /** Noise whose power falls by 3 dB per octave, which is how music is spread over the pitches. */
    private fun pinkNoise(random: Random, size: Int, rms: Double): FloatArray {
        val feed = doubleArrayOf(0.049922035, -0.095993537, 0.050612699, -0.004408786)
        val back = doubleArrayOf(-2.494956002, 2.017265875, -0.522189400)
        val warmUp = 2_000
        val x = DoubleArray(3); val y = DoubleArray(3)
        val out = DoubleArray(size + warmUp)
        for (n in out.indices) {
            val white = random.nextGaussian()
            val value = feed[0] * white + feed[1] * x[0] + feed[2] * x[1] + feed[3] * x[2] - back[0] * y[0] - back[1] * y[1] - back[2] * y[2]
            x[2] = x[1]; x[1] = x[0]; x[0] = white
            y[2] = y[1]; y[1] = y[0]; y[0] = value
            out[n] = value
        }
        val used = out.copyOfRange(warmUp, out.size)
        val scale = rms / kotlin.math.sqrt(used.sumOf { it * it } / used.size)
        return FloatArray(size) { (used[it] * scale).toFloat() }
    }

    private fun bandCentre(band: Int, rate: Int, count: Int = 32) = 40.0 * (minOf(16_000.0, rate / 2.0) / 40.0).pow((band + .5) / count)

    @Test fun pinkNoiseGivesBarsOfEqualHeightAtEveryPitch() {
        for (rate in listOf(44_100, 48_000)) {
            val random = Random(7)
            val frames = 64
            val mean = FloatArray(32)
            repeat(frames) { Spectrum.bands(pinkNoise(random, Spectrum.SIZE, .1), rate).forEachIndexed { band, level -> mean[band] += level / frames } }
            val spread = mean.max() - mean.min()
            assertTrue("At $rate Hz the bars of pink noise differ by $spread, from ${mean.toList()}", spread < .1f)
            assertTrue("Noise at -20 dB sits in the middle of the range, not at its ends", mean.all { it in .25f..0.75f })
        }
    }

    @Test fun eachOfTheLowestBandsLightsForATonePlayedInIt() {
        for (rate in listOf(44_100, 48_000)) for (band in 0 until 8) {
            val levels = Spectrum.bands(tone(bandCentre(band, rate), rate, .5), rate)
            assertEquals("A tone at ${bandCentre(band, rate)} Hz at $rate Hz lights its own band most", band, levels.indices.maxByOrNull { levels[it] })
        }
    }

    @Test fun twoTonesOneBandApartGiveDifferentBars() {
        val first = Spectrum.bands(tone(bandCentre(1, 48_000), 48_000, .5), 48_000)
        val second = Spectrum.bands(tone(bandCentre(2, 48_000), 48_000, .5), 48_000)
        assertTrue("The bars of two neighbouring bass bands must not be the same", (0 until 8).any { kotlin.math.abs(first[it] - second[it]) > .1f })
    }

    @Test fun theAnalyzerGivesTheSameLevelsAsTheFunctionAndFillsTheArrayItIsGiven() {
        val samples = tone(1_500.0, 44_100, .3)
        val analyzer = SpectrumAnalyzer()
        val into = FloatArray(32)
        val returned = analyzer.bands(samples, 44_100, into = into)
        assertTrue("The result is the array that was given", returned === into)
        assertArrayEquals(Spectrum.bands(samples, 44_100), into, 1e-6f)
        // A second call on the same analyzer does not carry anything over from the first.
        analyzer.bands(tone(90.0, 44_100, 1.0), 44_100, into = into)
        assertArrayEquals(Spectrum.bands(samples, 44_100), analyzer.bands(samples, 44_100, into = into), 1e-6f)
    }

    @Test fun aSignalThatIsTooShortIsRefused() {
        val error = runCatching { Spectrum.bands(FloatArray(Spectrum.SIZE - 1), 44_100) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
