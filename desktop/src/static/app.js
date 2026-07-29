/**
 * Desktop Control Dashboard Frontend Logic.
 * Communicates with FastAPI REST endpoints for telemetry polling and control command dispatching.
 */

let isVcamActive = false;

// 1. Polling Telemetry Metrics
async function pollMetrics() {
    try {
        const response = await fetch('/api/status');
        if (!response.ok) return;
        const data = await response.json();

        // Update Connection Status
        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        if (data.is_connected) {
            statusDot.classList.add('connected');
            statusText.innerText = `Connected (${data.client_ip || 'Android'})`;
        } else {
            statusDot.classList.remove('connected');
            statusText.innerText = 'Disconnected';
        }

        // Update Telemetry Metrics Values
        document.getElementById('valLatency').innerText = `${data.latency_ms.toFixed(1)} ms`;
        document.getElementById('valFps').innerText = `${data.current_fps.toFixed(1)} FPS`;
        document.getElementById('valBitrate').innerText = `${data.current_bitrate_mbps.toFixed(2)} Mbps`;
        document.getElementById('valDropped').innerText = data.dropped_frames;

        // Update VCam Active Button State
        isVcamActive = data.vcam_active;
        const btnVcam = document.getElementById('btnVcam');
        if (isVcamActive) {
            btnVcam.innerText = 'Stop Virtual Camera';
            btnVcam.style.background = 'var(--accent-red)';
        } else {
            btnVcam.innerText = 'Start Virtual Camera';
            btnVcam.style.background = 'var(--primary)';
        }

    } catch (err) {
        console.error('Error fetching metrics:', err);
    }
}

// Poll telemetry metrics every 500ms
setInterval(pollMetrics, 500);

// 2. Control Command Actions
async function sendControl(payload) {
    try {
        const res = await fetch('/api/control', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const errData = await res.json();
            alert(`Control failed: ${errData.detail}`);
        }
    } catch (err) {
        console.error('Error sending control command:', err);
    }
}

function setResolution(resStr) {
    document.getElementById('btn720p').classList.toggle('active', resStr === '720p');
    document.getElementById('btn1080p').classList.toggle('active', resStr === '1080p');
    sendControl({ command: 'set_resolution', resolution: resStr });
}

function setFps(fpsNum) {
    document.getElementById('btn30fps').classList.toggle('active', fpsNum === 30);
    document.getElementById('btn60fps').classList.toggle('active', fpsNum === 60);
    sendControl({ command: 'set_fps', fps: fpsNum });
}

function onBitrateChange(valMbps) {
    document.getElementById('lblBitrate').innerText = `${valMbps} Mbps`;
    const bps = parseInt(valMbps) * 1000000;
    sendControl({ command: 'set_bitrate', bitrate_bps: bps });
}

function requestKeyframe() {
    sendControl({ command: 'request_keyframe' });
}

async function toggleVcam() {
    const endpoint = isVcamActive ? '/api/vcam/stop' : '/api/vcam/start';
    try {
        const res = await fetch(endpoint, { method: 'POST' });
        const data = await res.json();
        alert(data.message);
    } catch (err) {
        console.error('Error toggling virtual camera:', err);
    }
}
