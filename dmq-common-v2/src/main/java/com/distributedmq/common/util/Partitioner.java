package com.distributedmq.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Partitioner implementation for determining which partition a record should go to.
 * Uses consistent hashing based on MurmurHash3 algorithm.
 * 
 * Partitioning Logic (from Flow 1):
 * - If key exists: partition = hash(record.key) % partition_count
 * - If key is null: partition = round-robin or random
 */
public class Partitioner {
    
    /**
     * Determines the partition for a given key.
     * 
     * @param key The record key (can be null)
     * @param numPartitions Total number of partitions for the topic
     * @param roundRobinCounter Counter for round-robin when key is null
     * @return Partition number (0-indexed)
     */
    public static int partition(String key, int numPartitions, int roundRobinCounter) {
        if (key == null || key.isEmpty()) {
            // Use round-robin for null keys
            return Math.abs(roundRobinCounter) % numPartitions;
        }
        
        // Hash-based partitioning for non-null keys
        int hash = murmur3Hash(key.getBytes(StandardCharsets.UTF_8));
        return Math.abs(hash) % numPartitions;
    }
    
    /**
     * MurmurHash3 32-bit implementation.
     * Fast, non-cryptographic hash function with good distribution.
     * 
     * @param data The data to hash
     * @return 32-bit hash value
     */
    public static int murmur3Hash(byte[] data) {
        int seed = 0x9747b28c; // Arbitrary seed
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;
        
        int hash = seed;
        int length = data.length;
        int processedBytes = 0;
        
        // Process 4-byte chunks
        while (processedBytes + 4 <= length) {
            int k = (data[processedBytes] & 0xff)
                  | ((data[processedBytes + 1] & 0xff) << 8)
                  | ((data[processedBytes + 2] & 0xff) << 16)
                  | ((data[processedBytes + 3] & 0xff) << 24);
            
            k *= c1;
            k = Integer.rotateLeft(k, r1);
            k *= c2;
            
            hash ^= k;
            hash = Integer.rotateLeft(hash, r2);
            hash = hash * m + n;
            
            processedBytes += 4;
        }
        
        // Process remaining bytes
        if (processedBytes < length) {
            int k = 0;
            for (int i = length - 1; i >= processedBytes; i--) {
                k <<= 8;
                k |= data[i] & 0xff;
            }
            
            k *= c1;
            k = Integer.rotateLeft(k, r1);
            k *= c2;
            hash ^= k;
        }
        
        // Finalization
        hash ^= length;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);
        
        return hash;
    }
    
    /**
     * Alternative MD5-based partitioning (simpler but slower).
     * Kept for compatibility purposes.
     */
    public static int md5Partition(String key, int numPartitions) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // Use first 4 bytes as integer
            int hashInt = ((hash[0] & 0xFF) << 24)
                        | ((hash[1] & 0xFF) << 16)
                        | ((hash[2] & 0xFF) << 8)
                        | (hash[3] & 0xFF);
            
            return Math.abs(hashInt) % numPartitions;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to Java hashCode
            return Math.abs(key.hashCode()) % numPartitions;
        }
    }
}
