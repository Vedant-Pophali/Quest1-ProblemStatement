import uvicorn
from fastapi import FastAPI, Response, status
from pydantic import BaseModel
from typing import Optional
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
def recognize_text(req: OcrRequest, response: Response):
    ocr_result = analyze_frame_text(req.imagePath, req.targetText)
    
    if ocr_result is None:
        response.status_code = status.HTTP_404_NOT_FOUND
        return {"detail": "Target text not found in frame."}
        
    return FrameResult(
        timestamp="00:00:00.000", # Java orchestrator calculates this
        frameNumber=0,            # Java orchestrator calculates this
        extractedText=ocr_result["extractedText"],
        imagePath=req.imagePath,
        confidenceScore=ocr_result["fuzzyScore"]
    )

@app.post("/api/v1/recognize-audio")
def recognize_audio(req: AudioRequest, response: Response):
    timestamp = find_target_timestamp(req.audioPath, req.targetText)
    
    if timestamp is None:
        response.status_code = status.HTTP_404_NOT_FOUND
        return {"detail": "Target text not found in audio track."}
        
    return {"timestampSeconds": timestamp}

if __name__ == "__main__":
    # Runs on port 8000 to match the Java PythonOcrClient configuration
    uvicorn.run("start_server:app", host="127.0.0.1", port=8000, reload=True)