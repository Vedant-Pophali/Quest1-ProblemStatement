import logging
import re
from faster_whisper import WhisperModel
from thefuzz import fuzz

logger = logging.getLogger(__name__)

# Initialize globally to prevent reloading the model on every API call.
logger.info("Loading Whisper base.en model into CPU memory...")
audio_model = WhisperModel("base.en", device="cpu", compute_type="int8")

def normalize_text(text: str) -> str:
    """Removes all punctuation and special characters for robust matching."""
    return re.sub(r'[^\w\s]', '', text).strip().lower()

def find_target_timestamp(audio_path: str, target_text: str, fuzzy_threshold: int = 85) -> float:
    """
    Transcribes the audio track and searches for the target text using sequence-preserving matching.
    Utilizes word_timestamps to pinpoint the exact start of the dialogue.
    """
    try:
        segments, info = audio_model.transcribe(audio_path, word_timestamps=True)
        
        target_clean = normalize_text(target_text)
        target_words = target_clean.split()
        if not target_words:
            return None
            
        target_first_word = target_words[0]

        for segment in segments:
            seg_clean = normalize_text(segment.text)
            
            # THE FIX: Prevent catastrophic false positives from short hallucinations!
            # If Whisper transcribes a breath as "I", partial_ratio returns 100 
            # because "i" is a perfect substring of our target. 
            # We enforce that the audio segment must contain at least 50% of the target's length.
            if len(seg_clean) < len(target_clean) * 0.5:
                score_partial = 0
            else:
                score_partial = fuzz.partial_ratio(target_clean, seg_clean)
            
            # 2. Segment-level ratio (exact match)
            score_exact = fuzz.ratio(target_clean, seg_clean)
            
            score = max(score_partial, score_exact)
            
            if score >= fuzzy_threshold:
                exact_start = segment.start
                
                # Check word timestamps to get exact onset
                if getattr(segment, 'words', None) and segment.words:
                    for word_obj in segment.words:
                        clean_word = normalize_text(word_obj.word)
                        if fuzz.ratio(target_first_word, clean_word) >= 80:
                            exact_start = word_obj.start
                            break
                            
                logger.info(f"Audio match found! Score: {score}, Text: '{segment.text}', Exact Timestamp: {exact_start}s")
                return exact_start

        return None
    except Exception as e:
        logger.error(f"Failed to process audio file {audio_path}: {e}")
        raise e