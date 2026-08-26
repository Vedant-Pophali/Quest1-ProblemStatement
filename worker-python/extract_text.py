import logging
from rapidocr_onnxruntime import RapidOCR
from thefuzz import fuzz
from clean_image import preprocess_for_ocr

logger = logging.getLogger(__name__)

# Initialize RapidOCR (Runs natively on ONNX Runtime CPU)
logger.info("Loading RapidOCR ONNX model...")
ocr = RapidOCR()

def analyze_frame_text(image_path: str, target_text: str, fuzzy_threshold: int = 85) -> dict:
    """
    Cleans the image, runs RapidOCR, concatenates all text found on the screen,
    and fuzzy matches the combined text against the target using token arrays.
    """
    try:
        cleaned_image_matrix = preprocess_for_ocr(image_path)
        result, _ = ocr(cleaned_image_matrix)

        if result is None:
            return None

        # Aggregate all detected text on the entire screen into one string
        screen_text_fragments = [detection[1] for detection in result]
        full_screen_text = " ".join(screen_text_fragments)

        target_lower = target_text.lower()
        screen_lower = full_screen_text.lower()

        # token_set_ratio ignores exact ordering and is highly resilient to OCR typos
        score_partial = fuzz.partial_ratio(target_lower, screen_lower)
        score_token = fuzz.token_set_ratio(target_lower, screen_lower)
        score = max(score_partial, score_token)

        if score >= fuzzy_threshold:
            logger.info(f"Visual match found! Score: {score}, Screen Text: '{full_screen_text}'")
            return {
                "extractedText": full_screen_text,
                "boundingBox": result[0][0] if len(result) > 0 else [],
                "ocrConfidence": 1.0, 
                "fuzzyScore": float(score)
            }

        return None

    except Exception as e:
        logger.error(f"OCR processing failed for {image_path}: {e}")
        raise e