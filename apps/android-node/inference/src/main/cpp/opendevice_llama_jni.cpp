#include <jni.h>

#include "llama.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

constexpr jint kCancelled = -2;
constexpr jint kPromptTooLong = -3;
constexpr jint kDecodeFailed = -4;
constexpr jint kTokenizeFailed = -5;
constexpr jint kCallbackFailed = -6;

struct Engine {
    std::mutex mutex;
    std::atomic<bool> cancelled{false};
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    int32_t context_size = 0;
};

std::mutex g_engines_mutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> g_engines;
jlong g_next_handle = 1;
int g_backend_users = 0;

void throw_java(JNIEnv * env, const char * class_name, const char * message) {
    const jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
        env->DeleteLocalRef(exception_class);
    }
}

void throw_illegal_state(JNIEnv * env, const char * message) {
    throw_java(env, "java/lang/IllegalStateException", message);
}

void throw_illegal_argument(JNIEnv * env, const char * message) {
    throw_java(env, "java/lang/IllegalArgumentException", message);
}

void append_utf8(std::string & output, uint32_t code_point) {
    if (code_point <= 0x7f) {
        output.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7ff) {
        output.push_back(static_cast<char>(0xc0 | (code_point >> 6)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    } else if (code_point <= 0xffff) {
        output.push_back(static_cast<char>(0xe0 | (code_point >> 12)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    } else {
        output.push_back(static_cast<char>(0xf0 | (code_point >> 18)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 12) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    }
}

bool jstring_to_utf8(JNIEnv * env, jstring value, std::string & output) {
    if (value == nullptr) return false;
    const jsize length = env->GetStringLength(value);
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return false;
    output.clear();
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize index = 0; index < length; ++index) {
        uint32_t code_point = chars[index];
        if (code_point >= 0xd800 && code_point <= 0xdbff && index + 1 < length) {
            const uint32_t low = chars[index + 1];
            if (low >= 0xdc00 && low <= 0xdfff) {
                code_point = 0x10000 + ((code_point - 0xd800) << 10) + (low - 0xdc00);
                ++index;
            } else {
                code_point = 0xfffd;
            }
        } else if (code_point >= 0xdc00 && code_point <= 0xdfff) {
            code_point = 0xfffd;
        }
        append_utf8(output, code_point);
    }
    env->ReleaseStringChars(value, chars);
    return true;
}

jstring utf8_to_jstring(JNIEnv * env, const std::string & input) {
    std::vector<jchar> output;
    output.reserve(input.size());
    size_t index = 0;
    while (index < input.size()) {
        const uint8_t first = static_cast<uint8_t>(input[index]);
        uint32_t code_point = 0xfffd;
        size_t width = 1;
        if (first <= 0x7f) {
            code_point = first;
        } else if ((first & 0xe0) == 0xc0 && index + 1 < input.size()) {
            width = 2;
            code_point = first & 0x1f;
        } else if ((first & 0xf0) == 0xe0 && index + 2 < input.size()) {
            width = 3;
            code_point = first & 0x0f;
        } else if ((first & 0xf8) == 0xf0 && index + 3 < input.size()) {
            width = 4;
            code_point = first & 0x07;
        }

        bool valid = width > 1 || first <= 0x7f;
        for (size_t offset = 1; valid && offset < width; ++offset) {
            const uint8_t continuation = static_cast<uint8_t>(input[index + offset]);
            valid = (continuation & 0xc0) == 0x80;
            code_point = (code_point << 6) | (continuation & 0x3f);
        }
        if (!valid || code_point > 0x10ffff ||
            (code_point >= 0xd800 && code_point <= 0xdfff)) {
            code_point = 0xfffd;
            width = 1;
        }

        if (code_point <= 0xffff) {
            output.push_back(static_cast<jchar>(code_point));
        } else {
            code_point -= 0x10000;
            output.push_back(static_cast<jchar>(0xd800 + (code_point >> 10)));
            output.push_back(static_cast<jchar>(0xdc00 + (code_point & 0x3ff)));
        }
        index += width;
    }
    return env->NewString(output.data(), static_cast<jsize>(output.size()));
}

size_t complete_utf8_prefix(const std::string & input) {
    size_t index = 0;
    while (index < input.size()) {
        const uint8_t first = static_cast<uint8_t>(input[index]);
        size_t width = 1;
        if (first <= 0x7f) {
            width = 1;
        } else if ((first & 0xe0) == 0xc0) {
            width = 2;
        } else if ((first & 0xf0) == 0xe0) {
            width = 3;
        } else if ((first & 0xf8) == 0xf0) {
            width = 4;
        }
        if (index + width > input.size()) break;
        bool valid = true;
        for (size_t offset = 1; offset < width; ++offset) {
            const uint8_t continuation = static_cast<uint8_t>(input[index + offset]);
            if ((continuation & 0xc0) != 0x80) {
                valid = false;
                break;
            }
        }
        index += valid ? width : 1;
    }
    return index;
}

std::shared_ptr<Engine> get_engine(jlong handle) {
    std::lock_guard<std::mutex> lock(g_engines_mutex);
    const auto found = g_engines.find(handle);
    return found == g_engines.end() ? nullptr : found->second;
}

void acquire_backend() {
    std::lock_guard<std::mutex> lock(g_engines_mutex);
    if (g_backend_users == 0) llama_backend_init();
    ++g_backend_users;
}

void release_backend() {
    std::lock_guard<std::mutex> lock(g_engines_mutex);
    if (g_backend_users <= 0) return;
    --g_backend_users;
    if (g_backend_users == 0) llama_backend_free();
}

void free_engine(Engine & engine) {
    if (engine.sampler != nullptr) {
        llama_sampler_free(engine.sampler);
        engine.sampler = nullptr;
    }
    if (engine.context != nullptr) {
        llama_free(engine.context);
        engine.context = nullptr;
    }
    if (engine.model != nullptr) {
        llama_model_free(engine.model);
        engine.model = nullptr;
    }
}

bool token_piece(const llama_vocab * vocab, llama_token token, std::string & output) {
    std::vector<char> buffer(256);
    int32_t size = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        false
    );
    if (size < 0) {
        buffer.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            false
        );
    }
    if (size < 0) return false;
    output.assign(buffer.data(), static_cast<size_t>(size));
    return true;
}

bool send_token(
    JNIEnv * env,
    jobject callback,
    jmethodID on_token,
    const std::string & text,
    jint token_count
) {
    jstring java_text = utf8_to_jstring(env, text);
    if (java_text == nullptr) return false;
    env->CallVoidMethod(callback, on_token, java_text, token_count);
    env->DeleteLocalRef(java_text);
    return !env->ExceptionCheck();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_opendevice_node_inference_LlamaCppInferenceEngine_nativeLoad(
    JNIEnv * env,
    jobject,
    jstring path,
    jint context_size,
    jint threads,
    jobject load_control
) {
    if (context_size != 2048 || threads < 2 || threads > 4) {
        throw_illegal_argument(env, "invalid_native_load_options");
        return 0;
    }
    std::string model_path;
    if (!jstring_to_utf8(env, path, model_path) || model_path.empty()) {
        throw_illegal_argument(env, "invalid_model_path");
        return 0;
    }

    if (load_control == nullptr) {
        throw_illegal_argument(env, "load_control_missing");
        return 0;
    }
    const jclass control_class = env->GetObjectClass(load_control);
    if (control_class == nullptr) return 0;
    const jmethodID should_continue = env->GetMethodID(control_class, "shouldContinue", "()Z");
    env->DeleteLocalRef(control_class);
    if (should_continue == nullptr) return 0;

    acquire_backend();
    auto engine = std::make_shared<Engine>();
    engine->context_size = context_size;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    // The pinned llama-model-loader calls this on the loading thread, between
    // tensor loads. Do not retain JNIEnv/local references beyond this JNI call.
    struct LoadProgress { JNIEnv * env; jobject control; jmethodID method; };
    LoadProgress progress{env, load_control, should_continue};
    model_params.progress_callback_user_data = &progress;
    model_params.progress_callback = [](float, void * data) -> bool {
        const auto * progress = static_cast<LoadProgress *>(data);
        const jboolean active = progress->env->CallBooleanMethod(progress->control, progress->method);
        return !progress->env->ExceptionCheck() && active == JNI_TRUE;
    };
    engine->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (engine->model == nullptr) {
        release_backend();
        throw_illegal_state(env, "model_load_failed");
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    context_params.n_batch = static_cast<uint32_t>(context_size);
    context_params.n_ubatch = 512;
    context_params.n_seq_max = 1;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.no_perf = true;
    context_params.abort_callback = [](void * data) -> bool {
        return static_cast<std::atomic<bool> *>(data)->load(std::memory_order_relaxed);
    };
    context_params.abort_callback_data = &engine->cancelled;
    engine->context = llama_init_from_model(engine->model, context_params);
    if (engine->context == nullptr) {
        free_engine(*engine);
        release_backend();
        throw_illegal_state(env, "context_create_failed");
        return 0;
    }

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    const jlong handle = g_next_handle++;
    g_engines.emplace(handle, std::move(engine));
    return handle;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_opendevice_node_inference_LlamaCppInferenceEngine_nativeRenderChat(
    JNIEnv * env,
    jobject,
    jlong handle,
    jobjectArray roles,
    jobjectArray contents
) {
    const auto engine = get_engine(handle);
    if (engine == nullptr) {
        throw_illegal_state(env, "invalid_native_handle");
        return nullptr;
    }
    if (roles == nullptr || contents == nullptr) {
        throw_illegal_argument(env, "messages_missing");
        return nullptr;
    }
    const jsize count = env->GetArrayLength(roles);
    if (count <= 0 || count != env->GetArrayLength(contents)) {
        throw_illegal_argument(env, "messages_invalid");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(engine->mutex);
    std::vector<std::string> role_values;
    std::vector<std::string> content_values;
    role_values.reserve(static_cast<size_t>(count));
    content_values.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, index));
        auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, index));
        std::string role_value;
        std::string content_value;
        const bool converted = jstring_to_utf8(env, role, role_value) &&
            jstring_to_utf8(env, content, content_value);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
        if (!converted) {
            throw_illegal_argument(env, "message_encoding_failed");
            return nullptr;
        }
        role_values.push_back(std::move(role_value));
        content_values.push_back(std::move(content_value));
    }

    std::vector<llama_chat_message> chat;
    chat.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        chat.push_back({
            role_values[static_cast<size_t>(index)].c_str(),
            content_values[static_cast<size_t>(index)].c_str(),
        });
    }
    const char * chat_template = llama_model_chat_template(engine->model, nullptr);
    if (chat_template == nullptr) {
        throw_illegal_state(env, "chat_template_unavailable");
        return nullptr;
    }
    const int32_t required = llama_chat_apply_template(
        chat_template,
        chat.data(),
        chat.size(),
        true,
        nullptr,
        0
    );
    if (required < 0) {
        throw_illegal_state(env, "chat_template_failed");
        return nullptr;
    }
    std::vector<char> formatted(static_cast<size_t>(required) + 1);
    const int32_t written = llama_chat_apply_template(
        chat_template,
        chat.data(),
        chat.size(),
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );
    if (written < 0 || written > required) {
        throw_illegal_state(env, "chat_template_failed");
        return nullptr;
    }
    return utf8_to_jstring(
        env,
        std::string(formatted.data(), static_cast<size_t>(written))
    );
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_opendevice_node_inference_LlamaCppInferenceEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jobject callback
) {
    const auto engine = get_engine(handle);
    if (engine == nullptr) {
        throw_illegal_state(env, "invalid_native_handle");
        return kDecodeFailed;
    }
    if (max_tokens < 1 || max_tokens > 512 || !std::isfinite(temperature) ||
        temperature < 0.0f || temperature > 2.0f || callback == nullptr) {
        throw_illegal_argument(env, "invalid_generation_options");
        return kDecodeFailed;
    }
    std::string prompt_text;
    if (!jstring_to_utf8(env, prompt, prompt_text) ||
        prompt_text.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max())) {
        return kTokenizeFailed;
    }

    const jclass callback_class = env->GetObjectClass(callback);
    if (callback_class == nullptr) return kCallbackFailed;
    const jmethodID on_token = env->GetMethodID(
        callback_class,
        "onToken",
        "(Ljava/lang/String;I)V"
    );
    env->DeleteLocalRef(callback_class);
    if (on_token == nullptr) return kCallbackFailed;

    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->cancelled.store(false, std::memory_order_relaxed);
    llama_memory_t memory = llama_get_memory(engine->context);
    if (memory == nullptr) return kDecodeFailed;
    llama_memory_clear(memory, true);

    if (engine->sampler != nullptr) llama_sampler_free(engine->sampler);
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    engine->sampler = llama_sampler_chain_init(sampler_params);
    if (engine->sampler == nullptr) return kDecodeFailed;
    if (temperature <= 0.0f) {
        llama_sampler * greedy = llama_sampler_init_greedy();
        if (greedy == nullptr) return kDecodeFailed;
        llama_sampler_chain_add(engine->sampler, greedy);
    } else {
        llama_sampler * temperature_sampler = llama_sampler_init_temp(temperature);
        llama_sampler * distribution_sampler = llama_sampler_init_dist(LLAMA_DEFAULT_SEED);
        if (temperature_sampler == nullptr || distribution_sampler == nullptr) {
            if (temperature_sampler != nullptr) llama_sampler_free(temperature_sampler);
            if (distribution_sampler != nullptr) llama_sampler_free(distribution_sampler);
            return kDecodeFailed;
        }
        llama_sampler_chain_add(engine->sampler, temperature_sampler);
        llama_sampler_chain_add(engine->sampler, distribution_sampler);
    }

    const llama_vocab * vocab = llama_model_get_vocab(engine->model);
    if (vocab == nullptr) return kDecodeFailed;
    const int32_t tokenized = llama_tokenize(
        vocab,
        prompt_text.c_str(),
        static_cast<int32_t>(prompt_text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (tokenized == std::numeric_limits<int32_t>::min() || tokenized >= 0) {
        return kTokenizeFailed;
    }
    const int32_t prompt_tokens_count = -tokenized;
    if (prompt_tokens_count <= 0) return kTokenizeFailed;
    if (prompt_tokens_count + max_tokens > engine->context_size) return kPromptTooLong;

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(prompt_tokens_count));
    const int32_t actual_tokens = llama_tokenize(
        vocab,
        prompt_text.c_str(),
        static_cast<int32_t>(prompt_text.size()),
        prompt_tokens.data(),
        prompt_tokens_count,
        true,
        true
    );
    if (actual_tokens != prompt_tokens_count) return kTokenizeFailed;
    if (engine->cancelled.load(std::memory_order_relaxed)) return kCancelled;

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens_count);
    const int32_t prompt_decode = llama_decode(engine->context, batch);
    if (engine->cancelled.load(std::memory_order_relaxed)) return kCancelled;
    if (prompt_decode != 0) return kDecodeFailed;

    std::string pending_utf8;
    jint pending_token_count = 0;
    for (jint generated = 0; generated < max_tokens; ++generated) {
        if (engine->cancelled.load(std::memory_order_relaxed)) return kCancelled;
        const llama_token token = llama_sampler_sample(engine->sampler, engine->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        std::string piece;
        if (!token_piece(vocab, token, piece)) return kDecodeFailed;
        pending_utf8 += piece;
        ++pending_token_count;
        const size_t complete = complete_utf8_prefix(pending_utf8);
        if (complete > 0) {
            if (!send_token(
                    env,
                    callback,
                    on_token,
                    pending_utf8.substr(0, complete),
                    pending_token_count
                )) {
                return kCallbackFailed;
            }
            pending_utf8.erase(0, complete);
            pending_token_count = 0;
        }

        if (generated + 1 < max_tokens) {
            llama_token next = token;
            batch = llama_batch_get_one(&next, 1);
            const int32_t decode_result = llama_decode(engine->context, batch);
            if (engine->cancelled.load(std::memory_order_relaxed)) return kCancelled;
            if (decode_result != 0) return kDecodeFailed;
        }
    }

    if (!pending_utf8.empty() &&
        !send_token(env, callback, on_token, pending_utf8, pending_token_count)) {
        return kCallbackFailed;
    }
    if (pending_utf8.empty() && pending_token_count > 0 &&
        !send_token(env, callback, on_token, std::string(), pending_token_count)) {
        return kCallbackFailed;
    }
    return prompt_tokens_count;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_opendevice_node_inference_LlamaCppInferenceEngine_nativeCancel(
    JNIEnv *,
    jobject,
    jlong handle
) {
    const auto engine = get_engine(handle);
    if (engine != nullptr) engine->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_opendevice_node_inference_LlamaCppInferenceEngine_nativeClose(
    JNIEnv *,
    jobject,
    jlong handle
) {
    std::shared_ptr<Engine> engine;
    {
        std::lock_guard<std::mutex> lock(g_engines_mutex);
        const auto found = g_engines.find(handle);
        if (found == g_engines.end()) return;
        engine = found->second;
        g_engines.erase(found);
    }
    engine->cancelled.store(true, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(engine->mutex);
        free_engine(*engine);
    }
    release_backend();
}
