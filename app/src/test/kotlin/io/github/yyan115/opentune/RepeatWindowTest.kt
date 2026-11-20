package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.RepeatWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatWindowTest {
    @Test fun aWindowRunsFromOneMinuteToThreeHundredSixtyFiveDays() {
        assertEquals(1, RepeatWindow.minutes(0, 0, 1))
        assertEquals(60, RepeatWindow.minutes(0, 1, 0))
        assertEquals(1440, RepeatWindow.minutes(1, 0, 0))
        assertEquals(365 * 24 * 60, RepeatWindow.minutes(365, 0, 0))
        assertEquals(365 * 24 * 60, RepeatWindow.minutes(364, 23, 60))
    }

    @Test fun theThreePartsAddUp() {
        assertEquals(2 * 1440 + 3 * 60 + 4, RepeatWindow.minutes(2, 3, 4))
        assertEquals(90, RepeatWindow.minutes(0, 0, 90))
        assertEquals(1500, RepeatWindow.minutes(0, 25, 0))
    }

    @Test fun aTimeOutsideTheRangeIsNotAWindow() {
        assertNull(RepeatWindow.minutes(0, 0, 0))
        assertNull(RepeatWindow.minutes(365, 0, 1))
        assertNull(RepeatWindow.minutes(366, 0, 0))
        assertNull(RepeatWindow.minutes(0, 8761, 0))
        assertNull(RepeatWindow.minutes(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertNull(RepeatWindow.minutes(-1, 5, 0))
    }

    @Test fun aWindowIsSplitIntoTheLargestUnitsFirst() {
        assertEquals(RepeatWindow.Parts(1, 12, 0), RepeatWindow.parts(36 * 60))
        assertEquals(RepeatWindow.Parts(0, 0, 45), RepeatWindow.parts(45))
        assertEquals(RepeatWindow.Parts(0, 1, 30), RepeatWindow.parts(90))
        assertEquals(RepeatWindow.Parts(365, 0, 0), RepeatWindow.parts(365 * 1440))
        assertEquals(RepeatWindow.Parts(0, 0, 0), RepeatWindow.parts(0))
    }

    @Test fun splittingAndAddingAreInverse() {
        for (total in listOf(1, 59, 60, 61, 1439, 1440, 1441, 4321, 525_600)) {
            val parts = RepeatWindow.parts(total)
            assertEquals(total, RepeatWindow.minutes(parts.days, parts.hours, parts.minutes))
        }
    }

    @Test fun thePresetsIncludeOffAndEndAtAWeek() {
        assertEquals(listOf(0, 60, 360, 1440, 10_080), RepeatWindow.presets)
    }

    @Test fun anOldWindowInHoursBecomesMinutes() {
        assertEquals(36 * 60, RepeatWindow.fromHours(36))
        assertEquals(0, RepeatWindow.fromHours(0))
        assertEquals(365 * 1440, RepeatWindow.fromHours(365 * 24))
        assertEquals(0, RepeatWindow.fromHours(-5))
        assertEquals(365 * 1440, RepeatWindow.fromHours(Int.MAX_VALUE))
    }
}
