document.getElementById('extraction-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const url = document.getElementById('url').value;
    const targetText = document.getElementById('target-text').value;
    const threshold = parseInt(document.getElementById('threshold').value, 10);
    const submitBtn = document.getElementById('submit-btn');
    const terminal = document.getElementById('terminal');
    const resultPanel = document.getElementById('result-panel');
    
    // Reset UI
    submitBtn.disabled = true;
    terminal.innerHTML = '';
    resultPanel.classList.add('hidden');
    document.getElementById('res-image').classList.add('hidden');
    document.getElementById('res-audio-msg').classList.add('hidden');
    addLog('system', 'Initiating connection to Java orchestrator...');

    try {
        // 1. Submit the Job to Java Backend (now includes threshold)
        const response = await fetch('/api/jobs', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, targetText, threshold })
        });

        if (!response.ok) throw new Error('Failed to start job');
        
        const data = await response.json();
        const jobId = data.jobId;
        addLog('system', `Job accepted. Assigned ID: ${jobId}`);

        // 2. Connect to Server-Sent Events (SSE) for live telemetry
        const eventSource = new EventSource(`/api/jobs/${jobId}/stream`);

        eventSource.addEventListener('job-update', (event) => {
            const payload = JSON.parse(event.data);
            
            // Format terminal output
            let logClass = 'normal';
            if (payload.state === 'SYSTEM_ERROR') logClass = 'error';
            if (payload.state.startsWith('SUCCESS')) logClass = 'success';
            
            addLog(logClass, `[${payload.state}] ${payload.message}`);

            // Handle Completion States
            if (payload.state.startsWith('SUCCESS') || payload.state === 'TEXT_NOT_FOUND' || payload.state === 'SYSTEM_ERROR') {
                eventSource.close();
                submitBtn.disabled = false;
                
                if (payload.state === 'SUCCESS_VISUAL') {
                    // Inject explicit JSON data
                    document.getElementById('res-timestamp').textContent = payload.timestamp;
                    document.getElementById('res-frame').textContent = payload.frameNumber;
                    document.getElementById('res-text').textContent = targetText;
                    
                    // Render Base64 Image
                    const imgElement = document.getElementById('res-image');
                    imgElement.src = `data:image/jpeg;base64,${payload.image}`;
                    imgElement.classList.remove('hidden');
                    
                    resultPanel.classList.remove('hidden');
                } 
                else if (payload.state === 'SUCCESS_AUDIO') {
                    document.getElementById('res-timestamp').textContent = payload.timestamp;
                    document.getElementById('res-frame').textContent = `${payload.frameNumber} (Audio Match Context)`;
                    document.getElementById('res-text').textContent = targetText;
                    
                    // Show Audio Fallback Message
                    const audioMsg = document.getElementById('res-audio-msg');
                    audioMsg.textContent = 'Target text was spoken, but did not appear visually on screen.';
                    audioMsg.classList.remove('hidden');
                    
                    // Render the contextual Base64 Image if FFmpeg successfully grabbed it
                    if (payload.image) {
                        const imgElement = document.getElementById('res-image');
                        imgElement.src = `data:image/jpeg;base64,${payload.image}`;
                        imgElement.classList.remove('hidden');
                    }
                    
                    resultPanel.classList.remove('hidden');
                }
            }
        });

        eventSource.onerror = (err) => {
            // Check if the connection is completely dead (readyState 2 = CLOSED)
            if (eventSource.readyState === EventSource.CLOSED) {
                addLog('error', 'Stream disconnected unexpectedly. Check Java console for fatal errors.');
            } else {
                addLog('error', 'Stream interrupted. Attempting to reconnect...');
            }
            
            eventSource.close();
            submitBtn.disabled = false;
        };

    } catch (error) {
        addLog('error', `Initialization failed: ${error.message}`);
        submitBtn.disabled = false;
    }
});

function addLog(type, message) {
    const terminal = document.getElementById('terminal');
    const span = document.createElement('span');
    span.className = `log-entry ${type}`;
    
    const time = new Date().toLocaleTimeString();
    span.textContent = `[${time}] ${message}`;
    
    terminal.appendChild(span);
    terminal.scrollTop = terminal.scrollHeight;
}