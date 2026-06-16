package com.office.meetmind;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.office.meetmind.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ActivityResultLauncher<String> audioPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openRecordActivity();
                    } else {
                        showPermissionDeniedDialog();
                    }
                }
        );

        binding.titleText.setText(R.string.title_main);
        binding.startRecordingButton.setOnClickListener(v -> requestAudioPermissionAndStart());
        binding.viewRecordingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, RecordingsActivity.class)));
    }

    private void requestAudioPermissionAndStart() {
        if (hasAudioPermission()) {
            openRecordActivity();
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void openRecordActivity() {
        startActivity(new Intent(this, RecordActivity.class));
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_main)
                .setMessage(R.string.audio_permission_required)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
