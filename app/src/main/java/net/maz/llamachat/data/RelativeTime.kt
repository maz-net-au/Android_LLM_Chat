package net.maz.llamachat.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Compact relative-time labels for the conversation list ("now", "2h", "Yesterday", "Mon", "3d"). */
object RelativeTime {
    private val weekday = SimpleDateFormat("EEE", Locale.getDefault())
    private val date = SimpleDateFormat("d MMM", Locale.getDefault())

    fun format(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestampMillis
        if (diff < TimeUnit.MINUTES.toMillis(1)) return "now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return "${minutes}m"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24 && isSameDay(timestampMillis, now)) return "${hours}h"

        val days = daysBetween(timestampMillis, now)
        return when {
            days <= 0 -> "${hours}h"
            days == 1 -> "Yesterday"
            days < 7 -> weekday.format(Date(timestampMillis))
            days < 365 -> date.format(Date(timestampMillis))
            else -> "${days / 365}y"
        }
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun daysBetween(a: Long, b: Long): Int {
        val ca = Calendar.getInstance().apply { timeInMillis = a; zero() }
        val cb = Calendar.getInstance().apply { timeInMillis = b; zero() }
        val diff = cb.timeInMillis - ca.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }

    private fun Calendar.zero() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
