package dev.opendevice.node.kernel

import dev.opendevice.node.contract.ModuleManifest
import kotlinx.serialization.json.Json

object JsonModuleFixture {
    private val strictJson = Json { ignoreUnknownKeys = false }

    fun aiNode(owner: Class<*>): ModuleManifest {
        val fixture = requireNotNull(owner.getResource("/ai-node.json"))
            .readText(Charsets.UTF_8)
        return strictJson.decodeFromString<ModuleManifest>(fixture)
    }
}
