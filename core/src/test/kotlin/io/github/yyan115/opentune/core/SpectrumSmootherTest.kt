package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumSmootherTest {
    private val frame = 1f / 60f
    private fun SpectrumSmoother.run(frames: Int, level: Float?) { val fresh = level?.let { FloatArray(count) { _ -> it } }; repeat(frames) { update(fresh, frame) } }

    @Test fun aBarRisesToANewLevelWithinAFewFrames() {
        val smoother = SpectrumSmoother(4)
        smoother.run(3, 1f)
        assertTrue("After 50 ms the bar is near the top, not yet at it: ${smoother.levels[0]}", smoother.levels[0] in .85f..1f)
    }

    @Test fun aBarSinksSlowlyAfterTheMusicStops() {
        val smoother = SpectrumSmoother(4)
        smoother.run(30, 1f)
        smoother.run(13, null)
        assertEquals("After about 220 ms a bar has lost about two thirds of its height", .37f, smoother.levels[0], .08f)
    }

    @Test fun aMarkerStaysAtThePeakForAMomentAndThenSinks() {
        val smoother = SpectrumSmoother(4)
        smoother.run(30, 1f)
        smoother.run(12, null)
        assertEquals("The marker holds for a quarter of a second", 1f, smoother.peaks[0], 1e-4f)
        smoother.run(60, null)
        assertTrue("A second later it has sunk: ${smoother.peaks[0]}", smoother.peaks[0] < .6f)
    }

    @Test fun aMarkerNeverLiesBelowItsBar() {
        val smoother = SpectrumSmoother(4)
        val random = java.util.Random(3)
        repeat(600) { smoother.update(FloatArray(4) { random.nextFloat() }, frame); for (i in 0 until 4) assertTrue(smoother.peaks[i] >= smoother.levels[i]) }
    }

    @Test fun everythingSinksToTheBottomAndThenTheSmootherIsIdle() {
        val smoother = SpectrumSmoother(4)
        assertTrue("A new smoother has nothing to draw", smoother.idle)
        smoother.run(30, 1f)
        assertFalse(smoother.idle)
        smoother.run(60 * 4, null)
        assertTrue("Four seconds of silence leave nothing to draw", smoother.idle)
    }

    @Test fun aLongPauseBetweenFramesCountsAsOneShortStep() {
        val smoother = SpectrumSmoother(4)
        smoother.update(FloatArray(4) { 1f }, 30f)
        assertTrue(smoother.levels[0] <= 1f && smoother.levels[0] > .9f)
        smoother.update(null, -1f)
        assertTrue("A negative time does not move the bars", smoother.levels[0] > .9f)
    }
}
