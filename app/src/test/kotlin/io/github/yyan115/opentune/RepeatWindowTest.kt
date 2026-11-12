package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.RepeatWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatWindowTest {
    @Test fun aWindowRunsFromOneHourToThreeHundredSixtyFiveDays() {
        assertEquals(1, RepeatWindow.hours(1, days = false))
        assertEquals(8760, RepeatWindow.hours(8760, days = false))
        assertEquals(24, RepeatWindow.hours(1, days = true))
        assertEquals(8760, RepeatWindow.hours(365, days = true))
    }

    @Test fun aNumberOutsideTheRangeIsNotAWindow() {
        assertNull(RepeatWindow.hours(null, days = false))
        assertNull(RepeatWindow.hours(0, days = false))
        assertNull(RepeatWindow.hours(8761, days = false))
        assertNull(RepeatWindow.hours(366, days = true))
        assertNull(RepeatWindow.hours(Int.MAX_VALUE, days = true))
    }

    @Test fun onlyAWholeNumberOfDaysIsShownInDays() {
        assertTrue(RepeatWindow.inDays(48))
        assertFalse(RepeatWindow.inDays(36))
        assertFalse(RepeatWindow.inDays(0))
        assertEquals(3, RepeatWindow.number(72, days = true))
        assertEquals(72, RepeatWindow.number(72, days = false))
    }

    @Test fun changingTheUnitKeepsTheSpanAndNeverShortensIt() {
        assertEquals(72, RepeatWindow.convert(3, toDays = false))
        assertEquals(3, RepeatWindow.convert(72, toDays = true))
        assertEquals(2, RepeatWindow.convert(25, toDays = true))
        assertEquals(1, RepeatWindow.convert(1, toDays = true))
        assertEquals(8760, RepeatWindow.convert(365, toDays = false))
    }

    @Test fun thePresetsIncludeOffAndEndAtAWeek() {
        assertEquals(listOf(0, 1, 6, 24, 168), RepeatWindow.presets)
    }
}
