package com.example

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.network.CobaltRequest
import com.example.network.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: DownloadRepository
    val downloadHistory: StateFlow<List<DownloadItem>>

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DownloadRepository(database.downloadDao())
        downloadHistory = repository.allDownloads.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(urlInput = url, error = null)
    }

    fun onQualityChange(quality: String) {
        _uiState.value = _uiState.value.copy(selectedQuality = quality)
    }

    fun startDownload() {
        val url = _uiState.value.urlInput
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.err_empty_url))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)

        viewModelScope.launch {
            try {
                val isMp3 = _uiState.value.selectedQuality == "MP3"
                val requestBody = CobaltRequest(
                    url = url,
                    videoQuality = if (isMp3) "720" else _uiState.value.selectedQuality,
                    isAudioOnly = isMp3,
                    audioFormat = "mp3"
                )

                val instances = listOf(
                    "https://api.cobalt.tools/",
                    "https://cobalt.meowing.de/",
                    "https://cobalt.canine.tools/"
                )

                var response: com.example.network.CobaltResponse? = null
                var lastError: Exception? = null

                for (instance in instances) {
                    try {
                        Log.d("DownloaderViewModel", "Trying cobalt instance: $instance")
                        val res = RetrofitInstance.api.extractVideo(instance, requestBody)
                        if (res.status == "error") {
                            Log.w("DownloaderViewModel", "Instance $instance returned error: ${res.error?.code}")
                            response = res
                            // If rate-limited or general error, try another instance
                            if (res.error?.code == "rate-limit" || res.error?.code == "error") {
                                continue
                            } else {
                                break
                            }
                        } else {
                            response = res
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("DownloaderViewModel", "Failed to extract from $instance", e)
                        lastError = e
                    }
                }

                if (response == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to extract content: ${lastError?.message ?: "Internet/Server Connection Failed"}"
                    )
                    return@launch
                }

                if (response.status == "error") {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to extract content: ${response.error?.code ?: "Unknown Server Error"}"
                    )
                    return@launch
                }

                val finalDownloadUrl = response.url
                // Add an extension if filename doesn't have one
                var fileName = response.filename ?: (if (isMp3) "audio_${System.currentTimeMillis()}.mp3" else "video_${System.currentTimeMillis()}.mp4")
                if (!fileName.contains(".")) {
                    fileName += if (isMp3) ".mp3" else ".mp4"
                }

                if (finalDownloadUrl != null) {
                    enqueueDownloadManager(url, finalDownloadUrl, fileName, _uiState.value.selectedQuality)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        urlInput = "",
                        successMessage = "Download started. Check notifications!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Direct link not found")
                }
            } catch (e: Exception) {
                Log.e("DownloaderViewModel", "Download error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, error = null)
    }

    private suspend fun enqueueDownloadManager(originalUrl: String, downloadUrl: String, fileName: String, quality: String) {
        val context = getApplication<Application>()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        val isMp3 = quality == "MP3"
        val directoryType = if (isMp3) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription(if (isMp3) "Downloading audio..." else "Downloading video...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(directoryType, fileName)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        try {
            downloadManager.enqueue(request)
            
            // Generate the anticipated local path
            val directory = Environment.getExternalStoragePublicDirectory(directoryType)
            val physicalFile = File(directory, fileName)

            val item = DownloadItem(
                originalUrl = originalUrl,
                quality = quality,
                status = "COMPLETED", // Simplified status for history logging
                localUri = physicalFile.absolutePath
            )
            repository.insert(item)
        } catch (e: Exception) {
            Log.e("DownloaderViewModel", "DownloadManager error", e)
            _uiState.value = _uiState.value.copy(error = "Failed to enqueue download: ${e.message}")
        }
    }

    fun deleteHistoryItem(item: DownloadItem) {
        viewModelScope.launch {
            repository.deleteById(item.id)
            try {
                if (item.localUri != null) {
                    val file = File(item.localUri)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloaderViewModel", "Delete file error", e)
            }
        }
    }
}

data class DownloaderUiState(
    val urlInput: String = "",
    val selectedQuality: String = "1080",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
