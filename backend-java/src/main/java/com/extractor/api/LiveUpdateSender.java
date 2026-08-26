package com.extractor.api;

import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LiveUpdateSender {
    private static final Logger log = LoggerFactory.getLogger(LiveUpdateSender.class);
    
    // Thread-safe map mapping a specific Job ID to a connected frontend client
    private final Map<String, SseClient> clients = new ConcurrentHashMap<>();

    public void addClient(String jobId, SseClient client) {
        clients.put(jobId, client);
        client.onClose(() -> {
            clients.remove(jobId);
            log.info("SSE Client disconnected for Job: {}", jobId);
        });
    }
    /**
     * Broadcasts state changes or log messages directly to the browser.
     */
    public void sendUpdate(String jobId, String state, String message) {
        SseClient client = clients.get(jobId);
        if (client != null) {
            String jsonPayload = String.format("{\"state\":\"%s\", \"message\":\"%s\"}", state, message);
            client.sendEvent("job-update", jsonPayload);
        }
    }
    /**
     * Broadcasts a successful visual match payload containing the Base64 image.
     */
    public void sendVisualSuccess(String jobId, String timestamp, int frameNumber, String base64Image) {
        SseClient client = clients.get(jobId);
        if (client != null) {
            // Construct a robust JSON payload. (In production, use Jackson/Gson, but this keeps our dependencies zero)
            String jsonPayload = String.format(
                "{\"state\":\"SUCCESS_VISUAL\", \"message\":\"Visual match found!\", \"timestamp\":\"%s\", \"frameNumber\":%d, \"image\":\"%s\"}",
                timestamp, frameNumber, base64Image
            );
            client.sendEvent("job-update", jsonPayload);
        }
    }
    // Add this beneath your sendVisualSuccess method
    public void sendAudioSuccess(String jobId, String formattedTimestamp) {
        SseClient client = clients.get(jobId);
        if (client != null) {
            String jsonPayload = String.format(
                "{\"state\":\"SUCCESS_AUDIO\", \"message\":\"Text not found visually. Audio match located.\", \"timestamp\":\"%s\"}",
                formattedTimestamp
            );
            client.sendEvent("job-update", jsonPayload);
        }
    }
}