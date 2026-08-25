document.getElementById('extraction-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const url = document.getElementById('url').value;
    const targetText = document.getElementById('target-text').value;
    const submitBtn = document.getElementById('submit-btn');
    const terminal = document.getElementById('terminal');
    const resultPanel = document.getElementById('result-panel');
    
    // Reset UI
    submitBtn.disabled = true;
    terminal.innerHTML = '';
    resultPanel.classList.add('hidden');
    addLog('system', 'Initiating connection to Java orchestrator...');

    try {
        // 1. Submit the Job to Java Backend
        const response = await fetch('/api/jobs', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, targetText })
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
            if (payload.state === 'SUCCESS') logClass = 'success';
            
            addLog(logClass, `[${payload.state}] ${payload.message}`);

            // Handle Completion States
            if (payload.state === 'SUCCESS' || payload.state === 'TEXT_NOT_FOUND' || payload.state === 'SYSTEM_ERROR') {
                eventSource.close();
                submitBtn.disabled = false;
                
                if (payload.state === 'SUCCESS') {
                    const timestampStr = payload.message.split('! ')[1] || "00:00:00.000";
                    
                    // Display the timestamp
                    document.getElementById('res-timestamp').textContent = timestampStr;
                    
                    // Calculate the exact frame number
                    // Formula: (Hours * 3600 + Minutes * 60 + Seconds) * FPS
                    let totalSeconds = 0;
                    
                    // Handle HH:MM:SS.sss format if present
                    if (timestampStr.includes(':')) {
                        const parts = timestampStr.split(':');
                        const hours = parseInt(parts[0]) || 0;
                        const minutes = parseInt(parts[1]) || 0;
                        const seconds = parseFloat(parts[2]) || 0;
                        totalSeconds = (hours * 3600) + (minutes * 60) + seconds;
                    } else {
                        // Handle raw seconds if the backend passed a float
                        totalSeconds = parseFloat(timestampStr) || 0;
                    }
                    
                    // Assuming a standard 24 FPS (which we hardcoded in JobController for now)
                    const fps = 24.0;
                    const frameNumber = Math.round(totalSeconds * fps);
                    
                    document.getElementById('res-frame').textContent = frameNumber;
                    document.getElementById('res-text').textContent = targetText;
                    
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