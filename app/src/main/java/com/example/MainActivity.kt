package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DownloadItem
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: DownloaderViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        checkIntentAndClipboard(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentAndClipboard(intent)
    }

    private fun checkIntentAndClipboard(currIntent: Intent?) {
        val fromWidget = currIntent?.getBooleanExtra("FROM_WIDGET", false) == true
        if (fromWidget) {
            currIntent.putExtra("FROM_WIDGET", false) // Reset to avoid double pasta on orientation changes
            try {
                val clipboardManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (clipboardManager.hasPrimaryClip()) {
                    val clipData = clipboardManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            viewModel.onUrlChange(text)
                            Toast.makeText(this, "Pasted URL from clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore clipboard or intent format issues
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) { // disable dynamic color to show off our glass gradient
                // Create an animated gradient background
                val infiniteTransition = rememberInfiniteTransition()
                val offset1 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(15000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val offset2 by infiniteTransition.animateFloat(
                    initialValue = 1000f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                val gradientBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF28005A), // Deep Purple
                        Color(0xFF0D032B), // Very Dark
                        Color(0xFF07476F), // Deep Teal
                        Color(0xFF0D032B)
                    ),
                    start = Offset(offset1, offset2),
                    end = Offset(offset1 + 1000f, offset2 + 1000f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent, // Transparent so background shows
                        topBar = {
                            TopAppBar(
                                title = { 
                                    Text(
                                        "Video Downloader",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ) 
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    titleContentColor = Color.White
                                )
                            )
                        }
                    ) { innerPadding ->
                        DownloaderScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            onPlayClick = { item -> playVideo(item) }
                        )
                    }
                }
            }
        }
    }

    private fun playVideo(item: DownloadItem) {
        if (item.localUri == null) {
            Toast.makeText(this, "File path not available", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(item.localUri)
        if (!file.exists()) {
            Toast.makeText(this, "File not found on device", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val isMp3 = item.quality == "MP3"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (isMp3) "audio/*" else "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .blur(8.dp) // Creates a subtle blur for the container style (blurring underneath is tricky directly without renderEffect but this adds nice texture)
    )
    
    // The actual content layer, over the blurred box
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .background(Color.White.copy(alpha = 0.1f)),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    modifier: Modifier = Modifier,
    onPlayClick: (DownloadItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Column(modifier = modifier) {
        // Input Section
        GlassContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.urlInput,
                    onValueChange = viewModel::onUrlChange,
                    label = { Text("Video URL", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("https://...", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input"),
                    singleLine = true,
                    isError = uiState.error != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White
                    )
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Text(
                    text = "Format / Quality:", 
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val qualities = listOf("1080", "720", "480", "MP3")
                    qualities.forEach { q ->
                        val isSelected = uiState.selectedQuality == q
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onQualityChange(q) },
                            label = { 
                                val labelText = if (q == "MP3") "MP3" else "${q}p"
                                Text(labelText, color = if (isSelected) Color.Black else Color.White) 
                            },
                            modifier = Modifier.testTag("quality_$q"),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                labelColor = Color.White,
                                selectedContainerColor = Color.White.copy(alpha = 0.9f),
                                selectedLabelColor = Color.Black
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Button(
                    onClick = viewModel::startDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("download_button"),
                    enabled = !uiState.isLoading && uiState.urlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History Section
        Text(
            text = "History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No previous downloads",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            items(history, key = { it.id }) { item ->
                HistoryItemRow(
                    item = item,
                    onPlayClick = { onPlayClick(item) },
                    onDeleteClick = { viewModel.deleteHistoryItem(item) }
                )
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    item: DownloadItem,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    GlassContainer(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.originalUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (item.quality == "MP3") "MP3" else "${item.quality}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.testTag("play_button_${item.id}")
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick, 
                    modifier = Modifier.testTag("delete_button_${item.id}")
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete Record",
                        tint = Color(0xFFFF6B6B), // Light red for glass UI
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
