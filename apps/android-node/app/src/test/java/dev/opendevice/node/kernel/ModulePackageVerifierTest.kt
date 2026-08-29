package dev.opendevice.node.kernel

import dev.opendevice.node.contract.Integrity
import dev.opendevice.node.contract.IntegrityKind
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.Permission
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ModulePackageVerifierTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun verifiesNodeSignedInteroperabilityFixture() {
        val fixtureText = requireNotNull(
            javaClass.classLoader?.getResource("signed-device-info-v1.json"),
        ).readText()
        val fixture = json.parseToJsonElement(fixtureText).jsonObject
        val manifestBytes = fixture.getValue("manifest").jsonPrimitive.content.encodeToByteArray()
        val manifest = json.decodeFromString<ModuleManifest>(manifestBytes.toString(Charsets.UTF_8))
        assertEquals(
            manifestBytes.toString(Charsets.UTF_8),
            ModulePackageVerifier.canonicalizeUnsignedManifest(manifest).toString(Charsets.UTF_8),
        )
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
            host = testHost,
            trustedPublisherKeySha256 = setOf(requireNotNull(manifest.integrity.publisherKeySha256)),
        )

        assertEquals(ModulePackageVerificationStatus.VERIFIED, result.status)
    }

    @Test
    fun verifiesCanonicalFullManifestWithP256Signature() {
        val signed = signedPackage()

        val result = ModulePackageVerifier.verify(
            components = signed.components,
            host = testHost,
            trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
        )

        assertEquals(ModulePackageVerificationStatus.VERIFIED, result.status)
        assertNotNull(result.manifest)
        assertEquals(RuntimeKind.Declarative, result.manifest.runtime.kind)
        assertFalse(result.manifest.protected)
    }

    @Test
    fun rejectsManifestPermissionTampering() {
        val signed = signedPackage()
        val parsed = json.decodeFromString<ModuleManifest>(
            signed.components.manifestBytes.toString(Charsets.UTF_8),
        )
        val tampered = parsed.copy(
            permissions = parsed.permissions + Permission.NetworkOutbound,
        )

        val result = ModulePackageVerifier.verify(
            components = signed.components.copy(
                manifestBytes = ModulePackageVerifier.canonicalizeUnsignedManifest(tampered),
            ),
            host = testHost,
            trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
        )

        assertEquals(ModulePackageVerificationStatus.SIGNATURE_MISMATCH, result.status)
    }

    @Test
    fun rejectsCorruptedPayloadAndUntrustedPublisher() {
        val signed = signedPackage()

        assertEquals(
            ModulePackageVerificationStatus.HASH_MISMATCH,
            ModulePackageVerifier.verify(
                components = signed.components.copy(payloadBytes = "corrupt".encodeToByteArray()),
                host = testHost,
                trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
            ).status,
        )
        assertEquals(
            ModulePackageVerificationStatus.UNTRUSTED_PUBLISHER,
            ModulePackageVerifier.verify(
                components = signed.components,
                host = testHost,
                trustedPublisherKeySha256 = emptySet(),
            ).status,
        )
    }

    @Test
    fun rejectsExternalBuiltinRuntimeAndNonCanonicalJson() {
        val builtin = signedPackage { manifest ->
            manifest.copy(runtime = manifest.runtime.copy(kind = RuntimeKind.Builtin))
        }
        assertEquals(
            ModulePackageVerificationStatus.RUNTIME_FORBIDDEN,
            ModulePackageVerifier.verify(
                components = builtin.components,
                host = testHost,
                trustedPublisherKeySha256 = setOf(builtin.publisherKeySha256),
            ).status,
        )

        val signed = signedPackage()
        val nonCanonical = signed.components.manifestBytes
            .toString(Charsets.UTF_8)
            .replaceFirst("{", "{\n")
            .encodeToByteArray()
        assertEquals(
            ModulePackageVerificationStatus.MANIFEST_NOT_CANONICAL,
            ModulePackageVerifier.verify(
                components = signed.components.copy(manifestBytes = nonCanonical),
                host = testHost,
                trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
            ).status,
        )
    }

    @Test
    fun rejectsNumbersWhoseCanonicalFormWouldExhaustMemory() {
        val signed = signedPackage()
        val abusive = signed.components.manifestBytes
            .toString(Charsets.UTF_8)
            .replace("\"minSdk\":28", "\"minSdk\":1e1000000000")
            .encodeToByteArray()

        assertEquals(
            ModulePackageVerificationStatus.MANIFEST_JSON_INVALID,
            ModulePackageVerifier.verify(
                components = signed.components.copy(manifestBytes = abusive),
                host = testHost,
                trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
            ).status,
        )
    }

    @Test
    fun rejectsSchemaViolationsThatKotlinTypesCanStillRepresent() {
        val signed = signedPackage { manifest ->
            manifest.copy(name = "")
        }

        assertEquals(
            ModulePackageVerificationStatus.MANIFEST_INVALID,
            ModulePackageVerifier.verify(
                components = signed.components,
                host = testHost,
                trustedPublisherKeySha256 = setOf(signed.publisherKeySha256),
            ).status,
        )
    }

    private fun signedPackage(
        mutate: (ModuleManifest) -> ModuleManifest = { it },
    ): SignedPackage {
        val fixtureText = requireNotNull(
            javaClass.classLoader?.getResource("device-info.json"),
        ).readText()
        val fixture = json.decodeFromString<ModuleManifest>(fixtureText)
        val payload = "OpenDevice declarative module payload".encodeToByteArray()
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        val publicKey = keyPair.public.encoded
        val unsigned = mutate(
            fixture.copy(
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
            ),
        )
        val manifestBytes = ModulePackageVerifier.canonicalizeUnsignedManifest(unsigned)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(ModulePackageVerifier.signaturePayload(manifestBytes))
            sign()
        }
        return SignedPackage(
            components = ModulePackageComponents(
                manifestBytes = manifestBytes,
                payloadBytes = payload,
                publisherPublicKeySpki = publicKey,
                publisherSignature = signature,
            ),
            publisherKeySha256 = sha256(publicKey),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private data class SignedPackage(
        val components: ModulePackageComponents,
        val publisherKeySha256: String,
    )

    private companion object {
        val testHost = ModuleHost(
            kernelVersion = "0.1.0",
            androidSdk = 29,
            abis = setOf(dev.opendevice.node.contract.ABI.Arm64V8A),
            availableRuntimes = setOf(RuntimeKind.Declarative),
        )
    }
}
