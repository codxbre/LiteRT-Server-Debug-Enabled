package com.litert.server.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.data.RequestLogEntry
import com.litert.server.util.DebugLogger
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServerScreen(
    isRunning: Boolean,
    port: Int,
    requestLog: List<RequestLogEntry>,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val debugLogs = remember { mutableStateListOf<String>() }
    val logListState = rememberLazyListState()

    LaunchedEffect(isRunning) {
        while (true) {
            val newLogs = DebugLogger.getGlobalLogs()
            if (newLogs.size != debugLogs.size) {
                debugLogs.clear()
                debugLogs.addAll(newLogs)
            }
            delay(1000)
        }
    }

    // Auto-scroll to bottom of logs
    LaunchedEffect(debugLogs.size) {
        if (debugLogs.isNotEmpty()) {
            logListState.animateScrollToItem(debugLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text("Server Control", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) GreenPrimary else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRunning) "Running" else "Stopped",
                        color = if (isRunning) GreenPrimary else Color(0xFFEF4444),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                if (isRunning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "API: http://localhost:$port",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFEF4444) else GreenPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (isRunning) "STOP SERVER" else "START SERVER", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug Logs Section (Taking up most of the space)
        Text("Debug Logs", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceColor)
        ) {
            if (debugLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs yet", color = Color.DarkGray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    state = logListState,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(debugLogs) { log ->
                        Text(
                            log,
                            color = if (log.contains("ERROR")) Color(0xFFEF4444) else if (log.contains("WARN")) Color(0xFFFFB74D) else Color(0xFF9CCC65),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Request log
        Text("Recent Requests", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
            if (requestLog.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No requests yet", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(requestLog.takeLast(10).reversed()) { entry ->
                        RequestLogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun RequestLogRow(entry: RequestLogEntry) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fmt.format(Date(entry.timestamp)), color = Color.Gray, fontSize = 10.sp)
        Text(entry.endpoint, color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text("${entry.responseTimeMs}ms", color = Color.White, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(entry.statusCode.toString(), color = if (entry.statusCode == 200) Color(0xFF9CCC65) else Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
