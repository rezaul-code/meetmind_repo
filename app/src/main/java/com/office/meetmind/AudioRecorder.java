package com.office.meetmind;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecorder {

    private MediaRecorder mediaRecorder;
    private File outputFile;
    private FileOutputStream outputStream;

    public void startRecording(Context context, File outputFile) throws IOException {
        stopRecording();

        this.outputFile = outputFile;
        outputStream = new FileOutputStream(outputFile, false);
        try {
            mediaRecorder = createMediaRecorder(context);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(outputStream.getFD());
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException | RuntimeException e) {
            stopRecording();
            throw e;
        }
    }

    private MediaRecorder createMediaRecorder(Context context) throws IOException {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return new MediaRecorder(context);
            }
            return MediaRecorder.class.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IOException("Unable to create MediaRecorder", e);
        }
    }

    public void stopRecording() {
        if (mediaRecorder == null) {
            return;
        }

        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
        } finally {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
                outputStream = null;
            }
            outputFile = null;
        }
    }

    public static File createRecordingFile(Context context) {
        File directory = getRecordingsDirectory(context);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(directory, "Meeting_" + timestamp + ".m4a");
    }

    public static File getRecordingsDirectory(Context context) {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (directory == null) {
            directory = new File(context.getFilesDir(), "recordings");
        }
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    public static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatDate(long timeMs) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(timeMs));
    }
}
