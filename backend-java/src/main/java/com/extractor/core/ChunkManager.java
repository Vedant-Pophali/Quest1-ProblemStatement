package com.extractor.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChunkManager {
    private static final Logger log = LoggerFactory.getLogger(ChunkManager.class);
    
    // Defines a safe 60-second window for the Coarse Scan (1 FPS = 60 images max in memory per chunk)
    public static final double DEFAULT_MACRO_CHUNK_SECONDS = 60.0;

    /**
     * Represents an immutable, discrete segment of the video timeline.
     */
    public record TimeChunk(double startTime, double endTime) {
        public double getDuration() {
            return endTime - startTime;
        }
    }

    /**
     * Slices the overarching search space into small, memory-safe micro-batches for the Video Pointer.
     */
    public List<TimeChunk> createMacroChunks(double startSeconds, double endSeconds) {
        List<TimeChunk> chunks = new ArrayList<>();
        double currentStart = startSeconds;

        while (currentStart < endSeconds) {
            double currentEnd = Math.min(currentStart + DEFAULT_MACRO_CHUNK_SECONDS, endSeconds);
            chunks.add(new TimeChunk(currentStart, currentEnd));
            currentStart = currentEnd;
        }

        log.info("Divided timeline ({}s to {}s) into {} memory-safe chunks.", startSeconds, endSeconds, chunks.size());
        return chunks;
    }

    /**
     * Creates the strict localized window for the Binary Search once the coarse target is found.
     * 
     * @param targetTime The second where the text was detected (e.g., 305.0)
     * @return A strict 1-second boundary (e.g., 304.0 to 305.0)
     */
    public TimeChunk createFineScanWindow(double targetTime) {
        // The text was not in (targetTime - 1), but is in targetTime. 
        // We isolate this exact 1-second gap.
        double start = Math.max(0.0, targetTime - 1.0);
        
        log.info("Calculated Fine Scan binary search window: {}s to {}s", start, targetTime);
        return new TimeChunk(start, targetTime);
    }
    
    /**
     * Creates a bounded search space centered around the Audio Pointer's discovery.
     */
    public TimeChunk createAudioBoundedWindow(double audioTimestamp, double maxVideoDuration) {
        // Subtitles often appear slightly before the audio starts.
        double start = Math.max(0.0, audioTimestamp - 3.0);
        double end = Math.min(maxVideoDuration, audioTimestamp + 2.0);
        
        log.info("Calculated Audio-bounded window: {}s to {}s", start, end);
        return new TimeChunk(start, end);
    }
}