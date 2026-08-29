package dev.opendevice.node.kernel

import dev.opendevice.node.contract.Integrity
import dev.opendevice.node.contract.IntegrityKind
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.RuntimeKind
import dev.opendevice.node.contract.Source
import dev.opendevice.node.contract.SourceKind
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModulePackageStoreTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun storesVersionedPackageAndAtomicallyMovesTheCurrentPointer() {
        withStore { store ->
            val first = verifiedPackage(version = "0.1.0", payload = "first")
            val second = verifiedPackage(
                version = "0.2.0",
                rollbackVersion = "0.1.0",
                payload = "second",
            )

            assertEquals(ModulePackageStoreStatus.REVIEW_REQUIRED, store.store(first).status)
            assertNull(store.pointer(first.manifest.id))
            assertEquals(
                ModulePackageStoreStatus.STORED,
                store.store(first, reviewAccepted = true).status,
            )
            assertEquals(
                StoredModulePointer(currentVersion = "0.1.0", previousVersion = null),
                store.pointer(first.manifest.id),
            )
            assertEquals(ModulePackageStoreStatus.STORED, store.store(second).status)
            assertEquals(
                StoredModulePointer(currentVersion = "0.2.0", previousVersion = "0.1.0"),
                store.pointer(first.manifest.id),
            )

            val loaded = assertNotNull(store.load(first.manifest.id, "0.2.0"))
            assertContentEquals(second.payloadBytes, loaded.payloadBytes)
            assertContentEquals(second.unsignedManifestBytes, loaded.manifestBytes)
        }
    }

    @Test
    fun rollsBackBySwappingOnlyPreviouslyVerifiedVersionPointers() {
        withStore { store ->
            val first = verifiedPackage(version = "0.1.0", payload = "first")
            val second = verifiedPackage(
                version = "0.2.0",
                rollbackVersion = "0.1.0",
                payload = "second",
            )
            store.store(first, reviewAccepted = true)
            store.store(second)

            assertEquals(ModulePackageStoreStatus.ROLLED_BACK, store.rollback(first.manifest.id).status)
            assertEquals(
                StoredModulePointer(currentVersion = "0.1.0", previousVersion = "0.2.0"),
                store.pointer(first.manifest.id),
            )
            assertEquals(ModulePackageStoreStatus.ROLLED_BACK, store.rollback(first.manifest.id).status)
            assertEquals(
                StoredModulePointer(currentVersion = "0.2.0", previousVersion = "0.1.0"),
                store.pointer(first.manifest.id),
            )
        }
    }

    @Test
    fun rejectsVersionConflictsDowngradesAndPathLikeIdentitiesWithoutMutation() {
        withStore { store ->
            val first = verifiedPackage(version = "0.1.0", payload = "first")
            assertEquals(
                ModulePackageStoreStatus.STORED,
                store.store(first, reviewAccepted = true).status,
            )

            assertEquals(
                ModulePackageStoreStatus.VERSION_CONFLICT,
                store.store(verifiedPackage(version = "0.1.0", payload = "different")).status,
            )
            assertEquals(
                ModulePackageStoreStatus.VERSION_NOT_NEWER,
                store.store(
                    verifiedPackage(
                        version = "0.0.9",
                        rollbackVersion = "0.1.0",
                        payload = "old",
                    ),
                ).status,
            )
            assertEquals(
                ModulePackageStoreStatus.IDENTITY_INVALID,
                store.store(
                    verifiedPackage(version = "0.2.0", payload = "bad")
                        .copy(manifest = first.manifest.copy(id = "../escape")),
                ).status,
            )
            assertEquals(
                StoredModulePointer(currentVersion = "0.1.0", previousVersion = null),
                store.pointer(first.manifest.id),
            )
            assertNull(store.pointer("../escape"))
        }
    }

    @Test
    fun requiresFreshReviewBeforeAnUpdateAddsPermissions() {
        withStore { store ->
            val first = verifiedPackage(version = "0.1.0", payload = "first")
            val update = verifiedPackage(
                version = "0.2.0",
                rollbackVersion = "0.1.0",
                payload = "second",
                permissions = first.manifest.permissions +
                    dev.opendevice.node.contract.Permission.NetworkOutbound,
            )
            store.store(first, reviewAccepted = true)

            val blocked = store.store(update)

            assertEquals(ModulePackageStoreStatus.REVIEW_REQUIRED, blocked.status)
            assertEquals(
                setOf(dev.opendevice.node.contract.Permission.NetworkOutbound),
                blocked.review?.addedPermissions,
            )
            assertEquals("0.1.0", store.pointer(first.manifest.id)?.currentVersion)
            assertEquals(
                ModulePackageStoreStatus.STORED,
                store.store(update, reviewAccepted = true).status,
            )
        }
    }

    @Test
    fun requiresFreshReviewWhenRollbackWouldRestorePermissions() {
        withStore { store ->
            val permission = dev.opendevice.node.contract.Permission.NetworkOutbound
            val first = verifiedPackage(
                version = "0.1.0",
                payload = "first",
                permissions = listOf(permission),
            )
            val update = verifiedPackage(
                version = "0.2.0",
                rollbackVersion = "0.1.0",
                payload = "second",
                permissions = emptyList(),
            )
            store.store(first, reviewAccepted = true)
            store.store(update)

            val blocked = store.rollback(first.manifest.id)

            assertEquals(ModulePackageStoreStatus.REVIEW_REQUIRED, blocked.status)
            assertEquals(setOf(permission), blocked.review?.addedPermissions)
            assertEquals("0.2.0", store.pointer(first.manifest.id)?.currentVersion)
            assertEquals(
                ModulePackageStoreStatus.ROLLED_BACK,
                store.rollback(first.manifest.id, reviewAccepted = true).status,
            )
        }
    }

    private fun verifiedPackage(
        version: String,
        payload: String,
        rollbackVersion: String? = null,
        permissions: List<dev.opendevice.node.contract.Permission>? = null,
    ): VerifiedModulePackage {
        val fixtureText = requireNotNull(
            javaClass.classLoader?.getResource("device-info.json"),
        ).readText()
        val fixture = json.decodeFromString<ModuleManifest>(fixtureText)
        val payloadBytes = payload.encodeToByteArray()
        val keyBytes = "test-publisher-key".encodeToByteArray()
        val signatureBytes = "detached-signature".encodeToByteArray()
        val manifest = fixture.copy(
            permissions = permissions ?: fixture.permissions,
            version = version,
            source = Source(
                kind = SourceKind.Official,
                repository = "https://github.com/opendevice-forge/modules",
                revision = "release-$version",
            ),
            runtime = fixture.runtime.copy(
                kind = RuntimeKind.Declarative,
                entry = "device-info.json",
            ),
            protected = false,
            integrity = Integrity(
                kind = IntegrityKind.Package,
                sha256 = sha256(payloadBytes),
                publisherKeySha256 = sha256(keyBytes),
                publisherSignature = Base64.getEncoder().encodeToString(signatureBytes),
                rollbackVersion = rollbackVersion,
            ),
        )
        return VerifiedModulePackage(
            manifest = manifest,
            unsignedManifestBytes = ModulePackageVerifier.canonicalizeUnsignedManifest(manifest),
            payloadBytes = payloadBytes,
            publisherPublicKeySpki = keyBytes,
            publisherSignature = signatureBytes,
            publisherKeySha256 = sha256(keyBytes),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun withStore(block: (ModulePackageStore) -> Unit) {
        val root = Files.createTempDirectory("opendevice-package-store-").toFile()
        try {
            block(ModulePackageStore(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
