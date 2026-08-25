package dev.opendevice.node.model

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
}
