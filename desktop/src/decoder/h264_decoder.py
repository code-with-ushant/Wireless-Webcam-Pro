"""
Ultra-Low Latency PyAV H.264 Hardware/Software Video Decoder.

Configures FFmpeg libavcodec options for zero-latency streaming (no frame lookahead,
no reordering buffers, single-thread low delay execution).
"""

import logging
from typing import Generator, Optional, Tuple
import av
import numpy as np

logger = logging.getLogger("WirelessWebcam.Decoder")


class H264Decoder:
    """
    Decodes raw H.264 Annex-B bitstreams into BGR24 NumPy arrays with minimal latency.
    """

    def __init__(self, low_delay_flags: bool = True) -> None:
        """
        Initializes the H.264 decoder context.

        Args:
            low_delay_flags: If True, applies low-latency FFmpeg decoding flags.
        """
        self._codec = av.CodecContext.create("h264", "r")
        
        # Configure FFmpeg context for sub-10ms decoding delay
        if low_delay_flags:
            self._codec.options = {
                "tune": "zerolatency",
                "flags": "low_delay",
                "flags2": "fast",
                "threads": "1",  # Single-threaded decoding eliminates frame-delay in multi-threading
            }

        self._is_initialized: bool = False
        logger.info("Initialized PyAV zero-latency H.264 decoder context")

    def decode_packet(
        self, payload: bytes
    ) -> Generator[np.ndarray, None, None]:
        """
        Decodes incoming H.264 NAL unit bytes into BGR numpy arrays.

        Args:
            payload: Raw H.264 Annex-B bytes (including 0x00000001 start codes).

        Yields:
            bgr_frame for each decoded image.
        """
        try:
            # Parse bitstream into PyAV Packets using CodecContext parse
            packets = self._codec.parse(payload)
            for packet in packets:
                # Decode parsed packet into VideoFrames
                frames = self._codec.decode(packet)
                for frame in frames:
                    # Convert PyAV VideoFrame directly to BGR24 contiguous numpy array
                    bgr_array = frame.to_ndarray(format="bgr24")
                    yield bgr_array
        except av.AVError as err:
            logger.warning(f"PyAV decode error (non-fatal): {err}")
        except Exception as ex:
            logger.error(f"Unexpected exception during H.264 decoding: {ex}", exc_info=True)

    def reset(self) -> None:
        """Flushes and resets the internal decoder state."""
        try:
            self._codec.flush()
        except Exception:
            pass
        self._codec = av.CodecContext.create("h264", "r")
        self._codec.options = {
            "tune": "zerolatency",
            "flags": "low_delay",
            "flags2": "fast",
            "threads": "1",
        }
        logger.info("H.264 Decoder flushed and reset")
