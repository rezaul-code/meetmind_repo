package com.office.meetmind;

import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.office.meetmind.databinding.ActivityRecordingsBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsActivity extends AppCompatActivity {

    private ActivityRecordingsBinding binding;
    private final RecordingAdapter adapter = new RecordingAdapter();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.titleText.setText(R.string.recordings_title);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecordings();
    }

    private void loadRecordings() {
        executorService.execute(() -> {
            List<RecordingModel> recordings = readRecordings();
            mainHandler.post(() -> {
                adapter.submitList(recordings);
                boolean isEmpty = recordings.isEmpty();
                binding.emptyStateText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private List<RecordingModel> readRecordings() {
        File directory = AudioRecorder.getRecordingsDirectory(this);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".m4a"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        Collections.sort(fileList, Comparator.comparingLong(File::lastModified).reversed());

        List<RecordingModel> recordings = new ArrayList<>();
        for (File file : fileList) {
            recordings.add(createRecordingModel(file));
        }
        return recordings;
    }

    private RecordingModel createRecordingModel(File file) {
        long durationMs = readDuration(file);
        String dateText = AudioRecorder.formatDate(file.lastModified());
        String durationText = AudioRecorder.formatDuration(durationMs);
        return new RecordingModel(
                file.getName(),
                file.getAbsolutePath(),
                dateText,
                durationText,
                file.lastModified(),
                durationMs
        );
    }

    private long readDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String durationValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationValue == null) {
                return 0L;
            }
            return Long.parseLong(durationValue);
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }
}
