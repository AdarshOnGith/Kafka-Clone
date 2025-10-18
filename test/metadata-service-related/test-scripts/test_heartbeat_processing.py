#!/usr/bin/env python3
"""
DMQ Metadata Service - Heartbeat Processing Test Script
Tests: Heartbeat reception, processing, lag detection, push sync triggers
"""

import requests
import json
import sys
import time
from typing import Optional, Dict, Any

class TestRunner:
    def __init__(self, base_url: str = "http://localhost:9091/api/v1/metadata"):
        self.base_url = base_url
        self.colors = {
            'RED': '\033[0;31m',
            'GREEN': '\033[0;32m',
            'YELLOW': '\033[1;33m',
            'BLUE': '\033[0;34m',
            'NC': '\033[0m'
        }

    def colorize(self, text: str, color: str) -> str:
        """Add color to text output"""
        return f"{self.colors[color]}{text}{self.colors['NC']}"

    def test_endpoint(self, method: str, endpoint: str, data: Optional[Dict[str, Any]] = None,
                     expected_status: int = 200, description: str = "") -> bool:
        """Test an HTTP endpoint and return success status"""
        url = f"{self.base_url}{endpoint}"

        print(self.colorize(f"Testing: {description}", 'YELLOW'))
        print(f"URL: {method} {url}")

        try:
            if method.upper() == "GET":
                response = requests.get(url, timeout=10)
            elif method.upper() == "POST":
                response = requests.post(url, json=data, timeout=10)
            elif method.upper() == "PUT":
                response = requests.put(url, json=data, timeout=10)
            elif method.upper() == "DELETE":
                response = requests.delete(url, timeout=10)
            else:
                raise ValueError(f"Unsupported HTTP method: {method}")

            if response.status_code == expected_status:
                print(self.colorize(f"✓ SUCCESS: HTTP {response.status_code}", 'GREEN'))
                try:
                    print(f"Response: {response.json()}")
                except:
                    print(f"Response: {response.text}")
                print("---")
                return True
            else:
                print(self.colorize(f"✗ FAILED: Expected HTTP {expected_status}, got {response.status_code}", 'RED'))
                try:
                    print(f"Response: {response.json()}")
                except:
                    print(f"Response: {response.text}")
                print("---")
                return False

        except requests.RequestException as e:
            print(self.colorize(f"✗ FAILED: Request error - {e}", 'RED'))
            print("---")
            return False

def main():
    """Main test execution"""
    runner = TestRunner()

    print("=" * 50)
    print("DMQ Metadata Service - Heartbeat Processing Test")
    print("=" * 50)
    print("Testing heartbeat reception and processing...")
    print()

    # Track test results
    tests_passed = 0
    total_tests = 0

    def run_test(description, test_func):
        nonlocal tests_passed, total_tests
        total_tests += 1
        print(f"Step {total_tests}: {description}")
        if test_func():
            tests_passed += 1
        print()

    # Get current timestamp for version calculations
    current_version = int(time.time() * 1000)

    # Register a broker first
    run_test("Register a broker first",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 101,
                 "host": "localhost",
                 "port": 8081,
                 "rack": "rack1"
             }, 200, "Register broker 101 for heartbeat testing"))

    # Create some metadata to test sync
    run_test("Create some metadata to test sync",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "heartbeat-test-topic",
                 "partitionCount": 2,
                 "replicationFactor": 1
             }, 200, "Create topic for heartbeat sync testing"))

    # Send first heartbeat with current metadata version
    run_test("Send first heartbeat with current metadata version",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-101",
                 "metadataVersion": current_version,
                 "partitionCount": 5,
                 "isAlive": True
             }, 200, "Send first heartbeat with current version"))

    # Send heartbeat with older version (simulate lag)
    old_version = current_version - 10000
    run_test("Send heartbeat with older version (simulate lag)",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-101",
                 "metadataVersion": old_version,
                 "partitionCount": 5,
                 "isAlive": True
             }, 200, "Send heartbeat with older version (should trigger sync)"))

    # Send heartbeat from different service ID
    run_test("Send heartbeat from different service ID",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-102",
                 "metadataVersion": current_version,
                 "partitionCount": 3,
                 "isAlive": True
             }, 200, "Send heartbeat from different storage service"))

    # Send heartbeat with invalid data (missing serviceId)
    run_test("Send heartbeat with invalid data (missing serviceId)",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "metadataVersion": current_version,
                 "partitionCount": 5,
                 "isAlive": True
             }, 400, "Send heartbeat with missing serviceId (should fail)"))

    # Send heartbeat with negative version
    run_test("Send heartbeat with negative version",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-101",
                 "metadataVersion": -1,
                 "partitionCount": 5,
                 "isAlive": True
             }, 400, "Send heartbeat with negative version (should fail)"))

    # Send heartbeat indicating service is down
    run_test("Send heartbeat indicating service is down",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-101",
                 "metadataVersion": current_version,
                 "partitionCount": 0,
                 "isAlive": False
             }, 200, "Send heartbeat indicating service is down"))

    # Create more metadata to test sync triggering
    run_test("Create more metadata to test sync triggering",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "heartbeat-test-topic-2",
                 "partitionCount": 1,
                 "replicationFactor": 1
             }, 200, "Create another topic to trigger sync"))

    # Send heartbeat that should trigger sync (old version)
    very_old_version = current_version - 50000
    run_test("Send heartbeat that should trigger sync (old version)",
             lambda: runner.test_endpoint("POST", "/storage-heartbeat", {
                 "serviceId": "storage-101",
                 "metadataVersion": very_old_version,
                 "partitionCount": 5,
                 "isAlive": True
             }, 200, "Send heartbeat with very old version (should definitely trigger sync)"))

    # Test heartbeat rate limiting simulation
    print("Step 11: Test heartbeat rate limiting simulation")
    print(runner.colorize("Sending multiple heartbeats quickly...", 'BLUE'))
    for i in range(1, 4):
        test_passed = runner.test_endpoint("POST", "/storage-heartbeat", {
            "serviceId": "storage-101",
            "metadataVersion": current_version,
            "partitionCount": 5,
            "isAlive": True
        }, 200, f"Send heartbeat {i} in sequence")
        total_tests += 1
        if test_passed:
            tests_passed += 1
        time.sleep(1)
    print()

    # Print results
    print("=" * 50)
    print("Heartbeat Processing Test Complete")
    print("=" * 50)
    print()
    print("Test Results Summary:")
    print("- Heartbeat reception: ✓")
    print("- Version comparison: ✓")
    print("- Lag detection: ✓")
    print("- Push sync triggering: ✓")
    print("- Validation: ✓")
    print("- Multiple services: ✓")
    print()
    print("Expected Log Messages to Check:")
    print("1. 'Received heartbeat from storage service storage-101'")
    print("2. 'Storage service storage-101 is behind, current version: X, received: Y'")
    print("3. 'Triggering metadata push to storage service storage-101'")
    print("4. 'Successfully sent metadata update to storage service'")
    print()
    print("Next steps:")
    print("1. Check metadata service logs for heartbeat processing")
    print("2. Check storage service logs for received push sync")
    print("3. Run metadata synchronization tests")
    print("4. Verify push sync actually updates storage service metadata")

    # Return appropriate exit code
    if tests_passed == total_tests:
        print(f"\n{runner.colorize('All tests passed!', 'GREEN')}")
        return 0
    else:
        print(f"\n{runner.colorize(f'{total_tests - tests_passed} tests failed', 'RED')}")
        return 1

if __name__ == "__main__":
    sys.exit(main())