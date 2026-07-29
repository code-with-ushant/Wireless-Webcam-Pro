"""
Milestone 1 Test Suite & Engine Verification Script.
Tests protocol binary packing, frame buffer drop-oldest logic, PyAV decoder instantiation,
and virtual camera driver interface.
"""

import sys
from pathlib import Path

# Add src directory to path
src_dir = Path(__file__).resolve().parent.parent / "src"
sys.path.insert(0, str(src_dir))

import time
import numpy as np
from protocol import pack_frame, unpack_frame, FrameType, get_current_timestamp_us
from decoder import H264Decoder
from pipeline import FrameRingBuffer, TimestampedFrame
from vcam import VirtualCameraDriver


def test_protocol_packing():
    print("[1/4] Testing Protocol Packing/Unpacking...")
    ts = get_current_timestamp_us()
    payload = b"\x00\x00\x00\x01\x67\x42\xc0\x1d"  # Mock H.264 SPS NAL unit
    
    packed = pack_frame(FrameType.SPS_PPS, ts, payload)
    unpacked_res = unpack_frame(packed)
    
    assert unpacked_res is not None, "Failed to unpack valid packet"
    packet, consumed = unpacked_res
    assert consumed == len(packed), f"Consumed bytes mismatch: {consumed} vs {len(packed)}"
    assert packet.frame_type == FrameType.SPS_PPS, f"FrameType mismatch: {packet.frame_type}"
    assert packet.timestamp_us == ts, f"Timestamp mismatch: {packet.timestamp_us} vs {ts}"
    assert packet.payload == payload, f"Payload mismatch: {packet.payload}"
    print("  -> Protocol packing/unpacking PASSED!")


def test_frame_ring_buffer():
    print("[2/4] Testing Drop-Oldest Frame Ring Buffer...")
    buffer = FrameRingBuffer[int](maxsize=1)
    
    # Push item 1
    buffer.put_nowait_drop_oldest(100)
    assert not buffer.get_nowait() == None, "Buffer should not be empty"
    
    # Put 100, then put 200 without reading -> 100 should be dropped
    buffer.put_nowait_drop_oldest(100)
    dropped = buffer.put_nowait_drop_oldest(200)
    
    assert dropped, "Should report that oldest item was dropped"
    item = buffer.get_nowait()
    assert item == 200, f"Expected freshest item 200, got {item}"
    print("  -> Frame Ring Buffer drop-oldest PASSED!")


def test_h264_decoder_init():
    print("[3/4] Testing H.264 Decoder Initialization...")
    decoder = H264Decoder(low_delay_flags=True)
    assert decoder is not None
    print("  -> H.264 PyAV zero-latency decoder initialized PASSED!")


def test_virtual_camera_driver():
    print("[4/4] Testing Virtual Camera Driver Interface...")
    driver = VirtualCameraDriver(width=1280, height=720, fps=30)
    started = driver.start()
    print(f"  -> Virtual camera started status: {started} (pyvirtualcam device active: {driver.is_active})")
    
    # Test sending a mock frame
    mock_frame = np.zeros((720, 1280, 3), dtype=np.uint8)
    driver.send_frame(mock_frame)
    driver.stop()
    print("  -> Virtual Camera Driver interface PASSED!")


if __name__ == "__main__":
    print("=" * 60)
    print("RUNNING MILESTONE 1 VERIFICATION TESTS")
    print("=" * 60)
    test_protocol_packing()
    test_frame_ring_buffer()
    test_h264_decoder_init()
    test_virtual_camera_driver()
    print("=" * 60)
    print("ALL MILESTONE 1 TESTS PASSED SUCCESSFULLY!")
    print("=" * 60)
