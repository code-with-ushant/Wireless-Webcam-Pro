"""
Non-Blocking Drop-Oldest Async Frame Ring Buffer.

Prevents queue buildup and latency drift by prioritizing frame freshless over frame retention.
"""

import asyncio
from dataclasses import dataclass
import logging
from typing import Optional, Generic, TypeVar
import numpy as np

T = TypeVar("T")
logger = logging.getLogger("WirelessWebcam.Pipeline")


@dataclass(slots=True)
class TimestampedFrame:
    """Holds a decoded video frame alongside telemetry metrics."""
    bgr_array: np.ndarray
    capture_timestamp_us: int
    receive_timestamp_us: int
    decode_timestamp_us: int

    @property
    def total_latency_ms(self) -> float:
        """Calculates total end-to-end latency from capture to post-decode in milliseconds."""
        return (self.decode_timestamp_us - self.capture_timestamp_us) / 1000.0


class FrameRingBuffer(Generic[T]):
    """
    Thread-safe, non-blocking ring buffer with maxsize capacity that drops oldest
    items when full to enforce strict bounded latency.
    """

    def __init__(self, maxsize: int = 1) -> None:
        """
        Args:
            maxsize: Maximum capacity. Defaults to 1 for zero-latency frame dropping.
        """
        self._maxsize = max(1, maxsize)
        self._queue: asyncio.Queue[T] = asyncio.Queue(maxsize=self._maxsize)
        self._dropped_frames_count: int = 0
        self._processed_frames_count: int = 0

    def put_nowait_drop_oldest(self, item: T) -> bool:
        """
        Pushes a new item into the queue. If full, pops and discards the oldest item first.

        Returns:
            True if an old item was dropped to make room, False otherwise.
        """
        was_dropped = False
        if self._queue.full():
            try:
                self._queue.get_nowait()
                self._dropped_frames_count += 1
                was_dropped = True
            except asyncio.QueueEmpty:
                pass

        try:
            self._queue.put_nowait(item)
            self._processed_frames_count += 1
        except asyncio.QueueFull:
            # Should not happen due to get_nowait above
            pass

        return was_dropped

    async def get(self) -> T:
        """
        Async retrieval of the next available frame. Blocks if buffer is empty.
        """
        return await self._queue.get()

    def get_nowait(self) -> Optional[T]:
        """
        Non-blocking retrieval of the frame. Returns None if empty.
        """
        try:
            return self._queue.get_nowait()
        except asyncio.QueueEmpty:
            return None

    @property
    def dropped_count(self) -> int:
        """Total count of frames dropped to prevent latency drift."""
        return self._dropped_frames_count

    @property
    def processed_count(self) -> int:
        """Total count of frames processed by the buffer."""
        return self._processed_frames_count

    def clear(self) -> None:
        """Clears all buffered frames."""
        while not self._queue.empty():
            try:
                self._queue.get_nowait()
            except asyncio.QueueEmpty:
                break
