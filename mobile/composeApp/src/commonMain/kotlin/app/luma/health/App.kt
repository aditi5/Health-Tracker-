package app.luma.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.*

private val Ink = Color(0xFF233F3B)
private val Rose = Color(0xFFC35C70)
private val Paper = Color(0xFFF8F5EF)

@Composable
fun App(baseUrl: String) {
    val controller = remember(baseUrl) { Controller(Api(baseUrl)) }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Today") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Rose, background = Paper, surface = Paper)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeContentPadding()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Luma", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("A little more in tune with you", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.signedIn) TextButton(onClick = { scope.launch { controller.refresh() } }, enabled = !state.busy) { Text("Refresh") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
                state.message?.let { Text(it, color = Ink, modifier = Modifier.padding(horizontal = 20.dp)) }
                if (!state.signedIn) {
                    Login(Modifier.weight(1f), state.busy) { email, password, register ->
                        scope.launch { controller.authenticate(email, password, register) }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            when (tab) {
                                "Today" -> TodayScreen(state, onTrack = { tab = "Track" })
                                "Calendar" -> CalendarScreen(state, !state.busy,
                                    move = { scope.launch { controller.moveMonth(it) } },
                                    select = { date -> scope.launch { controller.selectDate(date); tab = "Track" } })
                                "Track" -> TrackScreen(state, controller)
                                "Insights" -> InsightsScreen(state)
                                "You" -> ProfileScreen(state, controller)
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                    NavigationBar {
                        listOf("Today", "Calendar", "Track", "Insights", "You").forEach { name ->
                            NavigationBarItem(selected = tab == name, onClick = { tab = name },
                                icon = { Text(when (name) { "Today" -> "◉"; "Calendar" -> "▦"; "Track" -> "+"; "Insights" -> "≋"; else -> "○" }) },
                                label = { Text(name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Login(modifier: Modifier, busy: Boolean, submit: (String, String, Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(true) }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Your cycle.\nYour own rhythm.", style = MaterialTheme.typography.displaySmall, color = Ink)
        Text("Keep track of your period, notice patterns, and make room for how you feel.")
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { submit(email.trim(), password, register) }, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (register) "Create account" else "Sign in")
        }
        TextButton(onClick = { register = !register }) { Text(if (register) "Already have an account? Sign in" else "Create a new account") }
        Text("Development build · Your session is kept in memory. Sign in again after restarting.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TodayScreen(state: AppState, onTrack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Hello${state.profile?.displayName?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.headlineMedium)
        val summary = state.summary
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0DEE1)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YOUR CYCLE", style = MaterialTheme.typography.labelLarge)
                Text(summary?.cycleDay?.let { "Day $it" } ?: "Let's begin", style = MaterialTheme.typography.displayMedium)
                Text(summary?.nextPeriod?.let { "Next period estimate · $it" } ?: "Log a period to see your cycle here")
                Button(onClick = onTrack) { Text("Check in today") }
            }
        }
        Text("How are you feeling?", style = MaterialTheme.typography.titleLarge)
        Text("Every day is useful context. Track flow, symptoms, and mood in a quick daily check-in.")
        summary?.let { Text(it.notice, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CalendarScreen(state: AppState, enabled: Boolean, move: (Int) -> Unit, select: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { move(-1) }, enabled = enabled) { Text("‹") }
            Text(monthOf(state.month), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { move(1) }, enabled = enabled) { Text("›") }
        }
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f).padding(8.dp)) } }
        val first = state.month.minus(DatePeriod(days = state.month.dayOfWeek.isoDayNumber - 1))
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val day = first.plus(DatePeriod(days = week * 7 + weekday))
                    val entry = state.calendar?.logs?.find { it.date == day.toString() }
                    val isPeriod = state.calendar?.periods?.any { period ->
                        day >= LocalDate.parse(period.start) && day <= LocalDate.parse(period.end ?: period.start)
                    } == true
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)
                        .background(if (isPeriod) Color(0xFFEEC5CD) else if (entry != null) Color(0xFFD8E6DE) else Color.Transparent, CircleShape)
                        .clickable(enabled = enabled && day.month == state.month.month) { select(day) }, contentAlignment = Alignment.Center) {
                        Text(day.day.toString(), color = if (day.month == state.month.month) Ink else Color.Gray)
                    }
                }
            }
        }
        Text("Rose · logged period     Green · daily entry", style = MaterialTheme.typography.bodySmall)
        Text("Tap a date to add or edit a check-in.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackScreen(state: AppState, controller: Controller) {
    val scope = rememberCoroutineScope()
    var dateText by remember(state.selectedDate) { mutableStateOf(state.selectedDate.toString()) }
    val existing = state.calendar?.logs?.find { it.date == state.selectedDate.toString() }
    var flow by remember(state.selectedDate, existing) { mutableStateOf(existing?.flow ?: "") }
    var mood by remember(state.selectedDate, existing) { mutableStateOf(existing?.mood ?: "") }
    var symptoms by remember(state.selectedDate, existing) { mutableStateOf(existing?.symptoms ?: emptyList()) }
    var notes by remember(state.selectedDate, existing) { mutableStateOf(existing?.notes ?: "") }
    var periodStart by remember { mutableStateOf(state.selectedDate.toString()) }
    var periodEnd by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily check-in", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(dateText, { dateText = it }, label = { Text("Date · YYYY-MM-DD") }, singleLine = true)
        TextButton(onClick = { scope.launch { controller.selectDate(LocalDate.parse(dateText)) } }, enabled = !state.busy && runCatching { LocalDate.parse(dateText) }.isSuccess) { Text("Select date") }
        Text("Flow", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.catalog?.flows?.forEach { value -> FilterChip(selected = flow == value, onClick = { flow = if (flow == value) "" else value }, label = { Text(value) }) }
        }
        Text("Symptoms", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.catalog?.symptoms?.forEach { value -> FilterChip(selected = value in symptoms, onClick = { symptoms = if (value in symptoms) symptoms - value else symptoms + value }, label = { Text(value.replace('_', ' ')) }) }
        }
        Text("Mood", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.catalog?.moods?.forEach { value -> FilterChip(selected = mood == value, onClick = { mood = if (mood == value) "" else value }, label = { Text(value) }) }
        }
        OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Anything else?") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { scope.launch { controller.saveLog(DailyLog(state.selectedDate.toString(), flow, symptoms, mood, notes)) } }, enabled = !state.busy) { Text("Save check-in") }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Log a period", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Start · YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("End · optional YYYY-MM-DD") }, singleLine = true)
        Button(onClick = { scope.launch { controller.savePeriod(periodStart, periodEnd) } }, enabled = !state.busy) { Text("Save period") }
        state.periods.forEach { period ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${period.start} → ${period.end ?: "End not logged"}", Modifier.weight(1f))
                TextButton(onClick = { scope.launch { controller.deletePeriod(period.id) } }, enabled = !state.busy) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun InsightsScreen(state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your patterns", style = MaterialTheme.typography.headlineMedium)
        state.summary?.let { summary ->
            listOf("Estimated cycle" to "${summary.cycleLength} days", "Estimated period" to "${summary.periodLength} days",
                "Cycle variation" to (summary.cycleVariation?.let { "$it days" } ?: "More history needed"),
                "Completed intervals" to summary.completedIntervals.toString()).forEach { (title, value) ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(title); Text(value, style = MaterialTheme.typography.headlineSmall) } }
            }
            Text(if (summary.completedIntervals == 0) "Estimates use your cycle settings until you log more periods." else "Estimates use the median interval between your logged period starts.")
            Text(summary.notice, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileScreen(state: AppState, controller: Controller) {
    val scope = rememberCoroutineScope()
    val profile = state.profile ?: return
    var name by remember(profile) { mutableStateOf(profile.displayName) }
    var timezone by remember(profile) { mutableStateOf(profile.timezone) }
    var cycle by remember(profile) { mutableStateOf(profile.cycleLength.toString()) }
    var period by remember(profile) { mutableStateOf(profile.periodLength.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Made for you", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Your name") })
        OutlinedTextField(timezone, { timezone = it }, label = { Text("Timezone · e.g. Asia/Kolkata") })
        OutlinedTextField(cycle, { cycle = it }, label = { Text("Usual cycle length in days") })
        OutlinedTextField(period, { period = it }, label = { Text("Usual period length in days") })
        Button(onClick = { scope.launch { controller.saveProfile(Profile(name, timezone, cycle.toInt(), period.toInt())) } }, enabled = !state.busy && cycle.toIntOrNull() != null && period.toIntOrNull() != null) { Text("Save settings") }
        OutlinedButton(onClick = { scope.launch { controller.exportData() } }, enabled = !state.busy) { Text("View my data export") }
        state.export?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { scope.launch { controller.logout() } }, enabled = !state.busy) { Text("Sign out") }
        TextButton(onClick = { confirmDelete = true }, enabled = !state.busy) { Text("Delete account and all data", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete your account?") },
        text = { Text("This permanently deletes your profile, periods, and daily entries.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { controller.deleteAccount() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
