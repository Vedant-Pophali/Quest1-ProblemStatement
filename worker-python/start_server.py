import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from extract_audio import find_target_timestamp
from extract_text import analyze_frame_text

# Initialize FastAPI application
app = FastAPI(title="Video Text & Audio Extraction Worker")

# Define strict Pydantic models matching the Java JSON payloads
class OcrRequest(BaseModel):
    imagePath: str
    targetText: str

class AudioRequest(BaseModel):
    audioPath: str
    targetText: str

class FrameResult(BaseModel):
    timestamp: str
    frameNumber: int
    extractedText: str
    imagePath: str
    confidenceScore: float

@app.post("/api/v1/recognize-text", response_model=FrameResult)
def recognize_text(req: OcrRequest):
    """
    Receives an image path from Java, preprocesses it, runs OCR, 
    and checks for the target text using fuzzy matching.
    """
    ocr_result = analyze_frame_text(req.imagePath, req.targetText)
    
    if ocr_result is None:
        # Use HTTPException to bypass Pydantic response_model validation for errors
        raise HTTPException(status_code=404, detail="Target text not found in frame.")
        
    return FrameResult(
        timestamp="00:00:00.000",
        frameNumber=0,
        extractedText=ocr_result["extractedText"],
        imagePath=req.imagePath,
        confidenceScore=ocr_result["fuzzyScore"]
    )

@app.post("/api/v1/recognize-audio")
def recognize_audio(req: AudioRequest):
    """
    Receives an audio track path, runs faster-whisper, 
    and returns the timestamp if the phrase is spoken.
    """
    timestamp = find_target_timestamp(req.audioPath, req.targetText)
    
    if timestamp is None:
        raise HTTPException(status_code=404, detail="Target text not found in audio track.")
        
    return {"timestampSeconds": timestamp}

if __name__ == "__main__":
    uvicorn.run("start_server:app", host="127.0.0.1", port=8000, reload=True)