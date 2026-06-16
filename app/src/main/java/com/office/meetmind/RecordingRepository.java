package com.office.meetmind;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.text.format.Formatter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RecordingRepository {

    public List<RecordingModel> loadRecordings(Context context) {
        File directory = AudioRecorder.getRecordingsDirectory(context);
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".m4a"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        Collections.sort(fileList, Comparator.comparingLong(File::lastModified).reversed());

        List<RecordingModel> recordings = new ArrayList<>();
        for (File file : fileList) {
            RecordingModel recording = buildRecordingModel(context, file);
            if (recording != null) {
                recordings.add(recording);
            }
        }
        return recordings;
    }

    public RecordingModel loadRecording(Context context, String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        return buildRecordingModel(context, file);
    }

    public boolean deleteRecording(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile() && file.delete();
    }

    private RecordingModel buildRecordingModel(Context context, File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        long durationMs = readDuration(file);
        String displayName = stripExtension(file.getName());
        String dateText = AudioRecorder.formatDate(file.lastModified());
        String durationText = AudioRecorder.formatDuration(durationMs);
        String fileSizeText = Formatter.formatFileSize(context, file.length());

        return new RecordingModel(
                displayName,
                file.getAbsolutePath(),
                dateText,
                durationText,
                fileSizeText,
                file.lastModified(),
                durationMs,
                file.length()
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
        } catch (Exception ignored) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
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
}
