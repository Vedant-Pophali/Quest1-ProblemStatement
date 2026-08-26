FROM python:3.11-slim
WORKDIR /app

# Install OS dependencies required for OpenCV
RUN apt-get update && apt-get install -y libglib2.0-0 libgl1-mesa-glx && apt-get clean

# Install Python libraries
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy your ML scripts into the container
COPY . .

EXPOSE 8000
CMD ["uvicorn", "start_server:app", "--host", "0.0.0.0", "--port", "8000"]