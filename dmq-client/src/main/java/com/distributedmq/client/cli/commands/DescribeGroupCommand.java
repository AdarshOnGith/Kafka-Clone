package com.distributedmq.client.cli.commands;

import com.distributedmq.client.cli.utils.ArgumentParser;
import com.distributedmq.client.cli.utils.MetadataServiceClient;
import com.distributedmq.common.dto.ConsumerGroupResponse;
import com.distributedmq.common.dto.ConsumerMemberInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Command to describe a consumer group
 * Usage: mycli describe-group --group <group-id>
 */
public class DescribeGroupCommand implements Command {
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    
    @Override
    public void execute(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser(args);
        
        if (parser.hasFlag("help") || parser.hasFlag("h")) {
            printHelp();
            return;
        }
        
        String groupId = parser.getOption("group");
        if (groupId == null) {
            throw new IllegalArgumentException("Missing required argument: --group");
        }
        
        String metadataUrl = parser.getOption("metadata-url");
        
        System.out.println("Fetching consumer group details...");
        System.out.println();
        
        MetadataServiceClient client = new MetadataServiceClient(metadataUrl);
        ConsumerGroupResponse group = client.describeConsumerGroup(groupId);
        
        if (group == null) {
            System.out.println("❌ Consumer group not found: " + groupId);
            return;
        }
        
        // Print group overview
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        Consumer Group Details                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Group ID:           " + group.getGroupId());
        System.out.println("Topic:              " + group.getTopic());
        System.out.println("App ID:             " + group.getAppId());
        System.out.println("Coordinator Broker: " + group.getGroupLeaderBrokerId());
        System.out.println("Coordinator URL:    " + group.getGroupLeaderUrl());
        
        if (group.getCreatedAt() != null) {
            String created = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(group.getCreatedAt()));
            System.out.println("Created At:         " + created);
        }
        
        if (group.getLastModifiedAt() != null) {
            String modified = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(group.getLastModifiedAt()));
            System.out.println("Last Modified:      " + modified);
        }
        
        System.out.println();
    }
    
    @Override
    public void printHelp() {
        System.out.println("Describe a consumer group");
        System.out.println();
        System.out.println("Usage: mycli describe-group --group <group-id> [options]");
        System.out.println();
        System.out.println("Required Arguments:");
        System.out.println("  --group <group-id>        Consumer group ID");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  --metadata-url <url>      Metadata service URL (default: from config)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mycli describe-group --group order-processors");
        System.out.println("  mycli describe-group --group analytics-consumers --metadata-url http://localhost:9091");
    }
}
