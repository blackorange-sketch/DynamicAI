// JNI bridge between DynamicAI (Kotlin) and llama.cpp.
// Uses only the public C API (llama.h), so it is independent of llama.cpp's "common" helpers.
// Strings travel as UTF-8 byte arrays in both directions: JNI's NewStringUTF/GetStringUTFChars use
// "modified UTF-8" and break (or abort) on emoji and other 4-byte characters.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <random>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "LlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_ctx = 0;
    int n_batch = 512;
    std::string tmpl;            // model's built-in chat template (may be empty)
    std::atomic<bool> stop{false};
};

bool g_backend_ready = false;

void log_cb(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, "llama.cpp", "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, "llama.cpp", "%s", text);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        __android_log_print(ANDROID_LOG_INFO, "llama.cpp", "%s", text);
    }
}

bool abort_cb(void * data) {
    auto * e = static_cast<Engine *>(data);
    return e != nullptr && e->stop.load();
}

std::string bytes_to_string(JNIEnv * env, jbyteArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

jbyteArray string_to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (arr != nullptr && !s.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    }
    return arr;
}

// Length of the longest prefix of `s` that does not end in the middle of a UTF-8 sequence.
size_t complete_utf8_prefix(const std::string & s) {
    const size_t n = s.size();
    // Look at most 3 bytes back for the start of a multi-byte sequence.
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const auto c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80) continue;          // continuation byte, keep looking
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return back >= need ? n : n - back;          // incomplete -> cut before it
    }
    return n;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool add_special) {
    int n = -llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, add_special, true);
    if (n <= 0) n = static_cast<int>(text.size()) + 8;
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    int got = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), n, add_special, true);
    if (got < 0) {
        tokens.resize(static_cast<size_t>(-got));
        got = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), -got, add_special, true);
    }
    tokens.resize(got > 0 ? static_cast<size_t>(got) : 0);
    return tokens;
}

std::string apply_template(const Engine * e, const std::string & user) {
    if (e->tmpl.empty()) return user;
    llama_chat_message msg{"user", user.c_str()};
    std::vector<char> buf(user.size() * 2 + 512);
    int n = llama_chat_apply_template(e->tmpl.c_str(), &msg, 1, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n < 0) {
        LOGW("chat template not supported by llama_chat_apply_template, using raw prompt");
        return user;
    }
    if (n > static_cast<int>(buf.size())) {
        buf.resize(static_cast<size_t>(n));
        n = llama_chat_apply_template(e->tmpl.c_str(), &msg, 1, true, buf.data(), static_cast<int32_t>(buf.size()));
        if (n < 0) return user;
    }
    return std::string(buf.data(), static_cast<size_t>(n));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dynamicai_launcher_llm_LlamaCppNative_nativeLoad(JNIEnv * env, jobject, jbyteArray jpath,
                                                          jint n_ctx, jint n_threads) {
    if (!g_backend_ready) {
        llama_log_set(log_cb, nullptr);
        llama_backend_init();
        g_backend_ready = true;
    }
    const std::string path = bytes_to_string(env, jpath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("failed to load model: %s", path.c_str());
        return 0;
    }

    auto * e = new Engine();
    e->model = model;
    e->vocab = llama_model_get_vocab(model);

    const int trained = llama_model_n_ctx_train(model);
    int ctx_size = n_ctx > 0 ? n_ctx : 4096;
    if (trained > 0 && ctx_size > trained) ctx_size = trained;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(ctx_size);
    cparams.n_batch = static_cast<uint32_t>(e->n_batch);
    cparams.n_ubatch = static_cast<uint32_t>(e->n_batch);
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.no_perf = true;
    e->ctx = llama_init_from_model(model, cparams);
    if (e->ctx == nullptr) {
        LOGE("failed to create context (n_ctx=%d)", ctx_size);
        llama_model_free(model);
        delete e;
        return 0;
    }
    llama_set_abort_callback(e->ctx, abort_cb, e);
    e->n_ctx = static_cast<int>(llama_n_ctx(e->ctx));

    const char * t = llama_model_chat_template(model, nullptr);
    if (t != nullptr) e->tmpl = t;

    char desc[256] = {0};
    llama_model_desc(model, desc, sizeof(desc));
    LOGI("model loaded: %s, n_ctx=%d, threads=%d, template=%s", desc, e->n_ctx, cparams.n_threads,
         e->tmpl.empty() ? "none" : "yes");
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT jint JNICALL
Java_com_dynamicai_launcher_llm_LlamaCppNative_nativeGenerate(JNIEnv * env, jobject, jlong handle,
                                                              jbyteArray jprompt, jint max_tokens,
                                                              jfloat temperature, jboolean use_template,
                                                              jobject callback) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e == nullptr || e->ctx == nullptr) return -1;
    e->stop.store(false);

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_bytes = env->GetMethodID(cb_cls, "onBytes", "([B)Z");
    if (on_bytes == nullptr) {
        LOGE("callback has no onBytes([B)Z");
        return -2;
    }

    const std::string user = bytes_to_string(env, jprompt);
    const std::string prompt = use_template ? apply_template(e, user) : user;
    std::vector<llama_token> tokens = tokenize(e->vocab, prompt, true);
    if (tokens.empty()) return -3;

    const int n_predict = std::max(1, static_cast<int>(max_tokens));
    const int budget = std::max(64, e->n_ctx - n_predict - 8);
    if (static_cast<int>(tokens.size()) > budget) {
        // Keep the first token (BOS) and the tail: the question and the assistant header are at the end.
        LOGW("prompt too long (%zu tokens), keeping the last %d", tokens.size(), budget);
        std::vector<llama_token> cut;
        cut.reserve(static_cast<size_t>(budget));
        cut.push_back(tokens.front());
        cut.insert(cut.end(), tokens.end() - (budget - 1), tokens.end());
        tokens.swap(cut);
    }

    llama_memory_clear(llama_get_memory(e->ctx), true);

    // Prompt processing in chunks of n_batch.
    for (size_t i = 0; i < tokens.size(); i += static_cast<size_t>(e->n_batch)) {
        const int n = static_cast<int>(std::min(tokens.size() - i, static_cast<size_t>(e->n_batch)));
        const int rc = llama_decode(e->ctx, llama_batch_get_one(tokens.data() + i, n));
        if (rc != 0) {
            LOGE("prompt decode failed rc=%d", rc);
            return e->stop.load() ? 0 : -4;
        }
    }

    // Sampler chain.
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(e->vocab), 64, 1.1f, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        std::random_device rd;
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(rd()));
    }

    int produced = 0;
    int n_past = static_cast<int>(tokens.size());
    std::string pending;
    char piece[256];
    while (produced < n_predict && !e->stop.load()) {
        llama_token tok = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, tok)) break;

        const int n = llama_token_to_piece(e->vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) pending.append(piece, static_cast<size_t>(n));
        produced++;

        const size_t ready = complete_utf8_prefix(pending);
        if (ready > 0) {
            jbyteArray arr = string_to_bytes(env, pending.substr(0, ready));
            pending.erase(0, ready);
            const jboolean want_stop = env->CallBooleanMethod(callback, on_bytes, arr);
            env->DeleteLocalRef(arr);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                break;
            }
            if (want_stop) break;
        }

        if (n_past + 1 >= e->n_ctx) {
            LOGW("context full, stopping");
            break;
        }
        if (llama_decode(e->ctx, llama_batch_get_one(&tok, 1)) != 0) {
            if (!e->stop.load()) LOGE("token decode failed");
            break;
        }
        n_past++;
    }
    llama_sampler_free(smpl);
    return produced;
}

JNIEXPORT void JNICALL
Java_com_dynamicai_launcher_llm_LlamaCppNative_nativeStop(JNIEnv *, jobject, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e != nullptr) e->stop.store(true);
}

JNIEXPORT jbyteArray JNICALL
Java_com_dynamicai_launcher_llm_LlamaCppNative_nativeInfo(JNIEnv * env, jobject, jlong handle) {
    std::string info;
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e != nullptr && e->model != nullptr) {
        char desc[256] = {0};
        llama_model_desc(e->model, desc, sizeof(desc));
        info += desc;
        info += " | ctx " + std::to_string(e->n_ctx);
        info += e->tmpl.empty() ? " | no template" : " | template";
        info += "\n";
    }
    info += llama_print_system_info();
    return string_to_bytes(env, info);
}

JNIEXPORT void JNICALL
Java_com_dynamicai_launcher_llm_LlamaCppNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e == nullptr) return;
    if (e->ctx != nullptr) llama_free(e->ctx);
    if (e->model != nullptr) llama_model_free(e->model);
    delete e;
}

} // extern "C"
