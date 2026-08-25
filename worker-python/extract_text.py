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
    Cleans the image, runs RapidOCR, and fuzzy matches against the target.
    Returns a dictionary containing bounding boxes, extracted text, and confidence,
    or None if the text is not found.
    """
    try:
        # 1. Feed the image through our OpenCV enhancement pipeline
        cleaned_image_matrix = preprocess_for_ocr(image_path)

        # 2. Execute OCR on the NumPy array
        # RapidOCR returns a tuple: (result_list, elapse_time)
        result, _ = ocr(cleaned_image_matrix)

        if result is None:
            return None

        best_score = 0
        best_match = None
        target_lower = target_text.lower()

        # 3. Iterate through detected text blocks
        # result format: [[ [x1,y1], [x2,y2], [x3,y3], [x4,y4] ], "text", confidence]
        for detection in result:
            box, extracted_string, confidence = detection
            
            # Use partial_ratio to handle extra characters/noise around the dialogue
            score = fuzz.partial_ratio(target_lower, extracted_string.lower())

            if score > best_score:
                best_score = score
                best_match = {
                    "extractedText": extracted_string,
                    "boundingBox": box,
                    "ocrConfidence": float(confidence),
                    "fuzzyScore": score
                }

        # 4. Return result if it meets our 80% threshold
        if best_score >= fuzzy_threshold:
            logger.info(f"Visual match found! Score: {best_score}, Text: '{best_match['extractedText']}'")
            return best_match

        return None

    except Exception as e:
        logger.error(f"OCR processing failed for {image_path}: {e}")
        raise e