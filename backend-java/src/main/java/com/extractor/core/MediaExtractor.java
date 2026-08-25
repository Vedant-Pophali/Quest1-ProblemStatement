package com.extractor.core;

import java.util.List;

public interface MediaExtractor {
    
    /**
     * Extracts the audio track from the media stream.
     * 
     * @param streamUrl The raw media URL.
     * @param startTime The timestamp to start extraction (in seconds).
     * @param duration  The duration of audio to extract (in seconds).
     * @return The local file path to the extracted lightweight .wav file.
     * @throws Exception If the extraction process fails.
     */
    String extractAudio(String streamUrl, double startTime, double duration) throws Exception;

    /**
     * Extracts visual frames from a specific time window into local temporary storage.
     * 
     * @param streamUrl The raw media URL.
     * @param startTime The start of the time window (in seconds).
     * @param endTime   The end of the time window (in seconds).
     * @param fps       The frame rate to extract at (e.g., 1 for coarse search, 24 for fine search).
     * @return A sequentially ordered list of file paths to the extracted images.
     * @throws Exception If the extraction process fails.
     */
    List<String> extractVisualFrames(String streamUrl, double startTime, double endTime, double fps) throws Exception;

    /**
     * Extracts the raw media stream URL bypassing the web player.
     * 
     * @param targetUrl The public webpage URL containing the video (e.g., an ok.ru link).
     * @return The direct raw media stream URL (typically an .m3u8 or .mp4 link) used for processing.
     * @throws Exception If the extraction tool (e.g., yt-dlp) fails or times out.
     */
    String extractRawStreamUrl(String targetUrl) throws Exception;
}