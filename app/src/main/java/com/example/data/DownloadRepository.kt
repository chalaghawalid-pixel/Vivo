package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun insert(item: DownloadItem): Int {
        return downloadDao.insertDownload(item).toInt()
    }

    suspend fun updateStatus(id: Int, status: String, localUri: String?) {
        downloadDao.updateDownloadStatus(id, status, localUri)
    }

    suspend fun markFailed(id: Int) {
        downloadDao.updateStatus(id, "FAILED")
    }

    suspend fun deleteById(id: Int) = downloadDao.deleteDownloadById(id)
}
