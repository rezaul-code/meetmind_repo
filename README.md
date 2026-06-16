# MeetMind

MeetMind is an Android application that records meetings, stores recordings locally on the device, and converts speech into searchable meeting notes using on-device AI models. The application is designed to work offline while maintaining user privacy.

## Tech Stack

* Java
* Android SDK (API 26+)
* AndroidX
* Material Design
* View Binding
* MediaRecorder
* RecyclerView

## Application Flow

1. User opens MeetMind.
2. User taps **Start Recording**.
3. Audio is captured using MediaRecorder.
4. Recording is saved locally as an `.m4a` file.
5. User can view all recordings from the Recordings screen.
6. Future AI modules will transcribe recordings into text.
7. Meeting summaries, action items, and searchable notes will be generated locally.

## AI Models (Planned)

### Speech-to-Text

* Whisper Tiny
* Whisper Base
* Whisper Small

### Summarization

* Gemma 2B
* Gemma 3 1B (Mobile Optimized)

### Optional Translation

* LibreTranslate Offline

## Agile Development Plan

### Sprint 1 – Recording Module ✅

* MainActivity UI
* Start Recording
* Stop Recording
* Save recordings locally
* View recordings screen

### Sprint 2 – Audio Playback

* Play recordings
* Pause/Resume audio
* Delete recordings
* Recording details screen

### Sprint 3 – AI Transcription

* Integrate Whisper model
* Convert audio to text
* Display transcript
* Store transcript locally

### Sprint 4 – Meeting Intelligence

* AI summarization
* Action item extraction
* Search transcripts
* Export meeting notes

## Goal

Create a fully offline AI-powered meeting assistant that records, transcribes, summarizes, and organizes meetings directly on Android devices while keeping all user data private.
