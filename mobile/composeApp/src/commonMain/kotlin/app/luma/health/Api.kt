package app.luma.health

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

val appJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
expect fun platformClient(): HttpClient

fun HttpClient.configured() = config {
    install(ContentNegotiation) { json(appJson) }
    expectSuccess = false
}

class ApiFailure(val status: Int, message: String) : Exception(message)

class Api(private val baseUrl: String, private val client: HttpClient = platformClient().configured()) {
    private var tokens: Tokens? = null
    private val refreshMutex = Mutex()
    val signedIn get() = tokens != null

    private suspend fun checked(response: HttpResponse): HttpResponse {
        if (!response.status.isSuccess()) {
            val detail = response.bodyAsText().take(800)
            throw ApiFailure(response.status.value, detail.ifBlank { "Request failed (${response.status.value})" })
        }
        return response
    }

    suspend fun authenticate(email: String, password: String, register: Boolean) {
        val response = client.post("${baseUrl.trimEnd('/')}/auth/${if (register) "register" else "login"}/") {
            contentType(ContentType.Application.Json)
            setBody(Credentials(email, password))
        }
        tokens = checked(response).body()
    }

    private suspend fun request(path: String, method: HttpMethod = HttpMethod.Get, data: JsonElement? = null): HttpResponse {
        val attemptedAccess = tokens?.access ?: throw ApiFailure(401, "Please sign in.")
        suspend fun send(access: String): HttpResponse = client.request("${baseUrl.trimEnd('/')}/$path") {
            this.method = method
            bearerAuth(access)
            if (data != null) { contentType(ContentType.Application.Json); setBody(data) }
        }
        var response = send(attemptedAccess)
        if (response.status == HttpStatusCode.Unauthorized) {
            refreshMutex.withLock {
                if (tokens?.access == attemptedAccess) {
                    val refresh = client.post("${baseUrl.trimEnd('/')}/auth/refresh/") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("refresh" to tokens!!.refresh))
                    }
                    if (!refresh.status.isSuccess()) {
                        tokens = null
                        throw ApiFailure(401, "Your session expired. Please sign in again.")
                    }
                    tokens = refresh.body()
                }
            }
            response = send(tokens?.access ?: throw ApiFailure(401, "Please sign in again."))
        }
        return checked(response)
    }

    suspend fun profile(): Profile = request("profile/").body()
    suspend fun saveProfile(value: Profile): Profile = request("profile/", HttpMethod.Patch, appJson.encodeToJsonElement(value)).body()
    suspend fun summary(): Summary = request("summary/").body()
    suspend fun catalog(): Catalog = request("catalog/").body()
    suspend fun calendar(month: String): CalendarData = request("calendar/?month=${month.encodeURLParameter()}").body()
    suspend fun saveLog(log: DailyLog): DailyLog = request("logs/${log.date}/", HttpMethod.Put, appJson.encodeToJsonElement(log)).body()
    suspend fun periods(): List<Period> = request("periods/").body()
    suspend fun savePeriod(period: PeriodInput) { request("periods/", HttpMethod.Post, appJson.encodeToJsonElement(period)) }
    suspend fun deletePeriod(id: Long) { request("periods/$id/", HttpMethod.Delete) }
    suspend fun exportData(): String = request("account/").bodyAsText()
    suspend fun deleteAccount() { request("account/", HttpMethod.Delete); tokens = null }
    suspend fun logout() {
        try { request("auth/logout/", HttpMethod.Post, appJson.encodeToJsonElement(mapOf("refresh" to tokens?.refresh))) }
        finally { tokens = null }
    }
}
