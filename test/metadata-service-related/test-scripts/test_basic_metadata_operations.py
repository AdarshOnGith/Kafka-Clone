#!/usr/bin/env python3
"""
DMQ Metadata Service - Basic Metadata Operations Test Script
Tests: Topic CRUD operations, broker registration, basic metadata retrieval
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
    print("DMQ Metadata Service - Basic Operations Test")
    print("=" * 50)
    print("Testing basic metadata operations...")
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

    # Test broker registration
    run_test("Testing broker registration",
             lambda: runner.test_endpoint("POST", "/brokers", {
                 "id": 101,
                 "host": "localhost",
                 "port": 8081,
                 "rack": "rack1"
             }, 200, "Register broker 101 (storage service)"))

    # Test topic creation
    run_test("Testing topic creation",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "test-topic-1",
                 "partitionCount": 3,
                 "replicationFactor": 1
             }, 200, "Create test-topic-1"))

    # Test topic listing
    run_test("Testing topic listing",
             lambda: runner.test_endpoint("GET", "/topics", None, 200, "List all topics"))

    # Test topic metadata retrieval
    run_test("Testing topic metadata retrieval",
             lambda: runner.test_endpoint("GET", "/topics/test-topic-1", None, 200, "Get test-topic-1 metadata"))

    # Create another topic
    run_test("Creating another topic",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "test-topic-2",
                 "partitionCount": 2,
                 "replicationFactor": 1
             }, 200, "Create test-topic-2"))

    # Test updated topic list
    run_test("Testing updated topic list",
             lambda: runner.test_endpoint("GET", "/topics", None, 200, "List all topics (should show 2)"))

    # Test topic metadata for second topic
    run_test("Testing topic metadata for second topic",
             lambda: runner.test_endpoint("GET", "/topics/test-topic-2", None, 200, "Get test-topic-2 metadata"))

    # Test non-existent topic
    run_test("Testing non-existent topic",
             lambda: runner.test_endpoint("GET", "/topics/non-existent-topic", None, 404, "Get non-existent topic (should fail)"))

    # Test invalid topic creation (empty name)
    run_test("Testing invalid topic creation (empty name)",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "",
                 "partitionCount": 1,
                 "replicationFactor": 1
             }, 400, "Create topic with empty name (should fail)"))

    # Test invalid topic creation (zero partitions)
    run_test("Testing invalid topic creation (zero partitions)",
             lambda: runner.test_endpoint("POST", "/topics", {
                 "topicName": "invalid-topic",
                 "partitionCount": 0,
                 "replicationFactor": 1
             }, 400, "Create topic with zero partitions (should fail)"))

    # Print results
    print("=" * 50)
    print("Basic Operations Test Complete")
    print("=" * 50)
    print()
    print("Next steps:")
    print("1. Check service logs for KRaft consensus messages")
    print("2. Verify metadata persistence in PostgreSQL")
    print("3. Run heartbeat processing tests")
    print("4. Run metadata synchronization tests")

    # Return appropriate exit code
    if tests_passed == total_tests:
        print(f"\n{runner.colorize('All tests passed!', 'GREEN')}")
        return 0
    else:
        print(f"\n{runner.colorize(f'{total_tests - tests_passed} tests failed', 'RED')}")
        return 1

if __name__ == "__main__":
    sys.exit(main())