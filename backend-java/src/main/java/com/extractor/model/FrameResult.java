package com.extractor.model;

public record FrameResult(
    String timestamp,       // Format: HH:MM:SS.sss
    int frameNumber,
    String extractedText,
    String imagePath,       // Local path or Base64 string of the saved frame
    double confidenceScore  // Fuzzy match score to validate the result
) {}