package dev.opendevice.node.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

data class CreatedClient(
    val id: String,
    val label: String,
    val rawToken: String,
    val fingerprint: String,
)

data class ApiClient(
    val id: String,
    val label: String,
    val fingerprint: String,
    val createdAtMillis: Long,
    val revokedAtMillis: Long? = null,
)

interface ApiKeyStore {
    val clients: StateFlow<List<ApiClient>>

    suspend fun create(label: String): CreatedClient

    suspend fun verify(rawToken: String): ApiClient?

    suspend fun revoke(id: String): Boolean

    suspend fun revokeAll()
}

interface TokenMac {
    fun sign(value: ByteArray): ByteArray

    fun deleteKey()
}

interface ApiClientStorage {
    suspend fun load(): List<ApiClientVerifierRecord>

    suspend fun save(records: List<ApiClientVerifierRecord>)
}

@Serializable
data class ApiClientVerifierRecord(
    val id: String,
    val label: String,
    val fingerprint: String,
    val createdAtMillis: Long,
    val verifier: String,
    val revokedAtMillis: Long? = null,
) {
    fun publicClient() = ApiClient(
        id = id,
        label = label,
        fingerprint = fingerprint,
        createdAtMillis = createdAtMillis,
        revokedAtMillis = revokedAtMillis,
    )
}

class InMemoryApiClientStorage(
    initial: List<ApiClientVerifierRecord> = emptyList(),
) : ApiClientStorage {
    private var records = initial.toList()

    override suspend fun load(): List<ApiClientVerifierRecord> = records.toList()

    override suspend fun save(records: List<ApiClientVerifierRecord>) {
        this.records = records.toList()
    }

    fun snapshot(): List<ApiClientVerifierRecord> = records.toList()
}

class DefaultApiKeyStore private constructor(
    private val storage: ApiClientStorage,
    private val tokenMac: TokenMac,
    initialRecords: List<ApiClientVerifierRecord>,
    private val clockMillis: () -> Long,
    private val fillRandom: (ByteArray) -> Unit,
) : ApiKeyStore {
    private val mutex = Mutex()
    private val mutableRecords = initialRecords.toMutableList()
    private val mutableClients = MutableStateFlow(initialRecords.map { it.publicClient() })

    override val clients: StateFlow<List<ApiClient>> = mutableClients.asStateFlow()

    override suspend fun create(label: String): CreatedClient = mutex.withLock {
        val normalizedLabel = label.trim()
        require(normalizedLabel.isNotEmpty() && normalizedLabel.length <= MAX_LABEL_CHARACTERS) {
            "client_label_invalid"
        }
        val tokenBytes = ByteArray(TOKEN_BYTES).also(fillRandom)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        tokenBytes.fill(0)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .toHex()
            .take(FINGERPRINT_HEX_CHARACTERS)
        val record = ApiClientVerifierRecord(
            id = UUID.randomUUID().toString(),
            label = normalizedLabel,
            fingerprint = fingerprint,
            createdAtMillis = clockMillis(),
            verifier = Base64.getEncoder().encodeToString(
                tokenMac.sign(rawToken.toByteArray(Charsets.UTF_8)),
            ),
        )
        persist(mutableRecords + record)
        CreatedClient(record.id, record.label, rawToken, record.fingerprint)
    }

    override suspend fun verify(rawToken: String): ApiClient? = mutex.withLock {
        if (rawToken.isEmpty() || rawToken.length > MAX_TOKEN_CHARACTERS) return@withLock null
        val candidate = tokenMac.sign(rawToken.toByteArray(Charsets.UTF_8))
        var match: ApiClientVerifierRecord? = null
        mutableRecords.forEach { record ->
            val expected = runCatching { Base64.getDecoder().decode(record.verifier) }
                .getOrDefault(ByteArray(0))
            val equals = MessageDigest.isEqual(candidate, expected)
            if (equals && record.revokedAtMillis == null) match = record
            expected.fill(0)
        }
        candidate.fill(0)
        match?.publicClient()
    }

    override suspend fun revoke(id: String): Boolean = mutex.withLock {
        val index = mutableRecords.indexOfFirst { it.id == id && it.revokedAtMillis == null }
        if (index < 0) return@withLock false
        val next = mutableRecords.toMutableList()
        next[index] = next[index].copy(
            verifier = "",
            revokedAtMillis = clockMillis(),
        )
        persist(next)
        true
    }

    override suspend fun revokeAll() = mutex.withLock {
        persist(emptyList())
        tokenMac.deleteKey()
    }

    private suspend fun persist(next: List<ApiClientVerifierRecord>) {
        storage.save(next)
        mutableRecords.clear()
        mutableRecords.addAll(next)
        mutableClients.value = next.map { it.publicClient() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val TOKEN_BYTES = 32
        private const val MAX_TOKEN_CHARACTERS = 256
        private const val MAX_LABEL_CHARACTERS = 64
        private const val FINGERPRINT_HEX_CHARACTERS = 16

        suspend fun create(
            storage: ApiClientStorage,
            tokenMac: TokenMac,
            clockMillis: () -> Long = System::currentTimeMillis,
            fillRandom: (ByteArray) -> Unit = SecureRandom()::nextBytes,
        ): DefaultApiKeyStore = DefaultApiKeyStore(
            storage = storage,
            tokenMac = tokenMac,
            initialRecords = storage.load(),
            clockMillis = clockMillis,
            fillRandom = fillRandom,
        )
    }
}
