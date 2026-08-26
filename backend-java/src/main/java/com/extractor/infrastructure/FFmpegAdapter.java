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
    
    private static final int TIMEOUT_SECONDS = 600;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    public StreamMetadata getMetadata(String streamUrl) throws Exception {
        String videoStream = streamUrl.contains("|||") ? streamUrl.split("\\|\\|\\|")[0] : streamUrl;
        
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", 
                "-user_agent", USER_AGENT,
                "-headers", getDomainReferer(videoStream), // <-- DYNAMIC REFERER
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate,duration",
                "-of", "csv=p=0",
                videoStream
        );

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor(10, TimeUnit.SECONDS);

        try {
            if (output.isBlank() || !output.contains(",")) {
                throw new IllegalArgumentException("ffprobe returned empty or invalid data: " + output);
            }

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
        String audioStream = streamUrl.contains("|||") ? streamUrl.split("\\|\\|\\|")[1] : streamUrl;
        
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", 
                "-user_agent", USER_AGENT,
                "-headers", getDomainReferer(audioStream), // <-- DYNAMIC REFERER
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-ss", String.valueOf(startTime),
                "-t", String.valueOf(duration),
                "-i", audioStream,
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

        String videoStream = streamUrl.contains("|||") ? streamUrl.split("\\|\\|\\|")[0] : streamUrl;

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-user_agent", USER_AGENT,
                "-headers", getDomainReferer(videoStream), // <-- DYNAMIC REFERER
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-i", videoStream, 
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
        ProcessBuilder pb = new ProcessBuilder(
                "python", "-m", "yt_dlp", 
                "--no-check-certificate",
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best", 
                "--user-agent", USER_AGENT, 
                "-g", targetUrl
        );
        pb.redirectErrorStream(true); 
        Process process = pb.start();
        
        String videoUrl = null;
        String audioUrl = null;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[yt-dlp] {}", line); 
                String trimmed = line.trim();
                
                if (trimmed.startsWith("http")) {
                    if (videoUrl == null) {
                        videoUrl = trimmed; 
                    } else if (audioUrl == null) {
                        audioUrl = trimmed; 
                    }
                }
            }
        }
        
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("yt-dlp process timed out after 120 seconds.");
        }
        
        if (videoUrl == null) {
            throw new RuntimeException("Failed to extract stream URL via yt-dlp.");
        }
        
        if (audioUrl != null) {
            return videoUrl + "|||" + audioUrl;
        }
        
        return videoUrl;
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

    /**
     * Helper method to dynamically generate the correct Referer header based on the CDN.
     */
    private String getDomainReferer(String url) {
        if (url.contains("okcdn") || url.contains("ok.ru")) {
            return "Referer: https://ok.ru/\r\n";
        } else if (url.contains("googlevideo") || url.contains("youtube")) {
            return "Referer: https://www.youtube.com/\r\n";
        }
        return "Referer: " + url + "\r\n"; 
    }
}