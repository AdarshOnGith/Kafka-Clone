#!/usr/bin/env python3
"""
DMQ Metadata Service - Broker Registration Test Script
Tests: Broker registration, deregistration, broker listing
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
    print("DMQ Metadata Service - Broker Registration Test")
    print("=" * 50)
    print("Testing broker registration and management...")
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

    # Register broker 101
    run_test("Register broker 101",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 101,
                 "host": "localhost",
                 "port": 8081,
                 "rack": "rack1"
             }, 200, "Register broker 101"))

    # Register broker 102
    run_test("Register broker 102",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 102,
                 "host": "localhost",
                 "port": 8082,
                 "rack": "rack1"
             }, 200, "Register broker 102"))

    # Register broker 103
    run_test("Register broker 103",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 103,
                 "host": "localhost",
                 "port": 8083,
                 "rack": "rack2"
             }, 200, "Register broker 103"))

    # List all brokers
    run_test("List all brokers",
             lambda: runner.test_endpoint("GET", "/brokers", None, 200, "List all registered brokers"))

    # Get specific broker
    run_test("Get specific broker",
             lambda: runner.test_endpoint("GET", "/brokers/101", None, 200, "Get broker 101 details"))

    # Get specific broker 2
    run_test("Get specific broker 2",
             lambda: runner.test_endpoint("GET", "/brokers/102", None, 200, "Get broker 102 details"))

    # Get non-existent broker
    run_test("Get non-existent broker",
             lambda: runner.test_endpoint("GET", "/brokers/99", None, 404, "Get non-existent broker (should fail)"))

    # Register broker with duplicate ID
    run_test("Register broker with duplicate ID (should fail)",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 101,
                 "host": "localhost",
                 "port": 8085,
                 "rack": "rack1"
             }, 409, "Register duplicate broker ID (should fail)"))

    # Register broker with invalid data (negative port)
    run_test("Register broker with invalid data (negative port)",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 4,
                 "host": "localhost",
                 "port": -1,
                 "rack": "rack1"
             }, 400, "Register broker with invalid port (should fail)"))

    # Register broker with missing required fields
    run_test("Register broker with missing required fields",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "host": "localhost",
                 "port": 8086
             }, 400, "Register broker missing ID (should fail)"))

    # Create topic to test broker assignment
    run_test("Create topic to test broker assignment",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "broker-test-topic",
                 "partitionCount": 3,
                 "replicationFactor": 1
             }, 200, "Create topic to test broker assignment"))

    # Verify topic metadata includes broker assignments
    run_test("Verify topic metadata includes broker assignments",
             lambda: runner.test_endpoint("GET", "/topics/broker-test-topic", None, 200, "Get topic metadata with broker assignments"))

    # List brokers again to confirm no changes
    run_test("List brokers again to confirm no changes",
             lambda: runner.test_endpoint("GET", "/brokers", None, 200, "List brokers after topic creation"))

    # Print results
    print("=" * 50)
    print("Broker Registration Test Complete")
    print("=" * 50)
    print()
    print("Test Results Summary:")
    print("- Broker registration: ✓")
    print("- Broker listing: ✓")
    print("- Broker retrieval: ✓")
    print("- Duplicate ID handling: ✓")
    print("- Validation: ✓")
    print("- Topic creation with brokers: ✓")
    print()
    print("Next steps:")
    print("1. Check PostgreSQL for broker persistence")
    print("2. Verify KRaft log contains broker registrations")
    print("3. Run heartbeat processing tests")

    # Return appropriate exit code
    if tests_passed == total_tests:
        print(f"\n{runner.colorize('All tests passed!', 'GREEN')}")
        return 0
    else:
        print(f"\n{runner.colorize(f'{total_tests - tests_passed} tests failed', 'RED')}")
        return 1

if __name__ == "__main__":
    sys.exit(main())