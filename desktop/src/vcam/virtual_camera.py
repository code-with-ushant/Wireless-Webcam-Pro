"""
Virtual Camera Output Driver for Windows / OBS Integration.

Wraps pyvirtualcam to stream raw BGR frames directly into the system's Virtual Camera device.
"""

import logging
from typing import Optional
import numpy as np

logger = logging.getLogger("WirelessWebcam.VCam")

try:
    import pyvirtualcam
    PYVIRTUALCAM_AVAILABLE = True
except ImportError:
    PYVIRTUALCAM_AVAILABLE = False
    logger.warning("pyvirtualcam package not found. Virtual camera driver will run in fallback mock mode.")


class VirtualCameraDriver:
    """
    Manages the lifecycle of the Windows Virtual Camera output stream.
    """

    def __init__(self, width: int = 1920, height: int = 1080, fps: int = 30) -> None:
        self.width = width
        self.height = height
        self.fps = fps
        self._cam: Optional[pyvirtualcam.Camera] = None
        self._is_active: bool = False

    def start(self) -> bool:
        """
        Initializes and starts the pyvirtualcam context.

        Returns:
            True if virtual camera started successfully, False otherwise.
        """
        if not PYVIRTUALCAM_AVAILABLE:
            logger.warning("Virtual camera hardware driver unavailable. Frames will be rendered to GUI preview only.")
            return False

        try:
            # pyvirtualcam automatically detects OBS Virtual Camera / Unity Capture on Windows
            self._cam = pyvirtualcam.Camera(
                width=self.width,
                height=self.height,
                fps=self.fps,
                fmt=pyvirtualcam.PixelFormat.BGR,
            )
            self._is_active = True
            logger.info(
                f"Virtual Camera initialized: device '{self._cam.device}', "
                f"resolution {self.width}x{self.height} @ {self.fps} FPS"
            )
            return True
        except Exception as err:
            logger.error(f"Failed to start pyvirtualcam virtual camera: {err}")
            self._cam = None
            self._is_active = False
            return False

    def send_frame(self, bgr_frame: np.ndarray) -> bool:
        """
        Sends a BGR image frame to the virtual camera output.

        Args:
            bgr_frame: Contiguous BGR24 NumPy array.

        Returns:
            True if frame was sent, False if camera is inactive.
        """
        if not self._is_active or self._cam is None:
            return False

        try:
            # Resize frame if resolution changed dynamically
            if bgr_frame.shape[1] != self.width or bgr_frame.shape[0] != self.height:
                import cv2
                bgr_frame = cv2.resize(bgr_frame, (self.width, self.height), interpolation=cv2.INTER_LINEAR)

            self._cam.send(bgr_frame)
            return True
        except Exception as err:
            logger.error(f"Error sending frame to pyvirtualcam: {err}")
            return False

    def stop(self) -> None:
        """Stops and closes the virtual camera device."""
        if self._cam is not None:
            try:
                self._cam.close()
                logger.info("Virtual Camera closed.")
            except Exception as err:
                logger.error(f"Error closing virtual camera: {err}")
            finally:
                self._cam = None
                self._is_active = False

    @property
    def is_active(self) -> bool:
        """Returns True if virtual camera is actively streaming."""
        return self._is_active
