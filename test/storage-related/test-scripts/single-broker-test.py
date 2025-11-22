#!/usr/bin/env python3
"""
Simple Single Broker Test
Tests produce and consume functionality on a single storage service
"""

import requests
import json
import time

def main():
    print('🧪 Single Broker Produce-Consume Test')
    print('=' * 50)

    # First populate metadata for our single broker
    metadata = {
        'brokers': [{
            'id': 101,
            'host': 'localhost',
            'port': 8081,
            'isAlive': True
        }],
        'partitions': [{
            'topic': 'test-topic',
            'partition': 0,
            'leaderId': 101,
            'followerIds': [],
            'isrIds': [101],
            'leaderEpoch': 1
        }],
        'timestamp': int(time.time() * 1000)
    }

    print('📤 Setting up metadata...')
    meta_response = requests.post('http://localhost:8081/api/v1/storage/metadata', json=metadata)
    print(f'Metadata setup: {meta_response.status_code}')

    # Test produce
    print('\n📤 Testing PRODUCE...')
    produce_data = {
        'topic': 'test-topic',
        'partition': 0,
        'producerId': 'single-broker-test',
        'producerEpoch': 1,
        'messages': [{
            'key': 'test-key-single',
            'value': list('Single broker test message'.encode()),
            'timestamp': int(time.time() * 1000),
            'headers': {'test': 'single-broker'}
        }],
        'requiredAcks': 1,
        'timeoutMs': 30000
    }

    response = requests.post('http://localhost:8081/api/v1/storage/messages', json=produce_data)
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            offset = result['results'][0]['offset']
            print(f'✅ PRODUCE SUCCESS: Message stored at offset {offset}')

            # Test consume
            print('\n📥 Testing CONSUME...')
            consume_data = {
                'topic': 'test-topic',
                'partition': 0,
                'offset': 0,
                'maxMessages': 10
            }

            consume_response = requests.post('http://localhost:8081/api/v1/storage/consume', json=consume_data)
            if consume_response.status_code == 200:
                consume_result = consume_response.json()
                if consume_result.get('success'):
                    messages = consume_result.get('messages', [])
                    print(f'✅ CONSUME SUCCESS: Retrieved {len(messages)} messages')
                    print(f'   High Water Mark: {consume_result["highWaterMark"]}')

                    # Show the message we just produced
                    for msg in messages:
                        if msg['key'] == 'test-key-single':
                            print(f'   ✅ Found our test message at offset {msg["offset"]}')
                            break
                else:
                    print('❌ CONSUME FAILED: No success in response')
            else:
                print(f'❌ CONSUME FAILED: HTTP {consume_response.status_code}')
        else:
            print(f'❌ PRODUCE FAILED: {result.get("errorMessage")}')
    else:
        print(f'❌ PRODUCE FAILED: HTTP {response.status_code}')

    print('\n🎉 Single Broker Test Complete!')

if __name__ == "__main__":
    main()