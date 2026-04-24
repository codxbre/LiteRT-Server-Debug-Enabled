package com.litert.server.service

import android.util.Log
import com.litert.server.data.ChatRequest
import com.litert.server.data.ChatResponse
import com.litert.server.data.ErrorResponse
import com.litert.server.data.HealthResponse
import com.litert.server.data.OaiChatRequest
import com.litert.server.data.OaiChatResponse
import com.litert.server.data.OaiChoice
import com.litert.server.data.OaiDelta
import com.litert.server.data.OaiMessage
import com.litert.server.data.OaiModelEntry
import com.litert.server.data.OaiModelsResponse
import com.litert.server.data.OaiStreamChunk
import com.litert.server.data.OaiStreamChoice
import com.litert.server.data.RequestLogEntry
import com.litert.server.data.VisionRequest
import com.litert.server.engine.LiteRTEngine
import com.litert.server.util.DebugLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class HttpApiServer(
    private val engine: LiteRTEngine,
    private val onRequest: (RequestLogEntry) -> Unit
) {
    private var server: ApplicationEngine? = null
    var port: Int = 8080
        private set

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun JsonElement.asContentString(): String {
        return try {
            this.jsonPrimitive.content
        } catch (e: Exception) {
            this.toString()
        }
    }

    /**
     * Truncates a string to fit within a rough character limit to avoid exceeding token limits.
     * Note: This is a rough estimation (4 chars per token).
     */
    private fun truncatePrompt(prompt: String, maxChars: Int = 12000): String {
        if (prompt.length <= maxChars) return prompt
        DebugLogger.log("Prompt too long (${prompt.length} chars), truncating to keep last $maxChars chars", Log.WARN)
        return "... [truncated] ... " + prompt.takeLast(maxChars)
    }

    fun start(): Int {
        for (tryPort in 8080..8082) {
            try {
                server = embeddedServer(CIO, port = tryPort) {
                    install(ContentNegotiation) {
                        json(json)
                    }
                    install(CORS) {
                        anyHost()
                    }
                    install(StatusPages) {
                        exception<Throwable> { call, cause ->
                            DebugLogger.log("Unhandled exception: ${cause.message}", Log.ERROR, cause)
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse(
                                    error = cause.message ?: "Unknown error",
                                    code = 500,
                                    debug = DebugLogger.getRequestLogs()
                                )
                            )
                        }
                    }

                    routing {

                        get("/health") {
                            DebugLogger.startRequest()
                            call.respond(HealthResponse("ok", "gemma-4-E2B", engine.getBackend() == "GPU", engine.isReady, DebugLogger.getRequestLogs()))
                            DebugLogger.clearRequest()
                        }

                        route("/v1") {
                            get("/models") {
                                call.respond(OaiModelsResponse(data = listOf(OaiModelEntry(id = "gemma-4-e2b"))))
                            }

                            post("/chat/completions") {
                                DebugLogger.startRequest()
                                val rawBody = call.receiveText()
                                DebugLogger.log("POST /v1/chat/completions - RAW BODY SIZE: ${rawBody.length}")
                                
                                try {
                                    val req = json.decodeFromString<OaiChatRequest>(rawBody)
                                    DebugLogger.log("Parsed OaiChatRequest: model=${req.model}, messages=${req.messages.size}, stream=${req.stream}")
                                    
                                    if (!engine.isReady) {
                                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Engine not ready", 503, DebugLogger.getRequestLogs()))
                                        return@post
                                    }

                                    val start = System.currentTimeMillis()
                                    // Build prompt and truncate if necessary
                                    val rawPrompt = req.messages.joinToString("\n") {
                                        "${it.role}: ${it.content.asContentString()}"
                                    } + "\nassistant:"
                                    
                                    val prompt = truncatePrompt(rawPrompt)

                                    if (req.stream) {
                                        val reqId = "chatcmpl-${System.currentTimeMillis()}"
                                        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                            val firstChunk = OaiStreamChunk(reqId, created = System.currentTimeMillis() / 1000, model = "gemma-4-e2b", 
                                                choices = listOf(OaiStreamChoice(0, OaiDelta(role = "assistant", content = ""))), debug = DebugLogger.getRequestLogs())
                                            write("data: ${json.encodeToString(firstChunk)}\n\n")
                                            flush()

                                            engine.generateText(prompt).collect { token ->
                                                val chunk = OaiStreamChunk(reqId, created = System.currentTimeMillis() / 1000, model = "gemma-4-e2b", 
                                                    choices = listOf(OaiStreamChoice(0, OaiDelta(content = token))))
                                                write("data: ${json.encodeToString(chunk)}\n\n")
                                                flush()
                                            }

                                            val stopChunk = OaiStreamChunk(reqId, created = System.currentTimeMillis() / 1000, model = "gemma-4-e2b", 
                                                choices = listOf(OaiStreamChoice(0, OaiDelta(), finishReason = "stop")))
                                            write("data: ${json.encodeToString(stopChunk)}\n\n")
                                            write("data: [DONE]\n\n")
                                            flush()
                                        }
                                        onRequest(RequestLogEntry(endpoint = "/v1/chat/completions", responseTimeMs = System.currentTimeMillis() - start, statusCode = 200))
                                    } else {
                                        val tokens = engine.generateText(prompt).toList()
                                        val content = tokens.joinToString("")
                                        val ms = System.currentTimeMillis() - start
                                        onRequest(RequestLogEntry(endpoint = "/v1/chat/completions", responseTimeMs = ms, statusCode = 200))
                                        call.respond(OaiChatResponse(
                                            id = "chatcmpl-${System.currentTimeMillis()}",
                                            created = System.currentTimeMillis() / 1000,
                                            model = "gemma-4-e2b",
                                            choices = listOf(OaiChoice(0, OaiMessage("assistant", json.parseToJsonElement(json.encodeToString(content))), "stop")),
                                            debug = DebugLogger.getRequestLogs()
                                        ))
                                    }
                                } catch (e: Exception) {
                                    DebugLogger.log("Failed to process request: ${e.message}", Log.ERROR, e)
                                    val status = if (e is kotlinx.serialization.SerializationException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                                    call.respond(status, ErrorResponse("Error: ${e.message}", status.value, DebugLogger.getRequestLogs()))
                                } finally {
                                    DebugLogger.clearRequest()
                                }
                            }
                        }

                        post("/chat") {
                            DebugLogger.startRequest()
                            val rawBody = call.receiveText()
                            try {
                                val req = json.decodeFromString<ChatRequest>(rawBody)
                                val start = System.currentTimeMillis()
                                if (!engine.isReady) {
                                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Engine not ready", 503, DebugLogger.getRequestLogs()))
                                    return@post
                                }
                                val tokens = engine.generateText(truncatePrompt(req.message)).toList()
                                val response = tokens.joinToString("")
                                val ms = System.currentTimeMillis() - start
                                onRequest(RequestLogEntry(endpoint = "/chat", responseTimeMs = ms, statusCode = 200))
                                call.respond(ChatResponse(response, tokens.size, ms, DebugLogger.getRequestLogs()))
                            } catch (e: Exception) {
                                DebugLogger.log("Error in /chat: ${e.message}", Log.ERROR, e)
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Error", 400, DebugLogger.getRequestLogs()))
                            } finally {
                                DebugLogger.clearRequest()
                            }
                        }

                        post("/vision") {
                            DebugLogger.startRequest()
                            val rawBody = call.receiveText()
                            try {
                                val req = json.decodeFromString<VisionRequest>(rawBody)
                                val start = System.currentTimeMillis()
                                if (!engine.isReady) {
                                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Engine not ready", 503, DebugLogger.getRequestLogs()))
                                    return@post
                                }
                                val tokens = engine.analyzeImage(req.imagePath, truncatePrompt(req.prompt)).toList()
                                val response = tokens.joinToString("")
                                val ms = System.currentTimeMillis() - start
                                onRequest(RequestLogEntry(endpoint = "/vision", responseTimeMs = ms, statusCode = 200))
                                call.respond(ChatResponse(response, tokens.size, ms, DebugLogger.getRequestLogs()))
                            } catch (e: Exception) {
                                DebugLogger.log("Error in /vision: ${e.message}", Log.ERROR, e)
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Error", 400, DebugLogger.getRequestLogs()))
                            } finally {
                                DebugLogger.clearRequest()
                            }
                        }

                        post("/reset") {
                            DebugLogger.startRequest()
                            engine.clearHistory()
                            onRequest(RequestLogEntry(endpoint = "/reset", responseTimeMs = 0, statusCode = 200))
                            call.respond(mapOf("status" to "conversation cleared", "debug" to DebugLogger.getRequestLogs()))
                            DebugLogger.clearRequest()
                        }
                    }
                }
                server!!.start(wait = false)
                port = tryPort
                DebugLogger.log("Server started on port $port")
                return tryPort
            } catch (e: Exception) {
                DebugLogger.log("Failed to start server on port $tryPort: ${e.message}", Log.WARN)
                if (tryPort == 8082) throw e
            }
        }
        throw IllegalStateException("Could not bind to any port (8080-8082)")
    }

    fun stop() {
        DebugLogger.log("Stopping server...")
        server?.stop(1000, 5000)
        server = null
    }
}
