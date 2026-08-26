package com.extractor.core;

import com.extractor.model.FrameResult;
import java.util.Optional;

public interface TextRecognizer {
    /**
     * Analyzes an image to find the target text using fuzzy matching.
     * 
     * @param imagePath  The local path to the extracted frame.
     * @param targetText The text to search for (e.g., "My mind rebels at stagnation").
     * @param threshold  The dynamic accuracy threshold (60-100) defined by the user.
     * @return An Optional containing the FrameResult if the text meets the confidence threshold,
     *         or an empty Optional if the text is not present.
     * @throws Exception If the OCR engine crashes or times out.
     */
    Optional<FrameResult> recognizeText(String imagePath, String targetText, int threshold) throws Exception;
    /**
     * Similar to visual text recognition, but acts as the Audio Pointer.
     * 
     * @param audioPath  The local path to the extracted audio track.
     * @param targetText The dialogue to search for.
     * @param threshold  The dynamic accuracy threshold (60-100) defined by the user.
     * @return The exact timestamp in seconds if the audio contains the phrase.
     */
    Optional<Double> recognizeAudioTimestamp(String audioPath, String targetText, int threshold) throws Exception;
}