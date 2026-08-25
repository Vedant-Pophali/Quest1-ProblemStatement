package com.extractor.infrastructure;

import com.extractor.core.TextRecognizer;
import com.extractor.model.FrameResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class PythonOcrClient implements TextRecognizer {
    private static final Logger log = LoggerFactory.getLogger(PythonOcrClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String pythonServerUrl;

    public PythonOcrClient(String pythonServerUrl) {
        this.pythonServerUrl = pythonServerUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Optional<FrameResult> recognizeText(String imagePath, String targetText) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(
                new OcrRequest(imagePath, targetText)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonServerUrl + "/api/v1/recognize-text"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 404) {
            return Optional.empty(); // Text not found in image
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Python worker failed with status: " + response.statusCode());
        }

        return Optional.of(objectMapper.readValue(response.body(), FrameResult.class));
    }

    @Override
    public Optional<Double> recognizeAudioTimestamp(String audioPath, String targetText) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(
                new AudioRequest(audioPath, targetText)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonServerUrl + "/api/v1/recognize-audio"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2)) // Audio transcription takes longer
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        return Optional.of(root.get("timestampSeconds").asDouble());
    }

    // Lightweight records for JSON serialization
    private record OcrRequest(String imagePath, String targetText) {}
    private record AudioRequest(String audioPath, String targetText) {}
}