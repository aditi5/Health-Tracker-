package app.luma.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(val access: String, val refresh: String)

@Serializable
data class Credentials(val email: String, val password: String)

@Serializable
data class Profile(
    @SerialName("display_name") val displayName: String = "",
    val timezone: String = "UTC",
    @SerialName("cycle_length") val cycleLength: Int = 28,
    @SerialName("period_length") val periodLength: Int = 5,
)

@Serializable
data class Period(val id: Long = 0, val start: String, val end: String? = null)

@Serializable
data class PeriodInput(val start: String, val end: String? = null)

@Serializable
data class DailyLog(
    val date: String,
    val flow: String = "",
    val symptoms: List<String> = emptyList(),
    val mood: String = "",
    val notes: String = "",
)

@Serializable
data class Summary(
    val today: String,
    @SerialName("cycle_day") val cycleDay: Int? = null,
    @SerialName("estimated_cycle_length") val cycleLength: Int,
    @SerialName("estimated_period_length") val periodLength: Int,
    @SerialName("next_period_start") val nextPeriod: String? = null,
    @SerialName("days_until_period") val daysUntilPeriod: Int? = null,
    @SerialName("completed_intervals") val completedIntervals: Int,
    @SerialName("cycle_variation") val cycleVariation: Int? = null,
    @SerialName("estimate_basis") val estimateBasis: String,
    val notice: String,
)

@Serializable
data class CalendarData(val month: String, val logs: List<DailyLog>, val periods: List<Period>)

@Serializable
data class Catalog(val symptoms: List<String>, val flows: List<String>, val moods: List<String>)
