package com.litert.server.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.litert.server.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class LiteRTEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var currentBackend: String = "GPU"
    private var currentSamplerConfig: SamplerConfig = SamplerConfig(
        topK = 40,
        topP = 0.9,
        temperature = 0.7
    )
    private var currentMaxTokens: Int = 4096

    var isReady = false
        private set

    suspend fun initialize(
        modelPath: String,
        useGpu: Boolean = true,
        temperature: Double = 0.7,
        maxTokens: Int = 4096,
        topK: Int = 40,
        topP: Double = 0.9,
        contextWindow: Int = 16384
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.log("Engine.initialize: model=$modelPath, gpu=$useGpu, ctx=$contextWindow, maxOut=$maxTokens")
                val backend = if (useGpu) Backend.GPU() else Backend.CPU()
                val visionBackend = if (useGpu) Backend.GPU() else Backend.CPU()

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    cacheDir = context.cacheDir.absolutePath,
                    maxNumTokens = contextWindow
                )
                
                DebugLogger.log("Creating Engine...")
                val newEngine = Engine(config)
                newEngine.initialize()
                DebugLogger.log("Engine initialized.")

                currentSamplerConfig = SamplerConfig(topK = topK, topP = topP, temperature = temperature)
                currentMaxTokens = maxTokens
                
                val conv = createNewConversation(newEngine, currentSamplerConfig)

                engine = newEngine
                conversation = conv
                currentBackend = if (useGpu) "GPU" else "CPU"
                isReady = true
                DebugLogger.log("Engine ready on $currentBackend")
                true
            } catch (e: Exception) {
                DebugLogger.log("Init failed: ${e.message}", Log.ERROR, e)
                if (useGpu) {
                    DebugLogger.log("Falling back to CPU...")
                    initialize(modelPath, false, temperature, maxTokens, topK, topP, contextWindow)
                } else {
                    isReady = false
                    false
                }
            }
        }
    }

    private fun createNewConversation(
        eng: Engine,
        samplerConfig: SamplerConfig
    ): com.google.ai.edge.litertlm.Conversation {
        return eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    Content.Text(
                        "You are a helpful AI assistant running locally on an Android device " +
                        "powered by Google's Gemma multimodal LLM via LiteRT."
                    )
                ),
                samplerConfig = samplerConfig
            )
        )
    }

    suspend fun generateText(prompt: String): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        // We can't pass maxTokens to sendMessageAsync directly in current SDK, 
        // it's usually handled by the EngineConfig's maxNumTokens as a total limit.
        return conv.sendMessageAsync(prompt)
            .map { it.toString() }
    }

    suspend fun analyzeImage(imagePath: String, prompt: String): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        val contents = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )
        return conv.sendMessageAsync(contents)
            .map { it.toString() }
    }

    fun clearHistory() {
        val eng = engine ?: return
        conversation?.close()
        conversation = createNewConversation(eng, currentSamplerConfig)
    }

    fun getBackend(): String = currentBackend

    fun shutdown() {
        isReady = false
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
