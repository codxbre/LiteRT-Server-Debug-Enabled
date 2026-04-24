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

    var isReady = false
        private set

    suspend fun initialize(
        modelPath: String,
        useGpu: Boolean = true,
        temperature: Double = 0.7,
        maxTokens: Int = 1024,
        topK: Int = 40,
        topP: Double = 0.9
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DebugLogger.log("Engine.initialize: modelPath=$modelPath, useGpu=$useGpu")
                val backend = if (useGpu) Backend.GPU() else Backend.CPU()
                val visionBackend = if (useGpu) Backend.GPU() else Backend.CPU()

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    cacheDir = context.cacheDir.absolutePath
                )
                DebugLogger.log("Creating Engine with config: model=$modelPath")
                val newEngine = Engine(config)
                DebugLogger.log("Calling Engine.initialize()...")
                newEngine.initialize()
                DebugLogger.log("Engine.initialize() done.")

                currentSamplerConfig = SamplerConfig(topK = topK, topP = topP, temperature = temperature)
                DebugLogger.log("Creating conversation with samplerConfig: topK=$topK, topP=$topP, temp=$temperature")
                val conv = createNewConversation(newEngine, currentSamplerConfig)

                engine = newEngine
                conversation = conv
                currentBackend = if (useGpu) "GPU" else "CPU"
                isReady = true
                DebugLogger.log("Engine initialized successfully with $currentBackend backend")
                true
            } catch (e: Exception) {
                DebugLogger.log("Failed to initialize engine", Log.ERROR, e)
                if (useGpu) {
                    DebugLogger.log("Falling back to CPU backend...")
                    initialize(modelPath, useGpu = false, temperature, maxTokens, topK, topP)
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
        DebugLogger.log("createNewConversation called")
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
        DebugLogger.log("generateText called with prompt length: ${prompt.length}")
        val conv = conversation ?: run {
            DebugLogger.log("generateText failed: Engine not initialized", Log.ERROR)
            throw IllegalStateException("Engine not initialized")
        }
        return conv.sendMessageAsync(prompt)
            .onEach { DebugLogger.log("Token: $it") }
            .map { it.toString() }
    }

    /**
     * Passes the image file + prompt as proper multimodal content.
     * imagePath must be an absolute file path readable by the engine.
     */
    suspend fun analyzeImage(imagePath: String, prompt: String): Flow<String> {
        DebugLogger.log("analyzeImage called: path=$imagePath, prompt=$prompt")
        val conv = conversation ?: run {
            DebugLogger.log("analyzeImage failed: Engine not initialized", Log.ERROR)
            throw IllegalStateException("Engine not initialized")
        }
        val contents = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )
        return conv.sendMessageAsync(contents)
            .onEach { DebugLogger.log("Vision Token: $it") }
            .map { it.toString() }
    }

    fun clearHistory() {
        DebugLogger.log("clearHistory called")
        val eng = engine ?: return
        conversation?.close()
        conversation = createNewConversation(eng, currentSamplerConfig)
        DebugLogger.log("Conversation history cleared")
    }

    fun getBackend(): String = currentBackend

    fun shutdown() {
        DebugLogger.log("Engine shutdown called")
        isReady = false
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
