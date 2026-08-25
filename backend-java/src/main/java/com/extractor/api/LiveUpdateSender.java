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
}