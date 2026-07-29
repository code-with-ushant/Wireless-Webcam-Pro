"""
FastAPI Server Entrypoint & WebSocket Networking Hub.

Provides low-latency binary WebSocket video ingestion, bi-directional control API,
MJPEG live stream preview, and virtual camera lifecycle management.
"""

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse

from network.control_models import ControlCommand, StreamMetrics
from service import StreamService

# Configure Logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("WirelessWebcam.Main")

# Global Stream Service Instance
stream_service = StreamService(width=1920, height=1080, fps=30)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifecycle manager for startup and shutdown procedures."""
    logger.info("Starting Wireless Webcam FastAPI Service...")
    # Attempt to auto-start virtual camera driver on startup
    vcam_started = stream_service.start_virtual_camera()
    logger.info(f"Virtual Camera Driver Startup Status: {vcam_started}")
    
    yield
    
    logger.info("Shutting down Wireless Webcam Service...")
    stream_service.stop_virtual_camera()


from pathlib import Path
from fastapi.staticfiles import StaticFiles
from fastapi.responses import StreamingResponse, JSONResponse, FileResponse

static_dir = Path(__file__).resolve().parent / "static"

app = FastAPI(
    title="Wireless Webcam Receiver API",
    description="Sub-50ms H.264 Wireless Video Ingestion Server & OBS Integration",
    version="1.0.0",
    lifespan=lifespan,
)

# Enable CORS for desktop dashboards and LAN access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


@app.get("/")
async def root():
    """Serves the Desktop Control Dashboard Web UI."""
    index_file = static_dir / "index.html"
    if index_file.exists():
        return FileResponse(index_file)
    return {"status": "online", "app": "Wireless Webcam Receiver", "version": "1.0.0"}


@app.get("/api/status", response_model=StreamMetrics)
async def get_stream_status():
    """Returns current real-time streaming telemetry and virtual camera status."""
    return stream_service.get_metrics()


@app.post("/api/control")
async def send_control_command(command: ControlCommand):
    """
    Sends dynamic control parameters (bitrate, resolution, FPS, keyframe refresh)
    over WebSocket to the connected Android phone.
    """
    json_text = command.model_dump_json(exclude_none=True)
    sent = await stream_service.send_control_command(json_text)
    if not sent:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Android client is not currently connected via WebSocket.",
        )
    return {"status": "success", "command_sent": command.command}


@app.post("/api/vcam/start")
async def start_virtual_camera():
    """Starts the Windows pyvirtualcam DirectShow Virtual Camera device."""
    success = stream_service.start_virtual_camera()
    if not success:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"status": "error", "message": "Failed to start Virtual Camera driver. Ensure OBS Studio is installed."},
        )
    return {"status": "success", "message": "Virtual Camera started successfully."}


@app.post("/api/vcam/stop")
async def stop_virtual_camera():
    """Stops the Windows Virtual Camera device."""
    stream_service.stop_virtual_camera()
    return {"status": "success", "message": "Virtual Camera stopped."}


@app.get("/api/mjpeg")
async def get_mjpeg_stream():
    """
    HTTP MJPEG Live Stream Endpoint.
    Can be loaded in web browsers or added as a Media/Browser Source directly in OBS Studio!
    """
    return StreamingResponse(
        stream_service.generate_mjpeg_stream(),
        media_type="multipart/x-mixed-replace; boundary=frame",
    )


@app.websocket("/ws/video")
async def websocket_video_endpoint(websocket: WebSocket):
    """
    Ultra-Low Latency Binary WebSocket Streaming Endpoint.
    Ingests raw H.264 wire packets from Android phone.
    """
    await websocket.accept()
    client_host = websocket.client.host if websocket.client else "unknown"
    logger.info(f"Android Phone Connected via WebSocket from {client_host}")

    stream_service.set_websocket_client(websocket)

    try:
        while True:
            # Receive binary packet from Android sender
            data = await websocket.receive_bytes()
            # Process packet off main thread
            await stream_service.process_binary_packet(data, client_host)
    except WebSocketDisconnect:
        logger.info(f"Android Phone Disconnected ({client_host})")
    except Exception as err:
        logger.error(f"WebSocket Error: {err}")
    finally:
        stream_service.reset_client()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False, log_level="info")
