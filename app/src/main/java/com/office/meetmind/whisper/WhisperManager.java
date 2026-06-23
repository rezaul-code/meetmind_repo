package com.office.meetmind.whisper;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.office.meetmind.R;
import com.office.meetmind.RecordingModel;
import com.office.meetmind.RecordingRepository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WhisperManager {

    public static final String TAG = "WhisperManager";
    private static final String MODEL_ASSET_NAME = "ggml-base.bin";
    private static final long MIN_MODEL_SIZE_BYTES = 100L * 1024L * 1024L;
    private static final String WHISPER_LIBRARY_NAME = "whisper";
    private static final String WHISPER_FALLBACK_LIBRARY_NAME = "whisper_jni";
    private static final String[] MODEL_ASSET_CANDIDATES = {
            MODEL_ASSET_NAME,
            "whisper/" + MODEL_ASSET_NAME
    };

    private static final boolean NATIVE_LIBRARY_AVAILABLE;
    private static final String LOADED_NATIVE_LIBRARY_NAME;
    private static final String NATIVE_LOAD_FAILURE_MESSAGE;
    private static volatile StartupReport latestStartupReport;

    static {
        boolean loaded;
        String loadedLibraryName = null;
        String failureMessage = null;
        try {
            System.loadLibrary(WHISPER_LIBRARY_NAME);
            loaded = true;
            loadedLibraryName = WHISPER_LIBRARY_NAME;
            Log.d(TAG, "Native library loaded: lib" + WHISPER_LIBRARY_NAME + ".so");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Native library load failed for lib" + WHISPER_LIBRARY_NAME + ".so", error);
            try {
                System.loadLibrary(WHISPER_FALLBACK_LIBRARY_NAME);
                loaded = true;
                loadedLibraryName = WHISPER_FALLBACK_LIBRARY_NAME;
                failureMessage = "libwhisper.so was not found, but libwhisper_jni.so loaded successfully.";
                Log.d(TAG, "Fallback native library loaded: lib" + WHISPER_FALLBACK_LIBRARY_NAME + ".so");
            } catch (Throwable fallbackError) {
                loaded = false;
                failureMessage = buildNativeLoadFailureMessage(error, fallbackError);
                Log.e(TAG, "Native library load failed for fallback lib" + WHISPER_FALLBACK_LIBRARY_NAME + ".so", fallbackError);
            }
        }
        NATIVE_LIBRARY_AVAILABLE = loaded;
        LOADED_NATIVE_LIBRARY_NAME = loadedLibraryName;
        NATIVE_LOAD_FAILURE_MESSAGE = failureMessage;
    }

    private final Context appContext;
    private final RecordingRepository recordingRepository = new RecordingRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public WhisperManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static void logStartupModelCheck(Context context) {
        WhisperManager manager = new WhisperManager(context);
        StartupReport report = manager.performStartupVerification();
        Log.i(TAG, "Startup verification ready: " + report.isReady());
        Log.i(TAG, "Startup verification native library available: " + report.nativeLibraryLoaded);
        Log.i(TAG, "Startup verification loaded native library: " + report.loadedNativeLibraryName);
        Log.i(TAG, "Startup verification native load failure message: " + report.nativeLoadFailureMessage);
        Log.i(TAG, "Startup verification asset model path: " + report.assetModelPath);
        Log.i(TAG, "Startup verification runtime model path: " + report.runtimeModelPath);
        manager.listAllAssets();
    }

    public boolean isModelAvailable() {
        Log.i(TAG, "Validating Whisper model availability");
        Log.i(TAG, "Asset candidates: " + Arrays.toString(MODEL_ASSET_CANDIDATES));
        Log.i(TAG, "App internal files directory: " + appContext.getFilesDir().getAbsolutePath());
        String resolvedAssetPath = resolveModelAssetPath();
        Log.i(TAG, "Model availability resolved asset path: " + resolvedAssetPath);
        return resolvedAssetPath != null;
    }

    private String resolveModelAssetPath() {
        AssetManager assetManager = appContext.getAssets();
        for (String assetPath : MODEL_ASSET_CANDIDATES) {
            Log.i(TAG, "Checking model asset candidate: " + assetPath);

            Long descriptorSize = null;
            try (AssetFileDescriptor assetFileDescriptor = assetManager.openFd(assetPath)) {
                descriptorSize = assetFileDescriptor.getLength();
                Log.i(TAG, "AssetFileDescriptor opened for " + assetPath + ", size=" + descriptorSize + " bytes");
            } catch (IOException descriptorException) {
                Log.w(TAG, "AssetFileDescriptor unavailable for " + assetPath + ": " + descriptorException.getMessage());
            }

            long streamSize = 0L;
            try (InputStream inputStream = new BufferedInputStream(assetManager.open(assetPath))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    streamSize += bytesRead;
                }
                Log.i(TAG, "Asset stream readable for " + assetPath + ", size=" + streamSize + " bytes");
            } catch (IOException streamException) {
                Log.e(TAG, "Asset stream check failed for " + assetPath, streamException);
                continue;
            }

            long resolvedSize = descriptorSize != null ? descriptorSize : streamSize;
            if (resolvedSize < MIN_MODEL_SIZE_BYTES) {
                Log.e(TAG, "Model asset is too small at " + assetPath + ". Expected >= " + MIN_MODEL_SIZE_BYTES + " bytes but found " + resolvedSize);
                continue;
            }

            Log.i(TAG, "Model asset is available and valid: " + assetPath + " (" + resolvedSize + " bytes)");
            return assetPath;
        }
        Log.e(TAG, "Whisper model is missing from assets or below minimum size. Checked candidates=" + Arrays.toString(MODEL_ASSET_CANDIDATES));
        return null;
    }

    public StartupReport performStartupVerification() {
        StartupReport report = new StartupReport();
        report.modelAvailable = isModelAvailable();
        report.nativeLibraryLoaded = NATIVE_LIBRARY_AVAILABLE;
        report.loadedNativeLibraryName = LOADED_NATIVE_LIBRARY_NAME;
        report.nativeLoadFailureMessage = NATIVE_LOAD_FAILURE_MESSAGE;
        report.abiCompatible = isAbiCompatible();
        report.abiMessage = buildAbiMessage();
        report.storageWritable = isStorageWritable();
        report.storageMessage = buildStorageMessage();
        report.nativeLibraryDir = appContext.getApplicationInfo().nativeLibraryDir;
        report.assetModelPath = report.modelAvailable ? resolveModelAssetPath() : null;
        report.runtimeModelPath = new File(appContext.getFilesDir(), "whisper" + File.separator + MODEL_ASSET_NAME).getAbsolutePath();
        report.whisperSoPresent = isInstalledNativeLibraryPresent("whisper");
        report.whisperJniSoPresent = isInstalledNativeLibraryPresent("whisper_jni");
        report.modelMessage = report.modelAvailable
                ? "Whisper model is available."
                : "Whisper model not installed. Please place ggml-base.bin inside app/src/main/assets.";
        report.summary = buildSummary(report);
        latestStartupReport = report;

        Log.i(TAG, "Startup verification summary: " + report.summary);
        Log.i(TAG, "Startup verification native loaded: " + report.nativeLibraryLoaded + ", library=" + report.loadedNativeLibraryName);
        Log.i(TAG, "Startup verification nativeLibraryDir: " + report.nativeLibraryDir);
        Log.i(TAG, "Startup verification libwhisper.so present: " + report.whisperSoPresent);
        Log.i(TAG, "Startup verification libwhisper_jni.so present: " + report.whisperJniSoPresent);
        Log.i(TAG, "Startup verification ABI compatible: " + report.abiCompatible + " (" + report.abiMessage + ")");
        Log.i(TAG, "Startup verification storage writable: " + report.storageWritable + " (" + report.storageMessage + ")");
        Log.i(TAG, "Startup verification model available: " + report.modelAvailable + " (" + report.modelMessage + ")");
        Log.i(TAG, "Startup verification resolved asset model path: " + report.assetModelPath);
        Log.i(TAG, "Startup verification runtime model path: " + report.runtimeModelPath);
        return report;
    }

    public static StartupReport getLatestStartupReport() {
        return latestStartupReport;
    }

    public static String getRootCauseMessage(Context context) {
        StartupReport report = latestStartupReport;
        if (report == null && context != null) {
            report = new WhisperManager(context).performStartupVerification();
        }
        if (report == null) {
            return "Whisper initialization failed.";
        }
        return report.getPrimaryFailureMessage();
    }

    public void listAllAssets() {
        Log.i(TAG, "Listing all packaged assets");
        try {
            listAllAssetsRecursive("");
        } catch (IOException exception) {
            Log.e(TAG, "Unable to list assets", exception);
        }
    }

    public void transcribeRecording(String recordingFilePath, TranscriptionCallback callback) {
        cancelled.set(false);
        executorService.execute(() -> {
            try {
                dispatchStatus(callback, appContext.getString(R.string.transcript_loading_recording));
                RecordingModel recordingModel = recordingRepository.loadRecording(appContext, recordingFilePath);
                if (recordingModel == null) {
                    dispatchError(callback, appContext.getString(R.string.transcript_error_missing_recording));
                    return;
                }

                dispatchRecordingLoaded(callback, recordingModel.getDisplayName());

                File transcriptFile = getTranscriptFile(recordingModel);
                if (transcriptFile.exists() && transcriptFile.length() > 0L) {
                    Log.i(TAG, "Using saved transcript at " + transcriptFile.getAbsolutePath());
                    dispatchStatus(callback, appContext.getString(R.string.transcript_loading_saved));
                    dispatchProgress(callback, 100);
                    String transcriptText = readTextFile(transcriptFile);
                    dispatchCompleted(callback, transcriptText, transcriptFile);
                    return;
                }

                dispatchStatus(callback, appContext.getString(R.string.transcript_preparing_model));
                String resolvedAssetPath = resolveModelAssetPath();
                Log.i(TAG, "Transcript flow resolved asset path: " + resolvedAssetPath);
                if (resolvedAssetPath == null) {
                    dispatchError(callback, appContext.getString(R.string.transcript_error_model_not_installed));
                    return;
                }
                File modelFile = ensureModelFile(resolvedAssetPath, callback);
                if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
                    Log.e(TAG, "Runtime model file missing or empty after ensureModelFile: " + (modelFile == null ? "null" : modelFile.getAbsolutePath()));
                    dispatchError(callback, appContext.getString(R.string.transcript_error_model_not_installed));
                    return;
                }
                Log.i(TAG, "Runtime model file ready at " + modelFile.getAbsolutePath() + " (" + modelFile.length() + " bytes)");

                dispatchStatus(callback, appContext.getString(R.string.transcript_decoding_audio));
                DecodedAudio decodedAudio = decodeRecordingAudio(recordingModel.getFilePath());
                Log.i(TAG, "Decoded audio ready: sampleRate=" + decodedAudio.sampleRate + ", samples=" + decodedAudio.samples.length);

                dispatchStatus(callback, appContext.getString(R.string.transcript_transcribing));
                String transcriptText = transcribeWithNative(modelFile, decodedAudio, callback);
                if (transcriptText == null) {
                    dispatchError(callback, appContext.getString(R.string.transcript_error_transcription_failed));
                    return;
                }

                dispatchStatus(callback, appContext.getString(R.string.transcript_saving));
                writeTextFile(transcriptFile, transcriptText);
                dispatchProgress(callback, 100);
                dispatchCompleted(callback, transcriptText, transcriptFile);
            } catch (OutOfMemoryError error) {
                Log.e(TAG, "Out of memory while transcribing", error);
                dispatchError(callback, appContext.getString(R.string.transcript_error_memory));
            } catch (IOException error) {
                Log.e(TAG, "I/O failure during transcription", error);
                dispatchError(callback, appContext.getString(R.string.transcript_error_corrupted_audio));
            } catch (UnsatisfiedLinkError error) {
                Log.e(TAG, "Whisper native library missing", error);
                dispatchError(callback, getRootCauseMessage(appContext));
            } catch (RuntimeException error) {
                Log.e(TAG, "Runtime failure during transcription", error);
                dispatchError(callback, appContext.getString(R.string.transcript_error_corrupted_audio));
            }
        });
    }

    public void cancel() {
        cancelled.set(true);
        executorService.shutdownNow();
    }

    private String transcribeWithNative(File modelFile, DecodedAudio decodedAudio, TranscriptionCallback callback) {
        if (!NATIVE_LIBRARY_AVAILABLE) {
            throw new UnsatisfiedLinkError(getRootCauseMessage(appContext));
        }

        Log.i(TAG, "Starting transcription with model=" + modelFile.getAbsolutePath()
                + ", sampleRate=" + decodedAudio.sampleRate
                + ", samples=" + decodedAudio.samples.length);
        String transcriptText = nativeTranscribe(
                modelFile.getAbsolutePath(),
                decodedAudio.samples,
                decodedAudio.sampleRate
        );
        if (transcriptText == null) {
            Log.e(TAG, "Native transcription returned null");
            return null;
        }
        Log.i(TAG, "Transcription finished successfully");
        return transcriptText.trim();
    }

    private DecodedAudio decodeRecordingAudio(String audioFilePath) throws IOException {
        Log.i(TAG, "Decoding recording audio from " + audioFilePath);
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(audioFilePath);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("No audio track found");
            }

            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType == null) {
                throw new IOException("Audio track MIME type missing");
            }

            int sourceSampleRate = mediaFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    : 16000;
            int channelCount = mediaFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 1;

            Log.i(TAG, "Selected audio track " + trackIndex + ", mime=" + mimeType
                    + ", sampleRate=" + sourceSampleRate + ", channels=" + channelCount);

            codec = MediaCodec.createDecoderByType(mimeType);
            codec.configure(mediaFormat, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;

            while (!outputEnded) {
                if (!inputEnded) {
                    int inputIndex = codec.dequeueInputBuffer(10_000L);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Decoder input buffer unavailable");
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                            Log.i(TAG, "Audio extractor reached end of stream");
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    sourceSampleRate = outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            : sourceSampleRate;
                    channelCount = outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            : channelCount;
                    Log.i(TAG, "Decoder output format changed: sampleRate=" + sourceSampleRate
                            + ", channels=" + channelCount);
                    continue;
                }
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer == null) {
                        codec.releaseOutputBuffer(outputIndex, false);
                        continue;
                    }

                    if (bufferInfo.size > 0 && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        byte[] chunk = new byte[bufferInfo.size];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        outputBuffer.get(chunk);
                        pcmOutput.write(chunk);
                    }

                    codec.releaseOutputBuffer(outputIndex, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEnded = true;
                    }
                }
            }

            short[] pcmShorts = toShortArray(pcmOutput.toByteArray());
            float[] monoSamples = convertToMono(pcmShorts, channelCount);
            float[] resampledSamples = resample(monoSamples, sourceSampleRate, 16000);
            Log.i(TAG, "Decoded PCM samples=" + pcmShorts.length + ", monoSamples=" + monoSamples.length
                    + ", resampledSamples=" + resampledSamples.length);
            return new DecodedAudio(resampledSamples, 16000);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("Unable to decode recording audio", exception);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                try {
                    codec.release();
                } catch (Exception ignored) {
                }
            }
            extractor.release();
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int index = 0; index < trackCount; index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType != null && mimeType.startsWith("audio/")) {
                return index;
            }
        }
        return -1;
    }

    private short[] toShortArray(byte[] pcmBytes) {
        short[] shorts = new short[pcmBytes.length / 2];
        ByteBuffer.wrap(pcmBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(shorts);
        return shorts;
    }

    private float[] convertToMono(short[] pcmShorts, int channelCount) {
        if (pcmShorts.length == 0) {
            return new float[0];
        }
        int effectiveChannels = Math.max(1, channelCount);
        int sampleCount = pcmShorts.length / effectiveChannels;
        float[] mono = new float[sampleCount];
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            float mixedSample = 0f;
            for (int channelIndex = 0; channelIndex < effectiveChannels; channelIndex++) {
                int sourceIndex = sampleIndex * effectiveChannels + channelIndex;
                if (sourceIndex < pcmShorts.length) {
                    mixedSample += pcmShorts[sourceIndex] / 32768f;
                }
            }
            mono[sampleIndex] = mixedSample / effectiveChannels;
        }
        return mono;
    }

    private float[] resample(float[] samples, int sourceSampleRate, int targetSampleRate) {
        if (samples.length == 0 || sourceSampleRate <= 0 || sourceSampleRate == targetSampleRate) {
            return samples;
        }

        double ratio = sourceSampleRate / (double) targetSampleRate;
        int outputLength = Math.max(1, (int) Math.round(samples.length / ratio));
        float[] output = new float[outputLength];

        for (int index = 0; index < outputLength; index++) {
            double sourcePosition = index * ratio;
            int lowerIndex = (int) Math.floor(sourcePosition);
            int upperIndex = Math.min(samples.length - 1, lowerIndex + 1);
            double fraction = sourcePosition - lowerIndex;
            float lowerSample = samples[Math.min(lowerIndex, samples.length - 1)];
            float upperSample = samples[upperIndex];
            output[index] = (float) (lowerSample + (upperSample - lowerSample) * fraction);
        }

        return output;
    }

    private File ensureModelFile(String resolvedAssetPath, TranscriptionCallback callback) throws IOException {
        File whisperDirectory = new File(appContext.getFilesDir(), "whisper");
        Log.i(TAG, "Ensuring Whisper runtime directory: " + whisperDirectory.getAbsolutePath());
        if (!whisperDirectory.exists() && !whisperDirectory.mkdirs()) {
            throw new IOException("Unable to create Whisper storage directory");
        }

        File targetFile = new File(whisperDirectory, MODEL_ASSET_NAME);
        Log.i(TAG, "Model destination path: " + targetFile.getAbsolutePath());
        if (targetFile.exists() && targetFile.length() > 0L) {
            Log.i(TAG, "Model already present in internal storage: " + targetFile.length() + " bytes");
            return targetFile;
        }

        File tempFile = new File(whisperDirectory, MODEL_ASSET_NAME + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        IOException lastException = null;
        try {
            copyAssetToFile(resolvedAssetPath, tempFile, callback);
            replaceFile(tempFile, targetFile);
            Log.i(TAG, "Model copied to internal storage: " + targetFile.getAbsolutePath() + " (" + targetFile.length() + " bytes)");
            return targetFile;
        } catch (IOException exception) {
            lastException = exception;
            Log.e(TAG, "Failed copying model from asset path: " + resolvedAssetPath, exception);
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return null;
    }

    private void copyAssetToFile(String assetPath, File destinationFile, TranscriptionCallback callback) throws IOException {
        AssetManager assetManager = appContext.getAssets();
        long assetLength = -1L;

        Log.i(TAG, "Copying model from asset path: " + assetPath + " to " + destinationFile.getAbsolutePath());
        try (AssetFileDescriptor assetFileDescriptor = assetManager.openFd(assetPath)) {
            assetLength = assetFileDescriptor.getLength();
            Log.i(TAG, "AssetFileDescriptor available for copy: " + assetPath + ", size=" + assetLength + " bytes");
        } catch (IOException descriptorException) {
            Log.w(TAG, "AssetFileDescriptor unavailable for copy source " + assetPath + ": " + descriptorException.getMessage());
        }

        try (InputStream inputStream = new BufferedInputStream(assetManager.open(assetPath));
             FileOutputStream fileOutputStream = new FileOutputStream(destinationFile, false);
             BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream)) {
            Log.i(TAG, "Beginning model copy to " + destinationFile.getAbsolutePath());
            byte[] buffer = new byte[8192];
            long copiedBytes = 0L;
            long lastLoggedPercent = -1L;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled.get()) {
                    throw new IOException("Transcription cancelled");
                }
                outputStream.write(buffer, 0, bytesRead);
                copiedBytes += bytesRead;
                if (assetLength > 0L) {
                    int progress = (int) Math.min(99L, Math.max(0L, copiedBytes * 100L / assetLength));
                    if (progress != lastLoggedPercent) {
                        Log.i(TAG, "Copy progress: " + progress + "% (" + copiedBytes + "/" + assetLength + " bytes)");
                        lastLoggedPercent = progress;
                    }
                    dispatchProgress(callback, progress);
                }
            }
            outputStream.flush();
            Log.i(TAG, "Model copy completed. Copied bytes=" + copiedBytes + ", destination size=" + destinationFile.length());
            if (assetLength <= 0L) {
                Log.i(TAG, "Copy progress: completed (asset size unknown)");
                dispatchProgress(callback, 100);
            }
        }
    }

    private File getTranscriptFile(RecordingModel recordingModel) {
        File recordingFile = new File(recordingModel.getFilePath());
        File parentDirectory = recordingFile.getParentFile();
        String transcriptName = stripExtension(recordingFile.getName()) + ".txt";
        if (parentDirectory == null) {
            parentDirectory = new File(appContext.getFilesDir(), "transcripts");
            if (!parentDirectory.exists()) {
                parentDirectory.mkdirs();
            }
        }
        return new File(parentDirectory, transcriptName);
    }

    private String readTextFile(File file) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void writeTextFile(File file, String content) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new IOException("Unable to create transcript directory");
        }
        Log.i(TAG, "Saving transcript to " + file.getAbsolutePath());
        File tempFile = parentFile == null
                ? new File(appContext.getFilesDir(), file.getName() + ".tmp")
                : new File(parentFile, file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile, false), StandardCharsets.UTF_8))) {
            writer.write(content == null ? "" : content);
        }
        replaceFile(tempFile, file);
    }

    private void replaceFile(File sourceFile, File targetFile) throws IOException {
        if (targetFile.exists() && !targetFile.delete()) {
            throw new IOException("Unable to replace file: " + targetFile.getName());
        }
        Log.i(TAG, "Replacing file " + targetFile.getAbsolutePath() + " from temp source " + sourceFile.getAbsolutePath());
        if (!sourceFile.renameTo(targetFile)) {
            try (InputStream inputStream = new BufferedInputStream(new FileInputStream(sourceFile));
                 FileOutputStream outputStream = new FileOutputStream(targetFile, false);
                 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    bufferedOutputStream.write(buffer, 0, bytesRead);
                }
                bufferedOutputStream.flush();
            }
            if (!sourceFile.delete()) {
                throw new IOException("Unable to clean up temporary file");
            }
        }
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }

    private void dispatchRecordingLoaded(TranscriptionCallback callback, String recordingName) {
        if (callback == null || cancelled.get()) {
            return;
        }
        mainHandler.post(() -> {
            if (!cancelled.get()) {
                callback.onRecordingLoaded(recordingName);
            }
        });
    }

    private void dispatchProgress(TranscriptionCallback callback, int progress) {
        if (callback == null || cancelled.get()) {
            return;
        }
        int safeProgress = Math.max(0, Math.min(progress, 100));
        mainHandler.post(() -> {
            if (!cancelled.get()) {
                callback.onProgress(safeProgress);
            }
        });
    }

    private void dispatchStatus(TranscriptionCallback callback, String statusMessage) {
        if (callback == null || cancelled.get()) {
            return;
        }
        mainHandler.post(() -> {
            if (!cancelled.get()) {
                callback.onStatusChanged(statusMessage);
            }
        });
    }

    private void dispatchCompleted(TranscriptionCallback callback, String transcriptText, File transcriptFile) {
        if (callback == null || cancelled.get()) {
            return;
        }
        mainHandler.post(() -> {
            if (!cancelled.get()) {
                callback.onTranscriptionCompleted(transcriptText, transcriptFile);
            }
        });
    }

    private void dispatchError(TranscriptionCallback callback, String errorMessage) {
        if (callback == null || cancelled.get()) {
            return;
        }
        mainHandler.post(() -> {
            if (!cancelled.get()) {
                callback.onError(errorMessage);
            }
        });
    }

    private static native String nativeTranscribe(String modelPath, float[] audioSamples, int sampleRate);

    private void listAllAssetsRecursive(String path) throws IOException {
        String[] children = appContext.getAssets().list(path);
        if (children == null || children.length == 0) {
            if (path != null && !path.isEmpty()) {
                Log.i(TAG, "Asset file: " + path);
            }
            return;
        }
        for (String child : children) {
            String childPath = path == null || path.isEmpty() ? child : path + "/" + child;
            String[] nestedChildren = appContext.getAssets().list(childPath);
            if (nestedChildren != null && nestedChildren.length > 0) {
                Log.i(TAG, "Asset directory: " + childPath);
                listAllAssetsRecursive(childPath);
            } else {
                Log.i(TAG, "Asset file: " + childPath);
            }
        }
    }

    private boolean isAbiCompatible() {
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        Log.i(TAG, "Device supported ABIs: " + Arrays.toString(supportedAbis));
        if (supportedAbis == null || supportedAbis.length == 0) {
            return false;
        }
        return true;
    }

    private String buildAbiMessage() {
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        if (supportedAbis == null || supportedAbis.length == 0) {
            return "No supported ABIs reported by the device.";
        }
        return "Supported ABIs: " + Arrays.toString(supportedAbis);
    }

    private boolean isStorageWritable() {
        File testFile = new File(appContext.getFilesDir(), "whisper_storage_check.tmp");
        try (FileOutputStream outputStream = new FileOutputStream(testFile)) {
            outputStream.write(1);
            outputStream.flush();
            return true;
        } catch (IOException exception) {
            Log.e(TAG, "Storage verification failed", exception);
            return false;
        } finally {
            if (testFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                testFile.delete();
            }
        }
    }

    private String buildStorageMessage() {
        return "Internal storage path: " + appContext.getFilesDir().getAbsolutePath();
    }

    private boolean isInstalledNativeLibraryPresent(String libraryBaseName) {
        File nativeLibraryDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
        File libraryFile = new File(nativeLibraryDir, System.mapLibraryName(libraryBaseName));
        Log.i(TAG, "Checking installed native library: " + libraryFile.getAbsolutePath());
        return libraryFile.exists() && libraryFile.length() > 0L;
    }

    private static String buildNativeLoadFailureMessage(Throwable primaryError, Throwable fallbackError) {
        StringBuilder builder = new StringBuilder();
        builder.append("Failed to load libwhisper.so. ");
        builder.append("Place libwhisper.so inside app/src/main/jniLibs/<abi>/ or app/src/main/jniLibs/. ");
        builder.append("Primary error: ").append(primaryError.getClass().getSimpleName());
        if (primaryError.getMessage() != null) {
            builder.append(" - ").append(primaryError.getMessage());
        }
        builder.append(". Fallback libwhisper_jni.so also failed: ");
        builder.append(fallbackError.getClass().getSimpleName());
        if (fallbackError.getMessage() != null) {
            builder.append(" - ").append(fallbackError.getMessage());
        }
        return builder.toString();
    }

    private String buildSummary(StartupReport report) {
        if (report.isReady()) {
            return "Whisper is ready.";
        }
        return report.getPrimaryFailureMessage();
    }

    private final class NativeProgressListener {

        private final TranscriptionCallback callback;

        private NativeProgressListener(TranscriptionCallback callback) {
            this.callback = callback;
        }

        @SuppressWarnings("unused")
        public void onProgress(int progress) {
            if (callback == null) {
                return;
            }
            int safeProgress = Math.max(0, Math.min(progress, 100));
            dispatchProgress(callback, safeProgress);
        }
    }

    private static final class DecodedAudio {
        private final float[] samples;
        private final int sampleRate;

        private DecodedAudio(float[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    public static final class StartupReport {

        private boolean nativeLibraryLoaded;
        private boolean modelAvailable;
        private boolean abiCompatible;
        private boolean storageWritable;
        private String loadedNativeLibraryName;
        private String nativeLoadFailureMessage;
        private String modelMessage;
        private String abiMessage;
        private String storageMessage;
        private String summary;
        private String nativeLibraryDir;
        private String assetModelPath;
        private String runtimeModelPath;
        private boolean whisperSoPresent;
        private boolean whisperJniSoPresent;

        public boolean isReady() {
            return nativeLibraryLoaded && modelAvailable && abiCompatible && storageWritable;
        }

        public String getPrimaryFailureMessage() {
            if (!nativeLibraryLoaded) {
                return nativeLoadFailureMessage != null
                        ? nativeLoadFailureMessage
                        : "Failed to load libwhisper.so.";
            }
            if (!modelAvailable) {
                return modelMessage != null ? modelMessage : "Whisper model not installed.";
            }
            if (!abiCompatible) {
                return abiMessage != null ? abiMessage : "ABI not supported.";
            }
            if (!storageWritable) {
                return storageMessage != null ? storageMessage : "Storage initialization failed.";
            }
            return summary != null ? summary : "Whisper is ready.";
        }
    }
}
