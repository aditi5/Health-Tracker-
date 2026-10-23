package app.luma.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.*
import kotlin.time.Clock

@OptIn(kotlin.time.ExperimentalTime::class)
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun monthOf(date: LocalDate): String = date.toString().take(7)

data class AppState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val profile: Profile? = null,
    val summary: Summary? = null,
    val catalog: Catalog? = null,
    val periods: List<Period> = emptyList(),
    val calendar: CalendarData? = null,
    val selectedDate: LocalDate = today(),
    val month: LocalDate = LocalDate(today().year, today().month, 1),
    val export: String? = null,
)

class Controller(private val api: Api) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()

    private suspend fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.update { it.copy(error = error.message ?: "Something went wrong.", signedIn = api.signedIn) }
        } finally { mutable.update { it.copy(busy = false) } }
    }

    suspend fun authenticate(email: String, password: String, register: Boolean) = action {
        api.authenticate(email, password, register)
        mutable.update { it.copy(signedIn = true) }
        if (register) api.saveProfile(Profile(timezone = TimeZone.currentSystemDefault().id))
        reload()
    }

    private suspend fun reload() {
        val profile = api.profile()
        val summary = api.summary()
        val catalog = api.catalog()
        val periods = api.periods()
        val calendar = api.calendar(monthOf(mutable.value.month))
        mutable.update { it.copy(profile = profile, summary = summary, catalog = catalog, periods = periods, calendar = calendar) }
    }

    suspend fun refresh() = action { reload() }
    suspend fun selectDate(value: LocalDate) = action {
        val month = LocalDate(value.year, value.month, 1)
        val calendar = api.calendar(monthOf(month))
        mutable.update { it.copy(selectedDate = value, month = month, calendar = calendar) }
    }
    suspend fun moveMonth(amount: Int) = action {
        val month = mutable.value.month.plus(DatePeriod(months = amount))
        val calendar = api.calendar(monthOf(month))
        mutable.update { it.copy(month = month, calendar = calendar) }
    }
    suspend fun saveLog(log: DailyLog) = action {
        api.saveLog(log)
        reload()
        mutable.update { it.copy(message = "Entry saved") }
    }
    suspend fun savePeriod(start: String, end: String) = action {
        LocalDate.parse(start)
        if (end.isNotBlank()) LocalDate.parse(end)
        api.savePeriod(PeriodInput(start, end.ifBlank { null }))
        reload()
        mutable.update { it.copy(message = "Period saved") }
    }
    suspend fun deletePeriod(id: Long) = action { api.deletePeriod(id); reload() }
    suspend fun saveProfile(profile: Profile) = action {
        api.saveProfile(profile)
        reload()
        mutable.update { it.copy(message = "Settings saved") }
    }
    suspend fun exportData() = action { val export = api.exportData(); mutable.update { it.copy(export = export) } }
    suspend fun deleteAccount() = action { api.deleteAccount(); mutable.value = AppState(message = "Account deleted") }
    suspend fun logout() = action { try { api.logout() } finally { mutable.value = AppState() } }
}
