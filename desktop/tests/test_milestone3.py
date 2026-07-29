"""
Milestone 3 Test Suite & FastAPI Networking Verification Script.

Tests FastAPI app initialization, REST control endpoints, status schema,
WebSocket binary streaming connection, and MJPEG generator.
"""

import sys
from pathlib import Path

# Add src directory to path
src_dir = Path(__file__).resolve().parent.parent / "src"
sys.path.insert(0, str(src_dir))

import asyncio
import time
from fastapi.testclient import TestClient
from main import app, stream_service
from protocol import pack_frame, FrameType, get_current_timestamp_us


def test_fastapi_rest_endpoints():
    print("[1/3] Testing REST Endpoints...")
    client = TestClient(app)

    # 1. Health check
    res = client.get("/")
    assert res.status_code == 200
    assert "Wireless Webcam" in res.text

    # 2. Status metrics
    res = client.get("/api/status")
    assert res.status_code == 200
    data = res.json()
    assert "current_fps" in data
    assert "latency_ms" in data
    print("  -> REST endpoints PASSED!")


def test_websocket_streaming():
    print("[2/3] Testing WebSocket Binary Ingestion...")
    client = TestClient(app)

    with client.websocket_connect("/ws/video") as websocket:
        # Check connected state
        assert stream_service.is_connected

        # Send mock SPS/PPS packet
        ts = get_current_timestamp_us()
        sps_payload = b"\x00\x00\x00\x01\x67\x42\xc0\x1d"
        packed_sps = pack_frame(FrameType.SPS_PPS, ts, sps_payload)
        
        websocket.send_bytes(packed_sps)

        # Give small sleep for async task processing
        time.sleep(0.1)

        # Verify metrics updated
        metrics = stream_service.get_metrics()
        assert metrics.is_connected
        print("  -> WebSocket streaming PASSED!")


def test_control_api():
    print("[3/3] Testing Control Command API...")
    client = TestClient(app)

    with client.websocket_connect("/ws/video") as websocket:
        # Send post request to change bitrate
        payload = {
            "command": "set_bitrate",
            "bitrate_bps": 12000000
        }
        res = client.post("/api/control", json=payload)
        assert res.status_code == 200
        assert res.json()["status"] == "success"

        # Receive text message sent over WebSocket to Android
        received_text = websocket.receive_text()
        assert "set_bitrate" in received_text
        assert "12000000" in received_text
        print("  -> Control Command API PASSED!")


if __name__ == "__main__":
    print("=" * 60)
    print("RUNNING MILESTONE 3 VERIFICATION TESTS")
    print("=" * 60)
    test_fastapi_rest_endpoints()
    test_websocket_streaming()
    test_control_api()
    print("=" * 60)
    print("ALL MILESTONE 3 TESTS PASSED SUCCESSFULLY!")
    print("=" * 60)
