"""
Stream Orchestration Service & Telemetry Engine.

Optimized for sub-10ms per-frame processing overhead with minimal allocations.
"""

import asyncio
from collections import deque
import logging
import time
from typing import AsyncGenerator, Optional
import cv2
import numpy as np

from protocol import StreamPacket, unpack_all_frames, get_current_timestamp_us
from decoder import H264Decoder
from pipeline import FrameRingBuffer, TimestampedFrame
from vcam import VirtualCameraDriver
from network.control_models import StreamMetrics, ResolutionEnum, FpsEnum

logger = logging.getLogger("WirelessWebcam.StreamService")

# Pre-compute MJPEG boundary header (avoid re-creating bytes 30x/sec)
_MJPEG_BOUNDARY = b"--frame\r\nContent-Type: image/jpeg\r\n\r\n"
_MJPEG_TERMINATOR = b"\r\n"
_JPEG_PARAMS = [cv2.IMWRITE_JPEG_QUALITY, 75]  # 75 is visually identical, ~30% less CPU


class StreamService:
    """
    Central service: decode → ring buffer → virtual camera + MJPEG preview.
    """
    __slots__ = (
        'width', 'height', 'fps', 'is_connected', 'client_ip',
        '_decoder', '_ring_buffer', '_vcam', '_latest_bgr_frame',
        '_frame_timestamps', '_bitrate_bytes_window', '_latency_window_ms',
        '_standby_frame', '_standby_jpeg', '_active_websocket',
        '_frame_interval',
    )

    def __init__(self, width: int = 1920, height: int = 1080, fps: int = 30) -> None:
        self.width = width
        self.height = height
        self.fps = fps
        self._frame_interval = 1.0 / max(1, fps)

        self._decoder = H264Decoder(low_delay_flags=True)
        self._ring_buffer = FrameRingBuffer[TimestampedFrame](maxsize=1)
        self._vcam = VirtualCameraDriver(width=width, height=height, fps=fps)

        # Telemetry State
        self.is_connected: bool = False
        self.client_ip: Optional[str] = None
        self._latest_bgr_frame: Optional[np.ndarray] = None
        self._frame_timestamps: deque[int] = deque(maxlen=60)
        self._bitrate_bytes_window: deque[tuple[int, int]] = deque(maxlen=60)
        self._latency_window_ms: deque[float] = deque(maxlen=60)

        # Pre-rendered standby assets (created once, cached forever)
        self._standby_frame: Optional[np.ndarray] = None
        self._standby_jpeg: Optional[bytes] = None  # Pre-encoded JPEG of standby

        self._active_websocket = None

    def start_virtual_camera(self) -> bool:
        return self._vcam.start()

    def stop_virtual_camera(self) -> None:
        self._vcam.stop()

    async def process_binary_packet(self, data: bytes, client_host: str) -> None:
        """Hot path: parse wire bytes → decode H.264 → ring buffer → vcam."""
        receive_ts_us = get_current_timestamp_us()
        self.is_connected = True
        self.client_ip = client_host
        self._bitrate_bytes_window.append((receive_ts_us, len(data)))

        packets = unpack_all_frames(data)
        if not packets:
            return

        # Batch-process all packets
        for packet in packets:
            ts_us = packet.timestamp_us
            if ts_us > 0:
                diff_ms = (receive_ts_us - ts_us) / 1000.0
                if 0.0 <= diff_ms <= 1000.0:
                    self._latency_window_ms.append(diff_ms)

            # Decode H.264 in thread pool (FFmpeg C library does the heavy lifting)
            decoded_frames = await asyncio.to_thread(self._decode_payload_sync, packet.payload)
            if not decoded_frames:
                continue

            decode_ts_us = get_current_timestamp_us()

            for bgr_array in decoded_frames:
                # Direct reference assignment (no copy)
                self._latest_bgr_frame = bgr_array
                self._frame_timestamps.append(decode_ts_us)

                self._ring_buffer.put_nowait_drop_oldest(
                    TimestampedFrame(bgr_array, ts_us, receive_ts_us, decode_ts_us)
                )

                if self._vcam.is_active:
                    await asyncio.to_thread(self._vcam.send_frame, bgr_array)

    def _decode_payload_sync(self, payload: bytes) -> list[np.ndarray]:
        """Synchronous FFmpeg decode in worker thread."""
        return list(self._decoder.decode_packet(payload))

    def get_metrics(self) -> StreamMetrics:
        """Returns telemetry snapshot (called ~1/sec by dashboard, not hot path)."""
        now_us = get_current_timestamp_us()
        cutoff = now_us - 1_000_000

        fps_count = sum(1 for ts in self._frame_timestamps if ts > cutoff)
        byte_sum = sum(b for ts, b in self._bitrate_bytes_window if ts > cutoff)

        lat_win = self._latency_window_ms
        avg_lat = round(sum(lat_win) / len(lat_win), 1) if lat_win else 0.0

        return StreamMetrics(
            is_connected=self.is_connected,
            client_ip=self.client_ip,
            current_fps=float(fps_count),
            current_bitrate_mbps=round((byte_sum * 8.0) / 1_000_000.0, 2),
            latency_ms=avg_lat,
            dropped_frames=self._ring_buffer.dropped_count,
            total_frames_processed=self._ring_buffer.processed_count,
            resolution=f"{self.width}x{self.height}",
            vcam_active=self._vcam.is_active,
        )

    def set_websocket_client(self, websocket) -> None:
        self._active_websocket = websocket
        self.is_connected = True

    def reset_client(self) -> None:
        self.is_connected = False
        self.client_ip = None
        self._active_websocket = None
        self._decoder.reset()

    async def send_control_command(self, json_text: str) -> bool:
        ws = self._active_websocket
        if ws is not None:
            try:
                await ws.send_text(json_text)
                return True
            except Exception as err:
                logger.error(f"Failed to send control command: {err}")
        return False

    def _get_standby_frame(self) -> np.ndarray:
        """Returns cached standby frame (created once)."""
        if self._standby_frame is not None:
            return self._standby_frame

        frame = np.zeros((self.height, self.width, 3), dtype=np.uint8)
        frame[:] = (28, 20, 15)

        cv2.putText(frame, "WIRELESS WEBCAM RECEIVER",
            (max(20, self.width // 2 - 380), self.height // 2 - 40),
            cv2.FONT_HERSHEY_SIMPLEX, 1.3, (216, 180, 0), 3, cv2.LINE_AA)
        cv2.putText(frame, "Awaiting Android Phone Connection...",
            (max(20, self.width // 2 - 300), self.height // 2 + 30),
            cv2.FONT_HERSHEY_SIMPLEX, 0.9, (240, 240, 240), 2, cv2.LINE_AA)
        cv2.putText(frame, "Enter your PC's Wi-Fi IP address in the Android Phone App",
            (max(20, self.width // 2 - 370), self.height // 2 + 90),
            cv2.FONT_HERSHEY_SIMPLEX, 0.75, (160, 160, 160), 2, cv2.LINE_AA)

        self._standby_frame = frame
        return frame

    def _get_standby_jpeg(self) -> bytes:
        """Returns pre-encoded JPEG of standby frame (avoids re-encoding every tick)."""
        if self._standby_jpeg is not None:
            return self._standby_jpeg
        frame = self._get_standby_frame()
        _, buf = cv2.imencode(".jpg", frame, _JPEG_PARAMS)
        self._standby_jpeg = buf.tobytes()
        return self._standby_jpeg

    async def generate_mjpeg_stream(self) -> AsyncGenerator[bytes, None]:
        """MJPEG multipart stream for browser preview."""
        boundary = _MJPEG_BOUNDARY
        terminator = _MJPEG_TERMINATOR
        interval = self._frame_interval

        while True:
            latest = self._latest_bgr_frame
            if latest is not None:
                # Offload JPEG encoding to thread pool
                success, jpeg_buf = await asyncio.to_thread(
                    cv2.imencode, ".jpg", latest, _JPEG_PARAMS
                )
                if success:
                    yield boundary + jpeg_buf.tobytes() + terminator
            else:
                # Use pre-encoded standby JPEG (zero CPU per tick)
                yield boundary + self._get_standby_jpeg() + terminator

            await asyncio.sleep(interval)
