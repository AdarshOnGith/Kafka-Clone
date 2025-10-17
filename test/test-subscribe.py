"""
Enhanced Test Script for DMQConsumer.subscribe() method
Tests the consumer client library's subscribe functionality
with comprehensive test coverage including edge cases and error scenarios
"""

import requests
import json
import time
import sys
from datetime import datetime
from typing import Dict, Any, List

# Configuration
CES_URL = "http://localhost:8081"
TEST_CATEGORIES = {
    "BASIC": "Basic Functionality Tests",
    "EDGE_CASE": "Edge Case Tests",
    "ERROR": "Error Handling Tests",
    "VALIDATION": "Request Validation Tests",
    "PERFORMANCE": "Performance Tests",
    "IDEMPOTENCY": "Idempotency Tests"
}

TEST_CASES = [
    # ============ BASIC FUNCTIONALITY TESTS ============
    {
        "category": "BASIC",
        "name": "Subscribe to single topic (orders)",
        "request": {
            "groupId": "test-group-1",
            "consumerId": "test-consumer-1",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "expected_partitions": 2,
        "validate_response": {
            "check_success": True,
            "check_group_id": "test-group-1",
            "check_partition_count": 2,
            "check_topics": ["orders"]
        }
    },
    {
        "category": "BASIC",
        "name": "Subscribe to multiple topics (orders, payments)",
        "request": {
            "groupId": "test-group-2",
            "consumerId": "test-consumer-2",
            "topics": ["orders", "payments"],
            "clientId": "test-client"
        },
        "expected_partitions": 3,  # 2 from orders + 1 from payments
        "validate_response": {
            "check_success": True,
            "check_topics": ["orders", "payments"]
        }
    },
    {
        "category": "BASIC",
        "name": "Subscribe to all available topics",
        "request": {
            "groupId": "test-group-3",
            "consumerId": "test-consumer-3",
            "topics": ["orders", "payments", "inventory"],
            "clientId": "test-client"
        },
        "expected_partitions": 5  # 2 + 1 + 2
    },
    
    # ============ EDGE CASE TESTS ============
    {
        "category": "EDGE_CASE",
        "name": "Empty topic list (should fail)",
        "request": {
            "groupId": "test-group-empty",
            "consumerId": "test-consumer-empty",
            "topics": [],
            "clientId": "test-client"
        },
        "should_fail": True,
        "expected_error": "Topics cannot be empty"
    },
    {
        "category": "EDGE_CASE",
        "name": "Duplicate topics in list",
        "request": {
            "groupId": "test-group-dup",
            "consumerId": "test-consumer-dup",
            "topics": ["orders", "orders", "payments"],
            "clientId": "test-client"
        },
        "expected_partitions": 3,  # Should deduplicate
        "validate_response": {
            "check_no_duplicate_partitions": True
        }
    },
    {
        "category": "EDGE_CASE",
        "name": "Very long group ID",
        "request": {
            "groupId": "test-group-" + "a" * 200,  # 200+ chars
            "consumerId": "test-consumer-long",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "expected_partitions": 2
    },
    {
        "category": "EDGE_CASE",
        "name": "Special characters in group ID",
        "request": {
            "groupId": "test-group_special-chars.123",
            "consumerId": "test-consumer-special",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "expected_partitions": 2
    },
    {
        "category": "EDGE_CASE",
        "name": "Same consumer subscribing twice (idempotent)",
        "request": {
            "groupId": "test-group-idempotent",
            "consumerId": "test-consumer-same",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "expected_partitions": 2,
        "run_twice": True  # Should succeed both times
    },
    
    # ============ ERROR HANDLING TESTS ============
    {
        "category": "ERROR",
        "name": "Subscribe to non-existent topic",
        "request": {
            "groupId": "test-group-4",
            "consumerId": "test-consumer-4",
            "topics": ["non-existent-topic"],
            "clientId": "test-client"
        },
        "should_fail": True,
        "expected_error": "not found"
    },
    {
        "category": "ERROR",
        "name": "Mixed valid and invalid topics",
        "request": {
            "groupId": "test-group-mixed",
            "consumerId": "test-consumer-mixed",
            "topics": ["orders", "fake-topic", "payments"],
            "clientId": "test-client"
        },
        "should_fail": True,
        "expected_error": "fake-topic"
    },
    {
        "category": "ERROR",
        "name": "All topics non-existent",
        "request": {
            "groupId": "test-group-none",
            "consumerId": "test-consumer-none",
            "topics": ["fake1", "fake2"],
            "clientId": "test-client"
        },
        "should_fail": True
    },
    
    # ============ VALIDATION TESTS ============
    {
        "category": "VALIDATION",
        "name": "Missing groupId (should fail)",
        "request": {
            "consumerId": "test-consumer-no-group",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "should_fail": True,
        "skip_validation": False
    },
    {
        "category": "VALIDATION",
        "name": "Missing consumerId (should fail)",
        "request": {
            "groupId": "test-group-no-consumer",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "should_fail": True,
        "skip_validation": False
    },
    {
        "category": "VALIDATION",
        "name": "Missing topics (should fail)",
        "request": {
            "groupId": "test-group-no-topics",
            "consumerId": "test-consumer-no-topics",
            "clientId": "test-client"
        },
        "should_fail": True,
        "skip_validation": False
    },
    {
        "category": "VALIDATION",
        "name": "Null/None values in request",
        "request": {
            "groupId": None,
            "consumerId": "test-consumer-null",
            "topics": ["orders"],
            "clientId": "test-client"
        },
        "should_fail": True
    },
    
    # ============ PERFORMANCE TESTS ============
    {
        "category": "PERFORMANCE",
        "name": "Subscribe with many topics (10+)",
        "request": {
            "groupId": "test-group-many-topics",
            "consumerId": "test-consumer-many",
            "topics": ["orders", "payments", "inventory"] * 5,  # 15 topics (with duplicates)
            "clientId": "test-client"
        },
        "expected_partitions": 5,  # Should deduplicate to 3 topics
        "performance_check": {
            "max_response_time_ms": 1000  # Should respond within 1 second
        }
    },
    {
        "category": "PERFORMANCE",
        "name": "Large payload test",
        "request": {
            "groupId": "test-group-large",
            "consumerId": "test-consumer-large-" + "x" * 100,
            "topics": ["orders", "payments", "inventory"],
            "clientId": "test-client-" + "y" * 100
        },
        "expected_partitions": 5
    },
    
    # ============ IDEMPOTENCY TESTS ============
    {
        "category": "IDEMPOTENCY",
        "name": "Multiple consumers in same group",
        "requests": [
            {
                "groupId": "shared-group",
                "consumerId": "consumer-1",
                "topics": ["orders"],
                "clientId": "client-1"
            },
            {
                "groupId": "shared-group",
                "consumerId": "consumer-2",
                "topics": ["orders"],
                "clientId": "client-2"
            }
        ],
        "multi_request": True,
        "expected_partitions": 2  # Each should succeed
    }
]


def print_header(text):
    """Print a formatted header"""
    print("\n" + "=" * 80)
    print(f"  {text}")
    print("=" * 80)


def print_section(text):
    """Print a section divider"""
    print("\n" + "-" * 80)
    print(f"  {text}")
    print("-" * 80)


def check_ces_health():
    """Check if Mock CES is running"""
    print_section("Checking Mock CES Health")
    try:
        response = requests.get(f"{CES_URL}/health", timeout=2)
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Mock CES is healthy: {data}")
            return True
        else:
            print(f"❌ Mock CES health check failed: {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print(f"❌ Cannot connect to Mock CES: {str(e)}")
        print(f"\nPlease start the Mock CES server first:")
        print(f"  python test/mock-ces-server.py")
        return False


def list_available_topics():
    """List available topics from Mock CES"""
    print_section("Available Topics in Mock CES")
    try:
        response = requests.get(f"{CES_URL}/topics", timeout=2)
        if response.status_code == 200:
            data = response.json()
            print(f"Topics: {data['topics']}")
            print(f"Count: {data['count']}")
            return data['topics']
        else:
            print(f"❌ Failed to list topics: {response.status_code}")
            return []
    except Exception as e:
        print(f"❌ Error listing topics: {str(e)}")
        return []


def validate_response_structure(response_data: Dict[str, Any], test_case: Dict[str, Any]) -> tuple[bool, List[str]]:
    """Validate response structure and content"""
    errors = []
    validation = test_case.get('validate_response', {})
    
    # Check success flag
    if validation.get('check_success') and not response_data.get('success'):
        errors.append("Response success flag is False")
    
    # Check group ID matches
    if 'check_group_id' in validation:
        expected_group = validation['check_group_id']
        actual_group = response_data.get('groupId')
        if expected_group != actual_group:
            errors.append(f"Group ID mismatch: expected '{expected_group}', got '{actual_group}'")
    
    # Check partition count
    if 'check_partition_count' in validation:
        expected_count = validation['check_partition_count']
        actual_count = len(response_data.get('partitions', []))
        if expected_count != actual_count:
            errors.append(f"Partition count mismatch: expected {expected_count}, got {actual_count}")
    
    # Check topics in response
    if 'check_topics' in validation:
        expected_topics = set(validation['check_topics'])
        partitions = response_data.get('partitions', [])
        actual_topics = set(p['topicName'] for p in partitions)
        if expected_topics != actual_topics:
            errors.append(f"Topics mismatch: expected {expected_topics}, got {actual_topics}")
    
    # Check for duplicate partitions
    if validation.get('check_no_duplicate_partitions'):
        partitions = response_data.get('partitions', [])
        partition_keys = [(p['topicName'], p['partitionId']) for p in partitions]
        if len(partition_keys) != len(set(partition_keys)):
            errors.append("Duplicate partitions found in response")
    
    # Validate each partition has required fields
    partitions = response_data.get('partitions', [])
    for i, partition in enumerate(partitions):
        required_fields = ['topicName', 'partitionId', 'leader', 'currentOffset', 'highWaterMark']
        for field in required_fields:
            if field not in partition:
                errors.append(f"Partition {i} missing required field: {field}")
        
        # Validate leader structure
        if 'leader' in partition:
            leader = partition['leader']
            leader_fields = ['id', 'host', 'port']
            for field in leader_fields:
                if field not in leader:
                    errors.append(f"Partition {i} leader missing field: {field}")
    
    return len(errors) == 0, errors


def test_subscribe(test_case: Dict[str, Any]) -> bool:
    """Test a single subscribe scenario with enhanced validation"""
    category = test_case.get('category', 'UNKNOWN')
    print_section(f"[{category}] {test_case['name']}")
    
    # Handle multi-request tests
    if test_case.get('multi_request'):
        return test_multi_request(test_case)
    
    request_data = test_case.get('request')
    should_fail = test_case.get('should_fail', False)
    run_twice = test_case.get('run_twice', False)
    performance_check = test_case.get('performance_check', {})
    
    # Print request
    print("\n📤 Request to /api/consumer/join-group:")
    print(json.dumps(request_data, indent=2, default=str))
    
    try:
        # First request
        result = send_subscribe_request(request_data, should_fail, test_case, performance_check)
        
        # If run_twice is True, send same request again (idempotency check)
        if run_twice and result:
            print("\n🔄 Running same request again (idempotency check)...")
            time.sleep(0.5)
            result = send_subscribe_request(request_data, should_fail, test_case, performance_check)
            if result:
                print("✅ Idempotency check passed - Same request succeeded twice")
        
        return result
                
    except Exception as e:
        print(f"\n❌ Test FAILED - Exception occurred: {str(e)}")
        return False


def send_subscribe_request(request_data: Dict[str, Any], should_fail: bool, 
                           test_case: Dict[str, Any], performance_check: Dict[str, Any]) -> bool:
    """Send a single subscribe request and validate response"""
    try:
        # Send request
        start_time = time.time()
        response = requests.post(
            f"{CES_URL}/api/consumer/join-group",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=5
        )
        elapsed = (time.time() - start_time) * 1000  # Convert to ms
        
        # Performance check
        if performance_check.get('max_response_time_ms'):
            max_time = performance_check['max_response_time_ms']
            if elapsed > max_time:
                print(f"\n⚠️  Performance Warning: Response took {elapsed:.2f}ms (max: {max_time}ms)")
            else:
                print(f"\n✅ Performance OK: Response took {elapsed:.2f}ms (max: {max_time}ms)")
        
        # Print response
        print(f"\n📥 Response (HTTP {response.status_code}) - {elapsed:.2f}ms:")
        response_data = response.json()
        print(json.dumps(response_data, indent=2, default=str))
        
        # Validate response
        if should_fail:
            # Expected to fail
            if response.status_code != 200 or not response_data.get('success'):
                error_msg = response_data.get('errorMessage', 'Unknown error')
                expected_error = test_case.get('expected_error', '')
                
                if expected_error and expected_error.lower() in error_msg.lower():
                    print(f"\n✅ Test PASSED - Failed as expected with correct error: '{error_msg}'")
                elif expected_error:
                    print(f"\n⚠️  Test PASSED - Failed as expected but error message differs")
                    print(f"   Expected: '{expected_error}' in error")
                    print(f"   Got: '{error_msg}'")
                else:
                    print(f"\n✅ Test PASSED - Request failed as expected: {error_msg}")
                return True
            else:
                print("\n❌ Test FAILED - Request should have failed but succeeded")
                return False
        else:
            # Expected to succeed
            if response.status_code == 200 and response_data.get('success'):
                partitions = response_data.get('partitions', [])
                expected_count = test_case.get('expected_partitions')
                
                # Validate response structure
                valid, errors = validate_response_structure(response_data, test_case)
                if not valid:
                    print(f"\n❌ Test FAILED - Response validation errors:")
                    for error in errors:
                        print(f"   - {error}")
                    return False
                
                # Check partition count
                if expected_count is not None and len(partitions) != expected_count:
                    print(f"\n❌ Test FAILED - Expected {expected_count} partitions, got {len(partitions)}")
                    return False
                
                print(f"\n✅ Test PASSED - Received {len(partitions)} partitions as expected")
                
                # Print partition details
                print("\n📊 Partition Details:")
                partition_summary = {}
                for partition in partitions:
                    topic = partition['topicName']
                    if topic not in partition_summary:
                        partition_summary[topic] = []
                    partition_summary[topic].append(partition['partitionId'])
                    
                    print(f"  - Topic: {partition['topicName']}, "
                          f"Partition: {partition['partitionId']}, "
                          f"Leader: {partition['leader']['host']}:{partition['leader']['port']}, "
                          f"CurrentOffset: {partition['currentOffset']}, "
                          f"HighWaterMark: {partition['highWaterMark']}")
                
                print("\n📈 Summary by Topic:")
                for topic, part_ids in partition_summary.items():
                    print(f"  - {topic}: {len(part_ids)} partitions {sorted(part_ids)}")
                
                return True
            else:
                print(f"\n❌ Test FAILED - Request failed unexpectedly")
                print(f"   Error: {response_data.get('errorMessage', 'Unknown error')}")
                return False
                
    except requests.exceptions.Timeout:
        print(f"\n❌ Test FAILED - Request timeout")
        return False
    except requests.exceptions.ConnectionError:
        print(f"\n❌ Test FAILED - Connection error (Is CES running?)")
        return False
    except Exception as e:
        print(f"\n❌ Test FAILED - Exception: {str(e)}")
        return False


def test_multi_request(test_case: Dict[str, Any]) -> bool:
    """Test scenario with multiple requests"""
    requests_list = test_case.get('requests', [])
    expected_partitions = test_case.get('expected_partitions')
    
    print(f"\n📤 Sending {len(requests_list)} requests...")
    
    all_passed = True
    for i, request_data in enumerate(requests_list, 1):
        print(f"\n--- Request {i}/{len(requests_list)} ---")
        print(json.dumps(request_data, indent=2))
        
        result = send_subscribe_request(request_data, False, 
                                       {'expected_partitions': expected_partitions}, {})
        if not result:
            all_passed = False
        
        time.sleep(0.3)  # Small delay between requests
    
    return all_passed


def run_all_tests():
    """Run all test cases organized by category"""
    print_header("DMQConsumer.subscribe() Enhanced Test Suite")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Mock CES URL: {CES_URL}")
    print(f"Total Test Cases: {len(TEST_CASES)}")
    
    # Check if CES is running
    if not check_ces_health():
        sys.exit(1)
    
    # List available topics
    list_available_topics()
    
    # Organize tests by category
    tests_by_category = {}
    for test_case in TEST_CASES:
        category = test_case.get('category', 'UNCATEGORIZED')
        if category not in tests_by_category:
            tests_by_category[category] = []
        tests_by_category[category].append(test_case)
    
    # Run tests category by category
    all_results = []
    category_results = {}
    
    for category in sorted(tests_by_category.keys()):
        tests = tests_by_category[category]
        category_name = TEST_CATEGORIES.get(category, category)
        
        print_header(f"{category_name} ({len(tests)} tests)")
        
        category_results[category] = {
            'total': len(tests),
            'passed': 0,
            'failed': 0,
            'tests': []
        }
        
        for i, test_case in enumerate(tests, 1):
            print(f"\n[{i}/{len(tests)}]")
            result = test_subscribe(test_case)
            
            test_result = {
                'category': category,
                'name': test_case['name'],
                'passed': result
            }
            all_results.append(test_result)
            category_results[category]['tests'].append(test_result)
            
            if result:
                category_results[category]['passed'] += 1
            else:
                category_results[category]['failed'] += 1
            
            time.sleep(0.5)  # Small delay between tests
        
        # Print category summary
        passed = category_results[category]['passed']
        failed = category_results[category]['failed']
        total = category_results[category]['total']
        print(f"\n{category_name}: {passed}/{total} passed")
    
    # Print overall summary
    print_header("Overall Test Summary")
    
    total_passed = sum(1 for r in all_results if r['passed'])
    total_failed = len(all_results) - total_passed
    
    print(f"\n📊 Overall Statistics:")
    print(f"   Total Tests: {len(all_results)}")
    print(f"   ✅ Passed: {total_passed} ({total_passed * 100 // len(all_results)}%)")
    print(f"   ❌ Failed: {total_failed} ({total_failed * 100 // len(all_results) if len(all_results) > 0 else 0}%)")
    
    print(f"\n📈 Results by Category:")
    for category in sorted(category_results.keys()):
        stats = category_results[category]
        category_name = TEST_CATEGORIES.get(category, category)
        pass_rate = (stats['passed'] * 100 // stats['total']) if stats['total'] > 0 else 0
        status_icon = "✅" if stats['failed'] == 0 else "⚠️" if stats['passed'] > stats['failed'] else "❌"
        print(f"   {status_icon} {category_name}: {stats['passed']}/{stats['total']} passed ({pass_rate}%)")
    
    print(f"\n📋 Detailed Results:")
    for i, result in enumerate(all_results, 1):
        status = "✅ PASS" if result['passed'] else "❌ FAIL"
        category_name = TEST_CATEGORIES.get(result['category'], result['category'])
        print(f"   {i:2d}. {status} [{result['category']:12s}] {result['name']}")
    
    # Failed tests detail
    failed_tests = [r for r in all_results if not r['passed']]
    if failed_tests:
        print(f"\n❌ Failed Tests ({len(failed_tests)}):")
        for i, result in enumerate(failed_tests, 1):
            print(f"   {i}. [{result['category']}] {result['name']}")
    
    # Exit code
    if total_failed > 0:
        print(f"\n❌ {total_failed} test(s) failed!")
        sys.exit(1)
    else:
        print("\n✅ All tests passed! 🎉")
        sys.exit(0)


def interactive_test():
    """Interactive mode for manual testing"""
    print_header("Interactive Test Mode")
    
    if not check_ces_health():
        sys.exit(1)
    
    topics = list_available_topics()
    
    print("\n📝 Enter test parameters:")
    
    # Get user input
    group_id = input("Group ID (default: test-group): ").strip() or "test-group"
    consumer_id = input("Consumer ID (default: test-consumer): ").strip() or "test-consumer"
    
    print(f"\nAvailable topics: {', '.join(topics)}")
    topics_input = input("Topics (comma-separated, e.g., orders,payments): ").strip()
    selected_topics = [t.strip() for t in topics_input.split(',') if t.strip()]
    
    if not selected_topics:
        print("❌ No topics provided!")
        return
    
    # Build request
    request_data = {
        "groupId": group_id,
        "consumerId": consumer_id,
        "topics": selected_topics,
        "clientId": "interactive-client"
    }
    
    # Run test
    test_case = {
        "name": "Interactive Test",
        "request": request_data,
        "expected_partitions": None
    }
    
    test_subscribe(test_case)


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Test DMQConsumer.subscribe() method')
    parser.add_argument('--interactive', '-i', action='store_true',
                        help='Run in interactive mode')
    
    args = parser.parse_args()
    
    if args.interactive:
        interactive_test()
    else:
        run_all_tests()
