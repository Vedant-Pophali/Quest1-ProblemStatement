import logging
from faster_whisper import WhisperModel
from thefuzz import fuzz

logger = logging.getLogger(__name__)

# Initialize globally to prevent reloading the model on every API call.
# 'base.en' is highly efficient and accurate enough for clear dialogue.
logger.info("Loading Whisper base.en model into CPU memory...")
audio_model = WhisperModel("base.en", device="cpu", compute_type="int8")

def find_target_timestamp(audio_path: str, target_text: str, fuzzy_threshold: int = 85) -> float:
    """
    Transcribes the audio track and searches for the target text using fuzzy matching.
    Returns the start timestamp in seconds if found, otherwise returns None.
    """
    try:
        # word_timestamps=True allows us to pinpoint the exact start of the dialogue
        segments, info = audio_model.transcribe(audio_path, word_timestamps=True)
        
        target_lower = target_text.lower()

        for segment in segments:
            # Check the transcribed segment against our target
            score = fuzz.partial_ratio(target_lower, segment.text.lower())
            
            if score >= fuzzy_threshold:
                logger.info(f"Audio match found! Score: {score}, Text: '{segment.text}', Timestamp: {segment.start}s")
                return segment.start

        return None
    except Exception as e:
        logger.error(f"Failed to process audio file {audio_path}: {e}")
        raise e