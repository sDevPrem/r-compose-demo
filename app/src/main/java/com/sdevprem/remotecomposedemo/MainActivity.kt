package com.sdevprem.remotecomposedemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.remote.core.Limits.ENABLE_IMAGE_URLS
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.sdevprem.remotecomposedemo.ui.theme.RemoteComposeDemoTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteComposeDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RemoteScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RemoteComposeDemoTheme {
        Greeting("Android")
    }
}

@Composable
@SuppressLint("RestrictedApi")
fun RemoteScreen(modifier: Modifier) {
    var uiPayload by remember { mutableStateOf<ByteArray?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val widthPixels = displayMetrics.widthPixels
    val heightPixels = displayMetrics.heightPixels
    val density = LocalDensity.current.density

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val client = HttpClient(CIO)
                val response = client.get("http://10.0.2.2:8080/remote-ui/text?w=$widthPixels&h=$heightPixels&d=$density")
                uiPayload = response.body<ByteArray>()
                client.close()
                println("DEBUG: Downloaded ${uiPayload?.size} bytes from server")
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            errorMessage != null -> Text("Error fetching UI: $errorMessage")
            uiPayload == null -> CircularProgressIndicator()
            else -> {
                RemoteDocumentView(
                    documentBytes = uiPayload!!,
                    modifier = Modifier.fillMaxSize(),
                    onAction = { id, metadata ->
                        Log.d("tag...tag", "Action (ID: $id, Data: $metadata)")
                    }
                )
            }
        }
    }
}

@Composable
fun RemoteDocumentView(
    documentBytes: ByteArray,
    modifier: Modifier = Modifier,
    contentKey: Any = documentBytes.contentHashCode(),
    onAction: (id: Int, metadata: String?) -> Unit = { _, _ -> },
) {
    key(contentKey) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                ENABLE_IMAGE_URLS = true
                RemoteComposePlayer(ctx).apply {
                    setBitmapLoader { url ->
                        var imageBytes = ByteArray(0)

                        runBlocking(Dispatchers.IO) {
                            try {
                                val connection = URL(url).openConnection()
                                connection.connect()
                                // readBytes() pulls the entire file over the network right now
                                imageBytes = connection.getInputStream().readBytes()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        ByteArrayInputStream(imageBytes)
                    }
                    addIdActionListener { id, metadata ->
                        onAction(id, metadata)
                    }
                    setDocument(documentBytes)

                }
            }
        )
    }
}