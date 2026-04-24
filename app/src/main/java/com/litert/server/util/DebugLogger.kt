package com.litert.server.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogger {
    private const val TAG = "LiteRTDebug"
    private var logFile: File? = null
    private val requestLogs = ThreadLocal<MutableList<String>>()
    private val globalLogs = CopyOnWriteArrayList<String>()
    
    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "debug_log.txt")
        if (logFile?.exists() == true) {
            logFile?.delete() // Start fresh
        }
        log("Logger initialized. File: ${logFile?.absolutePath}")
    }

    fun startRequest() {
        requestLogs.set(mutableListOf())
    }

    fun getRequestLogs(): List<String> {
        return requestLogs.get() ?: emptyList()
    }

    fun clearRequest() {
        requestLogs.remove()
    }

    fun log(message: String, level: Int = Log.DEBUG, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val formattedMessage = "[$timestamp] $message"
        
        // Console
        when (level) {
            Log.VERBOSE -> Log.v(TAG, message, throwable)
            Log.DEBUG -> Log.d(TAG, message, throwable)
            Log.INFO -> Log.i(TAG, message, throwable)
            Log.WARN -> Log.w(TAG, message, throwable)
            Log.ERROR -> Log.e(TAG, message, throwable)
        }

        // Request-specific
        requestLogs.get()?.add(formattedMessage)
        if (throwable != null) {
            requestLogs.get()?.add(throwable.stackTraceToString())
        }

        // Global (for app UI)
        globalLogs.add(formattedMessage)
        if (globalLogs.size > 1000) globalLogs.removeAt(0)

        // File
        try {
            logFile?.appendText("$formattedMessage\n")
            if (throwable != null) {
                logFile?.appendText("${throwable.stackTraceToString()}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getGlobalLogs(): List<String> = globalLogs
}
