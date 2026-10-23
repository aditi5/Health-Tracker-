package app.luma.health

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformClient() = HttpClient(OkHttp)
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Luma development preview") {
        App(System.getenv("LUMA_API_URL") ?: "http://127.0.0.1:8000/api/v1")
    }
}
