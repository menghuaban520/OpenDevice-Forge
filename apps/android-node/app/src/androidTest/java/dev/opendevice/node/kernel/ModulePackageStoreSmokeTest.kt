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
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.UUID
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModulePackageStoreSmokeTest {
    @Test
    fun storesUpdatesAndRollsBackWithAtomicMovesOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "package-store-smoke-${UUID.randomUUID()}")
        val json = Json { ignoreUnknownKeys = false }
        val fixture = context.assets.open("device-info.json")
            .bufferedReader()
            .use { json.decodeFromString<ModuleManifest>(it.readText()) }
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        try {
            val first = verifiedPackage(
                fixture = fixture,
                keyPair = keyPair,
                version = "0.1.0",
                rollbackVersion = null,
                payload = "first",
            )
            val second = verifiedPackage(
                fixture = fixture,
                keyPair = keyPair,
                version = "0.2.0",
                rollbackVersion = "0.1.0",
                payload = "second",
            )
            val store = ModulePackageStore(root)

            assertEquals(ModulePackageStoreStatus.REVIEW_REQUIRED, store.store(first).status)
            assertEquals(
                ModulePackageStoreStatus.STORED,
                store.store(first, reviewAccepted = true).status,
            )
            assertEquals(ModulePackageStoreStatus.STORED, store.store(second).status)
            assertEquals(
                StoredModulePointer("0.2.0", "0.1.0"),
                store.pointer(first.manifest.id),
            )
            assertNotNull(store.load(first.manifest.id, "0.2.0"))
            assertEquals(
                ModulePackageStoreStatus.ROLLED_BACK,
                store.rollback(first.manifest.id).status,
            )
            assertEquals(
                StoredModulePointer("0.1.0", "0.2.0"),
                store.pointer(first.manifest.id),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun verifiedPackage(
        fixture: ModuleManifest,
        keyPair: KeyPair,
        version: String,
        rollbackVersion: String?,
        payload: String,
    ): VerifiedModulePackage {
        val payloadBytes = payload.encodeToByteArray()
        val publicKey = keyPair.public.encoded
        val unsigned = fixture.copy(
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
                publisherKeySha256 = sha256(publicKey),
                publisherSignature = null,
                rollbackVersion = rollbackVersion,
            ),
        )
        val manifestBytes = ModulePackageVerifier.canonicalizeUnsignedManifest(unsigned)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(ModulePackageVerifier.signaturePayload(manifestBytes))
            sign()
        }
        val verification = ModulePackageVerifier.verify(
            components = ModulePackageComponents(
                manifestBytes = manifestBytes,
                payloadBytes = payloadBytes,
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
        assertEquals(ModulePackageVerificationStatus.VERIFIED, verification.status)
        return requireNotNull(verification.verifiedPackage)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
