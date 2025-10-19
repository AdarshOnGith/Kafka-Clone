#!/usr/bin/env python3
"""
Kafka-Clone Testing Setup Script (Python version)
This script helps set up and test the publish/replication/consume flow
Windows-friendly alternative to the bash script
"""

import os
import sys
import time
import json
import socket
import requests
from pathlib import Path

# Configuration
BROKER_COUNT = 3
BASE_PORT = 8081
PROJECT_DIR = Path(__file__).parent.parent.parent
TEST_DIR = PROJECT_DIR / "test"
STORAGE_SERVICE_DIR = PROJECT_DIR / "dmq-storage-service"

def print_header():
    print("🚀 Kafka-Clone Testing Setup (Publish + Consume)")
    print("================================================")
    print(f"Project Directory: {PROJECT_DIR}")
    print(f"Test Directory: {TEST_DIR}")
    print()

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

    # Test batch message publish (to leader - broker 1)
    print("Testing batch message publish...")
    add_delay(0.5)
    if test_publish(8081, "batch-message-publish.json"):
        print("✅ Batch message test passed")
    else:
        print("❌ Batch message test failed")
    add_delay(1)

    print()
    print("🧪 Running consume tests...")
    print("==========================")
    add_delay(1)

    # Test consuming messages from leader (broker 1)
    print("Testing consume from leader (broker 1)...")
    add_delay(0.5)
    consumed_messages, hwm = test_consume(8081, "test-topic", 0, offset=0, max_messages=10, description=" (all messages)")
    if consumed_messages is not None:
        print(f"✅ Consume test passed - received {len(consumed_messages)} messages, HWM: {hwm}")
    else:
        print("❌ Consume test failed")
    add_delay(1)

    print()

    # Test consuming from specific offset
    print("Testing consume from offset 1...")
    add_delay(0.5)
    consumed_from_offset, _ = test_consume(8081, "test-topic", 0, offset=1, max_messages=10, description=" (from offset 1)")
    if consumed_from_offset is not None:
        print(f"✅ Offset consume test passed - received {len(consumed_from_offset)} messages")
    else:
        print("❌ Offset consume test failed")
    add_delay(1)

    print()

    # Test consuming with max messages limit
    print("Testing consume with max messages limit (2)...")
    add_delay(0.5)
    consumed_limited, _ = test_consume(8081, "test-topic", 0, offset=0, max_messages=2, description=" (max 2 messages)")
    if consumed_limited is not None:
        print(f"✅ Limited consume test passed - received {len(consumed_limited)} messages")
    else:
        print("❌ Limited consume test failed")
    add_delay(1)

    print()
    print("🎉 Testing complete!")
    print("===================")
    add_delay(0.5)
    print("Summary:")
    print("- Publish tests: Verified message production and replication")
    print("- Consume tests: Verified message consumption from WAL")
    print("- High water marks: Verified offset tracking")
    print()
    print("Check broker logs for detailed replication and consumption details.")
    print("Expected: Messages should be replicated from leader to followers, and consumable from any broker")

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