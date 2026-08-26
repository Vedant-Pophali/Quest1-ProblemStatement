package com.extractor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class ImageEncoder {
    private static final Logger log = LoggerFactory.getLogger(ImageEncoder.class);

    public static String encodeToBase64(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            byte[] fileBytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(fileBytes);
        } catch (Exception e) {
            log.error("Failed to encode image to Base64: {}", imagePath, e);
            return null;
        }
    }
}