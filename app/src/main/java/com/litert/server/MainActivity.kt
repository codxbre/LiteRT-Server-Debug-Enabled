package com.litert.server

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.litert.server.data.*
import com.litert.server.download.GemmaVariant
import com.litert.server.download.ModelDownloadManager
import com.litert.server.service.LLMForegroundService
import com.litert.server.ui.*
import com.litert.server.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var settingsManager: SettingsManager
    private var appState by mutableStateOf(AppState())
    private var chatMessages = mutableStateListOf<ChatMessage>()
    private var isGenerating by mutableStateOf(false)
    private var visionResult by mutableStateOf("")
    private var isAnalyzing by mutableStateOf(false)
    private var selectedTab by mutableIntStateOf(3) // Default to Settings

    private var liteRTEngine: com.litert.server.engine.LiteRTEngine? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        appState = appState.copy(status = AppStatus.INITIALIZING)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")
                val dest = File(downloadManager.getModelPath())
                dest.parentFile?.mkdirs()
                inputStream.use { ins ->
                    dest.outputStream().use { out -> ins.copyTo(out) }
                }
                withContext(Dispatchers.Main) { startEngineService() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appState = appState.copy(
                        status = AppStatus.DOWNLOAD_ERROR,
                        errorMessage = "Failed to copy file: ${e.message}"
                    )
                }
            }
        }
    }

    private val engineReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LLMForegroundService.ACTION_ENGINE_READY -> {
                    val port = intent.getIntExtra(LLMForegroundService.EXTRA_SERVER_PORT, 8999)
                    val isGpu = intent.getBooleanExtra(LLMForegroundService.EXTRA_IS_GPU, true)
                    liteRTEngine = LLMForegroundService.engineInstance
                    appState = appState.copy(
                        status = AppStatus.READY,
                        isServerRunning = true,
                        serverPort = port,
                        isGpuBackend = isGpu,
                        engineReady = true
                    )
                    selectedTab = 0 // Switch to Chat when ready
                }
                LLMForegroundService.ACTION_ENGINE_ERROR -> {
                    val msg = intent.getStringExtra(LLMForegroundService.EXTRA_ERROR_MESSAGE)
                    appState = appState.copy(status = AppStatus.ERROR, errorMessage = msg)
                }
            }
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = ModelDownloadManager(this)
        settingsManager = SettingsManager(this)

        val filter = IntentFilter().apply {
            addAction(LLMForegroundService.ACTION_ENGINE_READY)
            addAction(LLMForegroundService.ACTION_ENGINE_ERROR)
        }
        registerReceiver(engineReceiver, filter, RECEIVER_NOT_EXPORTED)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        // We don't call checkModelAndUpdateState() here anymore to avoid auto-loading
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppContent()
            }
        }
    }

    @Composable
    fun AppContent() {
        when (appState.status) {
            AppStatus.CONFIGURING -> MainTabLayout()
            AppStatus.MODEL_NOT_FOUND, AppStatus.DOWNLOADING, AppStatus.DOWNLOAD_ERROR, AppStatus.INITIALIZING -> {
                DownloadScreen(
                    status = appState.status,
                    progressPercent = appState.downloadProgress,
                    downloadedMb = appState.downloadedMb,
                    totalMb = appState.totalMb,
                    speedMbps = appState.downloadSpeedMbps,
                    etaSeconds = appState.etaSeconds,
                    errorMessage = appState.errorMessage,
                    selectedVariant = GemmaVariant.valueOf(settingsManager.modelVariant),
                    onVariantSelected = { variant ->
                        settingsManager.modelVariant = variant.name
                        downloadManager.setVariant(variant)
                    },
                    onDownload = ::startDownload,
                    onRetry = ::startDownload,
                    onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) }
                )
            }
            AppStatus.READY -> MainTabLayout()
            AppStatus.ERROR -> {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Error: ${appState.errorMessage}", color = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { appState = appState.copy(status = AppStatus.CONFIGURING) }) { 
                            Text("Back to Settings") 
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainTabLayout() {
        val tabs = listOf("Chat", "Vision", "Server", "Settings")
        val icons = listOf(Icons.Default.Chat, Icons.Default.Image, Icons.Default.Api, Icons.Default.Settings)
        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(containerColor = SurfaceColor) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], contentDescription = tab) },
                            label = { Text(tab, color = if (selectedTab == index) GreenPrimary else Color.Gray) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> ChatScreen(messages = chatMessages, isGenerating = isGenerating, onSend = ::sendMessage, onClear = { chatMessages.clear() })
                    1 -> VisionScreen(isAnalyzing = isAnalyzing, analysisResult = visionResult, onAnalyze = ::analyzeImage, onShare = { visionResult = it })
                    2 -> ServerScreen(isRunning = appState.isServerRunning, port = appState.serverPort, requestLog = appState.requestLog, onToggle = ::toggleServer)
                    3 -> SettingsScreen(
                        modelPath = downloadManager.getModelPath(),
                        isServerRunning = appState.isServerRunning,
                        onClearCache = { downloadManager.deleteModel(); appState = appState.copy(status = AppStatus.CONFIGURING) },
                        onSaveSettings = { Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show() },
                        onStartEngine = ::checkAndStartEngine
                    )
                }
            }
        }
    }

    private fun checkAndStartEngine() {
        downloadManager.setVariant(GemmaVariant.valueOf(settingsManager.modelVariant))
        if (downloadManager.isModelDownloaded()) {
            startEngineService()
        } else {
            appState = appState.copy(status = AppStatus.MODEL_NOT_FOUND)
        }
    }

    private fun sendMessage(text: String) {
        val engine = liteRTEngine ?: return
        val userMsg = ChatMessage(role = MessageRole.USER, content = text)
        chatMessages.add(userMsg)
        val assistantMsg = ChatMessage(role = MessageRole.ASSISTANT, content = "", isStreaming = true)
        chatMessages.add(assistantMsg)
        val assistantIndex = chatMessages.lastIndex
        isGenerating = true
        lifecycleScope.launch {
            try {
                engine.generateText(text).onCompletion { isGenerating = false }.collect { token ->
                    chatMessages[assistantIndex] = chatMessages[assistantIndex].copy(content = chatMessages[assistantIndex].content + token)
                }
            } catch (e: Exception) {
                isGenerating = false
            }
        }
    }

    private fun analyzeImage(uri: Uri, prompt: String) {
        val engine = liteRTEngine ?: return
        isAnalyzing = true
        visionResult = ""
        lifecycleScope.launch {
            try {
                val tmpFile = File(cacheDir, "vision_input.jpg")
                withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { it.copyTo(tmpFile.outputStream()) } }
                engine.analyzeImage(tmpFile.absolutePath, prompt).onCompletion { isAnalyzing = false }.collect { visionResult += it }
            } catch (e: Exception) { isAnalyzing = false }
        }
    }

    private fun startDownload() {
        appState = appState.copy(status = AppStatus.DOWNLOADING)
        lifecycleScope.launch(Dispatchers.IO) {
            downloadManager.downloadModel().catch { appState = appState.copy(status = AppStatus.DOWNLOAD_ERROR, errorMessage = it.message) }
                .collect { progress ->
                    appState = appState.copy(downloadProgress = progress.progressPercent)
                    if (progress.isDone) withContext(Dispatchers.Main) { startEngineService() }
                }
        }
    }

    private fun startEngineService() {
        appState = appState.copy(status = AppStatus.INITIALIZING)
        val intent = Intent(this, LLMForegroundService::class.java).apply {
            putExtra(LLMForegroundService.EXTRA_MODEL_PATH, downloadManager.getModelPath())
        }
        startForegroundService(intent)
    }

    private fun toggleServer() {
        if (appState.isServerRunning) {
            stopService(Intent(this, LLMForegroundService::class.java))
            liteRTEngine = null
            appState = appState.copy(isServerRunning = false, engineReady = false)
        } else {
            checkAndStartEngine()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(engineReceiver)
        super.onDestroy()
    }
}
