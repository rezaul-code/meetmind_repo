package com.office.meetmind.whisper;

import java.io.File;

public interface TranscriptionCallback {

    void onRecordingLoaded(String recordingName);

    void onProgress(int progress);

    void onStatusChanged(String statusMessage);

    void onTranscriptionCompleted(String transcriptText, File transcriptFile);

    void onError(String errorMessage);
}
