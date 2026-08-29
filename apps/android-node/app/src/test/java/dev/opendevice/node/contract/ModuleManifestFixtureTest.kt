package dev.opendevice.node.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleManifestFixtureTest {
    private val strictJson = Json { ignoreUnknownKeys = false }

    @Test
    fun deviceInfoUsesTheSharedContractWithoutAiOrNetworkPermissions() {
        val fixture = requireNotNull(javaClass.getResource("/device-info.json")).readText(Charsets.UTF_8)
        val manifest = strictJson.decodeFromString<ModuleManifest>(fixture)
        assertEquals("dev.opendevice.module.device-info", manifest.id)
        assertEquals(listOf(Permission.DeviceRead), manifest.permissions)
        assertEquals(null, manifest.service)
        assertEquals(false, manifest.protected)
    }

    @Test
    fun sharedAiNodeFixtureDecodesStrictly() {
        val fixture = requireNotNull(javaClass.getResource("/ai-node.json"))
            .readText(Charsets.UTF_8)
        val manifest = strictJson.decodeFromString<ModuleManifest>(fixture)

        assertEquals("dev.opendevice.module.ai-node", manifest.id)
        assertEquals(RuntimeKind.Builtin, manifest.runtime.kind)
        assertEquals(
            listOf(
                Permission.DeviceRead,
                Permission.ModelRead,
                Permission.NetworkOutbound,
                Permission.ServiceLocal,
            ),
            manifest.permissions,
        )
    }
}
