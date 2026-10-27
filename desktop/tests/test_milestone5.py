"""
Milestone 5 Test Suite & End-to-End System Verification Script.

Tests static Web Dashboard serving, app.js loading, local IP detection,
and full REST/WebSocket server stack.
"""

import sys
from pathlib import Path

# Add desktop root and src directory to path
desktop_dir = Path(__file__).resolve().parent.parent
src_dir = desktop_dir / "src"
sys.path.insert(0, str(desktop_dir))
sys.path.insert(0, str(src_dir))

from fastapi.testclient import TestClient
from main import app
from run_desktop import get_local_ip


def test_static_dashboard_serving():
    print("[1/3] Testing Web Dashboard UI Serving...")
    client = TestClient(app)

    # 1. Test Root index.html serving
    res = client.get("/")
    assert res.status_code == 200
    assert "Wireless Webcam Receiver" in res.text
    assert "videoFeed" in res.text
    print("  -> Index HTML Dashboard serving PASSED!")

    # 2. Test app.js serving
    res_js = client.get("/static/app.js")
    assert res_js.status_code == 200
    assert "pollMetrics" in res_js.text
    print("  -> Static JavaScript app.js serving PASSED!")


def test_local_ip_detection():
    print("[2/3] Testing Local LAN IP Address Detection...")
    ip = get_local_ip()
    assert ip is not None
    assert len(ip.split(".")) == 4, f"Invalid IPv4 format: {ip}"
    print(f"  -> Local IP detected: {ip} PASSED!")


def test_end_to_end_api_stack():
    print("[3/3] Testing End-to-End API Stack...")
    client = TestClient(app)

    # Test status endpoint
    res = client.get("/api/status")
    assert res.status_code == 200
    metrics = res.json()
    assert metrics["is_connected"] is False

    # Test virtual camera endpoints
    res_vcam = client.post("/api/vcam/stop")
    assert res_vcam.status_code == 200
    print("  -> End-to-End API Stack PASSED!")


if __name__ == "__main__":
    print("=" * 60)
    print("RUNNING MILESTONE 5 VERIFICATION TESTS")
    print("=" * 60)
    test_static_dashboard_serving()
    test_local_ip_detection()
    test_end_to_end_api_stack()
    print("=" * 60)
    print("ALL MILESTONE 5 TESTS PASSED SUCCESSFULLY!")
    print("=" * 60)
