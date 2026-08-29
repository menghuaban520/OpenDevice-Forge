package dev.opendevice.node.kernel

import dev.opendevice.node.contract.IntegrityKind
import dev.opendevice.node.contract.Permission
import dev.opendevice.node.contract.RuntimeKind
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredModulePointer(
    val currentVersion: String,
    val previousVersion: String? = null,
)

enum class ModulePackageStoreStatus {
    STORED,
    UNCHANGED,
    ROLLED_BACK,
    ROLLBACK_UNAVAILABLE,
    IDENTITY_INVALID,
    ARTIFACT_INVALID,
    VERSION_CONFLICT,
    VERSION_NOT_NEWER,
    ROLLBACK_VERSION_MISMATCH,
    REVIEW_REQUIRED,
    ATOMIC_MOVE_UNAVAILABLE,
    STORAGE_ERROR,
}

data class ModulePackageStoreResult(
    val status: ModulePackageStoreStatus,
    val review: ModulePackageReview? = null,
)

data class ModulePackageReview(
    val addedPermissions: Set<Permission>,
    val runtimeChanged: Boolean,
    val publisherChanged: Boolean,
    val riskChanged: Boolean,
)

class ModulePackageStore(
    private val root: File,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    @Synchronized
    fun store(
        modulePackage: VerifiedModulePackage,
        reviewAccepted: Boolean = false,
    ): ModulePackageStoreResult {
        val manifest = modulePackage.manifest
        if (!safeModuleID.matches(manifest.id) || !safeVersion.matches(manifest.version)) {
            return result(ModulePackageStoreStatus.IDENTITY_INVALID)
        }
        if (!artifactMatchesManifest(modulePackage)) {
            return result(ModulePackageStoreStatus.ARTIFACT_INVALID)
        }

        val existing = pointer(manifest.id)
        if (existing != null) {
            val comparison = compareVersions(manifest.version, existing.currentVersion)
                ?: return result(ModulePackageStoreStatus.IDENTITY_INVALID)
            if (comparison < 0) {
                return result(ModulePackageStoreStatus.VERSION_NOT_NEWER)
            }
            if (comparison == 0) {
                return if (storedVersionMatches(modulePackage)) {
                    result(ModulePackageStoreStatus.UNCHANGED)
                } else {
                    result(ModulePackageStoreStatus.VERSION_CONFLICT)
                }
            }
            if (manifest.integrity.rollbackVersion != existing.currentVersion) {
                return result(ModulePackageStoreStatus.ROLLBACK_VERSION_MISMATCH)
            }
        } else if (manifest.integrity.rollbackVersion != null) {
            return result(ModulePackageStoreStatus.ROLLBACK_VERSION_MISMATCH)
        }

        val currentManifest = existing?.let {
            loadStoredManifest(manifest.id, it.currentVersion)
                ?: return result(ModulePackageStoreStatus.ARTIFACT_INVALID)
        }
        val review = packageReview(currentManifest, manifest)
        if (review.requiresReview && !reviewAccepted) {
            return ModulePackageStoreResult(
                status = ModulePackageStoreStatus.REVIEW_REQUIRED,
                review = review,
            )
        }

        val versionDirectory = versionDirectory(manifest.id, manifest.version)
        if (versionDirectory.exists() && !storedVersionMatches(modulePackage)) {
            return result(ModulePackageStoreStatus.VERSION_CONFLICT)
        }
        val stagingDirectory = File(
            File(root, STAGING_DIRECTORY),
            "${manifest.id}-${manifest.version}-${UUID.randomUUID()}",
        )
        return try {
            if (!versionDirectory.exists()) {
                Files.createDirectories(stagingDirectory.toPath())
                writeAndSync(File(stagingDirectory, MANIFEST_FILE), modulePackage.unsignedManifestBytes)
                writeAndSync(File(stagingDirectory, PAYLOAD_FILE), modulePackage.payloadBytes)
                writeAndSync(File(stagingDirectory, PUBLISHER_KEY_FILE), modulePackage.publisherPublicKeySpki)
                writeAndSync(File(stagingDirectory, SIGNATURE_FILE), modulePackage.publisherSignature)
                Files.createDirectories(requireNotNull(versionDirectory.parentFile).toPath())
                moveAtomically(stagingDirectory, versionDirectory, replace = false)
            }
            writePointer(
                moduleID = manifest.id,
                pointer = StoredModulePointer(
                    currentVersion = manifest.version,
                    previousVersion = existing?.currentVersion,
                ),
            )
            result(ModulePackageStoreStatus.STORED)
        } catch (_: AtomicMoveNotSupportedException) {
            result(ModulePackageStoreStatus.ATOMIC_MOVE_UNAVAILABLE)
        } catch (_: Exception) {
            result(ModulePackageStoreStatus.STORAGE_ERROR)
        } finally {
            if (stagingDirectory.exists()) stagingDirectory.deleteRecursively()
        }
    }

    @Synchronized
    fun rollback(
        moduleID: String,
        reviewAccepted: Boolean = false,
    ): ModulePackageStoreResult {
        if (!safeModuleID.matches(moduleID)) {
            return result(ModulePackageStoreStatus.IDENTITY_INVALID)
        }
        val current = pointer(moduleID)
            ?: return result(ModulePackageStoreStatus.ROLLBACK_UNAVAILABLE)
        val previous = current.previousVersion
            ?: return result(ModulePackageStoreStatus.ROLLBACK_UNAVAILABLE)
        if (!versionDirectory(moduleID, previous).isDirectory) {
            return result(ModulePackageStoreStatus.ROLLBACK_UNAVAILABLE)
        }
        val currentManifest = loadStoredManifest(moduleID, current.currentVersion)
            ?: return result(ModulePackageStoreStatus.ARTIFACT_INVALID)
        val previousManifest = loadStoredManifest(moduleID, previous)
            ?: return result(ModulePackageStoreStatus.ARTIFACT_INVALID)
        val review = packageReview(currentManifest, previousManifest)
        if (review.requiresReview && !reviewAccepted) {
            return ModulePackageStoreResult(
                status = ModulePackageStoreStatus.REVIEW_REQUIRED,
                review = review,
            )
        }
        return try {
            writePointer(
                moduleID = moduleID,
                pointer = StoredModulePointer(
                    currentVersion = previous,
                    previousVersion = current.currentVersion,
                ),
            )
            result(ModulePackageStoreStatus.ROLLED_BACK)
        } catch (_: AtomicMoveNotSupportedException) {
            result(ModulePackageStoreStatus.ATOMIC_MOVE_UNAVAILABLE)
        } catch (_: Exception) {
            result(ModulePackageStoreStatus.STORAGE_ERROR)
        }
    }

    fun pointer(moduleID: String): StoredModulePointer? {
        if (!safeModuleID.matches(moduleID)) return null
        val file = pointerFile(moduleID)
        if (!file.isFile || file.length() > MAX_POINTER_BYTES) return null
        return try {
            json.decodeFromString<StoredModulePointer>(file.readText()).takeIf {
                safeVersion.matches(it.currentVersion) &&
                    (it.previousVersion == null || safeVersion.matches(it.previousVersion)) &&
                    versionDirectory(moduleID, it.currentVersion).isDirectory &&
                    (it.previousVersion == null ||
                        versionDirectory(moduleID, it.previousVersion).isDirectory)
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun load(moduleID: String, version: String): ModulePackageComponents? {
        if (!safeModuleID.matches(moduleID) || !safeVersion.matches(version)) return null
        val directory = versionDirectory(moduleID, version)
        val manifest = File(directory, MANIFEST_FILE)
        val payload = File(directory, PAYLOAD_FILE)
        val key = File(directory, PUBLISHER_KEY_FILE)
        val signature = File(directory, SIGNATURE_FILE)
        if (!manifest.isFile || manifest.length() !in 1..MAX_MANIFEST_BYTES ||
            !payload.isFile || payload.length() !in 0..MAX_PAYLOAD_BYTES ||
            !key.isFile || key.length() !in 1..MAX_KEY_BYTES ||
            !signature.isFile || signature.length() !in 1..MAX_SIGNATURE_BYTES
        ) {
            return null
        }
        return try {
            ModulePackageComponents(
                manifestBytes = manifest.readBytes(),
                payloadBytes = payload.readBytes(),
                publisherPublicKeySpki = key.readBytes(),
                publisherSignature = signature.readBytes(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun artifactMatchesManifest(modulePackage: VerifiedModulePackage): Boolean {
        val manifest = modulePackage.manifest
        if (manifest.integrity.kind != IntegrityKind.Package ||
            manifest.runtime.kind != RuntimeKind.Declarative ||
            manifest.protected ||
            manifest.integrity.sha256 != sha256(modulePackage.payloadBytes) ||
            manifest.integrity.publisherKeySha256 !=
            sha256(modulePackage.publisherPublicKeySpki) ||
            manifest.integrity.publisherKeySha256 != modulePackage.publisherKeySha256 ||
            manifest.integrity.publisherSignature !=
            Base64.getEncoder().encodeToString(modulePackage.publisherSignature)
        ) {
            return false
        }
        return modulePackage.unsignedManifestBytes.contentEquals(
            ModulePackageVerifier.canonicalizeUnsignedManifest(manifest),
        )
    }

    private fun storedVersionMatches(modulePackage: VerifiedModulePackage): Boolean {
        val stored = load(modulePackage.manifest.id, modulePackage.manifest.version)
            ?: return false
        return stored.manifestBytes.contentEquals(modulePackage.unsignedManifestBytes) &&
            stored.publisherPublicKeySpki.contentEquals(modulePackage.publisherPublicKeySpki) &&
            stored.publisherSignature.contentEquals(modulePackage.publisherSignature) &&
            sha256(stored.payloadBytes) == modulePackage.manifest.integrity.sha256
    }

    private fun loadStoredManifest(moduleID: String, version: String) =
        load(moduleID, version)?.let { components ->
            try {
                json.decodeFromString<dev.opendevice.node.contract.ModuleManifest>(
                    components.manifestBytes.toString(Charsets.UTF_8),
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun packageReview(
        current: dev.opendevice.node.contract.ModuleManifest?,
        candidate: dev.opendevice.node.contract.ModuleManifest,
    ): ModulePackageReview {
        if (current == null) {
            return ModulePackageReview(
                addedPermissions = candidate.permissions.toSet(),
                runtimeChanged = true,
                publisherChanged = false,
                riskChanged = false,
            )
        }
        return ModulePackageReview(
            addedPermissions = candidate.permissions
                .filterNot(current.permissions::contains)
                .toSet(),
            runtimeChanged = candidate.runtime.kind != current.runtime.kind ||
                candidate.runtime.entry != current.runtime.entry ||
                candidate.runtime.companionPackage != current.runtime.companionPackage,
            publisherChanged = candidate.integrity.publisherKeySha256 !=
                current.integrity.publisherKeySha256,
            riskChanged = candidate.risk != current.risk,
        )
    }

    private val ModulePackageReview.requiresReview: Boolean
        get() = addedPermissions.isNotEmpty() || runtimeChanged || publisherChanged || riskChanged

    private fun writePointer(moduleID: String, pointer: StoredModulePointer) {
        val stateDirectory = File(root, STATE_DIRECTORY)
        Files.createDirectories(stateDirectory.toPath())
        val temporary = File(stateDirectory, ".${moduleID}-${UUID.randomUUID()}.tmp")
        try {
            writeAndSync(temporary, json.encodeToString(pointer).encodeToByteArray())
            moveAtomically(temporary, pointerFile(moduleID), replace = true)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun writeAndSync(file: File, bytes: ByteArray) {
        Files.createDirectories(requireNotNull(file.parentFile).toPath())
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun moveAtomically(source: File, target: File, replace: Boolean) {
        val options = if (replace) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        Files.move(source.toPath(), target.toPath(), *options)
    }

    private fun versionDirectory(moduleID: String, version: String): File =
        File(File(File(root, PACKAGES_DIRECTORY), moduleID), version)

    private fun pointerFile(moduleID: String): File =
        File(File(root, STATE_DIRECTORY), "$moduleID.json")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun compareVersions(left: String, right: String): Int? {
        val leftParts = parseVersion(left) ?: return null
        val rightParts = parseVersion(right) ?: return null
        return leftParts.indices
            .asSequence()
            .map { leftParts[it].compareTo(rightParts[it]) }
            .firstOrNull { it != 0 } ?: 0
    }

    private fun parseVersion(value: String): List<Int>? =
        safeVersion.matchEntire(value)
            ?.groupValues
            ?.drop(1)
            ?.take(3)
            ?.map(String::toInt)

    private fun result(status: ModulePackageStoreStatus) =
        ModulePackageStoreResult(status)

    private companion object {
        val safeModuleID = Regex("^(?:[a-z0-9]+[.-])+[a-z0-9-]+$")
        val safeVersion =
            Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?$")
        const val PACKAGES_DIRECTORY = "packages"
        const val STAGING_DIRECTORY = "staging"
        const val STATE_DIRECTORY = "state"
        const val MANIFEST_FILE = "manifest.json"
        const val PAYLOAD_FILE = "payload.bin"
        const val PUBLISHER_KEY_FILE = "publisher-key.spki"
        const val SIGNATURE_FILE = "signature.der"
        const val MAX_POINTER_BYTES = 4 * 1024L
        const val MAX_MANIFEST_BYTES = 256 * 1024L
        const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024L
        const val MAX_KEY_BYTES = 4 * 1024L
        const val MAX_SIGNATURE_BYTES = 512L
    }
}
