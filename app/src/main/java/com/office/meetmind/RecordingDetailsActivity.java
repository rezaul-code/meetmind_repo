package com.office.meetmind;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.office.meetmind.databinding.ActivityRecordDetailsBinding;
import com.office.meetmind.whisper.TranscriptActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingDetailsActivity extends AppCompatActivity implements AudioPlayerManager.Listener {

    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private ActivityRecordDetailsBinding binding;
    private final RecordingRepository repository = new RecordingRepository();
    private final AudioPlayerManager audioPlayerManager = new AudioPlayerManager();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlaybackProgress();
            if (audioPlayerManager.isPlaying()) {
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    private String filePath;
    private boolean isSeekBarUserChange;
    private boolean isRecordingReady;
    private int totalDurationMs;
    private RecordingModel recordingModel;

    public static Intent createIntent(Context context, String filePath) {
        Intent intent = new Intent(context, RecordingDetailsActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioPlayerManager.setListener(this);
        binding.titleText.setText(R.string.recording_details_title);
        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        setupListeners();
        loadRecording();
    }

    private void setupListeners() {
        binding.playButton.setOnClickListener(v -> playFromStart());
        binding.pauseButton.setOnClickListener(v -> pausePlayback());
        binding.resumeButton.setOnClickListener(v -> resumePlayback());
        binding.stopButton.setOnClickListener(v -> stopPlayback());
        binding.transcribeButton.setOnClickListener(v -> openTranscriptScreen());
        binding.deleteButton.setOnClickListener(v -> confirmDelete());
        binding.playbackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isRecordingReady) {
                    audioPlayerManager.seekTo(progress);
                    binding.currentTimeText.setText(AudioRecorder.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarUserChange = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarUserChange = false;
                if (isRecordingReady) {
                    audioPlayerManager.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void loadRecording() {
        setLoadingState(true);
        executorService.execute(() -> {
            RecordingModel recordingModel = repository.loadRecording(getApplicationContext(), filePath);
            mainHandler.post(() -> {
                setLoadingState(false);
                if (recordingModel == null) {
                    showMissingFileDialog();
                    return;
                }

                bindRecording(recordingModel);
                boolean prepared = audioPlayerManager.load(recordingModel.getFilePath());
                if (prepared) {
                    onPrepared(audioPlayerManager.getDuration());
                } else {
                    setPlaybackErrorState(getString(R.string.player_corrupted_file));
                    Toast.makeText(this, R.string.player_corrupted_file, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void bindRecording(RecordingModel recordingModel) {
        this.recordingModel = recordingModel;
        binding.recordingNameText.setText(recordingModel.getDisplayName());
        binding.recordingDateText.setText(recordingModel.getDateText());
        binding.recordingDurationText.setText(recordingModel.getDurationText());
        binding.recordingSizeText.setText(recordingModel.getFileSizeText());
        binding.recordingPathText.setText(recordingModel.getFilePath());
    }

    private void playFromStart() {
        if (!isRecordingReady) {
            return;
        }
        audioPlayerManager.stop();
        audioPlayerManager.seekTo(0);
        if (audioPlayerManager.play()) {
            showPlayingState();
            mainHandler.removeCallbacks(progressRunnable);
            mainHandler.post(progressRunnable);
        } else {
            setPlaybackErrorState(getString(R.string.player_playback_failed));
        }
    }

    private void pausePlayback() {
        if (!isRecordingReady || !audioPlayerManager.isPlaying()) {
            return;
        }
        if (audioPlayerManager.pause()) {
            showPausedState();
            mainHandler.removeCallbacks(progressRunnable);
            updatePlaybackProgress();
        }
    }

    private void resumePlayback() {
        if (!isRecordingReady || audioPlayerManager.isPlaying()) {
            return;
        }
        if (audioPlayerManager.resume()) {
            showPlayingState();
            mainHandler.removeCallbacks(progressRunnable);
            mainHandler.post(progressRunnable);
        } else {
            setPlaybackErrorState(getString(R.string.player_playback_failed));
        }
    }

    private void stopPlayback() {
        if (!isRecordingReady) {
            return;
        }
        audioPlayerManager.stop();
        mainHandler.removeCallbacks(progressRunnable);
        binding.playbackSeekBar.setProgress(0);
        binding.currentTimeText.setText(AudioRecorder.formatDuration(0));
        showStoppedState();
    }

    private void openTranscriptScreen() {
        if (recordingModel == null) {
            return;
        }
        startActivity(TranscriptActivity.createIntent(this, recordingModel.getFilePath()));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_recording)
                .setMessage(R.string.delete_recording_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteRecording())
                .show();
    }

    private void deleteRecording() {
        setLoadingState(true);
        mainHandler.removeCallbacks(progressRunnable);
        executorService.execute(() -> {
            boolean deleted = repository.deleteRecording(filePath);
            mainHandler.post(() -> {
                setLoadingState(false);
                if (deleted) {
                    audioPlayerManager.release();
                    Toast.makeText(this, R.string.delete_success, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setLoadingState(boolean loading) {
        binding.loadingProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.playButton.setEnabled(!loading);
        binding.pauseButton.setEnabled(false);
        binding.resumeButton.setEnabled(false);
        binding.stopButton.setEnabled(false);
        binding.transcribeButton.setEnabled(!loading && recordingModel != null);
        binding.deleteButton.setEnabled(!loading);
        binding.playbackSeekBar.setEnabled(!loading);
    }

    private void showReadyState(String status) {
        binding.statusText.setText(status);
        binding.playButton.setEnabled(true);
        binding.pauseButton.setEnabled(false);
        binding.resumeButton.setEnabled(false);
        binding.stopButton.setEnabled(false);
        binding.transcribeButton.setEnabled(recordingModel != null);
        binding.deleteButton.setEnabled(true);
        binding.playbackSeekBar.setEnabled(isRecordingReady);
    }

    private void showPlayingState() {
        binding.statusText.setText(R.string.player_playing);
        binding.playButton.setEnabled(false);
        binding.pauseButton.setEnabled(true);
        binding.resumeButton.setEnabled(false);
        binding.stopButton.setEnabled(true);
        binding.transcribeButton.setEnabled(recordingModel != null);
        binding.deleteButton.setEnabled(true);
        binding.playbackSeekBar.setEnabled(isRecordingReady);
    }

    private void showPausedState() {
        binding.statusText.setText(R.string.player_paused);
        binding.playButton.setEnabled(true);
        binding.pauseButton.setEnabled(false);
        binding.resumeButton.setEnabled(true);
        binding.stopButton.setEnabled(true);
        binding.transcribeButton.setEnabled(recordingModel != null);
        binding.deleteButton.setEnabled(true);
        binding.playbackSeekBar.setEnabled(isRecordingReady);
    }

    private void showStoppedState() {
        binding.statusText.setText(R.string.player_stopped);
        binding.playButton.setEnabled(true);
        binding.pauseButton.setEnabled(false);
        binding.resumeButton.setEnabled(false);
        binding.stopButton.setEnabled(false);
        binding.transcribeButton.setEnabled(recordingModel != null);
        binding.deleteButton.setEnabled(true);
        binding.playbackSeekBar.setEnabled(isRecordingReady);
    }

    private void setPlaybackErrorState(String message) {
        binding.statusText.setText(message);
        binding.playButton.setEnabled(false);
        binding.pauseButton.setEnabled(false);
        binding.resumeButton.setEnabled(false);
        binding.stopButton.setEnabled(false);
        binding.transcribeButton.setEnabled(recordingModel != null);
        binding.playbackSeekBar.setEnabled(false);
    }

    private void updatePlaybackProgress() {
        if (!isRecordingReady) {
            return;
        }
        int currentPosition = audioPlayerManager.getCurrentPosition();
        if (!isSeekBarUserChange) {
            binding.playbackSeekBar.setProgress(currentPosition);
        }
        binding.currentTimeText.setText(AudioRecorder.formatDuration(currentPosition));
    }

    private void showMissingFileDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.missing_file_title)
                .setMessage(R.string.missing_file_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onPrepared(int durationMs) {
        totalDurationMs = Math.max(durationMs, 0);
        isRecordingReady = true;
        binding.totalTimeText.setText(AudioRecorder.formatDuration(totalDurationMs));
        binding.currentTimeText.setText(AudioRecorder.formatDuration(0));
        binding.playbackSeekBar.setMax(Math.max(totalDurationMs, 1000));
        binding.playbackSeekBar.setProgress(0);
        binding.playbackSeekBar.setEnabled(true);
        showReadyState(getString(R.string.player_ready));
    }

    @Override
    public void onCompleted() {
        mainHandler.post(() -> {
            audioPlayerManager.seekTo(0);
            binding.playbackSeekBar.setProgress(0);
            binding.currentTimeText.setText(AudioRecorder.formatDuration(0));
            showReadyState(getString(R.string.player_ready));
        });
    }

    @Override
    public void onError(String message) {
        mainHandler.post(() -> {
            setPlaybackErrorState(message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (audioPlayerManager.isPlaying()) {
            pausePlayback();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(progressRunnable);
        audioPlayerManager.release();
        executorService.shutdownNow();
        super.onDestroy();
    }
}
