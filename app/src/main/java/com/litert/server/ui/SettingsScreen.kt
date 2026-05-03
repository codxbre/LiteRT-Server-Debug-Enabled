package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.util.SettingsManager

@Composable
fun SettingsScreen(
    modelPath: String,
    isServerRunning: Boolean,
    onClearCache: () -> Unit,
    onSaveSettings: () -> Unit,
    onStartEngine: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    var temperature by remember { mutableFloatStateOf(settingsManager.temperature) }
    var maxTokens by remember { mutableFloatStateOf(settingsManager.maxTokens.toFloat()) }
    var topK by remember { mutableFloatStateOf(settingsManager.topK.toFloat()) }
    var topP by remember { mutableFloatStateOf(settingsManager.topP) }
    var contextWindow by remember { mutableFloatStateOf(settingsManager.contextWindow.toFloat()) }
    var useGpu by remember { mutableStateOf(settingsManager.useGpu) }
    var modelVariant by remember { mutableStateOf(settingsManager.modelVariant) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    settingsManager.temperature = temperature
                    settingsManager.maxTokens = maxTokens.toInt()
                    settingsManager.topK = topK.toInt()
                    settingsManager.topP = topP
                    settingsManager.contextWindow = contextWindow.toInt()
                    settingsManager.useGpu = useGpu
                    settingsManager.modelVariant = modelVariant
                    onSaveSettings()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save", fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (!isServerRunning) {
            Button(
                onClick = onStartEngine,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Engine & Server", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text("Model Configuration", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Text("Select Variant", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("E2B", "E4B").forEach { variant ->
                    val selected = modelVariant == variant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .border(
                                width = 2.dp,
                                color = if (selected) GreenPrimary else Color(0xFF333333),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (selected) Color(0xFF1A3A1A) else SurfaceColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { modelVariant = variant }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                variant,
                                color = if (selected) GreenPrimary else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (variant == "E2B") "2.6B Params" else "4.3B Params",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(16.dp).align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GPU Acceleration", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Adreno 630 / OpenCL 2.0",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = useGpu,
                    onCheckedChange = { useGpu = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = Color(0xFF1A3A1A))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Temperature", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${"%.2f".format(temperature)}", color = GreenPrimary)
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = GreenPrimary,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Max Tokens (Output)", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${maxTokens.toInt()}", color = GreenPrimary)
            }
            Slider(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                valueRange = 128f..16384f,
                colors = SliderDefaults.colors(
                    thumbColor = GreenPrimary,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Context Window", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${contextWindow.toInt()}", color = GreenPrimary)
            }
            Slider(
                value = contextWindow,
                onValueChange = { contextWindow = it },
                valueRange = 1024f..32768f,
                colors = SliderDefaults.colors(
                    thumbColor = GreenPrimary,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Model & Cache")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("LiteRT Server v1.0", color = Color.Gray, fontSize = 12.sp)
        Text("Gemma 4 · LiteRT-LM SDK 0.10.0", color = Color.Gray, fontSize = 12.sp)
        Text("Optimized for 12GB RAM + 12GB Swap", color = Color.Gray, fontSize = 12.sp)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Model?") },
            text = { Text("This will delete the downloaded model and require a re-download.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearCache()
                    showDeleteDialog = false
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
