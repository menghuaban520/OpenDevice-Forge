package dev.opendevice.node.kernel

import android.content.Context
import dev.opendevice.node.contract.ModuleManifest
import kotlinx.serialization.json.Json

object BuiltinModules {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun aiNode(context: Context): ModuleManifest = context.assets
        .open(AI_NODE_ASSET)
        .bufferedReader(Charsets.UTF_8)
        .use { reader ->
            strictJson.decodeFromString<ModuleManifest>(reader.readText())
        }

    private const val AI_NODE_ASSET = "ai-node.json"
}
