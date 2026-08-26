package com.extractor.core;

import com.extractor.model.ExtractionResult;
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

    public ExtractionResult executeSearch(StreamMetadata metadata, String targetText, int threshold) throws Exception {
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            AtomicReference<Double> dynamicVisualUpperBound = new AtomicReference<>(metadata.durationSeconds());

            Callable<Optional<Double>> audioTask = () -> 
                runAudioPointer(metadata.rawStreamUrl(), targetText, threshold);
            Future<Optional<Double>> audioPointerFuture = virtualExecutor.submit(audioTask);

            Callable<Optional<FrameResult>> visualTask = () -> 
                runVisualPointer(metadata, targetText, dynamicVisualUpperBound, audioPointerFuture, threshold);
            Future<Optional<FrameResult>> visualPointerFuture = virtualExecutor.submit(visualTask);

            Optional<FrameResult> visualResult = visualPointerFuture.get();
            Optional<Double> audioResult = audioPointerFuture.get();

            return new ExtractionResult(visualResult, audioResult);
        }
    }

    private Optional<Double> runAudioPointer(String rawStreamUrl, String targetText, int threshold) {
        try {
            log.info("Audio Pointer started racing ahead...");
            String audioPath = mediaExtractor.extractAudio(rawStreamUrl, 0, 3600); 
            return textRecognizer.recognizeAudioTimestamp(audioPath, targetText, threshold);
        } catch (Exception e) {
            log.error("Audio Pointer failed. Visual Pointer will have to search the entire video.", e);
            return Optional.empty();
        }
    }

    private Optional<FrameResult> runVisualPointer(
            StreamMetadata metadata, 
            String targetText, 
            AtomicReference<Double> upperBound,
            Future<Optional<Double>> audioPointerFuture,
            int threshold) throws Exception {
        
        double currentCursor = 0.0;

        while (currentCursor < upperBound.get()) {
            
            if (audioPointerFuture.isDone()) {
                Optional<Double> audioHit = audioPointerFuture.get();
                if (audioHit.isPresent()) {
                    double audioBound = audioHit.get() + 15.0; 
                    upperBound.set(Math.min(upperBound.get(), audioBound));
                    log.info("Visual Pointer path dynamically shortened to {}s by Audio Pointer.", upperBound.get());
                }
            }
            if (currentCursor >= upperBound.get()) {
                log.info("Cursor ({}s) passed the new boundary ({}s). Stopping coarse scan.", currentCursor, upperBound.get());
                break;
            }

            List<ChunkManager.TimeChunk> chunks = chunkManager.createMacroChunks(currentCursor, upperBound.get());
            if (chunks.isEmpty()) break;

            ChunkManager.TimeChunk chunk = chunks.getFirst();
            log.info("Visual Pointer scanning chunk: {}s to {}s", chunk.startTime(), chunk.endTime());
            
            List<String> frames = mediaExtractor.extractVisualFrames(
                    metadata.rawStreamUrl(), chunk.startTime(), chunk.endTime(), 1.0);

            for (int i = 0; i < frames.size(); i++) {
                double frameTimestamp = chunk.startTime() + i;
                Optional<FrameResult> result = textRecognizer.recognizeText(frames.get(i), targetText, threshold);
                
                if (result.isPresent()) {
                    log.info("Coarse visual match found near {}s! Initiating Binary Search...", frameTimestamp);
                    
                    // FIX: Pass the successful coarse result into the fine scan as a fallback!
                    return executeFineScan(metadata, targetText, frameTimestamp, threshold, result.get());
                }
            }
            
            currentCursor = chunk.endTime();
        }

        return Optional.empty();
    }

    // FIX: Added coarseFallback parameter
    private Optional<FrameResult> executeFineScan(StreamMetadata metadata, String targetText, double coarseTimestamp, int threshold, FrameResult coarseFallback) throws Exception {
        ChunkManager.TimeChunk fineWindow = chunkManager.createFineScanWindow(coarseTimestamp);
        
        List<String> subFrames = mediaExtractor.extractVisualFrames(
                metadata.rawStreamUrl(), fineWindow.startTime(), fineWindow.endTime(), metadata.fps());
        
        int left = 0;
        int right = subFrames.size() - 1;
        Optional<FrameResult> absoluteFirstFrame = Optional.empty();

        while (left <= right) {
            int mid = left + (right - left) / 2;
            Optional<FrameResult> midResult = textRecognizer.recognizeText(subFrames.get(mid), targetText, threshold);

            if (midResult.isPresent()) {
                double exactTimeInSeconds = fineWindow.startTime() + (mid / metadata.fps());
                int exactFrameNumber = (int) Math.round(exactTimeInSeconds * metadata.fps());
                String formattedTime = formatTimestamp(exactTimeInSeconds);

                FrameResult raw = midResult.get();
                
                absoluteFirstFrame = Optional.of(new FrameResult(
                        formattedTime,
                        exactFrameNumber,
                        raw.extractedText(),
                        raw.imagePath(),
                        raw.confidenceScore()
                ));
                
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        // FIX: If binary search found an earlier frame, use it. 
        if (absoluteFirstFrame.isPresent()) {
            return absoluteFirstFrame;
        } else {
            log.info("Binary search found no earlier frames. The coarse frame is the exact start.");
            
            // We must inject the true video timeline data into Python's raw OCR result
            int coarseFrameNum = (int) Math.round(coarseTimestamp * metadata.fps());
            String formattedCoarse = formatTimestamp(coarseTimestamp);
            
            FrameResult correctedCoarse = new FrameResult(
                    formattedCoarse,
                    coarseFrameNum,
                    coarseFallback.extractedText(),
                    coarseFallback.imagePath(),
                    coarseFallback.confidenceScore()
            );
            
            return Optional.of(correctedCoarse);
        }
    }

    private String formatTimestamp(double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        double seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds);
    }
}