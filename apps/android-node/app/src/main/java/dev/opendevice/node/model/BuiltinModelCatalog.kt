package dev.opendevice.node.model

import dev.opendevice.node.device.DeviceFacts

object BuiltinModelCatalog {
    val qwen3_0_6b = ModelDescriptor(
        id = "qwen3-0.6b-q8_0",
        displayName = "Qwen3 0.6B Q8_0",
        revision = "23749fefcc72300e3a2ad315e1317431b06b590a",
        fileName = "Qwen3-0.6B-Q8_0.gguf",
        url = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/" +
            "23749fefcc72300e3a2ad315e1317431b06b590a/" +
            "Qwen3-0.6B-Q8_0.gguf?download=true",
        sizeBytes = 639_446_688L,
        sha256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
        license = "Apache-2.0",
    )

    val qwen3_0_6b_q4_0 = ModelDescriptor(
        id = "qwen3-0.6b-q4_0",
        displayName = "Qwen3 0.6B Q4_0",
        revision = "b5f37287796e5be0ea3dab2e7430873fb3f73e49",
        fileName = "Qwen3-0.6B-Q4_0.gguf",
        url = "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/" +
            "b5f37287796e5be0ea3dab2e7430873fb3f73e49/" +
            "Qwen3-0.6B-Q4_0.gguf?download=true",
        sizeBytes = 428_970_080L,
        sha256 = "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4",
        license = "Apache-2.0",
    )

    val qwen3_5_0_8b_q4_0 = ModelDescriptor(
        id = "qwen3.5-0.8b-q4_0",
        displayName = "Qwen3.5 0.8B Q4_0",
        revision = "8fea620810c4afa23dd6443f999a48574c1611a3",
        fileName = "Qwen3.5-0.8B-Q4_0.gguf",
        url = "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/" +
            "8fea620810c4afa23dd6443f999a48574c1611a3/" +
            "Qwen3.5-0.8B-Q4_0.gguf?download=true",
        sizeBytes = 563_036_064L,
        sha256 = "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
        license = "Apache-2.0",
    )

    fun recommend(facts: DeviceFacts): ModelDescriptor {
        val hasEnoughMemory = facts.totalMemoryBytes?.let { it >= VERIFIED_PROFILE_MIN_MEMORY }
            ?: false
        val hasEnoughStorage = facts.allocatableStorageBytes?.let {
            it >= qwen3_0_6b.sizeBytes + DOWNLOAD_STORAGE_RESERVE
        } ?: false
        return if (hasEnoughMemory && hasEnoughStorage) {
            qwen3_0_6b
        } else {
            qwen3_0_6b_q4_0
        }
    }

    private const val VERIFIED_PROFILE_MIN_MEMORY = 6L * 1_073_741_824L
    private const val DOWNLOAD_STORAGE_RESERVE = 256L * 1_024L * 1_024L
}
