package com.extractor.model;

public enum JobState {
    INITIALIZING,           // Fetching metadata via yt-dlp
    AUDIO_SEARCH_RUNNING,   // Fast Pointer (Whisper) is looking for the audio bound
    COARSE_SCANNING,        // Slow Pointer (1 FPS) is scanning visually
    FINE_SCANNING,          // Binary search on the 24 sub-frames is executing
    SUCCESS,                // Exact frame found and verified
    TEXT_NOT_FOUND,         // Video processed successfully, but the text never appeared
    SYSTEM_ERROR            // Subprocess crashed, OS killed a thread, or network failed
}