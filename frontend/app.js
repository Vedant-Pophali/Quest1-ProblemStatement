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
                    // Extract data from the message or make a final fetch call depending on backend structure
                    document.getElementById('res-timestamp').textContent = payload.message.split('! ')[1] || "Check logs";
                    document.getElementById('res-frame').textContent = "Calculated by timestamp";
                    document.getElementById('res-text').textContent = targetText;
                    resultPanel.classList.remove('hidden');
                }
            }
        });

        eventSource.onerror = () => {
            addLog('error', 'Lost connection to telemetry stream.');
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