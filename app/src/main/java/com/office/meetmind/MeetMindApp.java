package com.office.meetmind;

import android.app.Application;
import android.util.Log;

import com.office.meetmind.whisper.WhisperManager;

public class MeetMindApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        WhisperManager.logStartupModelCheck(this);
    }
}
