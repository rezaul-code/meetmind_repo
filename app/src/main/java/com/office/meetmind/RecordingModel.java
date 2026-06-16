package com.office.meetmind;

public class RecordingModel {

    private final String fileName;
    private final String filePath;
    private final String dateText;
    private final String durationText;
    private final long recordedAtMs;
    private final long durationMs;

    public RecordingModel(String fileName, String filePath, String dateText, String durationText, long recordedAtMs, long durationMs) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.dateText = dateText;
        this.durationText = durationText;
        this.recordedAtMs = recordedAtMs;
        this.durationMs = durationMs;
    }

    public String getFileName() {
        return fileName;
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

    public long getRecordedAtMs() {
        return recordedAtMs;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
