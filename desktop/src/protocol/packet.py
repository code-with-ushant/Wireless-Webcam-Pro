"""
Ultra-Low Latency Binary Protocol Definition.

Defines the binary framing protocol used between the Android sender and 
the Windows Python receiver for streaming video frames with embedded microsecond 
telemetry timestamps.
"""

from dataclasses import dataclass
from enum import IntFlag
import struct
import time
from typing import List, Optional, Tuple

HEADER_MAGIC = b"WC"
HEADER_VERSION = 1
HEADER_FORMAT = "!2sBBQI"  # Magic(2s), Version(B), FrameType(B), TimestampUS(Q), PayloadSize(I)
HEADER_SIZE = struct.calcsize(HEADER_FORMAT)  # 16 bytes total header size


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
        """Returns True if packet contains decoder configuration parameters (SPS/PPS)."""
        return bool(self.frame_type & FrameType.SPS_PPS)

    @property
    def is_keyframe(self) -> bool:
        """Returns True if packet is a keyframe (IDR)."""
        return bool(self.frame_type & FrameType.KEYFRAME)


def pack_frame(frame_type: FrameType, timestamp_us: int, payload: bytes) -> bytes:
    """
    Serializes a frame payload into a binary wire format packet.
    """
    header = struct.pack(
        HEADER_FORMAT,
        HEADER_MAGIC,
        HEADER_VERSION,
        int(frame_type),
        timestamp_us,
        len(payload),
    )
    return header + payload


def unpack_frame(data: bytes) -> Optional[Tuple[StreamPacket, int]]:
    """
    Deserializes a binary packet from raw bytes.

    Args:
        data: Buffer containing incoming wire bytes.

    Returns:
        Tuple of (StreamPacket, bytes_consumed) if a full frame is parsed,
        or None if the buffer contains insufficient data.
    """
    if len(data) < HEADER_SIZE:
        return None

    magic, version, raw_frame_type, timestamp_us, payload_size = struct.unpack(
        HEADER_FORMAT, data[:HEADER_SIZE]
    )

    if magic != HEADER_MAGIC:
        raise ValueError(f"Invalid packet magic header: {magic!r}")
    if version != HEADER_VERSION:
        raise ValueError(f"Unsupported protocol version: {version}")

    total_packet_size = HEADER_SIZE + payload_size
    if len(data) < total_packet_size:
        return None  # Partial payload received, wait for more data

    payload = data[HEADER_SIZE:total_packet_size]
    packet = StreamPacket(
        frame_type=FrameType(raw_frame_type),
        timestamp_us=timestamp_us,
        payload=payload,
    )
    return packet, total_packet_size


def unpack_all_frames(data: bytes) -> List[StreamPacket]:
    """
    Iteratively parses all concatenated StreamPackets from a binary byte buffer.
    """
    packets = []
    offset = 0
    buffer_len = len(data)

    while offset < buffer_len:
        res = unpack_frame(data[offset:])
        if res is None:
            break
        packet, consumed = res
        packets.append(packet)
        offset += consumed

    return packets


def get_current_timestamp_us() -> int:
    """Helper utility to fetch current system clock in microseconds."""
    return int(time.time_ns() // 1000)
