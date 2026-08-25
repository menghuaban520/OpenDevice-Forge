package dev.opendevice.node.model

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.StateFlow

interface ModelDownloadRepository {
    val state: StateFlow<ModelDownloadState>

    fun enqueue()

    fun cancel()

    suspend fun downloadNow()

    suspend fun verifiedModelFile(): java.io.File?
}

interface ModelWorkScheduler {
    fun enqueue(modelId: String)

    fun cancel(modelId: String)
}

object NoOpModelWorkScheduler : ModelWorkScheduler {
    override fun enqueue(modelId: String) = Unit

    override fun cancel(modelId: String) = Unit
}

class WorkManagerModelWorkScheduler(
    private val workManager: WorkManager,
) : ModelWorkScheduler {
    override fun enqueue(modelId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = Data.Builder()
            .putString(ModelDownloadWorker.MODEL_ID_INPUT, modelId)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(modelId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(modelId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(modelId))
    }

    private fun uniqueWorkName(modelId: String) = "model:$modelId"
}
