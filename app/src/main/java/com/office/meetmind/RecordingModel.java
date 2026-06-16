package com.office.meetmind;

public class RecordingModel {

    private final String displayName;
    private final String filePath;
    private final String dateText;
    private final String durationText;
    private final String fileSizeText;
    private final long recordedAtMs;
    private final long durationMs;
    private final long fileSizeBytes;

    public RecordingModel(
            String displayName,
            String filePath,
            String dateText,
            String durationText,
            String fileSizeText,
            long recordedAtMs,
            long durationMs,
            long fileSizeBytes
    ) {
        this.displayName = displayName;
        this.filePath = filePath;
        this.dateText = dateText;
        this.durationText = durationText;
        this.fileSizeText = fileSizeText;
        this.recordedAtMs = recordedAtMs;
        this.durationMs = durationMs;
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getFileName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDateText() {
        return dateText;
    }

    public String getDurationText() {
        return durationText;
    }

    public String getFileSizeText() {
        return fileSizeText;
    }

    public long getRecordedAtMs() {
        return recordedAtMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }
}
