package com.example.toolcare

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class TorquePoint(
    val setPoint: Double = 0.0,
    val actualValue: Double = 0.0,
    val errorPercent: Double = 0.0
)

data class CalibrationEntry(
    val id: String = "",
    val toolId: String = "",
    val testerId: String = "TQ-001",
    val targetTorque: Double = 12.0,
    val actualTorque: Double = 11.95,
    val errorPercent: Double = 0.42,
    val status: String = "PASS", // "PASS" or "FAIL"
    val calibrationDate: String = "",
    val nextCalibrationDate: String = "",
    val torquePoints: List<TorquePoint> = emptyList(),
    val company: String = "",
    val block: String = ""
)

object CalibrationUtils {

    /**
     * Calculates the absolute percentage error: |actual - target| / target * 100.
     * Returns rounded value to 2 decimal places.
     */
    fun calculateErrorPercent(target: Double, actual: Double): Double {
        if (target <= 0.0) return 0.0
        val diff = abs(actual - target)
        val pct = (diff / target) * 100.0
        return (pct * 100.0).roundToInt() / 100.0
    }

    /**
     * Evaluates whether the calibration passes or fails based on maximum allowed tolerance (default 2.0%).
     */
    fun evaluateCalibrationStatus(
        overallErrorPercent: Double,
        pointErrors: List<Double>,
        maxTolerancePercent: Double = 2.0
    ): String {
        val overallPass = overallErrorPercent <= maxTolerancePercent
        val pointsPass = pointErrors.all { it <= maxTolerancePercent }
        return if (overallPass && pointsPass) "PASS" else "FAIL"
    }

    /**
     * Formats current date as "dd MMM yyyy" (e.g. "25 Sep 2026").
     */
    fun formatCalibrationDate(date: Date = Date()): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }

    /**
     * Calculates next calibration date (default +1 month) as "dd MMM yyyy".
     */
    fun calculateNextCalibrationDate(currentDate: Date = Date(), monthsToAdd: Int = 1): String {
        val cal = Calendar.getInstance().apply {
            time = currentDate
            add(Calendar.MONTH, monthsToAdd)
        }
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
    }

    /**
     * Determines the greeting string based on the hour of the day (0..23).
     * 05:00 AM to 11:59 AM (hours 5..11) -> "Good Morning,"
     * 12:00 PM to 04:59 PM (hours 12..16) -> "Good Afternoon,"
     * 05:00 PM to 04:59 AM (hours 17..23, 0..4) -> "Good Evening,"
     */
    fun getTimeBasedGreeting(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        return when (hourOfDay) {
            in 5..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }
}
