package dev.opendevice.node.kernel

import dev.opendevice.node.contract.IntegrityKind
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.RuntimeKind
import dev.opendevice.node.contract.SourceKind
import java.math.BigDecimal
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

data class ModulePackageComponents(
    val manifestBytes: ByteArray,
    val payloadBytes: ByteArray,
    val publisherPublicKeySpki: ByteArray,
    val publisherSignature: ByteArray,
)

enum class ModulePackageVerificationStatus {
    VERIFIED,
    PACKAGE_TOO_LARGE,
    MANIFEST_ENCODING_INVALID,
    MANIFEST_JSON_INVALID,
    MANIFEST_NOT_CANONICAL,
    MANIFEST_INVALID,
    NOT_PACKAGE,
    SIGNATURE_NOT_DETACHED,
    HASH_MISMATCH,
    PUBLISHER_KEY_MISMATCH,
    PUBLISHER_KEY_INVALID,
    SIGNATURE_MISMATCH,
    UNTRUSTED_PUBLISHER,
    SOURCE_FORBIDDEN,
    RUNTIME_FORBIDDEN,
    PROTECTED_PACKAGE_FORBIDDEN,
}

data class ModulePackageVerificationResult(
    val status: ModulePackageVerificationStatus,
    val manifest: ModuleManifest? = null,
    val publisherKeySha256: String? = null,
    val verifiedPackage: VerifiedModulePackage? = null,
)

@ConsistentCopyVisibility
data class VerifiedModulePackage internal constructor(
    val manifest: ModuleManifest,
    val unsignedManifestBytes: ByteArray,
    val payloadBytes: ByteArray,
    val publisherPublicKeySpki: ByteArray,
    val publisherSignature: ByteArray,
    val publisherKeySha256: String,
)

object ModulePackageVerifier {
    private val signatureDomain = "OpenDevice Module Package v1\u0000".encodeToByteArray()
    private val semanticVersion =
        Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?$")
    private val moduleID = Regex("^(?:[a-z0-9]+[.-])+[a-z0-9-]+$")
    private val capabilityID = Regex("^[a-z][a-z0-9.-]+$")
    private val configurationKey = Regex("^[a-z][A-Za-z0-9]*$")
    private val companionPackage = Regex("^(?:[a-z0-9]+\\.)+[a-z0-9_]+$")
    private val runtimeEntry = Regex("^[a-z0-9][a-z0-9:._/-]*$")
    private val remoteSources = setOf(
        SourceKind.Official,
        SourceKind.Community,
        SourceKind.Github,
    )
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
        encodeDefaults = true
    }

    fun verify(
        components: ModulePackageComponents,
        host: ModuleHost,
        trustedPublisherKeySha256: Set<String>,
        maxManifestBytes: Int = 256 * 1024,
        maxPayloadBytes: Int = 8 * 1024 * 1024,
        allowedRuntimes: Set<RuntimeKind> = setOf(RuntimeKind.Declarative),
        allowedSources: Set<SourceKind> = remoteSources,
    ): ModulePackageVerificationResult {
        if (components.manifestBytes.isEmpty() ||
            components.manifestBytes.size > maxManifestBytes ||
            components.payloadBytes.size > maxPayloadBytes
        ) {
            return result(ModulePackageVerificationStatus.PACKAGE_TOO_LARGE)
        }

        val manifestText = decodeStrictUTF8(components.manifestBytes)
            ?: return result(ModulePackageVerificationStatus.MANIFEST_ENCODING_INVALID)
        val element = try {
            json.parseToJsonElement(manifestText)
        } catch (_: SerializationException) {
            return result(ModulePackageVerificationStatus.MANIFEST_JSON_INVALID)
        } catch (_: IllegalArgumentException) {
            return result(ModulePackageVerificationStatus.MANIFEST_JSON_INVALID)
        }
        val canonical = try {
            canonicalize(element).encodeToByteArray()
        } catch (_: IllegalArgumentException) {
            return result(ModulePackageVerificationStatus.MANIFEST_JSON_INVALID)
        }
        if (!components.manifestBytes.contentEquals(canonical)) {
            return result(ModulePackageVerificationStatus.MANIFEST_NOT_CANONICAL)
        }

        val unsigned = try {
            json.decodeFromJsonElement<ModuleManifest>(element)
        } catch (_: SerializationException) {
            return result(ModulePackageVerificationStatus.MANIFEST_INVALID)
        } catch (_: IllegalArgumentException) {
            return result(ModulePackageVerificationStatus.MANIFEST_INVALID)
        }
        if (unsigned.integrity.kind != IntegrityKind.Package) {
            return result(ModulePackageVerificationStatus.NOT_PACKAGE)
        }
        if (unsigned.integrity.publisherSignature != null) {
            return result(ModulePackageVerificationStatus.SIGNATURE_NOT_DETACHED)
        }
        if (unsigned.integrity.sha256 == null ||
            sha256(components.payloadBytes) != unsigned.integrity.sha256
        ) {
            return result(ModulePackageVerificationStatus.HASH_MISMATCH)
        }
        val publisherKeySha256 = sha256(components.publisherPublicKeySpki)
        if (unsigned.integrity.publisherKeySha256 == null ||
            publisherKeySha256 != unsigned.integrity.publisherKeySha256
        ) {
            return result(ModulePackageVerificationStatus.PUBLISHER_KEY_MISMATCH)
        }
        if (unsigned.source.kind !in allowedSources || !isRemoteHTTPSSource(unsigned)) {
            return result(ModulePackageVerificationStatus.SOURCE_FORBIDDEN)
        }
        if (unsigned.runtime.kind !in allowedRuntimes) {
            return result(ModulePackageVerificationStatus.RUNTIME_FORBIDDEN)
        }
        if (unsigned.protected) {
            return result(ModulePackageVerificationStatus.PROTECTED_PACKAGE_FORBIDDEN)
        }
        if (publisherKeySha256 !in trustedPublisherKeySha256) {
            return result(ModulePackageVerificationStatus.UNTRUSTED_PUBLISHER)
        }

        val signatureVerified = try {
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(components.publisherPublicKeySpki))
            if (publicKey !is ECPublicKey || !isP256(publicKey)) {
                return result(ModulePackageVerificationStatus.PUBLISHER_KEY_INVALID)
            }
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(signaturePayload(components.manifestBytes))
                verify(components.publisherSignature)
            }
        } catch (_: Exception) {
            return result(ModulePackageVerificationStatus.PUBLISHER_KEY_INVALID)
        }
        if (!signatureVerified) {
            return result(ModulePackageVerificationStatus.SIGNATURE_MISMATCH)
        }

        val manifest = unsigned.copy(
            integrity = unsigned.integrity.copy(
                publisherSignature = Base64.getEncoder()
                    .encodeToString(components.publisherSignature),
            ),
        )
        if (!criticalManifestValid(manifest) ||
            ModuleCompatibility.check(manifest, host).isNotEmpty()
        ) {
            return result(ModulePackageVerificationStatus.MANIFEST_INVALID)
        }
        return ModulePackageVerificationResult(
            status = ModulePackageVerificationStatus.VERIFIED,
            manifest = manifest,
            publisherKeySha256 = publisherKeySha256,
            verifiedPackage = VerifiedModulePackage(
                manifest = manifest,
                unsignedManifestBytes = components.manifestBytes.copyOf(),
                payloadBytes = components.payloadBytes.copyOf(),
                publisherPublicKeySpki = components.publisherPublicKeySpki.copyOf(),
                publisherSignature = components.publisherSignature.copyOf(),
                publisherKeySha256 = publisherKeySha256,
            ),
        )
    }

    fun canonicalizeUnsignedManifest(manifest: ModuleManifest): ByteArray =
        canonicalize(
            json.encodeToJsonElement(
                manifest.copy(
                    integrity = manifest.integrity.copy(publisherSignature = null),
                ),
            ),
        ).encodeToByteArray()

    fun signaturePayload(canonicalUnsignedManifestBytes: ByteArray): ByteArray =
        signatureDomain + canonicalUnsignedManifestBytes

    private fun criticalManifestValid(manifest: ModuleManifest): Boolean {
        if (!moduleID.matches(manifest.id) || !semanticVersion.matches(manifest.version)) {
            return false
        }
        if (!manifest.name.hasCodePointLength(1, 80) ||
            !manifest.summary.hasCodePointLength(1, 240) ||
            !manifest.publisher.hasCodePointLength(1, 80) ||
            !manifest.license.hasCodePointLength(1, 80)
        ) {
            return false
        }
        if (!semanticVersion.matches(manifest.kernel.min) ||
            !semanticVersion.matches(manifest.kernel.maxExclusive)
        ) {
            return false
        }
        if (manifest.capabilities.isEmpty() ||
            manifest.capabilities.any { !capabilityID.matches(it) } ||
            manifest.capabilities.size != manifest.capabilities.toSet().size ||
            manifest.contributes.isEmpty() ||
            manifest.platform.android.abis.isEmpty() ||
            manifest.platform.android.abis.size != manifest.platform.android.abis.toSet().size ||
            manifest.permissions.size != manifest.permissions.toSet().size ||
            !runtimeEntry.matches(manifest.runtime.entry)
        ) {
            return false
        }
        val companion = manifest.runtime.companionPackage
        if (companion != null && !companionPackage.matches(companion)) return false
        if (manifest.integrity.sha256?.matches(Regex("^[a-f0-9]{64}$")) != true ||
            manifest.integrity.publisherKeySha256
                ?.matches(Regex("^[a-f0-9]{64}$")) != true ||
            (manifest.integrity.publisherSignature?.length ?: 0) < 16
        ) {
            return false
        }
        if (manifest.platform.android.minSDK < 21 ||
            (manifest.platform.hardware.minMemoryBytes ?: 0L) < 0 ||
            (manifest.platform.hardware.minStorageBytes ?: 0L) < 0
        ) {
            return false
        }
        if (manifest.contributes.any {
                !capabilityID.matches(it.id) || !it.label.hasCodePointLength(1, 80)
            }
        ) {
            return false
        }
        if (manifest.configuration.fields.any {
                !configurationKey.matches(it.key) ||
                    !it.label.hasCodePointLength(1, 80) ||
                    it.default !is JsonPrimitive
            }
        ) {
            return false
        }
        val service = manifest.service
        if (service != null &&
            (!service.healthPath.startsWith("/") ||
                service.logFields.size != service.logFields.toSet().size ||
                !capabilityID.matches(service.result.schemaID) ||
                service.result.contentTypes.isEmpty() ||
                service.result.contentTypes.size != service.result.contentTypes.toSet().size)
        ) {
            return false
        }
        val contributionIDs = manifest.contributes.map { it.id }
        val configurationKeys = manifest.configuration.fields.map { it.key }
        return contributionIDs.size == contributionIDs.toSet().size &&
            configurationKeys.size == configurationKeys.toSet().size
    }

    private fun String.hasCodePointLength(minimum: Int, maximum: Int): Boolean =
        codePointCount(0, length) in minimum..maximum

    private fun isRemoteHTTPSSource(manifest: ModuleManifest): Boolean {
        if (manifest.source.kind !in remoteSources ||
            manifest.source.repository.isNullOrBlank() ||
            manifest.source.revision.isNullOrBlank() ||
            manifest.source.revision.length < 7
        ) {
            return false
        }
        return try {
            val source = URI(manifest.source.repository)
            source.scheme == "https" && !source.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun isP256(publicKey: ECPublicKey): Boolean {
        val expected = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        val actual = publicKey.params
        return actual.curve == expected.curve &&
            actual.generator == expected.generator &&
            actual.order == expected.order &&
            actual.cofactor == expected.cofactor
    }

    private fun decodeStrictUTF8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun canonicalize(value: JsonElement): String = when (value) {
        JsonNull -> "null"
        is JsonArray -> value.joinToString(separator = ",", prefix = "[", postfix = "]") {
            canonicalize(it)
        }
        is JsonObject -> value.entries
            .sortedBy { it.key }
            .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, element) ->
                "${quote(key)}:${canonicalize(element)}"
            }
        is JsonPrimitive -> when {
            value.isString -> quote(value.content)
            value.content == "true" || value.content == "false" -> value.content
            else -> canonicalNumber(value.content)
        }
    }

    private fun canonicalNumber(raw: String): String {
        val number = raw.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("invalid JSON number")
        if (number.compareTo(BigDecimal.ZERO) == 0) return "0"
        val normalized = number.stripTrailingZeros()
        if (normalized.precision() > MAX_CANONICAL_NUMBER_CHARS ||
            kotlin.math.abs(normalized.scale().toLong()) > MAX_CANONICAL_NUMBER_CHARS
        ) {
            throw IllegalArgumentException("JSON number expansion is too large")
        }
        return normalized.toPlainString().takeIf {
            it.length <= MAX_CANONICAL_NUMBER_CHARS
        } ?: throw IllegalArgumentException("JSON number expansion is too large")
    }

    private fun quote(value: String): String {
        val output = StringBuilder(value.length + 2).append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when (character) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\t' -> output.append("\\t")
                '\n' -> output.append("\\n")
                '\u000C' -> output.append("\\f")
                '\r' -> output.append("\\r")
                else -> when {
                    character.code < 0x20 ->
                        output.append("\\u%04x".format(character.code))
                    Character.isHighSurrogate(character) -> {
                        if (index + 1 >= value.length ||
                            !Character.isLowSurrogate(value[index + 1])
                        ) {
                            throw IllegalArgumentException("unpaired surrogate")
                        }
                        output.append(character).append(value[index + 1])
                        index += 1
                    }
                    Character.isLowSurrogate(character) ->
                        throw IllegalArgumentException("unpaired surrogate")
                    else -> output.append(character)
                }
            }
            index += 1
        }
        return output.append('"').toString()
    }

    private fun result(status: ModulePackageVerificationStatus) =
        ModulePackageVerificationResult(status = status)

    private const val MAX_CANONICAL_NUMBER_CHARS = 256 * 1024
}
