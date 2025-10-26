#!/usr/bin/env python3
"""
Check recent messages to verify our test message
"""

import requests
import json

def main():
    # Check the last few messages to see our test message
    consume_data = {
        'topic': 'test-topic',
        'partition': 0,
        'offset': 125,  # Start from a recent offset
        'maxMessages': 10
    }

    response = requests.post('http://localhost:8081/api/v1/storage/consume', json=consume_data)
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            messages = result.get('messages', [])
            print(f'Retrieved {len(messages)} messages from offset 125+:')
            print(f'High Water Mark: {result["highWaterMark"]}')
            print()

            for msg in messages:
                key = msg.get('key', 'None')
                offset = msg.get('offset', 'unknown')
                print(f'  Offset {offset}: key="{key}"')
                if key == 'test-key-single':
                    print(f'  🎯 FOUND OUR TEST MESSAGE at offset {offset}!')
                    print(f'     Value length: {len(msg.get("value", []))} bytes')
                    print(f'     Headers: {msg.get("headers", {})}')
        else:
            print('Consume failed')
    else:
        print(f'HTTP error: {response.status_code}')

if __name__ == "__main__":
    main()