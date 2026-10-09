package com.sdevprem.remotecomposedemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val REMOTE_COMPOSE_SERVER = "http://10.0.2.2:8080"

/** Add a new Remote Compose destination here to surface it in the app catalogue. */
data class RemoteCatalogueItem(
    val endpoint: String,
    val screenName: String,
)

private val catalogueItems = listOf(
    RemoteCatalogueItem(endpoint = "/festive", screenName = "Festive"),
    RemoteCatalogueItem(endpoint = "/clock", screenName = "Clock"),
    RemoteCatalogueItem(endpoint = "/counter", screenName = "Counter"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteComposeDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CatalogueApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun CatalogueApp(modifier: Modifier = Modifier) {
    var selectedItem by remember { mutableStateOf<RemoteCatalogueItem?>(null) }

    selectedItem?.let { item ->
        RemoteDetailScreen(
            item = item,
            modifier = modifier,
            onBack = { selectedItem = null },
        )
    } ?: CatalogueScreen(
        items = catalogueItems,
        modifier = modifier,
        onItemClick = { selectedItem = it },
    )
}

@Composable
private fun CatalogueScreen(
    items: List<RemoteCatalogueItem>,
    modifier: Modifier = Modifier,
    onItemClick: (RemoteCatalogueItem) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text("Remote Compose catalogue", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
        }
        items(items, key = { it.endpoint }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onItemClick(item) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.screenName, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("›", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
@SuppressLint("RestrictedApi")
private fun RemoteDetailScreen(
    item: RemoteCatalogueItem,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var refreshKey by remember(item.endpoint) { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(item.screenName, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            RemoteScreen(
                item = item,
                refreshKey = refreshKey,
                modifier = Modifier.fillMaxSize(),
            )
            FloatingActionButton(
                onClick = { refreshKey++ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Text("↻", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
@SuppressLint("RestrictedApi")
private fun RemoteScreen(
    item: RemoteCatalogueItem,
    refreshKey: Int,
    modifier: Modifier,
) {
    var uiPayload by remember { mutableStateOf<ByteArray?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val widthPixels = displayMetrics.widthPixels
    val heightPixels = displayMetrics.heightPixels
    val density = LocalDensity.current.density

    LaunchedEffect(item.endpoint, widthPixels, heightPixels, density, refreshKey) {
        uiPayload = null
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val client = HttpClient(CIO)
                val response = client.get(
                    "$REMOTE_COMPOSE_SERVER${item.endpoint}?w=$widthPixels&h=$heightPixels&d=$density",
                )
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
