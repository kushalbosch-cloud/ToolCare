package com.example.toolcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Date

class CalibrationUtilsTest {

    @Test
    fun calculateErrorPercent_calculatesCorrectAbsolutePercentage() {
        val err1 = CalibrationUtils.calculateErrorPercent(12.0, 11.95)
        assertEquals(0.42, err1, 0.01)

        val err2 = CalibrationUtils.calculateErrorPercent(10.0, 9.80)
        assertEquals(2.0, err2, 0.01)

        val err3 = CalibrationUtils.calculateErrorPercent(10.0, 9.50)
        assertEquals(5.0, err3, 0.01)

        val errZero = CalibrationUtils.calculateErrorPercent(0.0, 5.0)
        assertEquals(0.0, errZero, 0.0)
    }

    @Test
    fun evaluateCalibrationStatus_evaluatesPassAndFailWithinTolerance() {
        // PASS scenario
        val passStatus = CalibrationUtils.evaluateCalibrationStatus(
            overallErrorPercent = 0.42,
            pointErrors = listOf(0.40, 0.42, 0.14),
            maxTolerancePercent = 2.0
        )
        assertEquals("PASS", passStatus)

        // FAIL scenario due to overall error
        val failOverall = CalibrationUtils.evaluateCalibrationStatus(
            overallErrorPercent = 2.50,
            pointErrors = listOf(0.40, 0.42, 0.14),
            maxTolerancePercent = 2.0
        )
        assertEquals("FAIL", failOverall)

        // FAIL scenario due to one point error
        val failPoint = CalibrationUtils.evaluateCalibrationStatus(
            overallErrorPercent = 0.50,
            pointErrors = listOf(0.40, 2.80, 0.14),
            maxTolerancePercent = 2.0
        )
        assertEquals("FAIL", failPoint)
    }

    @Test
    fun dateFormattingAndNextCalibrationDateCalculation() {
        val now = Date()
        val currentFormatted = CalibrationUtils.formatCalibrationDate(now)
        assertNotNull(currentFormatted)

        val nextFormatted = CalibrationUtils.calculateNextCalibrationDate(now, 1)
        assertNotNull(nextFormatted)
    }

    @Test
    fun getTimeBasedGreeting_returnsCorrectGreetingForHours() {
        assertEquals("Good Morning,", CalibrationUtils.getTimeBasedGreeting(5))
        assertEquals("Good Morning,", CalibrationUtils.getTimeBasedGreeting(9))
        assertEquals("Good Morning,", CalibrationUtils.getTimeBasedGreeting(11))

        assertEquals("Good Afternoon,", CalibrationUtils.getTimeBasedGreeting(12))
        assertEquals("Good Afternoon,", CalibrationUtils.getTimeBasedGreeting(14))
        assertEquals("Good Afternoon,", CalibrationUtils.getTimeBasedGreeting(16))

        assertEquals("Good Evening,", CalibrationUtils.getTimeBasedGreeting(17))
        assertEquals("Good Evening,", CalibrationUtils.getTimeBasedGreeting(20))
        assertEquals("Good Evening,", CalibrationUtils.getTimeBasedGreeting(23))
        assertEquals("Good Evening,", CalibrationUtils.getTimeBasedGreeting(0))
        assertEquals("Good Evening,", CalibrationUtils.getTimeBasedGreeting(4))
    }
}
