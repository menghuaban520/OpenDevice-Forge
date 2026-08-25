# llama.cpp source boundary

- Repository: `https://github.com/ggml-org/llama.cpp.git`
- Commit: `3737e41370da1830a44c663f9929a0f27591ffa6`
- License: MIT; the verbatim upstream text is in `LICENSE.llama.cpp`.
- Android floor: API 28.
- Android ABI: `arm64-v8a` only.
- Toolchain: Android NDK `29.0.14206865`, CMake `3.31.6`, C++17.

Pinned upstream references:

- Android NDK cross-build guidance: `https://github.com/ggml-org/llama.cpp/blob/3737e41370da1830a44c663f9929a0f27591ffa6/docs/android.md#cross-compile-cli-using-android-ndk`
- Public C API used by the wrapper: `https://github.com/ggml-org/llama.cpp/blob/3737e41370da1830a44c663f9929a0f27591ffa6/include/llama.h`
- Upstream license: `https://github.com/ggml-org/llama.cpp/blob/3737e41370da1830a44c663f9929a0f27591ffa6/LICENSE`

The app links the upstream `llama` target statically into one JNI shared library.
It does not build the upstream CLI, server, examples, tests, common utilities,
OpenMP, llamafile, KleidiAI, GPU backends, or dynamic CPU variants. The wrapper
uses the fixed commit's public C API for model/context ownership, model chat
templates, tokenization, decoding, sampling, cancellation, and cleanup.

The upstream Android example targets a newer app surface and is not included.
This project follows the pinned source's NDK cross-compilation boundary with
`ANDROID_PLATFORM=android-28`, while retaining the project's Android 9 floor.

Verification:

```text
git -C apps/android-node/inference/third_party/llama.cpp rev-parse HEAD
```

must print exactly:

```text
3737e41370da1830a44c663f9929a0f27591ffa6
```
