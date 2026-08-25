package com.extractor.api;

import com.extractor.core.TwoPointerSearch;
import com.extractor.model.StreamMetadata;
import com.extractor.infrastructure.FFmpegAdapter;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;

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
        // Parse basic JSON input: { "url": "...", "targetText": "..." }
        JobRequest req = ctx.bodyAsClass(JobRequest.class);
        String jobId = UUID.randomUUID().toString();

        // Offload the entire process to a Virtual Thread to keep the HTTP server strictly non-blocking
        Thread.ofVirtual().start(() -> processJob(jobId, req.url(), req.targetText()));

        // Return immediately so the frontend can open the SSE connection
        ctx.status(202).json(new JobResponse(jobId, "Job accepted and initializing."));
    }

    private void processJob(String jobId, String targetUrl, String targetText) {
        try {
            liveUpdateSender.sendUpdate(jobId, "INITIALIZING", "Extracting raw stream URL from " + targetUrl);
            String rawUrl = fFmpegAdapter.extractRawStreamUrl(targetUrl);
            
            // In a real implementation, you'd use ffprobe to get exact duration and FPS here
            StreamMetadata metadata = new StreamMetadata(rawUrl, 24.0, 3600.0, null);
            
            liveUpdateSender.sendUpdate(jobId, "AUDIO_SEARCH_RUNNING", "Launching Audio Pointer and Coarse Visual Scan...");
            
            var result = searchOrchestrator.executeSearch(metadata, targetText);
            
            if (result.isPresent()) {
                liveUpdateSender.sendUpdate(jobId, "SUCCESS", "Frame found! " + result.get().timestamp());
            } else {
                liveUpdateSender.sendUpdate(jobId, "TEXT_NOT_FOUND", "Search exhausted. Text did not appear.");
            }
            
        } catch (Exception e) {
            log.error("Job {} failed critically.", jobId, e);
            liveUpdateSender.sendUpdate(jobId, "SYSTEM_ERROR", "Pipeline crashed: " + e.getMessage());
        }
    }

    private record JobRequest(String url, String targetText) {}
    private record JobResponse(String jobId, String message) {}
}