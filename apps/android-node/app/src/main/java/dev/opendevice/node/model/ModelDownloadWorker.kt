package dev.opendevice.node.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.opendevice.node.OpenDeviceNodeApp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ModelDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val modelId = inputData.getString(MODEL_ID_INPUT) ?: return@coroutineScope Result.failure()
        if (modelId != BuiltinModelCatalog.qwen3_0_6b.id) {
            return@coroutineScope Result.failure()
        }
        val application = applicationContext as? OpenDeviceNodeApp
            ?: return@coroutineScope Result.failure()
        val repository = application.modelDownloadRepository
        setForeground(foregroundInfo(repository.state.value))
        val progress = launch {
            repository.state.collect { state ->
                if (state is ModelDownloadState.Downloading) {
                    setProgress(
                        Data.Builder()
                            .putLong(DOWNLOADED_BYTES_PROGRESS, state.downloadedBytes)
                            .putLong(TOTAL_BYTES_PROGRESS, state.totalBytes)
                            .build(),
                    )
                }
                if (state is ModelDownloadState.Downloading ||
                    state is ModelDownloadState.Verifying
                ) {
                    setForeground(foregroundInfo(state))
                }
            }
        }
        try {
            repository.downloadNow()
            when (repository.state.value) {
                is ModelDownloadState.Ready -> Result.success()
                else -> Result.failure()
            }
        } finally {
            progress.cancel()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(ModelDownloadState.Missing)

    private fun foregroundInfo(state: ModelDownloadState): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("OpenDevice 模型下载")
            .setContentText(notificationText(state))
            .setOngoing(
                state is ModelDownloadState.Downloading ||
                    state is ModelDownloadState.Verifying,
            )
            .setOnlyAlertOnce(true)
            .apply {
                if (state is ModelDownloadState.Downloading) {
                    val progress = if (state.totalBytes > 0L) {
                        ((state.downloadedBytes * PROGRESS_MAX) / state.totalBytes)
                            .coerceIn(0L, PROGRESS_MAX.toLong())
                            .toInt()
                    } else {
                        0
                    }
                    setProgress(PROGRESS_MAX, progress, state.totalBytes <= 0L)
                } else if (state is ModelDownloadState.Verifying) {
                    setProgress(0, 0, true)
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notificationText(state: ModelDownloadState): String = when (state) {
        is ModelDownloadState.Downloading ->
            "${state.downloadedBytes} / ${state.totalBytes} 字节"
        is ModelDownloadState.Verifying -> "正在校验大小和 SHA-256"
        is ModelDownloadState.Ready -> "模型已校验"
        else -> "准备下载"
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "模型下载",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val MODEL_ID_INPUT = "modelId"
        const val DOWNLOADED_BYTES_PROGRESS = "downloadedBytes"
        const val TOTAL_BYTES_PROGRESS = "totalBytes"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 10_101
        private const val PROGRESS_MAX = 1_000
    }
}
