package com.distributedmq.client.cli.commands;

import com.distributedmq.client.cli.utils.ArgumentParser;
import com.distributedmq.client.producer.SimpleProducer;
import com.distributedmq.common.dto.ProduceResponse;

/**
 * Command to produce messages to a topic
 * Usage: mycli produce --topic <topic> --key <key> --value <value>
 */
public class ProduceCommand implements Command {
    
    @Override
    public void execute(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser(args);
        
        if (parser.hasFlag("help") || parser.hasFlag("h")) {
            printHelp();
            return;
        }
        
        // Parse required arguments
        String topic = parser.getOption("topic");
        if (topic == null) {
            throw new IllegalArgumentException("Missing required argument: --topic");
        }
        
        String value = parser.getOption("value");
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: --value");
        }
        
        // Optional arguments
        String key = parser.getOption("key");
        String partitionStr = parser.getOption("partition");
        String metadataUrl = parser.getOption("metadata-url");
        String acksStr = parser.getOption("acks");
        
        Integer partition = null;
        if (partitionStr != null) {
            try {
                partition = Integer.parseInt(partitionStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Partition must be a number");
            }
        }
        
        int acks = -1; // Default: wait for all replicas
        if (acksStr != null) {
            try {
                acks = Integer.parseInt(acksStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Acks must be a number (-1, 0, or 1)");
            }
        }
        
        // Create producer and send message
        System.out.println("Producing message to topic '" + topic + "'...");
        if (key != null) {
            System.out.println("  Key: " + key);
        }
        System.out.println("  Value: " + value);
        if (partition != null) {
            System.out.println("  Partition: " + partition);
        }
        System.out.println();
        
        SimpleProducer producer = new SimpleProducer(metadataUrl);
        
        try {
            ProduceResponse response = producer.send(topic, key, value, partition, acks);
            
            if (response.isSuccess() && response.getResults() != null && !response.getResults().isEmpty()) {
                ProduceResponse.ProduceResult result = response.getResults().get(0);
                System.out.println("✓ Message sent successfully!");
                System.out.println("  Topic: " + response.getTopic());
                System.out.println("  Partition: " + response.getPartition());
                System.out.println("  Offset: " + result.getOffset());
                System.out.println("  Timestamp: " + result.getTimestamp());
            } else {
                System.err.println("✗ Failed to send message");
                System.err.println("  Error: " + response.getErrorMessage());
                System.exit(1);
            }
        } finally {
            producer.close();
        }
    }
    
    @Override
    public void printHelp() {
        System.out.println("Produce messages to a topic");
        System.out.println();
        System.out.println("Usage: mycli produce --topic <topic> --value <value> [options]");
        System.out.println();
        System.out.println("Required Arguments:");
        System.out.println("  --topic <topic>       Topic name");
        System.out.println("  --value <value>       Message value");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  --key <key>           Message key (used for partitioning)");
        System.out.println("  --partition <n>       Target partition (overrides key-based partitioning)");
        System.out.println("  --acks <n>            Acknowledgment mode: -1 (all), 1 (leader), 0 (none)");
        System.out.println("  --metadata-url <url>  Metadata service URL (default: from config)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mycli produce --topic orders --value \"Order data\"");
        System.out.println("  mycli produce --topic orders --key order-123 --value \"Order data\"");
        System.out.println("  mycli produce --topic logs --value \"Log message\" --partition 0");
        System.out.println("  mycli produce --topic events --key evt-456 --value \"Event\" --acks 1");
    }
}
