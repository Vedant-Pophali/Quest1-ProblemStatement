package com.extractor.model;

public record StreamMetadata(
    String rawStreamUrl,
    double fps,
    double durationSeconds,
    Double audioTimestampMatch // Nullable: Populated once the Audio Pointer finds the phrase
) {
    /**
     * Helper to calculate the exact frame number from a given timestamp in seconds.
     * Frame Number = round(Timestamp * FPS)
     */
    public int calculateFrameNumber(double timestampInSeconds) {
        return (int) Math.round(timestampInSeconds * fps);
    }
}