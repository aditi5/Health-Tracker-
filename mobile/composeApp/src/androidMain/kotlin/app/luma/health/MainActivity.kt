package app.luma.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformClient() = HttpClient(OkHttp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(BuildConfig.API_BASE_URL) }
    }
}
