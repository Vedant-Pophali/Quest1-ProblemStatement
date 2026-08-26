package com.extractor.api;

import com.extractor.core.TwoPointerSearch;
import com.extractor.model.ExtractionResult;
import com.extractor.model.StreamMetadata;
import com.extractor.infrastructure.FFmpegAdapter;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);
    
    private final TwoPointerSearch searchOrchestrator;
    private final LiveUpdateSender liveUpdateSender;
    private final FFmpegAdapter fFmpegAdapter;

    public JobController(TwoPointerSearch searchOrchestrator, LiveUpdateSender liveUpdateSender, FFmpegAdapter fFmpegAdapter) {
        this.searchOrchestrator = searchOrchestrator;
        this.liveUpdateSender = liveUpdateSender;
        this.fFmpegAdapter = fFmpegAdapter;
    }

    public void startExtractionJob(Context ctx) {
        JobRequest req = ctx.bodyAsClass(JobRequest.class);
        String jobId = UUID.randomUUID().toString();

        Thread.ofVirtual().start(() -> processJob(jobId, req.url(), req.targetText(), req.threshold()));

        ctx.status(202).json(new JobResponse(jobId, "Job accepted and initializing."));
    }

    private void processJob(String jobId, String targetUrl, String targetText, int threshold) {
        try {
            Thread.sleep(1500);

            liveUpdateSender.sendUpdate(jobId, "INITIALIZING", "Extracting raw stream URL from " + targetUrl);
            String rawUrl = fFmpegAdapter.extractRawStreamUrl(targetUrl);
            
            liveUpdateSender.sendUpdate(jobId, "INITIALIZING", "Fetching stream metadata via ffprobe...");
            StreamMetadata metadata = fFmpegAdapter.getMetadata(rawUrl);
            
            liveUpdateSender.sendUpdate(jobId, "AUDIO_SEARCH_RUNNING", "Launching Audio Pointer and Coarse Visual Scan...");
            
            // Core Search Execution returning the unified result wrapper
            ExtractionResult result = searchOrchestrator.executeSearch(metadata, targetText, threshold);
            
            // PRIORITY 1: Visual Match (Absolute Source of Truth)
            if (result.visualResult().isPresent()) {
                var frame = result.visualResult().get();
                String base64Img = com.extractor.util.ImageEncoder.encodeToBase64(frame.imagePath());
                liveUpdateSender.sendVisualSuccess(jobId, frame.timestamp(), frame.frameNumber(), base64Img);
            } 
            // PRIORITY 2: Audio-Only Fallback
            else if (result.audioTimestamp().isPresent()) {
                double rawSeconds = result.audioTimestamp().get();
                String formattedTime = formatTimestamp(rawSeconds);
                int frameNumber = metadata.calculateFrameNumber(rawSeconds);
                String base64Img = null;

                liveUpdateSender.sendUpdate(jobId, "AUDIO_EXTRACTING_FRAME", "Fetching visual context for audio match...");
                
                try {
                    // Extract exactly 1 frame at the exact audio timestamp
                    List<String> frames = fFmpegAdapter.extractVisualFrames(rawUrl, rawSeconds, rawSeconds + 1.0, 1.0);
                    if (!frames.isEmpty()) {
                        base64Img = com.extractor.util.ImageEncoder.encodeToBase64(frames.getFirst());
                    }
                } catch (Exception e) {
                    log.warn("Could not extract fallback image for audio match.", e);
                }

                liveUpdateSender.sendAudioSuccess(jobId, formattedTime, frameNumber, base64Img);
            } 
            // PRIORITY 3: Total Failure
            else {
                liveUpdateSender.sendUpdate(jobId, "TEXT_NOT_FOUND", "Search exhausted. Text did not appear visually or in audio.");
            }
            
        } catch (Exception e) {
            log.error("Job {} failed critically.", jobId, e);
            liveUpdateSender.sendUpdate(jobId, "SYSTEM_ERROR", "Pipeline crashed: " + e.getMessage());
        }
    }

    private String formatTimestamp(double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        double seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%06.3f", hours, minutes, seconds);
    }

    private record JobRequest(String url, String targetText, int threshold) {}
    private record JobResponse(String jobId, String message) {}
}