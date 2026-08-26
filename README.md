# Video Dialogue & Text Extraction Engine

A highly resilient, polyglot microservices system that pinpoints the exact timestamp of target dialogue in video streams (YouTube, ok.ru) using Optical Character Recognition (OCR) and fallback audio transcription. Engineered with dynamic temporal boundaries and virtual threads to process long-form media without memory bottlenecks.

## 🛠️ Tech Stack

* **Orchestrator:** Java 21 (Javalin, Virtual Threads)
* **Machine Learning Worker:** Python 3.11 (FastAPI)
* **AI Models:** Faster-Whisper (Audio), RapidOCR ONNX (Visual Computer Vision)
* **Infrastructure:** FFmpeg, `yt-dlp`, Server-Sent Events (SSE)
* **Frontend:** Vanilla HTML/CSS/JS

## 📋 Prerequisites

If you are running this locally without Docker, ensure you have the following installed and added to your system's `PATH`:

* **Java 21** (Required for Virtual Threads)
* **Python 3.11+**
* **FFmpeg & FFprobe**

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/video-extraction-engine.git
cd video-extraction-engine
```

### 2. Start the Python ML Worker

Open a terminal and start the machine learning API:

```bash
cd worker-python
pip install -r requirements.txt
uvicorn start_server:app --host 0.0.0.0 --port 8000
```

### 3. Start the Java Orchestrator

Open a second terminal and boot up the Java backend (which also serves the frontend):

```bash
cd backend-java
mvn clean compile exec:java
```

> **Note:** If using an IDE like IntelliJ or VS Code, you can simply run the `App.java` main class.

### 4. Access the UI

Open your web browser and navigate to:

```
http://localhost:7070
```

## 🐳 Running with Docker (Recommended)

To avoid manual installations, you can spin up the entire polyglot architecture (Java, Python, FFmpeg, and the UI) using Docker:

```bash
docker-compose up --build
```

## 🧠 Architecture Highlights

* **Polyglot Design:** Leverages Java's high-speed concurrency for HTTP routing/chunking, while offloading heavy AI model inference to a dedicated Python process.
* **Dual-Pointer Search:** Employs a concurrent Producer-Consumer model where an Audio Pointer mathematically shrinks the workload boundary for the Visual OCR Pointer in real-time.
* **Resilience:** Built with the Strategy Pattern and custom Circuit Breakers to gracefully handle third-party CDN rate-limiting, missing codecs, and network TCP resets.