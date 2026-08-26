package com.extractor;

import com.extractor.api.JobController;
import com.extractor.api.LiveUpdateSender;
import com.extractor.core.ChunkManager;
import com.extractor.core.TextRecognizer;
import com.extractor.core.TwoPointerSearch;
import com.extractor.infrastructure.CircuitBreaker;
import com.extractor.infrastructure.FFmpegAdapter;
import com.extractor.infrastructure.PythonOcrClient;
import com.extractor.model.FrameResult;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Optional;

public class App {
    public static void main(String[] args) {
        // 1. Initialize Infrastructure (Adapters & Resilience)
        FFmpegAdapter fFmpegAdapter = new FFmpegAdapter();
        PythonOcrClient basePythonClient = new PythonOcrClient("http://localhost:8000");
        CircuitBreaker breaker = new CircuitBreaker(3, 10000);

        // 2. Wrap Python client in the Circuit Breaker transparently
        TextRecognizer resilientRecognizer = new TextRecognizer() {
            @Override
            public Optional<FrameResult> recognizeText(String imagePath, String targetText, int threshold) throws Exception {
                return breaker.execute(() -> basePythonClient.recognizeText(imagePath, targetText, threshold));
            }

            @Override
            public Optional<Double> recognizeAudioTimestamp(String audioPath, String targetText, int threshold) throws Exception {
                return breaker.execute(() -> basePythonClient.recognizeAudioTimestamp(audioPath, targetText, threshold));
            }
        };

        // 3. Initialize Core Business Logic
        ChunkManager chunkManager = new ChunkManager();
        TwoPointerSearch searchOrchestrator = new TwoPointerSearch(fFmpegAdapter, resilientRecognizer, chunkManager);

        // 4. Initialize API Layer
        LiveUpdateSender liveUpdateSender = new LiveUpdateSender();
        JobController jobController = new JobController(searchOrchestrator, liveUpdateSender, fFmpegAdapter);

        // 5. Start Lightweight Web Server on Virtual Threads
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("frontend", Location.EXTERNAL); 
        }).start(7070);

        // 6. Define Routes
        app.post("/api/jobs", jobController::startExtractionJob);
        
        // SSE Endpoint
        app.sse("/api/jobs/{id}/stream", client -> {
            String jobId = client.ctx().pathParam("id");
            client.keepAlive(); 
            liveUpdateSender.addClient(jobId, client);
        });
        
        System.out.println("Java Orchestrator started on http://localhost:7070");
    }
}