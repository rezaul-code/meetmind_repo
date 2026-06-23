#include <jni.h>
#include <android/log.h>

#include "whisper.h"

#include <algorithm>
#include <sstream>
#include <string>
#include <thread>

#define TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

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
    LOGI("Loading model from %s", modelPathChars);
    LOGI("Received %d audio samples at %d Hz", static_cast<int>(audioLength), static_cast<int>(sampleRate));

    struct whisper_context *context = whisper_init_from_file_with_params(
            modelPathChars,
            whisper_context_default_params());

    if (context == nullptr) {
        LOGE("Failed to initialize whisper context");
        env->ReleaseFloatArrayElements(audioSamples, audioData, JNI_ABORT);
        env->ReleaseStringUTFChars(modelPath, modelPathChars);
        return nullptr;
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = nullptr;
    params.n_threads = std::max(1u, std::thread::hardware_concurrency());
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, audioData, audioLength);
    env->ReleaseFloatArrayElements(audioSamples, audioData, JNI_ABORT);
    env->ReleaseStringUTFChars(modelPath, modelPathChars);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        whisper_free(context);
        return nullptr;
    }

    std::string transcript = buildTranscript(context);
    whisper_free(context);

    LOGI("Transcription finished. Transcript length=%zu", transcript.size());
    return env->NewStringUTF(transcript.c_str());
}
