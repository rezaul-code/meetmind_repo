package com.office.meetmind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.office.meetmind.databinding.ActivityRecordingsBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsActivity extends AppCompatActivity implements RecordingAdapter.OnRecordingClickListener {

    private ActivityRecordingsBinding binding;
    private final RecordingRepository repository = new RecordingRepository();
    private final RecordingAdapter adapter = new RecordingAdapter(this);
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
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.meetmind_primary);
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.meetmind_surface);
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadRecordings);
        binding.refreshButton.setOnClickListener(v -> loadRecordings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecordings();
    }

    private void loadRecordings() {
        binding.swipeRefreshLayout.setRefreshing(true);
        binding.loadingProgress.setVisibility(View.VISIBLE);
        binding.emptyStateContainer.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        executorService.execute(() -> {
            List<RecordingModel> recordings = repository.loadRecordings(getApplicationContext());
            mainHandler.post(() -> {
                adapter.submitList(recordings);
                boolean isEmpty = recordings.isEmpty();
                binding.emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                binding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                binding.loadingProgress.setVisibility(View.GONE);
                binding.swipeRefreshLayout.setRefreshing(false);
            });
        });
    }

    @Override
    public void onRecordingClicked(RecordingModel recordingModel) {
        Intent intent = RecordingDetailsActivity.createIntent(this, recordingModel.getFilePath());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }
}
