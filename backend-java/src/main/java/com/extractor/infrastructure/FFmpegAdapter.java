package com.extractor.infrastructure;

import com.extractor.core.MediaExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FFmpegAdapter implements MediaExtractor {
    private static final Logger log = LoggerFactory.getLogger(FFmpegAdapter.class);
    private static final int TIMEOUT_SECONDS = 60;

    @Override
    public String extractAudio(String streamUrl, double startTime, double duration) throws Exception {
        Path tempAudio = Files.createTempFile("audio_chunk_", ".wav");
        
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", 
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(duration),
                "-i", streamUrl,
                "-vn", "-ac", "1", "-ar", "16000", // 16kHz Mono for Whisper
                tempAudio.toString()
        );
        
        executeProcess(pb);
        return tempAudio.toString();
    }

    @Override
    public List<String> extractVisualFrames(String streamUrl, double startTime, double endTime, double fps) throws Exception {
        double duration = endTime - startTime;
        Path tempDir = Files.createTempDirectory("frames_" + UUID.randomUUID() + "_");
        
        // Output format: /tmp/frames_uuid/frame_0001.jpg
        String outputPattern = tempDir.resolve("frame_%04d.jpg").toString();

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(duration),
                "-i", streamUrl,
                "-vf", "fps=" + fps,
                "-q:v", "2", // High JPEG quality for OCR
                outputPattern
        );

        executeProcess(pb);

        // Read the directory and return sequentially ordered paths
        File dir = tempDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        
        if (files == null) return List.of();
        
        return Arrays.stream(files)
                .map(File::getAbsolutePath)
                .sorted()
                .collect(Collectors.toList());
    }

    private void executeProcess(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Consume output buffer to prevent hangs
        process.getInputStream().transferTo(System.out);

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg extraction timed out.");
        }
    }
    @Override
    public String extractRawStreamUrl(String targetUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("yt-dlp", "-g", targetUrl);
        pb.redirectErrorStream(true); 
        
        Process process = pb.start();
        String rawUrl = null;

        // Consume the stream immediately
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http")) {
                    rawUrl = line.trim();
                }
            }
        }

        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0 || rawUrl == null) {
            process.destroyForcibly();
            throw new RuntimeException("Failed to extract stream URL via yt-dlp.");
        }
        return rawUrl;
    }
}