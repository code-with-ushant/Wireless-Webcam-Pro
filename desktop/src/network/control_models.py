"""
Pydantic Schemas & Telemetry Data Models.

Defines control commands and real-time streaming telemetry models.
"""

from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field


class ResolutionEnum(str, Enum):
    RES_720P = "720p"
    RES_1080P = "1080p"

    @property
    def width_height(self) -> tuple[int, int]:
        if self == ResolutionEnum.RES_720P:
            return (1280, 720)
        return (1920, 1080)


class FpsEnum(int, Enum):
    FPS_30 = 30
    FPS_60 = 60


class CommandType(str, Enum):
    SET_BITRATE = "set_bitrate"
    SET_RESOLUTION = "set_resolution"
    SET_FPS = "set_fps"
    REQUEST_KEYFRAME = "request_keyframe"


class ControlCommand(BaseModel):
    """Command payload sent to Android device over WebSocket."""
    command: CommandType
    bitrate_bps: Optional[int] = Field(None, ge=1_000_000, le=30_000_000)
    resolution: Optional[ResolutionEnum] = None
    fps: Optional[FpsEnum] = None


class StreamMetrics(BaseModel):
    """Real-time streaming telemetry state."""
    is_connected: bool = False
    client_ip: Optional[str] = None
    current_fps: float = 0.0
    current_bitrate_mbps: float = 0.0
    latency_ms: float = 0.0
    dropped_frames: int = 0
    total_frames_processed: int = 0
    resolution: str = "1920x1080"
    vcam_active: bool = False
