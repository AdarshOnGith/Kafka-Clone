"""
Mock Consumer Egress Service (CES) for testing
Returns broker and partition metadata in the new format
"""

from flask import Flask, request, jsonify
import time

app = Flask(__name__)

# Mock data - topics and their partitions
MOCK_BROKERS = [
    {"id": 1, "host": "localhost", "port": 9092, "isAlive": True, "lastHeartbeat": int(time.time() * 1000)},
    {"id": 2, "host": "localhost", "port": 9093, "isAlive": True, "lastHeartbeat": int(time.time() * 1000)},
    {"id": 3, "host": "localhost", "port": 9094, "isAlive": True, "lastHeartbeat": int(time.time() * 1000)}
]

MOCK_TOPICS = {
    "orders": [
        {"topic": "orders", "partition": 0, "leaderId": 1, "followerIds": [2, 3], "isrIds": [1, 2, 3], "leaderEpoch": 1},
        {"topic": "orders", "partition": 1, "leaderId": 2, "followerIds": [1, 3], "isrIds": [2, 1], "leaderEpoch": 1}
    ],
    "payments": [
        {"topic": "payments", "partition": 0, "leaderId": 1, "followerIds": [2, 3], "isrIds": [1, 2], "leaderEpoch": 1}
    ],
    "inventory": [
        {"topic": "inventory", "partition": 0, "leaderId": 2, "followerIds": [1, 3], "isrIds": [2, 1, 3], "leaderEpoch": 1},
        {"topic": "inventory", "partition": 1, "leaderId": 3, "followerIds": [1, 2], "isrIds": [3, 2], "leaderEpoch": 1}
    ]
}

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'service': 'Mock Consumer Egress Service',
        'version': '2.0',
        'format': 'brokers + partitions with IDs'
    })

@app.route('/topics', methods=['GET'])
def list_topics():
    return jsonify({
        'topics': list(MOCK_TOPICS.keys()),
        'count': len(MOCK_TOPICS)
    })

@app.route('/api/consumer/join-group', methods=['POST'])
def join_group():
    data = request.get_json()
    
    # Validate request
    if not data:
        return jsonify({
            'success': False,
            'errorMessage': 'Request body is required'
        }), 400
    
    group_id = data.get('groupId')
    consumer_id = data.get('consumerId')
    topic = data.get('topic')  # Changed: single topic now
    
    if not group_id:
        return jsonify({
            'success': False,
            'errorMessage': 'groupId is required'
        }), 400
    
    if not consumer_id:
        return jsonify({
            'success': False,
            'errorMessage': 'consumerId is required'
        }), 400
    
    if not topic:
        return jsonify({
            'success': False,
            'errorMessage': 'topic is required'
        }), 400
    
    # Check if topic exists
    if topic not in MOCK_TOPICS:
        return jsonify({
            'success': False,
            'errorMessage': f'Topic not found: {topic}'
        }), 404
    
    # Get partitions for the topic
    topic_partitions = MOCK_TOPICS[topic]
    
    # Get unique broker IDs used by this topic
    broker_ids = set()
    for partition in topic_partitions:
        broker_ids.add(partition['leaderId'])
        broker_ids.update(partition['followerIds'])
        broker_ids.update(partition['isrIds'])
    
    # Filter brokers - only return brokers involved in this topic
    involved_brokers = [b for b in MOCK_BROKERS if b['id'] in broker_ids]
    
    # Build response
    response = {
        'success': True,
        'groupId': group_id,
        'consumerId': consumer_id,
        'generationId': 1,
        'brokers': involved_brokers,
        'partitions': topic_partitions,
        'timestamp': int(time.time() * 1000)
    }
    
    print(f"✅ Consumer {consumer_id} joined group {group_id} for topic {topic}")
    print(f"   Assigned {len(topic_partitions)} partitions")
    print(f"   Involved brokers: {[b['id'] for b in involved_brokers]}")
    
    return jsonify(response), 200

if __name__ == '__main__':
    print("=" * 80)
    print("  Mock Consumer Egress Service (CES)")
    print("  Version 2.0 - Brokers + Partitions with IDs format")
    print("=" * 80)
    print(f"\nAvailable Topics: {list(MOCK_TOPICS.keys())}")
    print(f"Available Brokers: {[b['id'] for b in MOCK_BROKERS]}")
    print(f"\nEndpoints:")
    print(f"  GET  /health                      - Health check")
    print(f"  GET  /topics                      - List topics")
    print(f"  POST /api/consumer/join-group     - Subscribe to topic")
    print(f"\nStarting server on http://localhost:8081")
    print("=" * 80)
    app.run(host='0.0.0.0', port=8081, debug=True)
