package com.office.meetmind;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.office.meetmind.databinding.ActivityRecordBinding;

import java.io.File;
import java.io.IOException;

public class RecordActivity extends AppCompatActivity {

    private ActivityRecordBinding binding;
    private final AudioRecorder audioRecorder = new AudioRecorder();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private long recordingStartedAtMs;
    private boolean isRecording;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                updateTimer();
                timerHandler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startRecording();
                    } else {
                        showPermissionDeniedDialog();
                    }
                }
        );

        binding.stopButton.setOnClickListener(v -> stopRecording());
        ensurePermissionAndStart();
    }

    private void ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        try {
            File currentRecordingFile = AudioRecorder.createRecordingFile(this);
            audioRecorder.startRecording(this, currentRecordingFile);
            isRecording = true;
            recordingStartedAtMs = System.currentTimeMillis();
            binding.statusText.setText(R.string.recording_title);
            binding.timerText.setText(R.string.recording_timer);
            binding.stopButton.setEnabled(true);
            binding.stopButton.setVisibility(View.VISIBLE);
            timerHandler.post(timerRunnable);
        } catch (IOException e) {
            showErrorAndFinish();
        }
    }

    private void stopRecording() {
        if (!isRecording) {
            finish();
            return;
        }

        timerHandler.removeCallbacks(timerRunnable);
        isRecording = false;
        audioRecorder.stopRecording();
        Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateTimer() {
        long elapsedMs = System.currentTimeMillis() - recordingStartedAtMs;
        binding.timerText.setText(AudioRecorder.formatDuration(elapsedMs));
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_main)
                .setMessage(R.string.audio_permission_required)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void showErrorAndFinish() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_main)
                .setMessage(R.string.recording_failed)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        if (isRecording) {
            audioRecorder.stopRecording();
            isRecording = false;
        }
        super.onDestroy();
    }
}
