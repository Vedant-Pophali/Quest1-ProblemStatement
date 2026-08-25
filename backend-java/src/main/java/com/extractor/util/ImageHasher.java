package com.extractor.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageHasher {
    private static final Logger log = LoggerFactory.getLogger(ImageHasher.class);
    private static final String ALGORITHM = "SHA-256";

    /**
     * Generates a SHA-256 hex string for a given file.
     */
    public static String hashFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            byte[] fileBytes = Files.readAllBytes(path);
            
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash file: {}", filePath, e);
            return "HASH_FAILED_" + System.currentTimeMillis();
        }
    }
}