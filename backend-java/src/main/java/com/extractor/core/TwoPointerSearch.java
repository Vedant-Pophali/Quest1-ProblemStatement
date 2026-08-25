package com.extractor.core;

import com.extractor.model.FrameResult;
import com.extractor.model.StreamMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class TwoPointerSearch {
    private static final Logger log = LoggerFactory.getLogger(TwoPointerSearch.class);

    private final MediaExtractor mediaExtractor;
    private final TextRecognizer textRecognizer;
    private final ChunkManager chunkManager;
    
    public TwoPointerSearch(MediaExtractor mediaExtractor, TextRecognizer textRecognizer, ChunkManager chunkManager) {
        this.mediaExtractor = mediaExtractor;
        this.textRecognizer = textRecognizer;
        this.chunkManager = chunkManager;
    }

    public Optional<FrameResult> executeSearch(StreamMetadata metadata, String targetText) throws Exception {
        // Java 21: Extremely lightweight concurrency
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            // Shared state: The dynamic upper bound for the visual pointer
            AtomicReference<Double> dynamicVisualUpperBound = new AtomicReference<>(metadata.durationSeconds());

            // 1. Launch the Audio Pointer (Fast Runner)
            Future<Optional<Double>> audioPointerFuture = virtualExecutor.submit(() -> 
                runAudioPointer(metadata.rawStreamUrl(), targetText)
            );

            // 2. Launch the Visual Pointer (Slow Runner / Coarse Scan)
            Future<Optional<FrameResult>> visualPointerFuture = virtualExecutor.submit(() -> 
                runVisualPointer(metadata, targetText, dynamicVisualUpperBound, audioPointerFuture)
            );

            // Wait for the visual pointer to finish its logic (which inherently includes the binary search)
            return visualPointerFuture.get();
        }
    }

    private Optional<Double> runAudioPointer(String rawStreamUrl, String targetText) {
        try {
            log.info("Audio Pointer started racing ahead...");
            // In a real implementation, you might chunk the audio too, or extract the whole track if it's small.
            String audioPath = mediaExtractor.extractAudio(rawStreamUrl, 0, 3600); 
            return textRecognizer.recognizeAudioTimestamp(audioPath, targetText);
        } catch (Exception e) {
            log.error("Audio Pointer failed. Visual Pointer will have to search the entire video.", e);
            return Optional.empty();
        }
    }

    private Optional<FrameResult> runVisualPointer(
            StreamMetadata metadata, 
            String targetText, 
            AtomicReference<Double> upperBound,
            Future<Optional<Double>> audioPointerFuture) throws Exception {
        
        double currentCursor = 0.0;

        // COARSE SCAN (1 FPS)
        while (currentCursor < upperBound.get()) {
            
            // Check if Audio Pointer found a limit while we were processing
            if (audioPointerFuture.isDone()) {
                Optional<Double> audioHit = audioPointerFuture.get();
                if (audioHit.isPresent()) {
                    double audioBound = audioHit.get() + 2.0; // Audio timestamp + 2 second buffer
                    upperBound.set(Math.min(upperBound.get(), audioBound));
                    log.info("Visual Pointer path dynamically shortened to {}s by Audio Pointer.", upperBound.get());
                }
            }

            // SAFETY FIX: If the cursor has bypassed the newly shortened bound, safely exit.
            if (currentCursor >= upperBound.get()) {
                log.info("Cursor ({}s) passed the new boundary ({}s). Stopping coarse scan.", currentCursor, upperBound.get());
                break;
            }

            // Get chunks. If empty (due to math edge cases), safely exit.
            List<ChunkManager.TimeChunk> chunks = chunkManager.createMacroChunks(currentCursor, upperBound.get());
            if (chunks.isEmpty()) break;

            ChunkManager.TimeChunk chunk = chunks.getFirst();
            log.info("Visual Pointer scanning chunk: {}s to {}s", chunk.startTime(), chunk.endTime());
            
            List<String> frames = mediaExtractor.extractVisualFrames(
                    metadata.rawStreamUrl(), chunk.startTime(), chunk.endTime(), 1.0);

            for (int i = 0; i < frames.size(); i++) {
                double frameTimestamp = chunk.startTime() + i;
                Optional<FrameResult> result = textRecognizer.recognizeText(frames.get(i), targetText);
                
                if (result.isPresent()) {
                    log.info("Coarse visual match found near {}s! Initiating Binary Search...", frameTimestamp);
                    return executeFineScan(metadata, targetText, frameTimestamp);
                }
            }
            
            currentCursor = chunk.endTime();
        }

        return Optional.empty();
    }
    private Optional<FrameResult> executeFineScan(StreamMetadata metadata, String targetText, double coarseTimestamp) throws Exception {
        ChunkManager.TimeChunk fineWindow = chunkManager.createFineScanWindow(coarseTimestamp);
        
        List<String> subFrames = mediaExtractor.extractVisualFrames(
                metadata.rawStreamUrl(), fineWindow.startTime(), fineWindow.endTime(), metadata.fps());
        
        int left = 0;
        int right = subFrames.size() - 1;
        Optional<FrameResult> absoluteFirstFrame = Optional.empty();

        while (left <= right) {
            int mid = left + (right - left) / 2;
            Optional<FrameResult> midResult = textRecognizer.recognizeText(subFrames.get(mid), targetText);

            if (midResult.isPresent()) {
                // MATHEMATICAL FIX: Calculate exact time using the sub-frame index
                double exactTimeInSeconds = fineWindow.startTime() + (mid / metadata.fps());
                int exactFrameNumber = metadata.calculateFrameNumber(exactTimeInSeconds);
                String formattedTime = formatTimestamp(exactTimeInSeconds);

                FrameResult raw = midResult.get();
                
                // Inject the calculated Java math into the final result
                absoluteFirstFrame = Optional.of(new FrameResult(
                        formattedTime,
                        exactFrameNumber,
                        raw.extractedText(),
                        raw.imagePath(),
                        raw.confidenceScore()
                ));
                
                // Keep searching left to find the absolute earliest sub-frame
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        return absoluteFirstFrame;
    }

    // Helper method to format seconds into HH:MM:SS.sss
    private String formatTimestamp(double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        double seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds);
    }
}