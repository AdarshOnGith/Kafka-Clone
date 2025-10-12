-- Metadata Service Database Schema
-- PostgreSQL version 14+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- TOPICS TABLE
-- Stores topic metadata
-- ============================================================
CREATE TABLE topics (
    topic_id SERIAL PRIMARY KEY,
    topic_name VARCHAR(255) NOT NULL UNIQUE,
    partition_count INTEGER NOT NULL DEFAULT 3,
    replication_factor INTEGER NOT NULL DEFAULT 3,
    min_in_sync_replicas INTEGER NOT NULL DEFAULT 2,
    retention_ms BIGINT NOT NULL DEFAULT 604800000, -- 7 days
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_partition_count CHECK (partition_count > 0),
    CONSTRAINT chk_replication_factor CHECK (replication_factor > 0),
    CONSTRAINT chk_min_isr CHECK (min_in_sync_replicas > 0 AND min_in_sync_replicas <= replication_factor)
);

CREATE INDEX idx_topics_name ON topics(topic_name);

-- ============================================================
-- PARTITIONS TABLE
-- Stores partition metadata and leader information
-- ============================================================
CREATE TABLE partitions (
    partition_id SERIAL PRIMARY KEY,
    topic_id INTEGER NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    partition_number INTEGER NOT NULL,
    leader_node_id VARCHAR(255),
    leader_node_address VARCHAR(255),
    leader_epoch INTEGER NOT NULL DEFAULT 0,
    high_water_mark BIGINT NOT NULL DEFAULT 0,
    log_end_offset BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_topic_partition UNIQUE (topic_id, partition_number),
    CONSTRAINT chk_partition_number CHECK (partition_number >= 0),
    CONSTRAINT chk_leader_epoch CHECK (leader_epoch >= 0),
    CONSTRAINT chk_hwm CHECK (high_water_mark >= 0)
);

CREATE INDEX idx_partitions_topic ON partitions(topic_id);
CREATE INDEX idx_partitions_leader ON partitions(leader_node_id);

-- ============================================================
-- PARTITION REPLICAS TABLE
-- Stores replica assignments for each partition
-- ============================================================
CREATE TABLE partition_replicas (
    replica_id SERIAL PRIMARY KEY,
    partition_id INTEGER NOT NULL REFERENCES partitions(partition_id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    node_address VARCHAR(255) NOT NULL,
    is_in_sync BOOLEAN NOT NULL DEFAULT false,
    last_caught_up_timestamp TIMESTAMP,
    lag_messages BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_partition_replica UNIQUE (partition_id, node_id)
);

CREATE INDEX idx_replicas_partition ON partition_replicas(partition_id);
CREATE INDEX idx_replicas_node ON partition_replicas(node_id);
CREATE INDEX idx_replicas_in_sync ON partition_replicas(partition_id, is_in_sync);

-- ============================================================
-- CONSUMER GROUPS TABLE
-- Stores consumer group metadata
-- ============================================================
CREATE TABLE consumer_groups (
    group_id VARCHAR(255) PRIMARY KEY,
    group_state VARCHAR(50) NOT NULL DEFAULT 'Empty',
    generation_id INTEGER NOT NULL DEFAULT 0,
    protocol_type VARCHAR(50),
    protocol_name VARCHAR(50),
    leader_member_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_group_state CHECK (group_state IN ('Empty', 'PreparingRebalance', 'CompletingRebalance', 'Stable', 'Dead')),
    CONSTRAINT chk_generation_id CHECK (generation_id >= 0)
);

CREATE INDEX idx_consumer_groups_state ON consumer_groups(group_state);

-- ============================================================
-- CONSUMER GROUP MEMBERS TABLE
-- Stores individual consumer members in a group
-- ============================================================
CREATE TABLE consumer_group_members (
    member_id VARCHAR(255) PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL REFERENCES consumer_groups(group_id) ON DELETE CASCADE,
    client_id VARCHAR(255) NOT NULL,
    client_host VARCHAR(255) NOT NULL,
    session_timeout_ms INTEGER NOT NULL DEFAULT 10000,
    rebalance_timeout_ms INTEGER NOT NULL DEFAULT 30000,
    assignment_strategy VARCHAR(50),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_session_timeout CHECK (session_timeout_ms > 0),
    CONSTRAINT chk_rebalance_timeout CHECK (rebalance_timeout_ms > 0)
);

CREATE INDEX idx_members_group ON consumer_group_members(group_id);
CREATE INDEX idx_members_heartbeat ON consumer_group_members(last_heartbeat);

-- ============================================================
-- CONSUMER OFFSETS TABLE
-- Stores committed offsets for consumer groups
-- ============================================================
CREATE TABLE consumer_offsets (
    offset_id SERIAL PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL REFERENCES consumer_groups(group_id) ON DELETE CASCADE,
    topic_id INTEGER NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    partition_number INTEGER NOT NULL,
    offset_value BIGINT NOT NULL DEFAULT 0,
    leader_epoch INTEGER NOT NULL DEFAULT 0,
    metadata TEXT,
    commit_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_timestamp TIMESTAMP,
    
    CONSTRAINT uq_group_topic_partition UNIQUE (group_id, topic_id, partition_number),
    CONSTRAINT chk_offset_value CHECK (offset_value >= 0)
);

CREATE INDEX idx_offsets_group ON consumer_offsets(group_id);
CREATE INDEX idx_offsets_topic ON consumer_offsets(topic_id);
CREATE INDEX idx_offsets_group_topic ON consumer_offsets(group_id, topic_id);
CREATE INDEX idx_offsets_expire ON consumer_offsets(expire_timestamp);

-- ============================================================
-- STORAGE NODES TABLE
-- Stores registered storage nodes
-- ============================================================
CREATE TABLE storage_nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    rack_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ALIVE',
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    heartbeat_count BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_port CHECK (port > 0 AND port <= 65535),
    CONSTRAINT chk_status CHECK (status IN ('ALIVE', 'SUSPECT', 'DEAD', 'DRAINING'))
);

CREATE INDEX idx_nodes_status ON storage_nodes(status);
CREATE INDEX idx_nodes_heartbeat ON storage_nodes(last_heartbeat);
CREATE INDEX idx_nodes_rack ON storage_nodes(rack_id);

-- ============================================================
-- CLUSTER METADATA TABLE
-- Stores cluster-wide configuration
-- ============================================================
CREATE TABLE cluster_metadata (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default cluster configuration
INSERT INTO cluster_metadata (key, value, description) VALUES
    ('cluster.id', uuid_generate_v4()::text, 'Unique cluster identifier'),
    ('default.partition.count', '3', 'Default number of partitions for new topics'),
    ('default.replication.factor', '3', 'Default replication factor for new topics'),
    ('min.in.sync.replicas', '2', 'Minimum in-sync replicas required'),
    ('controller.node.id', NULL, 'Current controller node ID');

-- ============================================================
-- AUDIT LOG TABLE
-- Stores metadata change history
-- ============================================================
CREATE TABLE audit_log (
    log_id SERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(255),
    change_details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('TOPIC', 'PARTITION', 'CONSUMER_GROUP', 'NODE', 'OFFSET')),
    CONSTRAINT chk_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LEADER_CHANGE'))
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);

-- ============================================================
-- FUNCTIONS AND TRIGGERS
-- ============================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_topics_updated_at BEFORE UPDATE ON topics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_partitions_updated_at BEFORE UPDATE ON partitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_replicas_updated_at BEFORE UPDATE ON partition_replicas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_consumer_groups_updated_at BEFORE UPDATE ON consumer_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to clean up expired offsets
CREATE OR REPLACE FUNCTION cleanup_expired_offsets()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM consumer_offsets 
    WHERE expire_timestamp IS NOT NULL 
    AND expire_timestamp < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- VIEWS
-- ============================================================

-- View for partition with full metadata
CREATE VIEW v_partition_details AS
SELECT 
    p.partition_id,
    t.topic_name,
    p.partition_number,
    p.leader_node_id,
    p.leader_node_address,
    p.leader_epoch,
    p.high_water_mark,
    p.log_end_offset,
    t.replication_factor,
    t.min_in_sync_replicas,
    COALESCE(
        (SELECT COUNT(*) FROM partition_replicas pr 
         WHERE pr.partition_id = p.partition_id AND pr.is_in_sync = true),
        0
    ) as in_sync_replica_count,
    COALESCE(
        (SELECT json_agg(node_id) FROM partition_replicas pr 
         WHERE pr.partition_id = p.partition_id AND pr.is_in_sync = true),
        '[]'::json
    ) as in_sync_replicas
FROM partitions p
JOIN topics t ON p.topic_id = t.topic_id;

-- View for consumer group summary
CREATE VIEW v_consumer_group_summary AS
SELECT 
    cg.group_id,
    cg.group_state,
    cg.generation_id,
    COUNT(DISTINCT cgm.member_id) as member_count,
    COUNT(DISTINCT co.topic_id) as subscribed_topic_count,
    MAX(cgm.last_heartbeat) as latest_heartbeat
FROM consumer_groups cg
LEFT JOIN consumer_group_members cgm ON cg.group_id = cgm.group_id
LEFT JOIN consumer_offsets co ON cg.group_id = co.group_id
GROUP BY cg.group_id, cg.group_state, cg.generation_id;

-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE topics IS 'Stores topic configuration and metadata';
COMMENT ON TABLE partitions IS 'Stores partition assignments and leader information';
COMMENT ON TABLE partition_replicas IS 'Tracks replica status and ISR membership';
COMMENT ON TABLE consumer_groups IS 'Manages consumer group coordination state';
COMMENT ON TABLE consumer_offsets IS 'Persists committed consumer offsets';
COMMENT ON TABLE storage_nodes IS 'Registry of available storage nodes';
COMMENT ON TABLE audit_log IS 'Audit trail for metadata changes';
