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
    private static final int TIMEOUT_SECONDS = 60;

    @Override
    public StreamMetadata getMetadata(String streamUrl) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
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
            double duration = Double.parseDouble(parts[1])+1.0;
            
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
        
        // AUDIO RULE: Fast-Seek (-ss BEFORE -i) is required for remote audio streams
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", 
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

        // VIDEO RULE: Frame-Accurate Seek (-ss AFTER -i) prevents I-Frame snapping
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
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
        // -f b forces yt-dlp to return a single combined video+audio stream
        ProcessBuilder pb = new ProcessBuilder("yt-dlp", "-f", "b", "-g", targetUrl);
        pb.redirectErrorStream(true); 
        Process process = pb.start();
        String rawUrl = null;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Grab the FIRST http link it finds and ignore anything else
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
        pb.redirectErrorStream(true); // Merge stderr to stdout
        Process process = pb.start();
        
        // Consume FFmpeg's output to prevent OS buffer deadlocks
        process.getInputStream().transferTo(System.out);

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg extraction timed out.");
        }

        // CRITICAL BUG FIX: Ensure FFmpeg didn't silently crash and create a 0-byte file
        if (process.exitValue() != 0) {
            throw new RuntimeException("FFmpeg process failed with exit code: " + process.exitValue());
        }
    }
}