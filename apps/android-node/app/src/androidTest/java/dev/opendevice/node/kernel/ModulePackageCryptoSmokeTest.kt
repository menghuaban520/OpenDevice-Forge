package dev.opendevice.node.kernel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opendevice.node.contract.ABI
import dev.opendevice.node.contract.Integrity
import dev.opendevice.node.contract.IntegrityKind
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.RuntimeKind
import dev.opendevice.node.contract.Source
import dev.opendevice.node.contract.SourceKind
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModulePackageCryptoSmokeTest {
    @Test
    fun verifiesNodeSignedFixtureWithTheDeviceCryptoProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json { ignoreUnknownKeys = false }
        val fixture = context.assets.open("signed-device-info-v1.json")
            .bufferedReader()
            .use { json.parseToJsonElement(it.readText()).jsonObject }
        val manifestBytes = fixture.getValue("manifest").jsonPrimitive.content.encodeToByteArray()
        val manifest = json.decodeFromString<ModuleManifest>(manifestBytes.toString(Charsets.UTF_8))

        val result = ModulePackageVerifier.verify(
            components = ModulePackageComponents(
                manifestBytes = manifestBytes,
                payloadBytes = Base64.getDecoder()
                    .decode(fixture.getValue("payloadBase64").jsonPrimitive.content),
                publisherPublicKeySpki = Base64.getDecoder()
                    .decode(fixture.getValue("publisherPublicKeySpkiBase64").jsonPrimitive.content),
                publisherSignature = Base64.getDecoder()
                    .decode(fixture.getValue("publisherSignatureBase64").jsonPrimitive.content),
            ),
            host = ModuleHost(
                kernelVersion = "0.1.0",
                androidSdk = android.os.Build.VERSION.SDK_INT,
                abis = setOf(ABI.Arm64V8A),
                availableRuntimes = setOf(RuntimeKind.Declarative),
            ),
            trustedPublisherKeySha256 = setOf(requireNotNull(manifest.integrity.publisherKeySha256)),
        )

        assertEquals(ModulePackageVerificationStatus.VERIFIED, result.status)
    }

    @Test
    fun verifiesP256PackageWithTheDeviceCryptoProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json { ignoreUnknownKeys = false }
        val fixture = context.assets.open("device-info.json")
            .bufferedReader()
            .use { json.decodeFromString<ModuleManifest>(it.readText()) }
        val payload = "OpenDevice declarative module payload".encodeToByteArray()
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        val publicKey = keyPair.public.encoded
        val unsigned = fixture.copy(
            source = Source(
                kind = SourceKind.Official,
                repository = "https://github.com/opendevice-forge/modules",
                revision = "release-v0.1.0",
            ),
            runtime = fixture.runtime.copy(
                kind = RuntimeKind.Declarative,
                entry = "device-info.json",
            ),
            protected = false,
            integrity = Integrity(
                kind = IntegrityKind.Package,
                sha256 = sha256(payload),
                publisherKeySha256 = sha256(publicKey),
                publisherSignature = null,
                rollbackVersion = null,
            ),
        )
        val manifestBytes = ModulePackageVerifier.canonicalizeUnsignedManifest(unsigned)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(ModulePackageVerifier.signaturePayload(manifestBytes))
            sign()
        }

        val result = ModulePackageVerifier.verify(
            components = ModulePackageComponents(
                manifestBytes = manifestBytes,
                payloadBytes = payload,
                publisherPublicKeySpki = publicKey,
                publisherSignature = signature,
            ),
            host = ModuleHost(
                kernelVersion = "0.1.0",
                androidSdk = android.os.Build.VERSION.SDK_INT,
                abis = setOf(ABI.Arm64V8A),
                availableRuntimes = setOf(RuntimeKind.Declarative),
            ),
            trustedPublisherKeySha256 = setOf(sha256(publicKey)),
        )

        assertEquals(ModulePackageVerificationStatus.VERIFIED, result.status)
        assertNotNull(result.manifest)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
