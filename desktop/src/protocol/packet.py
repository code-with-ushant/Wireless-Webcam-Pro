"""
Ultra-Low Latency Binary Protocol Definition.

Uses a native C extension for zero-overhead parsing when available,
with automatic fallback to optimized Python using pre-compiled struct.

Wire Format (16 Bytes Header + NAL Payload):
  [0..1]   Magic Bytes ("WC")
  [2]      Protocol Version (0x01)
  [3]      Frame Type Bitmask
  [4..11]  Capture Timestamp in Microseconds (64-bit, Big-Endian)
  [12..15] Payload Byte Count (32-bit, Big-Endian)
  [16..N]  Raw H.264 Annex-B NAL Unit Data
"""

from dataclasses import dataclass
from enum import IntFlag
import struct
import time
from typing import List, Optional, Tuple

# Pre-compiled struct for header parsing (avoids re-parsing format string per call)
_HEADER_STRUCT = struct.Struct("!2sBBQI")
HEADER_SIZE: int = _HEADER_STRUCT.size  # 16 bytes
HEADER_MAGIC = b"WC"
HEADER_VERSION = 1

# Try loading native C parser for ~20x faster protocol unpacking
try:
    from _native_protocol import unpack_all_frames_native as _native_unpack
    _HAS_NATIVE = True
except ImportError:
    _HAS_NATIVE = False


class FrameType(IntFlag):
    """Bitmask representing the characteristics of the H.264 payload."""
    UNKNOWN = 0x00
    SPS_PPS = 0x01   # Sequence / Picture Parameter Set NAL unit
    KEYFRAME = 0x02  # IDR Instantaneous Decoder Refresh frame
    P_FRAME = 0x04   # Predicted frame


@dataclass(slots=True, frozen=True)
class StreamPacket:
    """Represents a parsed streaming packet."""
    frame_type: FrameType
    timestamp_us: int
    payload: bytes

    @property
    def is_config(self) -> bool:
        return bool(self.frame_type & FrameType.SPS_PPS)

    @property
    def is_keyframe(self) -> bool:
        return bool(self.frame_type & FrameType.KEYFRAME)


def pack_frame(frame_type: FrameType, timestamp_us: int, payload: bytes) -> bytes:
    """Serializes a frame payload into a binary wire format packet."""
    header = _HEADER_STRUCT.pack(
        HEADER_MAGIC,
        HEADER_VERSION,
        int(frame_type),
        timestamp_us,
        len(payload),
    )
    return header + payload


def unpack_frame(data: bytes) -> Optional[Tuple[StreamPacket, int]]:
    """
    Deserializes a single binary packet from raw bytes.

    Returns:
        (StreamPacket, bytes_consumed) or None if insufficient data.
    """
    if len(data) < HEADER_SIZE:
        return None

    magic, version, raw_ft, timestamp_us, payload_size = _HEADER_STRUCT.unpack_from(data, 0)

    if magic != HEADER_MAGIC or version != HEADER_VERSION:
        return None

    total = HEADER_SIZE + payload_size
    if len(data) < total:
        return None

    payload = data[HEADER_SIZE:total]
    return StreamPacket(FrameType(raw_ft), timestamp_us, payload), total


def unpack_all_frames(data: bytes) -> List[StreamPacket]:
    """
    Parses all concatenated StreamPackets from a binary buffer.
    Uses native C extension when available (~20x faster).
    """
    if _HAS_NATIVE:
        # Native C path: returns list of (frame_type, timestamp_us, payload_bytes)
        raw_tuples = _native_unpack(data)
        return [
            StreamPacket(FrameType(ft), ts, payload)
            for ft, ts, payload in raw_tuples
        ]

    # Optimized Python fallback using pre-compiled struct + memoryview
    packets: list[StreamPacket] = []
    mv = memoryview(data)
    offset = 0
    buf_len = len(data)
    unpack_from = _HEADER_STRUCT.unpack_from

    while offset + HEADER_SIZE <= buf_len:
        magic, version, raw_ft, timestamp_us, payload_size = unpack_from(data, offset)
        if magic != HEADER_MAGIC or version != HEADER_VERSION:
            break
        total = HEADER_SIZE + payload_size
        if offset + total > buf_len:
            break
        payload = bytes(mv[offset + HEADER_SIZE : offset + total])
        packets.append(StreamPacket(FrameType(raw_ft), timestamp_us, payload))
        offset += total

    return packets


def get_current_timestamp_us() -> int:
    """Returns current system clock in microseconds (fastest path)."""
    return time.time_ns() // 1000
