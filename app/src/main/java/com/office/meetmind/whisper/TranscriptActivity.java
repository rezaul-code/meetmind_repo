package com.office.meetmind.whisper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.office.meetmind.R;
import com.office.meetmind.databinding.ActivityTranscriptBinding;

import java.io.File;

public class TranscriptActivity extends AppCompatActivity implements TranscriptionCallback {

    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private ActivityTranscriptBinding binding;
    private WhisperManager whisperManager;
    private String recordingFilePath;

    public static Intent createIntent(Context context, String recordingFilePath) {
        Intent intent = new Intent(context, TranscriptActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, recordingFilePath);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTranscriptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        whisperManager = new WhisperManager(getApplicationContext());
        recordingFilePath = getIntent().getStringExtra(EXTRA_FILE_PATH);

        binding.titleText.setText(R.string.transcript_title);
        binding.transcriptText.setText("");
        showWorkingState();

        if (recordingFilePath == null || recordingFilePath.trim().isEmpty()) {
            showFatalErrorAndFinish(getString(R.string.transcript_error_missing_recording));
            return;
        }

        whisperManager.transcribeRecording(recordingFilePath, this);
    }

    private void showWorkingState() {
        binding.loadingGroup.setVisibility(View.VISIBLE);
        binding.progressIndicator.setIndeterminate(true);
        binding.progressIndicator.setProgressCompat(0, false);
        binding.progressText.setText(getString(R.string.transcript_progress_format, 0));
        binding.statusText.setText(R.string.transcript_loading_recording);
    }

    private void showFatalErrorAndFinish(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.transcript_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onRecordingLoaded(String recordingName) {
        binding.recordingNameText.setText(recordingName);
    }

    @Override
    public void onProgress(int progress) {
        binding.progressIndicator.setIndeterminate(false);
        binding.progressIndicator.setProgressCompat(progress, true);
        binding.progressText.setText(getString(R.string.transcript_progress_format, progress));
    }

    @Override
    public void onStatusChanged(String statusMessage) {
        binding.statusText.setText(statusMessage);
    }

    @Override
    public void onTranscriptionCompleted(String transcriptText, File transcriptFile) {
        binding.loadingGroup.setVisibility(View.VISIBLE);
        binding.progressIndicator.setIndeterminate(false);
        binding.progressIndicator.setProgressCompat(100, true);
        binding.progressText.setText(getString(R.string.transcript_progress_format, 100));
        binding.statusText.setText(getString(R.string.transcript_saved_file, transcriptFile.getName()));
        binding.transcriptText.setText(transcriptText);
    }

    @Override
    public void onError(String errorMessage) {
        String resolvedMessage = resolveErrorMessage(errorMessage);
        binding.loadingGroup.setVisibility(View.VISIBLE);
        binding.progressIndicator.setIndeterminate(false);
        binding.progressIndicator.setProgressCompat(0, false);
        binding.progressText.setText(getString(R.string.transcript_progress_format, 0));
        binding.statusText.setText(resolvedMessage);
        binding.transcriptText.setText(resolvedMessage);
        Toast.makeText(this, resolvedMessage, Toast.LENGTH_LONG).show();

        if (getString(R.string.transcript_error_missing_recording).equals(resolvedMessage)
                || resolvedMessage.startsWith("Failed to load libwhisper.so")) {
            showFatalErrorAndFinish(resolvedMessage);
        }
    }

    private String resolveErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return WhisperManager.getRootCauseMessage(getApplicationContext());
        }
        if (getString(R.string.transcript_error_native_missing).equals(errorMessage)) {
            return WhisperManager.getRootCauseMessage(getApplicationContext());
        }
        return errorMessage;
    }

    @Override
    protected void onDestroy() {
        whisperManager.cancel();
        super.onDestroy();
    }
}
