package com.office.meetmind;

import android.media.MediaPlayer;

import java.io.IOException;

public class AudioPlayerManager {

    public interface Listener {
        void onPrepared(int durationMs);

        void onCompleted();

        void onError(String message);
    }

    private MediaPlayer mediaPlayer;
    private Listener listener;
    private boolean prepared;
    private String currentFilePath;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean load(String filePath) {
        release();
        currentFilePath = filePath;

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.setOnCompletionListener(player -> {
                prepared = true;
                if (listener != null) {
                    listener.onCompleted();
                }
            });
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                prepared = false;
                if (listener != null) {
                    listener.onError("Playback failed.");
                }
                return true;
            });
            mediaPlayer.prepare();
            prepared = true;
            if (listener != null) {
                listener.onPrepared(mediaPlayer.getDuration());
            }
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            release();
            return false;
        }
    }

    public boolean play() {
        if (!prepared || mediaPlayer == null) {
            return false;
        }
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        return true;
    }

    public boolean pause() {
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) {
            return false;
        }
        mediaPlayer.pause();
        return true;
    }

    public boolean resume() {
        if (mediaPlayer == null || mediaPlayer.isPlaying()) {
            return false;
        }
        mediaPlayer.start();
        return true;
    }

    public boolean stop() {
        if (mediaPlayer == null) {
            return false;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
            mediaPlayer.seekTo(0);
            return true;
        } catch (IllegalStateException e) {
            if (listener != null) {
                listener.onError("Unable to stop playback.");
            }
            return false;
        }
    }

    public boolean seekTo(int positionMs) {
        if (mediaPlayer == null || !prepared) {
            return false;
        }
        try {
            mediaPlayer.seekTo(positionMs);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getDuration() {
        return mediaPlayer == null || !prepared ? 0 : mediaPlayer.getDuration();
    }

    public int getCurrentPosition() {
        return mediaPlayer == null ? 0 : mediaPlayer.getCurrentPosition();
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public boolean isPrepared() {
        return prepared;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer = null;
        }
        prepared = false;
        currentFilePath = null;
    }
}
