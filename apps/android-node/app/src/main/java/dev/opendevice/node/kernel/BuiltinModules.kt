package dev.opendevice.node.kernel

import android.content.Context
import dev.opendevice.node.contract.ModuleManifest
import kotlinx.serialization.json.Json

object BuiltinModules {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    const val DEVICE_INFO_ID = "dev.opendevice.module.device-info"

    fun aiNode(context: Context): ModuleManifest = read(context, AI_NODE_ASSET)

    fun deviceInfo(context: Context): ModuleManifest = read(context, "device-info.json")

    private fun read(context: Context, asset: String): ModuleManifest = context.assets
        .open(asset)
        .bufferedReader(Charsets.UTF_8)
        .use { reader ->
            strictJson.decodeFromString<ModuleManifest>(reader.readText())
        }

    private const val AI_NODE_ASSET = "ai-node.json"
}
