package com.distributedmq.client.cli.commands;

import com.distributedmq.client.cli.utils.ArgumentParser;
import com.distributedmq.client.cli.utils.MetadataServiceClient;
import com.distributedmq.common.model.TopicMetadata;
import com.distributedmq.common.model.PartitionMetadata;
import com.distributedmq.common.model.BrokerNode;

/**
 * Command to describe a topic
 * Usage: mycli describe-topic --name <topic>
 */
public class DescribeTopicCommand implements Command {
    
    @Override
    public void execute(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser(args);
        
        if (parser.hasFlag("help") || parser.hasFlag("h")) {
            printHelp();
            return;
        }
        
        // Parse required arguments
        String topicName = parser.getOption("name");
        if (topicName == null) {
            throw new IllegalArgumentException("Missing required argument: --name");
        }
        
        // Optional metadata service URL
        String metadataUrl = parser.getOption("metadata-url");
        
        MetadataServiceClient client = new MetadataServiceClient(metadataUrl);
        
        System.out.println("Fetching topic metadata for '" + topicName + "'...");
        System.out.println();
        
        TopicMetadata metadata = client.getTopicMetadata(topicName);
        
        System.out.println("Topic: " + metadata.getTopicName());
        System.out.println("========================================");
        System.out.println("Partitions: " + metadata.getPartitions().size());
        System.out.println("Replication Factor: " + (metadata.getPartitions().isEmpty() ? "N/A" : 
                          metadata.getPartitions().get(0).getReplicas().size()));
        System.out.println();
        System.out.println("Partition Details:");
        System.out.println("----------------------------------------");
        
        for (PartitionMetadata partition : metadata.getPartitions()) {
            System.out.println("  Partition " + partition.getPartitionId() + ":");
            System.out.println("    Leader:   Broker " + partition.getLeader().getBrokerId() + 
                             " (" + partition.getLeader().getHost() + ":" + partition.getLeader().getPort() + ")");
            System.out.print("    Replicas: ");
            for (int i = 0; i < partition.getReplicas().size(); i++) {
                BrokerNode replica = partition.getReplicas().get(i);
                System.out.print("Broker " + replica.getBrokerId());
                if (i < partition.getReplicas().size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
            System.out.print("    ISR:      ");
            for (int i = 0; i < partition.getIsr().size(); i++) {
                BrokerNode isrNode = partition.getIsr().get(i);
                System.out.print("Broker " + isrNode.getBrokerId());
                if (i < partition.getIsr().size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
            System.out.println();
        }
    }
    
    @Override
    public void printHelp() {
        System.out.println("Describe a topic");
        System.out.println();
        System.out.println("Usage: mycli describe-topic --name <topic>");
        System.out.println();
        System.out.println("Required Arguments:");
        System.out.println("  --name <topic>          Topic name");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  --metadata-url <url>    Metadata service URL (default: from config)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mycli describe-topic --name orders");
        System.out.println("  mycli describe-topic --name logs --metadata-url http://localhost:9091");
    }
}
