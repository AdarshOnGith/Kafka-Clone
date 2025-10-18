#!/usr/bin/env python3
"""
Storage Service API Tester
Tests all working endpoints of the storage service and saves responses to JSON file
Shows exact response structure including headers, status codes, and data
"""

import json
import requests
import time
from datetime import datetime
from pathlib import Path

class StorageServiceTester:
    def __init__(self, base_url="http://localhost:8081"):
        self.base_url = base_url
        self.session = requests.Session()
        self.results = {
            "test_timestamp": datetime.now().isoformat(),
            "base_url": base_url,
            "endpoints_tested": [],
            "summary": {}
        }

    def make_request(self, method, endpoint, **kwargs):
        """Make HTTP request and capture full response details"""
        url = f"{self.base_url}{endpoint}"
        start_time = time.time()

        try:
            response = self.session.request(method, url, **kwargs)
            response_time = time.time() - start_time

            # Capture full response details
            result = {
                "timestamp": datetime.now().isoformat(),
                "method": method,
                "url": url,
                "endpoint": endpoint,
                "status_code": response.status_code,
                "response_time_seconds": round(response_time, 3),
                "headers": dict(response.headers),
                "content_type": response.headers.get('content-type', ''),
                "content_length": response.headers.get('content-length', ''),
                "success": response.status_code < 400
            }

            # Try to parse JSON response
            try:
                result["json_body"] = response.json()
                result["body_type"] = "json"
            except ValueError:
                result["text_body"] = response.text
                result["body_type"] = "text"

            # Raw response details
            result["raw_response"] = {
                "status_code": response.status_code,
                "reason": response.reason,
                "url": str(response.url),
                "encoding": response.encoding,
                "elapsed": str(response.elapsed)
            }

            return result

        except requests.RequestException as e:
            response_time = time.time() - start_time
            return {
                "timestamp": datetime.now().isoformat(),
                "method": method,
                "url": url,
                "endpoint": endpoint,
                "error": str(e),
                "error_type": type(e).__name__,
                "response_time_seconds": round(response_time, 3),
                "success": False
            }

    def test_health_check(self):
        """Test health check endpoint"""
        print("🔍 Testing health check endpoint...")
        result = self.make_request("GET", "/api/v1/storage/health")
        self.results["endpoints_tested"].append({
            "name": "health_check",
            "description": "Health check endpoint",
            **result
        })
        return result

    def test_produce_messages(self):
        """Test message production"""
        print("📤 Testing message production...")

        # Single message
        single_message = {
            "topic": "test-topic",
            "partition": 0,
            "producerId": 12345,
            "producerEpoch": 1,
            "messages": [
                {
                    "key": "test-key-1",
                    "value": [72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100],  # "Hello World" as bytes
                    "timestamp": int(time.time() * 1000),
                    "headers": {
                        "content-type": "text/plain"
                    }
                }
            ],
            "requiredAcks": 1,
            "timeoutMs": 30000
        }

        result = self.make_request("POST", "/api/v1/storage/messages",
                                 json=single_message,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "produce_single_message",
            "description": "Produce single message to partition",
            "request_body": single_message,
            **result
        })

        # Batch messages
        batch_messages = {
            "topic": "test-topic",
            "partition": 0,
            "producerId": 12345,
            "producerEpoch": 1,
            "messages": [
                {
                    "key": "batch-key-1",
                    "value": [66, 97, 116, 99, 104, 32, 77, 115, 103, 32, 49],  # "Batch Msg 1" as bytes
                    "timestamp": int(time.time() * 1000)
                },
                {
                    "key": "batch-key-2",
                    "value": [66, 97, 116, 99, 104, 32, 77, 115, 103, 32, 50],  # "Batch Msg 2" as bytes
                    "timestamp": int(time.time() * 1000)
                }
            ],
            "requiredAcks": 1,
            "timeoutMs": 30000
        }

        result = self.make_request("POST", "/api/v1/storage/messages",
                                 json=batch_messages,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "produce_batch_messages",
            "description": "Produce batch of messages to partition",
            "request_body": batch_messages,
            **result
        })

        return result

    def test_consume_messages(self):
        """Test message consumption"""
        print("📥 Testing message consumption...")

        # Consume from beginning
        consume_request = {
            "topic": "test-topic",
            "partition": 0,
            "offset": 0,
            "maxMessages": 10
        }

        result = self.make_request("POST", "/api/v1/storage/consume",
                                 json=consume_request,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "consume_from_beginning",
            "description": "Consume messages from offset 0",
            "request_body": consume_request,
            **result
        })

        # Consume from specific offset
        consume_request_offset = {
            "topic": "test-topic",
            "partition": 0,
            "offset": 1,
            "maxMessages": 5
        }

        result = self.make_request("POST", "/api/v1/storage/consume",
                                 json=consume_request_offset,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "consume_from_offset",
            "description": "Consume messages from specific offset",
            "request_body": consume_request_offset,
            **result
        })

        return result

    def test_high_water_mark(self):
        """Test high water mark retrieval"""
        print("📊 Testing high water mark retrieval...")

        result = self.make_request("GET", "/api/v1/storage/partitions/test-topic/0/high-water-mark")
        self.results["endpoints_tested"].append({
            "name": "get_high_water_mark",
            "description": "Get high water mark for partition",
            **result
        })

        return result

    def test_replication_endpoint(self):
        """Test replication endpoint (follower)"""
        print("🔄 Testing replication endpoint...")

        replication_request = {
            "leaderId": 1,
            "followerId": 2,
            "topic": "test-topic",
            "partition": 0,
            "leaderEpoch": 1,
            "messages": [
                {
                    "offset": 0,
                    "key": "replication-key",
                    "value": [82, 101, 112, 108, 105, 99, 97, 116, 105, 111, 110],  # "Replication" as bytes
                    "timestamp": int(time.time() * 1000)
                }
            ],
            "highWaterMark": 1
        }

        result = self.make_request("POST", "/api/v1/storage/replicate",
                                 json=replication_request,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "replication_request",
            "description": "Process replication request from leader",
            "request_body": replication_request,
            **result
        })

        return result

    def test_metadata_update(self):
        """Test metadata update endpoint"""
        print("📋 Testing metadata update endpoint...")

        metadata_request = {
            "brokers": [
                {
                    "id": 1,
                    "host": "localhost",
                    "port": 8081,
                    "rack": "rack1"
                },
                {
                    "id": 2,
                    "host": "localhost",
                    "port": 8082,
                    "rack": "rack1"
                }
            ],
            "partitions": [
                {
                    "topic": "test-topic",
                    "partition": 0,
                    "leaderId": 1,
                    "followerIds": [2],
                    "isrIds": [1, 2]
                }
            ],
            "timestamp": int(time.time() * 1000)
        }

        result = self.make_request("POST", "/api/v1/storage/metadata",
                                 json=metadata_request,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "metadata_update",
            "description": "Update metadata from metadata service",
            "request_body": metadata_request,
            **result
        })

        return result

    def test_invalid_endpoints(self):
        """Test some invalid requests to see error responses"""
        print("❌ Testing invalid requests...")

        # Invalid topic name
        invalid_produce = {
            "topic": "",
            "partition": 0,
            "messages": [{"value": [72, 101, 108, 108, 111]}]
        }

        result = self.make_request("POST", "/api/v1/storage/messages",
                                 json=invalid_produce,
                                 headers={"Content-Type": "application/json"})
        self.results["endpoints_tested"].append({
            "name": "invalid_produce_request",
            "description": "Test invalid produce request (empty topic)",
            "request_body": invalid_produce,
            **result
        })

        # Invalid endpoint
        result = self.make_request("GET", "/api/v1/storage/nonexistent")
        self.results["endpoints_tested"].append({
            "name": "nonexistent_endpoint",
            "description": "Test request to non-existent endpoint",
            **result
        })

        return result

    def generate_summary(self):
        """Generate test summary"""
        total_tests = len(self.results["endpoints_tested"])
        successful_tests = sum(1 for test in self.results["endpoints_tested"] if test.get("success", False))
        failed_tests = total_tests - successful_tests

        self.results["summary"] = {
            "total_endpoints_tested": total_tests,
            "successful_tests": successful_tests,
            "failed_tests": failed_tests,
            "success_rate": round((successful_tests / total_tests) * 100, 2) if total_tests > 0 else 0,
            "endpoints_by_status": {}
        }

        # Group by status code
        status_counts = {}
        for test in self.results["endpoints_tested"]:
            status = test.get("status_code", "error")
            status_counts[status] = status_counts.get(status, 0) + 1

        self.results["summary"]["endpoints_by_status"] = status_counts

    def save_results(self, output_file="storage_service_api_responses.json"):
        """Save test results to JSON file"""
        self.generate_summary()

        output_path = Path(output_file)
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(self.results, f, indent=2, ensure_ascii=False)

        print(f"\n💾 Results saved to: {output_path.absolute()}")
        print(f"📊 Total endpoints tested: {self.results['summary']['total_endpoints_tested']}")
        print(f"✅ Successful: {self.results['summary']['successful_tests']}")
        print(f"❌ Failed: {self.results['summary']['failed_tests']}")
        print(f"📈 Success rate: {self.results['summary']['success_rate']}%")

    def run_all_tests(self):
        """Run all endpoint tests"""
        print("🚀 Starting Storage Service API Testing")
        print("=" * 50)
        print(f"Target URL: {self.base_url}")
        print(f"Timestamp: {self.results['test_timestamp']}")
        print()

        # Test all endpoints
        self.test_health_check()
        time.sleep(0.5)

        self.test_produce_messages()
        time.sleep(0.5)

        self.test_consume_messages()
        time.sleep(0.5)

        self.test_high_water_mark()
        time.sleep(0.5)

        self.test_replication_endpoint()
        time.sleep(0.5)

        self.test_metadata_update()
        time.sleep(0.5)

        self.test_invalid_endpoints()

        # Save results
        self.save_results()

        print("\n🎉 Testing complete!")
        print("Check the JSON file for detailed response structures including:")
        print("- HTTP status codes and headers")
        print("- Response bodies (JSON/text)")
        print("- Request/response timing")
        print("- Error details for failed requests")


def main():
    import argparse

    parser = argparse.ArgumentParser(description="Test Storage Service API endpoints")
    parser.add_argument("--url", default="http://localhost:8081",
                       help="Base URL of storage service (default: http://localhost:8081)")
    parser.add_argument("--output", default="storage_service_api_responses.json",
                       help="Output JSON file path")

    args = parser.parse_args()

    tester = StorageServiceTester(args.url)
    tester.run_all_tests()


if __name__ == "__main__":
    main()