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
                System.err.println("❌ Unknown command: " + command);
                System.err.println();
                printUsage();
                System.exit(1);
            }
            
            cmd.execute(commandArgs);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
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
            case "produce":
                return new ProduceCommand();
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
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  MyCli - DistributedMQ Command Line Interface             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage: mycli <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  create-topic    Create a new topic");
        System.out.println("  produce         Produce messages to a topic");
        System.out.println("  help            Show this help message");
        System.out.println("  version         Show version information");
        System.out.println();
        System.out.println("Run 'mycli <command> --help' for more information on a command.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  mycli create-topic --name orders --partitions 3 --replication-factor 2");
        System.out.println("  mycli produce --topic orders --key order-123 --value \"Order data\"");
    }
}
