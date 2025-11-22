package com.distributedmq.client.cli;

import com.distributedmq.client.cli.commands.*;

import java.util.Arrays;

/**
 * Main entry point for MyCli - DMQ Command Line Interface
 * Usage: mycli <command> [options]
 * 
 * Available commands:
 *   create-topic  - Create a new topic
 *   produce       - Produce messages to a topic
 *   help          - Show help information
 */
public class MyCli {
    
    private static final String VERSION = "1.0.0";
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        
        try {
            Command cmd = getCommand(command);
            if (cmd == null) {
                System.err.println("[ERROR] Unknown command: " + command);
                System.err.println();
                printUsage();
                System.exit(1);
            }
            
            cmd.execute(commandArgs);
            
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.toString();
            System.err.println("[ERROR] " + errorMsg);
            if (System.getProperty("mycli.verbose") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static Command getCommand(String name) {
        switch (name.toLowerCase()) {
            case "create-topic":
                return new CreateTopicCommand();
            case "list-topics":
                return new ListTopicsCommand();
            case "describe-topic":
                return new DescribeTopicCommand();
            case "get-leader":
                return new GetLeaderCommand();
            case "produce":
                return new ProduceCommand();
            case "consume":
                return new ConsumeCommand();
            case "list-groups":
                return new ListGroupsCommand();
            case "describe-group":
                return new DescribeGroupCommand();
            case "help":
            case "--help":
            case "-h":
                printUsage();
                System.exit(0);
                return null;
            case "version":
            case "--version":
            case "-v":
                System.out.println("MyCli version " + VERSION);
                System.exit(0);
                return null;
            default:
                return null;
        }
    }
    
    private static void printUsage() {
        System.out.println("============================================================");
        System.out.println("  MyCli - DistributedMQ Command Line Interface");
        System.out.println("============================================================");
        System.out.println();
        System.out.println("Usage: mycli <command> [options]");
        System.out.println();
        System.out.println("Topic Management:");
        System.out.println("  create-topic       Create a new topic");
        System.out.println("  list-topics        List all topics");
        System.out.println("  describe-topic     Describe a specific topic");
        System.out.println();
        System.out.println("Cluster Management:");
        System.out.println("  get-leader         Get Raft leader information");
        System.out.println();
        System.out.println("Producer Commands:");
        System.out.println("  produce            Produce messages to a topic");
        System.out.println();
        System.out.println("Consumer Commands:");
        System.out.println("  consume            Consume messages from a topic");
        System.out.println("  list-groups        List all consumer groups");
        System.out.println("  describe-group     Describe a consumer group");
        System.out.println();
        System.out.println("General:");
        System.out.println("  help               Show this help message");
        System.out.println("  version            Show version information");
        System.out.println();
        System.out.println("Run 'mycli <command> --help' for more information on a command.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Create a topic");
        System.out.println("  mycli create-topic --name orders --partitions 3 --replication-factor 2");
        System.out.println();
        System.out.println("  # Produce a message");
        System.out.println("  mycli produce --topic orders --key order-123 --value \"Order data\"");
        System.out.println();
        System.out.println("  # Consume messages");
        System.out.println("  mycli consume --topic orders --partition 0 --from-beginning");
        System.out.println();
        System.out.println("  # List consumer groups");
        System.out.println("  mycli list-groups");
    }
}
