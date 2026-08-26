import logging
from faster_whisper import WhisperModel
from thefuzz import fuzz

logger = logging.getLogger(__name__)

# Initialize globally to prevent reloading the model on every API call.
logger.info("Loading Whisper base.en model into CPU memory...")
audio_model = WhisperModel("base.en", device="cpu", compute_type="int8")

def find_target_timestamp(audio_path: str, target_text: str, fuzzy_threshold: int = 85) -> float:
    """
    Transcribes the audio track and searches for the target text using fuzzy matching.
    Utilizes word_timestamps to pinpoint the exact start of the dialogue.
    """
    try:
        segments, info = audio_model.transcribe(audio_path, word_timestamps=True)
        
        target_lower = target_text.lower()
        target_words = target_lower.split()
        if not target_words:
            return None
            
        target_first_word = target_words[0]

        for segment in segments:
            # Combine algorithms: Token Set is better for out-of-order words/noise
            score_partial = fuzz.partial_ratio(target_lower, segment.text.lower())
            score_token = fuzz.token_set_ratio(target_lower, segment.text.lower())
            score = max(score_partial, score_token)
            
            if score >= fuzzy_threshold:
                exact_start = segment.start
                
                # Dive into the word-level timestamps to find the exact start of the phrase
                if getattr(segment, 'words', None):
                    for word_obj in segment.words:
                        clean_word = word_obj.word.lower().strip()
                        # If we find the first word of our target, snap the timestamp to it
                        if fuzz.ratio(target_first_word, clean_word) >= 80:
                            exact_start = word_obj.start
                            break
                            
                logger.info(f"Audio match found! Score: {score}, Text: '{segment.text}', Exact Timestamp: {exact_start}s")
                return exact_start

        return None
    except Exception as e:
        logger.error(f"Failed to process audio file {audio_path}: {e}")
        raise e