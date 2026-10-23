package app.luma.health

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApiTest {
    @Test
    fun expiredAccessRefreshesAndRetriesOriginalCall() = runTest {
        val calls = mutableListOf<String>()
        val engine = MockEngine { request ->
            calls += request.url.encodedPath
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/api/v1/auth/login/" -> respond("""{"access":"first","refresh":"refresh-first"}""", headers = headers)
                "/api/v1/auth/refresh/" -> respond("""{"access":"second","refresh":"refresh-second"}""", headers = headers)
                else -> if (request.headers[HttpHeaders.Authorization] == "Bearer first")
                    respond("{}", HttpStatusCode.Unauthorized, headers)
                else respond("""{"display_name":"Aditi","timezone":"UTC","cycle_length":28,"period_length":5}""", headers = headers)
            }
        }
        val client = HttpClient(engine).configured()
        try {
            val api = Api("http://localhost/api/v1", client)
            api.authenticate("sample@example.com", "sample", false)
            assertEquals("Aditi", api.profile().displayName)
            assertEquals(listOf("/api/v1/auth/login/", "/api/v1/profile/", "/api/v1/auth/refresh/", "/api/v1/profile/"), calls)
        } finally { client.close() }
    }

    @Test
    fun rejectedRefreshClearsSession() = runTest {
        val engine = MockEngine { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath.endsWith("login/")) respond("""{"access":"first","refresh":"old"}""", headers = headers)
            else respond("{}", HttpStatusCode.Unauthorized, headers)
        }
        val client = HttpClient(engine).configured()
        try {
            val api = Api("http://localhost/api/v1", client)
            api.authenticate("sample@example.com", "sample", false)
            assertFailsWith<ApiFailure> { api.profile() }
            assertFalse(api.signedIn)
        } finally { client.close() }
    }
}
