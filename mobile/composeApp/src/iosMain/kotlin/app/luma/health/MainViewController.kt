package app.luma.health

import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun platformClient() = HttpClient(Darwin)
fun MainViewController(baseUrl: String) = ComposeUIViewController { App(baseUrl) }
