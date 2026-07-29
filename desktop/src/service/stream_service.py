"""
Stream Orchestration Service & Telemetry Engine.

Integrates WebSocket networking, PyAV H.264 decoding, ring buffer management,
virtual camera publishing, and real-time telemetry metrics calculation.
"""

import asyncio
from collections import deque
import logging
import time
from typing import AsyncGenerator, Optional, Tuple
import cv2
import numpy as np

from protocol import StreamPacket, unpack_frame, unpack_all_frames, get_current_timestamp_us
from decoder import H264Decoder
from pipeline import FrameRingBuffer, TimestampedFrame
from vcam import VirtualCameraDriver
from network.control_models import StreamMetrics, ResolutionEnum, FpsEnum

logger = logging.getLogger("WirelessWebcam.StreamService")


class StreamService:
    """
    Central service managing low-latency video streaming, decoding pipeline,
    virtual camera driver output, and telemetry stats.
    """

    def __init__(self, width: int = 1920, height: int = 1080, fps: int = 30) -> None:
        self.width = width
        self.height = height
        self.fps = fps

        self._decoder = H264Decoder(low_delay_flags=True)
        self._ring_buffer = FrameRingBuffer[TimestampedFrame](maxsize=1)
        self._vcam = VirtualCameraDriver(width=width, height=height, fps=fps)

        # Telemetry State
        self.is_connected: bool = False
        self.client_ip: Optional[str] = None
        self._latest_bgr_frame: Optional[np.ndarray] = None
        self._frame_timestamps = deque(maxlen=60)
        self._bitrate_bytes_window = deque(maxlen=60)
        self._latency_window_ms = deque(maxlen=60)
        self._total_bytes_received: int = 0
        self._lock = asyncio.Lock()

        self._active_websocket = None

    def start_virtual_camera(self) -> bool:
        """Starts pyvirtualcam virtual camera driver."""
        return self._vcam.start()

    def stop_virtual_camera(self) -> None:
        """Stops pyvirtualcam virtual camera driver."""
        self._vcam.stop()

    async def process_binary_packet(self, data: bytes, client_host: str) -> None:
        """
        Parses incoming raw wire bytes from WebSocket and dispatches to decoder.
        """
        receive_ts_us = get_current_timestamp_us()
        self.is_connected = True
        self.client_ip = client_host
        self._bitrate_bytes_window.append((receive_ts_us, len(data)))

        # Unpack all binary protocol packets in buffer
        packets = unpack_all_frames(data)
        if not packets:
            return

        for packet in packets:
            # Calculate network latency with rolling delta window
            if packet.timestamp_us > 0:
                # Use current wall-clock epoch in microseconds
                diff_ms = (receive_ts_us - packet.timestamp_us) / 1000.0
                # Filter out initial clock sync offsets to keep telemetry accurate
                if 0.0 <= diff_ms <= 1000.0:
                    self._latency_window_ms.append(diff_ms)

            # Decode H.264 payload off the main async event loop
            payload = packet.payload
            decoded_frames = await asyncio.to_thread(self._decode_payload_sync, payload)

            decode_ts_us = get_current_timestamp_us()

            for bgr_array in decoded_frames:
                ts_frame = TimestampedFrame(
                    bgr_array=bgr_array,
                    capture_timestamp_us=packet.timestamp_us,
                    receive_timestamp_us=receive_ts_us,
                    decode_timestamp_us=decode_ts_us,
                )

                # Enqueue into drop-oldest ring buffer
                self._ring_buffer.put_nowait_drop_oldest(ts_frame)
                self._latest_bgr_frame = bgr_array
                self._frame_timestamps.append(decode_ts_us)

                # Send to Virtual Camera driver
                if self._vcam.is_active:
                    await asyncio.to_thread(self._vcam.send_frame, bgr_array)

    def _decode_payload_sync(self, payload: bytes) -> list[np.ndarray]:
        """Synchronous decoding helper called in worker thread."""
        return list(self._decoder.decode_packet(payload))

    def get_metrics(self) -> StreamMetrics:
        """Returns current stream telemetry metrics snapshot."""
        now_us = get_current_timestamp_us()
        
        # Calculate FPS over 1-second rolling window
        recent_frames = [ts for ts in self._frame_timestamps if (now_us - ts) <= 1_000_000]
        current_fps = float(len(recent_frames))

        # Calculate Bitrate in Mbps over 1-second rolling window
        recent_bytes = sum(b for ts, b in self._bitrate_bytes_window if (now_us - ts) <= 1_000_000)
        current_bitrate_mbps = round((recent_bytes * 8.0) / 1_000_000.0, 2)

        # Calculate Average Latency in milliseconds
        avg_latency_ms = (
            round(sum(self._latency_window_ms) / len(self._latency_window_ms), 1)
            if self._latency_window_ms
            else 0.0
        )

        return StreamMetrics(
            is_connected=self.is_connected,
            client_ip=self.client_ip,
            current_fps=current_fps,
            current_bitrate_mbps=current_bitrate_mbps,
            latency_ms=avg_latency_ms,
            dropped_frames=self._ring_buffer.dropped_count,
            total_frames_processed=self._ring_buffer.processed_count,
            resolution=f"{self.width}x{self.height}",
            vcam_active=self._vcam.is_active,
        )

    def set_websocket_client(self, websocket) -> None:
        """Stores reference to active WebSocket client for bi-directional commands."""
        self._active_websocket = websocket
        self.is_connected = True

    def reset_client(self) -> None:
        """Resets client connection telemetry on disconnect."""
        self.is_connected = False
        self.client_ip = None
        self._active_websocket = None
        self._decoder.reset()

    async def send_control_command(self, json_text: str) -> bool:
        """Sends a JSON control command text message back to Android phone."""
        if self._active_websocket is not None:
            try:
                await self._active_websocket.send_text(json_text)
                logger.info(f"Sent control text command to Android: {json_text}")
                return True
            except Exception as err:
                logger.error(f"Failed to send control command over WebSocket: {err}")
        return False

    def _create_standby_frame(self) -> np.ndarray:
        """Generates a stylish dark standby placeholder image when no phone is connected."""
        frame = np.zeros((self.height, self.width, 3), dtype=np.uint8)
        frame[:] = (28, 20, 15)  # Dark BGR background

        cv2.putText(
            frame,
            "WIRELESS WEBCAM RECEIVER",
            (max(20, self.width // 2 - 380), self.height // 2 - 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.3,
            (216, 180, 0),
            3,
            cv2.LINE_AA,
        )

        status_text = (
            "Awaiting Android Phone Connection..."
            if not self.is_connected
            else "Decoding Video Stream..."
        )
        cv2.putText(
            frame,
            status_text,
            (max(20, self.width // 2 - 300), self.height // 2 + 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.9,
            (240, 240, 240),
            2,
            cv2.LINE_AA,
        )

        ip_text = "Enter your PC's Wi-Fi IP address in the Android Phone App"
        cv2.putText(
            frame,
            ip_text,
            (max(20, self.width // 2 - 370), self.height // 2 + 90),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.75,
            (160, 160, 160),
            2,
            cv2.LINE_AA,
        )
        return frame

    async def generate_mjpeg_stream(self) -> AsyncGenerator[bytes, None]:
        """
        Generates an MJPEG multipart HTTP byte stream for zero-latency live browser preview.
        Serves a standby placeholder screen when no phone is connected.
        """
        while True:
            frame_to_send = (
                self._latest_bgr_frame
                if self._latest_bgr_frame is not None
                else self._create_standby_frame()
            )
            success, jpeg_bytes = cv2.imencode(
                ".jpg", frame_to_send, [cv2.IMWRITE_JPEG_QUALITY, 80]
            )
            if success:
                yield (
                    b"--frame\r\n"
                    b"Content-Type: image/jpeg\r\n\r\n"
                    + jpeg_bytes.tobytes()
                    + b"\r\n"
                )
            await asyncio.sleep(1.0 / max(1, self.fps))
