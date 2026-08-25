import cv2
import numpy as np

def preprocess_for_ocr(image_path: str, output_path: str = None) -> np.ndarray:
    """
    Reads an image from disk, converts to grayscale, and maximizes text visibility
    against the background using contrast enhancement and binarization.
    """
    # 1. Read the image from the path provided by Java
    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Failed to read image at path: {image_path}")

    # 2. Convert to Grayscale
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # 3. Enhance Contrast (CLAHE handles uneven video lighting/shadows perfectly)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    contrast_enhanced = clahe.apply(gray)

    # 4. Binarization (Otsu's method calculates the optimal threshold to separate text from background)
    _, binary_image = cv2.threshold(contrast_enhanced, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # Optional: Save the cleaned image to disk for debugging if needed
    if output_path:
        cv2.imwrite(output_path, binary_image)

    return binary_image