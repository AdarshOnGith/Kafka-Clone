"""
Simple Test Script for New Consumer Subscribe Format
Tests single topic subscription with brokers + partitions response
"""

import requests
import json

CES_URL = "http://localhost:8081"

def test_subscribe_single_topic(topic):
    """Test subscribing to a single topic"""
    print(f"\n{'='*80}")
    print(f"Testing: Subscribe to topic '{topic}'")
    print('='*80)
    
    request_data = {
        "groupId": "test-group",
        "consumerId": "test-consumer-123",
        "topic": topic,  # Single topic
        "clientId": "test-client"
    }
    
    print("\n📤 Request:")
    print(json.dumps(request_data, indent=2))
    
    try:
        response = requests.post(
            f"{CES_URL}/api/consumer/join-group",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=5
        )
        
        print(f"\n📥 Response (HTTP {response.status_code}):")
        response_data = response.json()
        print(json.dumps(response_data, indent=2))
        
        if response.status_code == 200 and response_data.get('success'):
            print(f"\n✅ SUCCESS")
            
            # Analyze response
            brokers = response_data.get('brokers', [])
            partitions = response_data.get('partitions', [])
            
            print(f"\n📊 Analysis:")
            print(f"  Brokers returned: {len(brokers)}")
            for broker in brokers:
                print(f"    - Broker {broker['id']}: {broker['host']}:{broker['port']}")
            
            print(f"\n  Partitions returned: {len(partitions)}")
            for partition in partitions:
                print(f"    - {partition['topic']}-{partition['partition']}: "
                      f"leader={partition['leaderId']}, "
                      f"ISR={partition['isrIds']}, "
                      f"followers={partition['followerIds']}")
            
            # Verify all broker IDs are in brokers list
            broker_ids = set(b['id'] for b in brokers)
            all_referenced_ids = set()
            for p in partitions:
                all_referenced_ids.add(p['leaderId'])
                all_referenced_ids.update(p['followerIds'])
                all_referenced_ids.update(p['isrIds'])
            
            if all_referenced_ids.issubset(broker_ids):
                print(f"\n✅ Validation: All broker IDs referenced in partitions are present in brokers list")
            else:
                missing = all_referenced_ids - broker_ids
                print(f"\n❌ Validation FAILED: Missing broker IDs: {missing}")
            
            return True
        else:
            print(f"\n❌ FAILED: {response_data.get('errorMessage', 'Unknown error')}")
            return False
            
    except Exception as e:
        print(f"\n❌ ERROR: {str(e)}")
        return False

def test_invalid_topic():
    """Test subscribing to non-existent topic"""
    print(f"\n{'='*80}")
    print(f"Testing: Subscribe to invalid topic")
    print('='*80)
    
    request_data = {
        "groupId": "test-group",
        "consumerId": "test-consumer-456",
        "topic": "non-existent-topic",
        "clientId": "test-client"
    }
    
    print("\n📤 Request:")
    print(json.dumps(request_data, indent=2))
    
    try:
        response = requests.post(
            f"{CES_URL}/api/consumer/join-group",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=5
        )
        
        print(f"\n📥 Response (HTTP {response.status_code}):")
        response_data = response.json()
        print(json.dumps(response_data, indent=2))
        
        if response.status_code == 404 or not response_data.get('success'):
            print(f"\n✅ EXPECTED FAILURE: Topic not found")
            return True
        else:
            print(f"\n❌ Should have failed but succeeded")
            return False
            
    except Exception as e:
        print(f"\n❌ ERROR: {str(e)}")
        return False

def main():
    print("="*80)
    print("  Consumer Subscribe Test - New Format (Brokers + Partitions)")
    print("="*80)
    
    # Check CES health
    try:
        response = requests.get(f"{CES_URL}/health", timeout=2)
        if response.status_code == 200:
            health_data = response.json()
            print(f"\n✅ CES is healthy")
            print(f"   Service: {health_data.get('service')}")
            print(f"   Version: {health_data.get('version')}")
            print(f"   Format: {health_data.get('format')}")
        else:
            print(f"\n❌ CES health check failed")
            return
    except Exception as e:
        print(f"\n❌ Cannot connect to CES at {CES_URL}")
        print(f"   Error: {str(e)}")
        print(f"\n   Please start the mock server first:")
        print(f"   python test/mock-ces-server.py")
        return
    
    # List available topics
    try:
        response = requests.get(f"{CES_URL}/topics", timeout=2)
        if response.status_code == 200:
            topics_data = response.json()
            print(f"\n📋 Available Topics: {topics_data['topics']}")
    except:
        pass
    
    # Run tests
    results = []
    
    # Test 1: Subscribe to orders
    results.append(("Subscribe to 'orders'", test_subscribe_single_topic("orders")))
    
    # Test 2: Subscribe to payments
    results.append(("Subscribe to 'payments'", test_subscribe_single_topic("payments")))
    
    # Test 3: Subscribe to inventory
    results.append(("Subscribe to 'inventory'", test_subscribe_single_topic("inventory")))
    
    # Test 4: Invalid topic
    results.append(("Subscribe to invalid topic", test_invalid_topic()))
    
    # Summary
    print(f"\n{'='*80}")
    print("  Test Summary")
    print('='*80)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    print(f"\nTotal: {total} tests")
    print(f"✅ Passed: {passed}")
    print(f"❌ Failed: {total - passed}")
    
    print(f"\nDetails:")
    for i, (name, result) in enumerate(results, 1):
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"  {i}. {status} - {name}")
    
    if passed == total:
        print(f"\n✅ All tests passed!")
    else:
        print(f"\n❌ Some tests failed")

if __name__ == '__main__':
    main()
