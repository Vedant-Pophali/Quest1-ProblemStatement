package com.extractor.infrastructure;

import com.extractor.core.MediaExtractor;
import com.extractor.model.StreamMetadata;
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
    
    // Increased to 600 seconds (10 mins) to allow full movie chunk processing over HLS
    private static final int TIMEOUT_SECONDS = 600;
    
    // Standard User-Agent to bypass CDN 403 Forbidden blocks
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    public StreamMetadata getMetadata(String streamUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", 
                "-user_agent", USER_AGENT, // Anti-bot spoofing
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate,duration",
                "-of", "csv=p=0",
                streamUrl
        );

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor(10, TimeUnit.SECONDS);

        try {
            String[] parts = output.split(",");
            String[] fpsParts = parts[0].split("/");
            double fps = Double.parseDouble(fpsParts[0]) / Double.parseDouble(fpsParts[1]);
            double duration = Double.parseDouble(parts[1]) + 1.0;
            
            log.info("Dynamically loaded metadata - FPS: {}, Duration: {}s", fps, duration);
            return new StreamMetadata(streamUrl, fps, duration, null);
        } catch (Exception e) {
            log.warn("Failed to parse ffprobe metadata, falling back to defaults.", e);
            return new StreamMetadata(streamUrl, 30.0, 3600.0, null);
        }
    }

    @Override
    public String extractAudio(String streamUrl, double startTime, double duration) throws Exception {
        Path tempAudio = Files.createTempFile("audio_chunk_", ".wav");
        
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", 
                "-user_agent", USER_AGENT,
                // Make HTTP connections highly resilient for ok.ru .ts chunks
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(duration),
                "-i", streamUrl,
                "-vn", "-ac", "1", "-ar", "16000",
                tempAudio.toString()
        );
        executeProcess(pb);
        return tempAudio.toString();
    }
    
    @Override
    public List<String> extractVisualFrames(String streamUrl, double startTime, double endTime, double fps) throws Exception {
        double duration = endTime - startTime;
        Path tempDir = Files.createTempDirectory("frames_" + UUID.randomUUID() + "_");
        String outputPattern = tempDir.resolve("frame_%04d.jpg").toString();

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-user_agent", USER_AGENT, // Anti-bot spoofing
                // Make HTTP connections highly resilient for ok.ru .ts chunks
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-i", streamUrl, 
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(duration),
                "-vf", "fps=" + fps,
                "-q:v", "2",
                outputPattern
        );
        executeProcess(pb);

        File dir = tempDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null) return List.of();
        
        return Arrays.stream(files).map(File::getAbsolutePath).sorted().collect(Collectors.toList());
    }

    @Override
    public String extractRawStreamUrl(String targetUrl) throws Exception {
        // Prioritize MP4 to avoid fragmented HLS (.m3u8) 403 blocks
        ProcessBuilder pb = new ProcessBuilder("yt-dlp", "-f", "best[ext=mp4]/best", "-g", targetUrl);
        pb.redirectErrorStream(true); 
        Process process = pb.start();
        String rawUrl = null;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http") && rawUrl == null) {
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

    private void executeProcess(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true); 
        Process process = pb.start();
        
        process.getInputStream().transferTo(System.out);

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg extraction timed out.");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("FFmpeg process failed with exit code: " + process.exitValue());
        }
    }
}