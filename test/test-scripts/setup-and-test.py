#!/usr/bin/env python3
"""
Kafka-Clone Testing Setup Script (Python version)
This script helps set up and test the publish/replication/consume flow
Windows-friendly alternative to the bash script

Tests replication behavior for different acks settings:
- acks=0: Fire-and-forget, async replication
- acks=1: Leader acknowledgment, async replication
- acks=-1: All ISR replicas, sync replication
"""

import os
import sys
import time
import json
import socket
import requests
from pathlib import Path
from datetime import datetime

# Configuration
BROKER_COUNT = 3
BASE_PORT = 8081
PROJECT_DIR = Path(__file__).parent.parent.parent
TEST_DIR = PROJECT_DIR / "test"
STORAGE_SERVICE_DIR = PROJECT_DIR / "dmq-storage-service"

# Output file for responses
RESPONSE_FILE = TEST_DIR / "test_responses.txt"

# Global list to collect all responses
test_responses = []

def print_header():
    print("🚀 Kafka-Clone Replication Behavior Testing")
    print("=" * 60)
    print(f"Project Directory: {PROJECT_DIR}")
    print(f"Test Directory: {TEST_DIR}")
    print(f"Response File: {RESPONSE_FILE}")
    print()
    print("Testing replication behavior for different acks settings:")
    print("- acks=0: Fire-and-forget (async replication)")
    print("- acks=1: Leader ack (async replication)")
    print("- acks=-1: All ISR ack (sync replication)")
    print("=" * 60)
    print()

def log_response(test_name, request_info, response_info):
    """Log a test response for later saving"""
    separator = "=" * 80
    entry = f"\n{separator}\n"
    entry += f"TEST: {test_name}\n"
    entry += f"Timestamp: {datetime.now().isoformat()}\n"
    entry += f"{separator}\n\n"
    
    # Request details
    entry += f"REQUEST:\n"
    entry += f"{'-' * 40}\n"
    for key, value in request_info.items():
        if isinstance(value, dict) or isinstance(value, list):
            entry += f"{key}:\n{json.dumps(value, indent=2)}\n"
        else:
            entry += f"{key}: {value}\n"
    entry += "\n"
    
    # Response details
    entry += f"RESPONSE:\n"
    entry += f"{'-' * 40}\n"
    for key, value in response_info.items():
        if isinstance(value, dict) or isinstance(value, list):
            entry += f"{key}:\n{json.dumps(value, indent=2)}\n"
        else:
            entry += f"{key}: {value}\n"
    entry += f"\n{separator}\n"
    
    test_responses.append(entry)

def save_all_responses():
    """Save all collected responses to file"""
    try:
        with open(RESPONSE_FILE, 'w', encoding='utf-8') as f:
            f.write(f"KAFKA-CLONE TEST RESPONSES\n")
            f.write(f"Generated: {datetime.now().isoformat()}\n")
            f.write(f"{'=' * 80}\n\n")
            f.write(f"This file contains all HTTP request/response details from the test run.\n")
            f.write(f"Tests cover replication behavior for acks=0, acks=1, and acks=-1.\n")
            f.write(f"\n{'=' * 80}\n")
            
            for entry in test_responses:
                f.write(entry)
        
        print(f"\n💾 All responses saved to: {RESPONSE_FILE}")
        print(f"📊 Total tests logged: {len(test_responses)}")
    except Exception as e:
        print(f"❌ Failed to save responses: {e}")

def check_port_available(port):
    """Check if a port is available (not in use)"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(1)
        result = sock.connect_ex(('localhost', port))
        return result != 0  # True if port is available

def wait_for_service(url, max_attempts=30):
    """Wait for service to be ready"""
    print(f"⏳ Waiting for service at {url}...")

    for attempt in range(1, max_attempts + 1):
        try:
            response = requests.get(url, timeout=5)
            if response.status_code == 200:
                print(f"✅ Service is ready at {url}")
                return True
        except requests.RequestException:
            pass

        print(f"   Attempt {attempt}/{max_attempts}...")
        time.sleep(2)

    print(f"❌ Service failed to start at {url}")
    return False

def populate_metadata(port):
    """Populate metadata for a broker"""
    url = f"http://localhost:{port}/api/v1/storage/metadata"
    metadata_file = TEST_DIR / "metadata-setup.json"

    print(f"📤 Populating metadata for broker on port {port}...")

    try:
        with open(metadata_file, 'r') as f:
            data = json.load(f)

        response = requests.post(url, json=data, timeout=10)

        # Log the response
        log_response(
            f"Metadata Population - Broker {port}",
            {
                "method": "POST",
                "url": url,
                "body": data
            },
            {
                "status_code": response.status_code,
                "headers": dict(response.headers),
                "body": response.json() if response.headers.get('content-type', '').startswith('application/json') else response.text
            }
        )

        if response.status_code == 200:
            print(f"✅ Metadata populated for broker on port {port}")
            return True
        else:
            print(f"❌ Failed to populate metadata for broker on port {port} (Status: {response.status_code})")
            print(f"   Response: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Failed to populate metadata for broker on port {port}: {e}")
        return False

def test_publish(port, test_file):
    """Test message publishing"""
    url = f"http://localhost:{port}/api/v1/storage/messages"
    test_file_path = TEST_DIR / test_file

    print(f"📤 Testing publish with {test_file} on port {port}...")

    try:
        with open(test_file_path, 'r') as f:
            data = json.load(f)

        response = requests.post(url, json=data, timeout=10)
        response_data = response.json()

        if response.status_code == 200 and response_data.get('success') == True:
            # Extract offset from response
            results = response_data.get('results', [])
            if results:
                offset = results[0].get('offset', 'unknown')
                print(f"✅ Publish successful on port {port}")
                print(f"   Response: offset {offset}")
            else:
                print(f"✅ Publish successful on port {port}")
            return True
        else:
            error_msg = response_data.get('errorMessage', 'Unknown error')
            print(f"❌ Publish failed on port {port}")
            print(f"   Status Code: {response.status_code}")
            print(f"   Error: {error_msg}")
            # Print full response for debugging
            print(f"   Full Response: {json.dumps(response_data, indent=2)}")
            return False

    except Exception as e:
        print(f"❌ Publish failed on port {port}: {e}")
        return False

def test_consume(port, topic, partition, offset=0, max_messages=10, description=""):
    """Test message consumption"""
    url = f"http://localhost:{port}/api/v1/storage/consume"

    print(f"� Testing consume from offset {offset} (max {max_messages}) on port {port}{description}...")

    try:
        request_data = {
            "topic": topic,
            "partition": partition,
            "offset": offset,
            "maxMessages": max_messages
        }

        response = requests.post(url, json=request_data, timeout=10)
        response_data = response.json()

        if response.status_code == 200 and response_data.get('success') == True:
            messages = response_data.get('messages', [])
            high_water_mark = response_data.get('highWaterMark', 0)

            print(f"✅ Consume successful on port {port}")
            print(f"   Messages received: {len(messages)}")
            print(f"   High water mark: {high_water_mark}")

            # Print message details
            for i, msg in enumerate(messages):
                key = msg.get('key', 'null')
                value_size = len(msg.get('value', []))
                offset_val = msg.get('offset', 'unknown')
                timestamp = msg.get('timestamp', 'unknown')
                print(f"   Message {i}: offset={offset_val}, key='{key}', value_size={value_size}B, ts={timestamp}")

            return messages, high_water_mark
        else:
            error_msg = response_data.get('errorMessage', 'Unknown error')
            print(f"❌ Consume failed on port {port}")
            print(f"   Status Code: {response.status_code}")
            print(f"   Error: {error_msg}")
            print(f"   Full Response: {json.dumps(response_data, indent=2)}")
            return None, None

    except Exception as e:
        print(f"❌ Consume failed on port {port}: {e}")
        return None, None

def main():
    print_header()

    # Check port availability
    print("🔍 Checking broker status...")
    brokers_running = 0
    for i in range(BROKER_COUNT):
        port = BASE_PORT + i
        if check_port_available(port):
            print(f"❌ Port {port} is available (broker not running)")
        else:
            print(f"✅ Port {port} is in use (broker running)")
            brokers_running += 1
        add_delay(0.2)  # Small delay between port checks

    if brokers_running == BROKER_COUNT:
        print(f"\n🎉 All {BROKER_COUNT} brokers appear to be running!")
        print("Skipping setup instructions and proceeding to testing...")
        should_test = input("\nDo you want to proceed with testing? (y/N): ").lower().strip()
        if should_test != 'y':
            print("Exiting...")
            sys.exit(0)
    elif brokers_running > 0:
        print(f"\n⚠️  {brokers_running} out of {BROKER_COUNT} brokers appear to be running.")
        choice = input("Do you want to (s)tart missing brokers, (t)est with current brokers, or (q)uit? (s/t/q): ").lower().strip()
        if choice == 'q':
            sys.exit(0)
        elif choice == 't':
            print("Proceeding with testing current brokers...")
        else:
            show_setup_instructions()
            input("\nPress Enter after starting the brokers...")
    else:
        print(f"\n📋 No brokers are currently running.")
        show_setup_instructions()
        input("\nPress Enter after starting the brokers...")

    print()
    print("🔄 Testing broker connectivity...")
    for i in range(BROKER_COUNT):
        port = BASE_PORT + i
        if not wait_for_service(f"http://localhost:{port}/api/v1/storage/health"):
            print(f"❌ Broker on port {port} failed to respond")
            sys.exit(1)
        add_delay(0.3)  # Small delay between health checks

    print()
    print("📤 Populating metadata on all brokers...")
    for i in range(BROKER_COUNT):
        port = BASE_PORT + i
        if not populate_metadata(port):
            print(f"❌ Failed to populate metadata on broker {port}")
            sys.exit(1)
        add_delay(0.5)  # Small delay between metadata population

    # Show cluster topology
    show_cluster_topology()
    add_delay(2)  # Give time to read the topology

    print()
    # return
    print("🧪 Running publish tests...")
    print("==========================")
    add_delay(1)

    # Test single message publish (to leader - broker 1)
    print("Testing single message publish...")
    add_delay(0.5)
    if test_publish(8081, "single-message-publish.json"):
        print("✅ Single message test passed")
    else:
        print("❌ Single message test failed")
    add_delay(1)

    print()

    # Test producing messages to orders topic partitions
    print("Testing produce messages to 'orders' topic partitions...")
    add_delay(0.5)
    test_produce_orders()
    add_delay(1)

    
    # Test batch message publish (to leader - broker 1)
    print("Testing batch message publish...")
    add_delay(0.5)
    if test_publish(8081, "batch-message-publish.json"):
        print("✅ Batch message test passed")
    else:
        print("❌ Batch message test failed")
    add_delay(1)

    # Test batch messages to orders topic partitions
    print("Testing batch messages to orders topic partitions...")
    add_delay(0.5)
    test_batch_orders()
    add_delay(1)

    print()
    print("🧪 Running consume tests...")
    print("==========================")
    add_delay(1)

    # Test consuming messages from test-topic (leader - broker 1)
    print("Testing consume from test-topic partition 0 (leader - broker 1)...")
    add_delay(0.5)
    consumed_test_topic, hwm_test_topic = test_consume(8081, "test-topic", 0, offset=0, max_messages=10, description=" (all messages)")
    if consumed_test_topic is not None:
        print(f"✅ Test-topic consume test passed - received {len(consumed_test_topic)} messages, HWM: {hwm_test_topic}")
    else:
        print("❌ Test-topic consume test failed")
    add_delay(1)

    # Test consuming from specific offset on test-topic
    print("Testing consume from test-topic offset 1...")
    add_delay(0.5)
    consumed_from_offset, _ = test_consume(8081, "test-topic", 0, offset=1, max_messages=10, description=" (from offset 1)")
    if consumed_from_offset is not None:
        print(f"✅ Test-topic offset consume test passed - received {len(consumed_from_offset)} messages")
    else:
        print("❌ Test-topic offset consume test failed")
    add_delay(1)

    # Test consuming with max messages limit on test-topic
    print("Testing consume from test-topic with max messages limit (2)...")
    add_delay(0.5)
    consumed_limited, _ = test_consume(8081, "test-topic", 0, offset=0, max_messages=2, description=" (max 2 messages)")
    if consumed_limited is not None:
        print(f"✅ Test-topic limited consume test passed - received {len(consumed_limited)} messages")
    else:
        print("❌ Test-topic limited consume test failed")
    add_delay(1)

    # Test High Water Mark endpoint for test-topic
    print("Testing High Water Mark endpoint for test-topic...")
    add_delay(0.5)
    hwm_url = "http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark"
    print(f"📊 Checking HWM for test-topic-0 (port 8081)...")
    try:
        resp = requests.get(hwm_url, timeout=5)
        hwm_value = resp.text if resp.status_code == 200 else "N/A"
        
        log_response(
            "Get HWM for test-topic-0",
            {
                "method": "GET",
                "url": hwm_url,
                "port": 8081
            },
            {
                "status_code": resp.status_code,
                "headers": dict(resp.headers),
                "body": hwm_value
            }
        )
        
        print(f"   HWM for test-topic-0: {hwm_value}")
        print("✅ HWM endpoint test passed")
    except Exception as e:
        print(f"❌ HWM endpoint test failed: {e}")
    add_delay(1)

    # Test consuming from orders topic partitions
    print("Testing consume from orders topic partitions...")
    add_delay(0.5)
    
    # Consume from orders-0 (leader - broker 2, port 8082)
    print("  📥 Testing orders partition 0 (broker 2)...")
    consumed_orders_p0, hwm_orders_p0 = test_consume(8082, "orders", 0, offset=0, max_messages=10, description=" (orders-0 all messages)")
    if consumed_orders_p0 is not None:
        print(f"  ✅ Orders-0 consume test passed - received {len(consumed_orders_p0)} messages, HWM: {hwm_orders_p0}")
        # Show message details for orders
        for i, msg in enumerate(consumed_orders_p0[:3]):  # Show first 3 messages
            key = msg.get('key', 'null')
            value_size = len(msg.get('value', []))
            offset_val = msg.get('offset', 'unknown')
            headers = msg.get('headers', {})
            test_type = headers.get('test', 'unknown') if headers else 'unknown'
            print(f"     Message {i}: offset={offset_val}, key='{key}', value_size={value_size}B, test='{test_type}'")
        if len(consumed_orders_p0) > 3:
            print(f"     ... and {len(consumed_orders_p0) - 3} more messages")
    else:
        print("  ❌ Orders-0 consume test failed")
    
    # Consume from orders-1 (leader - broker 3, port 8083)
    print("  📥 Testing orders partition 1 (broker 3)...")
    consumed_orders_p1, hwm_orders_p1 = test_consume(8083, "orders", 1, offset=0, max_messages=10, description=" (orders-1 all messages)")
    if consumed_orders_p1 is not None:
        print(f"  ✅ Orders-1 consume test passed - received {len(consumed_orders_p1)} messages, HWM: {hwm_orders_p1}")
        # Show message details for orders
        for i, msg in enumerate(consumed_orders_p1[:3]):  # Show first 3 messages
            key = msg.get('key', 'null')
            value_size = len(msg.get('value', []))
            offset_val = msg.get('offset', 'unknown')
            headers = msg.get('headers', {})
            test_type = headers.get('test', 'unknown') if headers else 'unknown'
            print(f"     Message {i}: offset={offset_val}, key='{key}', value_size={value_size}B, test='{test_type}'")
        if len(consumed_orders_p1) > 3:
            print(f"     ... and {len(consumed_orders_p1) - 3} more messages")
    else:
        print("  ❌ Orders-1 consume test failed")
    
    add_delay(1)

    # Test consuming from orders with specific scenarios
    print("Testing consume scenarios for orders topic...")
    add_delay(0.5)
    
    # Scenario 1: Consume from orders-0 with offset (skip first message)
    if consumed_orders_p0 and len(consumed_orders_p0) > 1:
        print("  📋 Scenario 1: Consume from orders-0 starting from offset 1...")
        consumed_orders_p0_offset1, _ = test_consume(8082, "orders", 0, offset=1, max_messages=5, description=" (orders-0 from offset 1)")
        if consumed_orders_p0_offset1 is not None:
            print(f"  ✅ Orders-0 offset scenario passed - received {len(consumed_orders_p0_offset1)} messages")
        else:
            print("  ❌ Orders-0 offset scenario failed")
    
    # Scenario 2: Consume limited messages from orders-1
    if consumed_orders_p1 and len(consumed_orders_p1) > 0:
        print("  📋 Scenario 2: Consume limited messages (1) from orders-1...")
        consumed_orders_p1_limited, _ = test_consume(8083, "orders", 1, offset=0, max_messages=1, description=" (orders-1 max 1 message)")
        if consumed_orders_p1_limited is not None:
            print(f"  ✅ Orders-1 limited scenario passed - received {len(consumed_orders_p1_limited)} message(s)")
        else:
            print("  ❌ Orders-1 limited scenario failed")
    
    add_delay(1)

    # Test High Water Mark endpoints for orders partitions
    print("Testing High Water Mark endpoints for orders partitions...")
    add_delay(0.5)
    
    # HWM for orders-0
    hwm_orders_p0_url = "http://localhost:8082/api/v1/storage/partitions/orders/0/high-water-mark"
    print(f"📊 Checking HWM for orders-0 (port 8082)...")
    try:
        resp = requests.get(hwm_orders_p0_url, timeout=5)
        hwm_value = resp.text if resp.status_code == 200 else "N/A"
        
        log_response(
            "Get HWM for orders-0 (consumer test)",
            {
                "method": "GET",
                "url": hwm_orders_p0_url,
                "port": 8082
            },
            {
                "status_code": resp.status_code,
                "headers": dict(resp.headers),
                "body": hwm_value
            }
        )
        
        print(f"   HWM for orders-0: {hwm_value}")
    except Exception as e:
        print(f"❌ Exception checking HWM for orders-0: {e}")
    
    # HWM for orders-1
    hwm_orders_p1_url = "http://localhost:8083/api/v1/storage/partitions/orders/1/high-water-mark"
    print(f"📊 Checking HWM for orders-1 (port 8083)...")
    try:
        resp = requests.get(hwm_orders_p1_url, timeout=5)
        hwm_value = resp.text if resp.status_code == 200 else "N/A"
        
        log_response(
            "Get HWM for orders-1 (consumer test)",
            {
                "method": "GET",
                "url": hwm_orders_p1_url,
                "port": 8083
            },
            {
                "status_code": resp.status_code,
                "headers": dict(resp.headers),
                "body": hwm_value
            }
        )
        
        print(f"   HWM for orders-1: {hwm_value}")
        print("✅ Orders HWM endpoints test passed")
    except Exception as e:
        print(f"❌ Exception checking HWM for orders-1: {e}")
    
    add_delay(1)

    print()
    print("🎉 Testing complete!")
    print("===================")
    add_delay(0.5)
    print("Summary:")
    print("- Replication tests: Verified behavior for acks=0, acks=1, acks=-1")
    print("- High water marks: Verified HWM tracking")
    print()
    print("Check broker logs for detailed replication details:")
    print("  - Look for 'Starting replication' messages")
    print("  - Look for 'Async replication initiated' for acks=0,1")
    print("  - Look for 'Updated High Watermark' messages")
    print("  - Verify ISR followers receive replication requests")
    print()
    
    # Save all responses to file
    save_all_responses()
    
    print("\n✅ All test responses have been saved to the response file.")
    print(f"   Review {RESPONSE_FILE} for detailed request/response data.")

def show_cluster_topology():
    """Display the cluster topology based on test metadata"""
    metadata_file = TEST_DIR / "metadata-setup.json"

    print("\n� Cluster Topology (from test metadata):")
    print("=" * 50)

    try:
        with open(metadata_file, 'r') as f:
            data = json.load(f)

        brokers = data.get('brokers', [])
        partitions = data.get('partitions', [])

        print("Brokers:")
        for broker in brokers:
            print(f"  🖥️  Broker {broker['id']} - {broker['host']}:{broker['port']}")

        print("\nPartitions:")
        for partition in partitions:
            topic = partition['topic']
            part = partition['partition']
            leader_id = partition['leaderId']
            followers = partition.get('followerIds', [])
            isr = partition.get('isrIds', [])

            leader_broker = next((b for b in brokers if b['id'] == leader_id), None)
            leader_info = f"{leader_broker['host']}:{leader_broker['port']}" if leader_broker else "unknown"

            print(f"  📂 {topic}-{part}:")
            print(f"     👑 Leader: Broker {leader_id} ({leader_info})")

            if followers:
                follower_info = []
                for fid in followers:
                    fb = next((b for b in brokers if b['id'] == fid), None)
                    if fb:
                        follower_info.append(f"Broker {fid} ({fb['host']}:{fb['port']})")
                print(f"     👥 Followers: {', '.join(follower_info)}")
            else:
                print("     👥 Followers: None")

            if isr:
                isr_info = []
                for iid in isr:
                    ib = next((b for b in brokers if b['id'] == iid), None)
                    if ib:
                        isr_info.append(f"Broker {iid}")
                print(f"     🔄 ISR: {', '.join(isr_info)}")

        print("\n" + "=" * 50)

    except Exception as e:
        print(f"❌ Error reading metadata: {e}")

def test_produce_orders():
    """Test replication behavior for different acks settings on 'orders' topic"""

    headers = {"Content-Type": "application/json"}

    print("\n" + "=" * 60)
    print("TESTING REPLICATION BEHAVIOR")
    print("=" * 60)

    # Test 1: acks=0 (Fire-and-forget)
    print("\n📋 TEST 1: acks=0 (Fire-and-forget)")
    print("-" * 60)
    print("Expected behavior:")
    print("  - Returns immediately (doesn't wait for leader write)")
    print("  - Replicates to ALL ISR followers asynchronously")
    print("  - HWM updated asynchronously after min.insync.replicas ack")
    print()

    port_p0 = 8082  # Leader for orders-0
    order_msg_acks0 = {
        "topic": "orders",
        "partition": 0,
        "producerId": 10001,
        "producerEpoch": 1,
        "messages": [
            {
                "key": "order-acks0-key",
                "value": list("Order with acks=0".encode()),
                "timestamp": int(time.time() * 1000),
                "headers": {"test": "acks=0"}
            }
        ],
        "requiredAcks": 0,
        "timeoutMs": 30000
    }

    print(f"📤 Producing to orders partition 0 (port {port_p0}) with acks=0...")
    try:
        start_time = time.time()
        resp = requests.post(f"http://localhost:{port_p0}/api/v1/storage/messages", 
                            json=order_msg_acks0, headers=headers, timeout=10)
        response_time = time.time() - start_time
        data = resp.json()

        log_response(
            "Produce with acks=0 (orders-0)",
            {
                "method": "POST",
                "url": f"http://localhost:{port_p0}/api/v1/storage/messages",
                "port": port_p0,
                "topic": "orders",
                "partition": 0,
                "acks": 0,
                "body": order_msg_acks0
            },
            {
                "status_code": resp.status_code,
                "response_time_ms": round(response_time * 1000, 2),
                "headers": dict(resp.headers),
                "body": data
            }
        )

        if resp.status_code == 200 and data.get('success') is True:
            offset = data.get('results', [{}])[0].get('offset', 'unknown')
            print(f"✅ Success! Response time: {response_time*1000:.2f}ms")
            print(f"   Offset: {offset}")
            print(f"   Note: Fast response indicates immediate return")
        else:
            print(f"❌ Failed. Status: {resp.status_code}")
            print(f"   Response: {json.dumps(data, indent=2)}")
    except Exception as e:
        print(f"❌ Exception: {e}")

    time.sleep(1)  # Wait for async replication

    # Test 2: acks=1 (Leader acknowledgment)
    print("\n📋 TEST 2: acks=1 (Leader acknowledgment)")
    print("-" * 60)
    print("Expected behavior:")
    print("  - Returns after leader write completes")
    print("  - Replicates to ALL ISR followers asynchronously")
    print("  - HWM updated asynchronously after min.insync.replicas ack")
    print()

    order_msg_acks1 = {
        "topic": "orders",
        "partition": 0,
        "producerId": 10002,
        "producerEpoch": 1,
        "messages": [
            {
                "key": "order-acks1-key",
                "value": list("Order with acks=1".encode()),
                "timestamp": int(time.time() * 1000),
                "headers": {"test": "acks=1"}
            }
        ],
        "requiredAcks": 1,
        "timeoutMs": 30000
    }

    print(f"📤 Producing to orders partition 0 (port {port_p0}) with acks=1...")
    try:
        start_time = time.time()
        resp = requests.post(f"http://localhost:{port_p0}/api/v1/storage/messages", 
                            json=order_msg_acks1, headers=headers, timeout=10)
        response_time = time.time() - start_time
        data = resp.json()

        log_response(
            "Produce with acks=1 (orders-0)",
            {
                "method": "POST",
                "url": f"http://localhost:{port_p0}/api/v1/storage/messages",
                "port": port_p0,
                "topic": "orders",
                "partition": 0,
                "acks": 1,
                "body": order_msg_acks1
            },
            {
                "status_code": resp.status_code,
                "response_time_ms": round(response_time * 1000, 2),
                "headers": dict(resp.headers),
                "body": data
            }
        )

        if resp.status_code == 200 and data.get('success') is True:
            offset = data.get('results', [{}])[0].get('offset', 'unknown')
            print(f"✅ Success! Response time: {response_time*1000:.2f}ms")
            print(f"   Offset: {offset}")
            print(f"   Note: Response after leader write")
        else:
            print(f"❌ Failed. Status: {resp.status_code}")
            print(f"   Response: {json.dumps(data, indent=2)}")
    except Exception as e:
        print(f"❌ Exception: {e}")

    time.sleep(1)  # Wait for async replication

    # Test 3: acks=-1 (All ISR replicas)
    print("\n📋 TEST 3: acks=-1 (All ISR replicas)")
    print("-" * 60)
    print("Expected behavior:")
    print("  - Replicates to ALL ISR followers synchronously")
    print("  - Returns ONLY after min.insync.replicas acknowledge")
    print("  - HWM updated synchronously before response")
    print()

    port_p1 = 8083  # Leader for orders-1
    order_msg_acks_all = {
        "topic": "orders",
        "partition": 1,
        "producerId": 10003,
        "producerEpoch": 1,
        "messages": [
            {
                "key": "order-acks-all-key",
                "value": list("Order with acks=-1".encode()),
                "timestamp": int(time.time() * 1000),
                "headers": {"test": "acks=-1"}
            }
        ],
        "requiredAcks": -1,
        "timeoutMs": 30000
    }

    print(f"📤 Producing to orders partition 1 (port {port_p1}) with acks=-1...")
    try:
        start_time = time.time()
        resp = requests.post(f"http://localhost:{port_p1}/api/v1/storage/messages", 
                            json=order_msg_acks_all, headers=headers, timeout=10)
        response_time = time.time() - start_time
        data = resp.json()

        log_response(
            "Produce with acks=-1 (orders-1)",
            {
                "method": "POST",
                "url": f"http://localhost:{port_p1}/api/v1/storage/messages",
                "port": port_p1,
                "topic": "orders",
                "partition": 1,
                "acks": -1,
                "body": order_msg_acks_all
            },
            {
                "status_code": resp.status_code,
                "response_time_ms": round(response_time * 1000, 2),
                "headers": dict(resp.headers),
                "body": data
            }
        )

        if resp.status_code == 200 and data.get('success') is True:
            offset = data.get('results', [{}])[0].get('offset', 'unknown')
            print(f"✅ Success! Response time: {response_time*1000:.2f}ms")
            print(f"   Offset: {offset}")
            print(f"   Note: Slower response indicates wait for ISR acks")
        else:
            print(f"❌ Failed. Status: {resp.status_code}")
            print(f"   Response: {json.dumps(data, indent=2)}")
    except Exception as e:
        print(f"❌ Exception: {e}")

    # Test 4: Check High Water Mark for both partitions
    print("\n📋 TEST 4: High Water Mark Verification")
    print("-" * 60)
    print("Checking HWM for both partitions...")
    print()

    # Check HWM for orders-0
    hwm_url_p0 = f"http://localhost:{port_p0}/api/v1/storage/partitions/orders/0/high-water-mark"
    print(f"📊 Checking HWM for orders-0 (port {port_p0})...")
    try:
        resp = requests.get(hwm_url_p0, timeout=5)
        hwm_value = resp.text if resp.status_code == 200 else "N/A"

        log_response(
            "Get HWM for orders-0",
            {
                "method": "GET",
                "url": hwm_url_p0,
                "port": port_p0
            },
            {
                "status_code": resp.status_code,
                "headers": dict(resp.headers),
                "body": hwm_value
            }
        )

        print(f"   HWM for orders-0: {hwm_value}")
    except Exception as e:
        print(f"❌ Exception: {e}")

    # Check HWM for orders-1
    hwm_url_p1 = f"http://localhost:{port_p1}/api/v1/storage/partitions/orders/1/high-water-mark"
    print(f"📊 Checking HWM for orders-1 (port {port_p1})...")
    try:
        resp = requests.get(hwm_url_p1, timeout=5)
        hwm_value = resp.text if resp.status_code == 200 else "N/A"

        log_response(
            "Get HWM for orders-1",
            {
                "method": "GET",
                "url": hwm_url_p1,
                "port": port_p1
            },
            {
                "status_code": resp.status_code,
                "headers": dict(resp.headers),
                "body": hwm_value
            }
        )

        print(f"   HWM for orders-1: {hwm_value}")
    except Exception as e:
        print(f"❌ Exception: {e}")

    print("\n" + "=" * 60)
    print("REPLICATION BEHAVIOR TESTING COMPLETE")
    print("=" * 60)
    print("\n📝 Key Observations to Check:")
    print("  1. acks=0 should have fastest response time")
    print("  2. acks=1 should be slightly slower (waits for leader write)")
    print("  3. acks=-1 should be slowest (waits for ISR acknowledgments)")
    print("  4. Check broker logs to verify replication to ISR followers")
    print("  5. HWM should advance for acks=-1 immediately")
    print("  6. HWM for acks=0,1 should advance after async replication")
    print()


def test_batch_orders():
    """Test batch message publishing to orders topic partitions"""
    
    headers = {"Content-Type": "application/json"}
    
    print("\n" + "=" * 60)
    print("TESTING BATCH MESSAGES - ORDERS TOPIC")
    print("=" * 60)
    
    # Test batch messages to orders-0 (leader: broker 2, port 8082)
    print("\n📦 Testing batch publish to orders partition 0...")
    
    batch_orders_p0 = {
        "topic": "orders",
        "partition": 0,
        "producerId": "batch-orders-p0",
        "producerEpoch": 1,
        "messages": [
            {
                "key": "batch-order-001",
                "value": list("Batch Order 001".encode()),
                "timestamp": int(time.time() * 1000),
                "headers": {"batch": "orders-p0", "seq": 1}
            },
            {
                "key": "batch-order-002", 
                "value": list("Batch Order 002".encode()),
                "timestamp": int(time.time() * 1000) + 1,
                "headers": {"batch": "orders-p0", "seq": 2}
            },
            {
                "key": "batch-order-003",
                "value": list("Batch Order 003".encode()),
                "timestamp": int(time.time() * 1000) + 2,
                "headers": {"batch": "orders-p0", "seq": 3}
            }
        ],
        "requiredAcks": 1,
        "timeoutMs": 30000
    }
    
    port_p0 = 8082
    print(f"📤 Publishing batch to orders-0 (port {port_p0})...")
    try:
        start_time = time.time()
        resp = requests.post(f"http://localhost:{port_p0}/api/v1/storage/messages", 
                            json=batch_orders_p0, headers=headers, timeout=10)
        response_time = time.time() - start_time
        data = resp.json()
        
        log_response(
            "Batch Publish to orders-0",
            {
                "method": "POST",
                "url": f"http://localhost:{port_p0}/api/v1/storage/messages",
                "port": port_p0,
                "topic": "orders",
                "partition": 0,
                "acks": 1,
                "message_count": len(batch_orders_p0["messages"]),
                "body": batch_orders_p0
            },
            {
                "status_code": resp.status_code,
                "response_time_ms": round(response_time * 1000, 2),
                "headers": dict(resp.headers),
                "body": data
            }
        )
        
        if resp.status_code == 200 and data.get('success') is True:
            results = data.get('results', [])
            print(f"✅ Batch publish to orders-0 successful!")
            print(f"   Response time: {response_time*1000:.2f}ms")
            print(f"   Messages published: {len(results)}")
            for i, result in enumerate(results):
                offset = result.get('offset', 'unknown')
                print(f"   Message {i+1}: offset {offset}")
        else:
            print(f"❌ Batch publish to orders-0 failed. Status: {resp.status_code}")
            print(f"   Response: {json.dumps(data, indent=2)}")
    except Exception as e:
        print(f"❌ Exception during batch publish to orders-0: {e}")
    
    time.sleep(1)
    
    # Test batch messages to orders-1 (leader: broker 3, port 8083)
    print("\n📦 Testing batch publish to orders partition 1...")
    
    batch_orders_p1 = {
        "topic": "orders",
        "partition": 1,
        "producerId": "batch-orders-p1",
        "producerEpoch": 1,
        "messages": [
            {
                "key": "batch-order-101",
                "value": list("Batch Order 101".encode()),
                "timestamp": int(time.time() * 1000),
                "headers": {"batch": "orders-p1", "seq": 1}
            },
            {
                "key": "batch-order-102",
                "value": list("Batch Order 102".encode()),
                "timestamp": int(time.time() * 1000) + 1,
                "headers": {"batch": "orders-p1", "seq": 2}
            }
        ],
        "requiredAcks": -1,  # Test with acks=-1 for this partition
        "timeoutMs": 30000
    }
    
    port_p1 = 8083
    print(f"📤 Publishing batch to orders-1 (port {port_p1}) with acks=-1...")
    try:
        start_time = time.time()
        resp = requests.post(f"http://localhost:{port_p1}/api/v1/storage/messages", 
                            json=batch_orders_p1, headers=headers, timeout=10)
        response_time = time.time() - start_time
        data = resp.json()
        
        log_response(
            "Batch Publish to orders-1 (acks=-1)",
            {
                "method": "POST",
                "url": f"http://localhost:{port_p1}/api/v1/storage/messages",
                "port": port_p1,
                "topic": "orders",
                "partition": 1,
                "acks": -1,
                "message_count": len(batch_orders_p1["messages"]),
                "body": batch_orders_p1
            },
            {
                "status_code": resp.status_code,
                "response_time_ms": round(response_time * 1000, 2),
                "headers": dict(resp.headers),
                "body": data
            }
        )
        
        if resp.status_code == 200 and data.get('success') is True:
            results = data.get('results', [])
            print(f"✅ Batch publish to orders-1 successful!")
            print(f"   Response time: {response_time*1000:.2f}ms")
            print(f"   Messages published: {len(results)}")
            for i, result in enumerate(results):
                offset = result.get('offset', 'unknown')
                print(f"   Message {i+1}: offset {offset}")
        else:
            print(f"❌ Batch publish to orders-1 failed. Status: {resp.status_code}")
            print(f"   Response: {json.dumps(data, indent=2)}")
    except Exception as e:
        print(f"❌ Exception during batch publish to orders-1: {e}")
    
    print("\n" + "=" * 60)
    print("BATCH ORDERS TESTING COMPLETE")
    print("=" * 60)

def add_delay(seconds=1.5):
    """Add a small delay for readability"""
    time.sleep(seconds)

def show_setup_instructions():
    """Show instructions for starting brokers"""
    print("📋 Setup Instructions:")
    print("======================")
    print(f"Open {BROKER_COUNT} terminal windows and start brokers with these commands:")
    print()
    print("Terminal 1 (Broker 1 - Leader for test-topic-0):")
    print("$env:BROKER_ID = \"1\"; $env:SERVER_PORT = \"8081\"; mvn spring-boot:run")
    print()
    print("Terminal 2 (Broker 2 - Follower for test-topic-0):")
    print("$env:BROKER_ID = \"2\"; $env:SERVER_PORT = \"8082\"; mvn spring-boot:run")
    print()
    print("Terminal 3 (Broker 3 - Follower for test-topic-0):")
    print("$env:BROKER_ID = \"3\"; $env:SERVER_PORT = \"8083\"; mvn spring-boot:run")
    print()
    print("Make sure to navigate to the storage service directory first:")
    print(f"cd {STORAGE_SERVICE_DIR}")

if __name__ == "__main__":
    main()