import logging
import re
from rapidocr_onnxruntime import RapidOCR
from thefuzz import fuzz
from clean_image import preprocess_for_ocr

logger = logging.getLogger(__name__)

# Initialize RapidOCR (Runs natively on ONNX Runtime CPU)
logger.info("Loading RapidOCR ONNX model...")
ocr = RapidOCR()

def normalize_text(text: str) -> str:
    """Removes all punctuation and special characters for robust matching."""
    return re.sub(r'[^\w\s]', '', text).strip().lower()

def analyze_frame_text(image_path: str, target_text: str, fuzzy_threshold: int = 85) -> dict:
    """
    Cleans the image, runs RapidOCR, concatenates detected text lines,
    and performs sequential substring matching to eliminate false positives.
    """
    try:
        cleaned_image_matrix = preprocess_for_ocr(image_path)
        result, _ = ocr(cleaned_image_matrix)

        if not result:
            return None

        # 1. Collect all detected text lines
        lines = [detection[1].strip() for detection in result if detection[1].strip()]
        if not lines:
            return None

        target_clean = normalize_text(target_text)
        full_screen_clean = normalize_text(" ".join(lines))

        # 2. Check each line individually first (direct hit)
        for detection in result:
            box, line_text, conf = detection
            line_clean = normalize_text(line_text)
            
            # Ratio checks on specific text block
            ratio = fuzz.ratio(target_clean, line_clean)
            partial = fuzz.partial_ratio(target_clean, line_clean)
            
            if ratio >= fuzzy_threshold or (len(line_clean) >= len(target_clean) * 0.8 and partial >= fuzzy_threshold):
                logger.info(f"Direct visual match found on line! Score: {max(ratio, partial)}, Line: '{line_text}'")
                return {
                    "extractedText": line_text,
                    "boundingBox": box,
                    "ocrConfidence": float(conf) if conf is not None else 1.0,
                    "fuzzyScore": float(max(ratio, partial))
                }

        # 3. Aggregated Screen Match (handles multi-line text / kinetic typography)
        # partial_ratio requires contiguous character/word alignments
        score_partial = fuzz.partial_ratio(target_clean, full_screen_clean)

        # Sliding window over words in full_screen_text matching target word length
        target_words = target_clean.split()
        target_word_len = len(target_words)
        screen_words = full_screen_clean.split()

        max_window_score = 0
        if len(screen_words) >= target_word_len:
            for i in range(len(screen_words) - target_word_len + 1):
                window = " ".join(screen_words[i : i + target_word_len])
                window_score = fuzz.ratio(target_clean, window)
                if window_score > max_window_score:
                    max_window_score = window_score

        best_score = max(score_partial if len(full_screen_clean) >= len(target_clean) * 0.7 else 0, max_window_score)

        if best_score >= fuzzy_threshold:
            logger.info(f"Aggregated visual match found! Score: {best_score}, Screen Text: '{full_screen_clean}'")
            return {
                "extractedText": full_screen_clean, # Return the clean sequence that matched
                "boundingBox": result[0][0] if len(result) > 0 else [],
                "ocrConfidence": 1.0,
                "fuzzyScore": float(best_score)
            }

        return None

    except Exception as e:
        logger.error(f"OCR processing failed for {image_path}: {e}")
        raise e