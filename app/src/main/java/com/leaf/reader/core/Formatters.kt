package com.leaf.reader.core

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "${value.toInt()} ${units[index]}" else String.format(Locale.US, "%.1f %s", value, units[index])
    }

    /** "just now", "12m ago", "3h ago", "2d ago", then a date. */
    fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "never"
        val diff = now - timestamp
        if (diff < 0) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> date(timestamp)
        }
    }

    fun date(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%02d/%02d/%d", day, month, year)
    }

    fun isToday(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = timestamp }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun startOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun startOfDay(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun percentLabel(percent: Int): String = "$percent%"

    fun durationLabel(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        return when {
            hours >= 1 -> "${hours}h ${minutes % 60}m"
            minutes >= 1 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
