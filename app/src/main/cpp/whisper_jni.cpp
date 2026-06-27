#include <jni.h>
#include <android/log.h>

#include "whisper.h"

#include <algorithm>
#include <chrono>
#include <sstream>
#include <string>
#include <thread>
#include <sys/stat.h>

#define TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void whisperProgressCallback(struct whisper_context * /*context*/,
                                    struct whisper_state * /*state*/,
                                    int progress,
                                    void * /*userData*/) {
    LOGI("whisper.cpp progress callback: %d%%", progress);
}

static long long getFileSizeBytes(const char *path) {
    struct stat fileStat{};
    if (path == nullptr) {
        return -1;
    }
    if (stat(path, &fileStat) != 0) {
        return -1;
    }
    return static_cast<long long>(fileStat.st_size);
}

static std::string buildTranscript(struct whisper_context *context) {
    const int segmentCount = whisper_full_n_segments(context);
    std::ostringstream transcript;
    for (int index = 0; index < segmentCount; ++index) {
        const char *segmentText = whisper_full_get_segment_text(context, index);
        if (segmentText != nullptr) {
            transcript << segmentText;
        }
    }
    return transcript.str();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_office_meetmind_whisper_WhisperManager_nativeTranscribe(
        JNIEnv *env,
        jclass,
        jstring modelPath,
        jfloatArray audioSamples,
        jint sampleRate) {
    LOGI("Entered native code on thread %zu", std::hash<std::thread::id>{}(std::this_thread::get_id()));
    if (modelPath == nullptr || audioSamples == nullptr) {
        LOGE("Missing model path or audio samples");
        return nullptr;
    }

    const char *modelPathChars = env->GetStringUTFChars(modelPath, nullptr);
    if (modelPathChars == nullptr) {
        LOGE("Unable to read model path");
        return nullptr;
    }

    jfloat *audioData = env->GetFloatArrayElements(audioSamples, nullptr);
    if (audioData == nullptr) {
        env->ReleaseStringUTFChars(modelPath, modelPathChars);
        LOGE("Unable to read audio samples");
        return nullptr;
    }

    const jsize audioLength = env->GetArrayLength(audioSamples);
    if (audioLength <= 0) {
        LOGE("Audio length is zero");
        env->ReleaseFloatArrayElements(audioSamples, audioData, JNI_ABORT);
        env->ReleaseStringUTFChars(modelPath, modelPathChars);
        return nullptr;
    }
    LOGI("Loading model from %s", modelPathChars);
    LOGI("Model file size from native stat() = %lld bytes", getFileSizeBytes(modelPathChars));
    LOGI("Received %d audio samples at %d Hz", static_cast<int>(audioLength), static_cast<int>(sampleRate));

    const auto initStart = std::chrono::steady_clock::now();
    struct whisper_context *context = whisper_init_from_file_with_params(
            modelPathChars,
            whisper_context_default_params());

    if (context == nullptr) {
        LOGE("Failed to initialize whisper context");
        env->ReleaseFloatArrayElements(audioSamples, audioData, JNI_ABORT);
        env->ReleaseStringUTFChars(modelPath, modelPathChars);
        return nullptr;
    }
    const auto initDurationMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - initStart).count();
    LOGI("whisper_init success after %lld ms", static_cast<long long>(initDurationMs));

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = nullptr;
    params.n_threads = 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;
    params.progress_callback = whisperProgressCallback;
    params.progress_callback_user_data = nullptr;

    whisper_reset_timings(context);

    LOGI("======================================");
    LOGI("whisper_full started");
    LOGI("Model : %s", modelPathChars);
    LOGI("Samples : %d", (int) audioLength);
    LOGI("Sample Rate : %d", (int) sampleRate);
    LOGI("Threads : %d", params.n_threads);
    LOGI("======================================");

    const auto fullStart = std::chrono::steady_clock::now();
    const int result = whisper_full(
            context,
            params,
            audioData,
            audioLength
    );
    const auto fullDurationMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - fullStart).count();

    LOGI("======================================");
    LOGI("whisper_full finished");
    LOGI("Result = %d", result);
    LOGI("whisper_full duration = %lld ms", static_cast<long long>(fullDurationMs));
    LOGI("======================================");
    env->ReleaseFloatArrayElements(audioSamples, audioData, JNI_ABORT);
    env->ReleaseStringUTFChars(modelPath, modelPathChars);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        whisper_free(context);
        return nullptr;
    }

    std::string transcript = buildTranscript(context);
    whisper_free(context);

    LOGI("Returning transcript. Transcript length=%zu", transcript.size());
    return env->NewStringUTF(transcript.c_str());
}
