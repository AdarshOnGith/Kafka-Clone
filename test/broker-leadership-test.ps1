# ========================================
# Broker Leadership Verification Test
# ========================================
# This script verifies that brokers properly track and remove
# partition leadership when topics are deleted
#
# Prerequisites:
# - Metadata service running on port 9091
# - Broker 101 running on port 8081
# - Broker 102 running on port 8082

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Broker Leadership Verification Test" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Step 1: Check running brokers
Write-Host "=== Step 1: Check Running Brokers ===" -ForegroundColor Green
$brokers = Invoke-RestMethod -Uri 'http://localhost:9091/api/v1/metadata/brokers' -Method GET
Write-Host "Active brokers:" -ForegroundColor Yellow
$brokers | ConvertTo-Json -Depth 2
Write-Host ""

# Step 2: Query broker 101 metadata (before topic creation)
Write-Host "=== Step 2: Broker 101 Metadata (Before Topic Creation) ===" -ForegroundColor Green
try {
    $broker101Before = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 101 is leader for:" -ForegroundColor Yellow
    $broker101Before.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker101Before.leaderPartitions.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Error querying broker 101: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 3: Query broker 102 metadata (before topic creation)
Write-Host "=== Step 3: Broker 102 Metadata (Before Topic Creation) ===" -ForegroundColor Green
try {
    $broker102Before = Invoke-RestMethod -Uri 'http://localhost:8082/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 102 is leader for:" -ForegroundColor Yellow
    $broker102Before.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker102Before.leaderPartitions.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Error querying broker 102: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 4: Create test topic
Write-Host "=== Step 4: Creating Test Topic 'broker-leader-verify' ===" -ForegroundColor Green
$createBody = @{
    topicName = 'broker-leader-verify'
    partitionCount = 4
    replicationFactor = 2
    config = @{ 'retention.ms' = '86400000' }
} | ConvertTo-Json

$topicCreated = Invoke-RestMethod -Uri 'http://localhost:9091/api/v1/metadata/topics' `
    -Method POST `
    -ContentType 'application/json' `
    -Body $createBody

Write-Host "Topic created with partitions:" -ForegroundColor Yellow
$topicCreated.partitions | ForEach-Object {
    Write-Host "  Partition $($_.partitionId): Leader = Broker $($_.leader.brokerId)" -ForegroundColor White
}
Write-Host ""

# Step 5: Wait for metadata propagation
Write-Host "=== Step 5: Waiting for Metadata Propagation ===" -ForegroundColor Green
Write-Host "Waiting 3 seconds for brokers to receive metadata updates..." -ForegroundColor Yellow
Start-Sleep -Seconds 3
Write-Host ""

# Step 6: Query broker 101 metadata (after topic creation)
Write-Host "=== Step 6: Broker 101 Metadata (After Topic Creation) ===" -ForegroundColor Green
try {
    $broker101After = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 101 is now leader for:" -ForegroundColor Yellow
    $broker101After.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker101After.leaderPartitions.Count)" -ForegroundColor Cyan
    
    # Count how many partitions of the new topic this broker leads
    $newTopicLeaderCount = ($broker101After.leaderPartitions | Where-Object { $_.topic -eq 'broker-leader-verify' }).Count
    Write-Host "Broker 101 leads $newTopicLeaderCount partitions of 'broker-leader-verify'" -ForegroundColor Magenta
} catch {
    Write-Host "❌ Error querying broker 101: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 7: Query broker 102 metadata (after topic creation)
Write-Host "=== Step 7: Broker 102 Metadata (After Topic Creation) ===" -ForegroundColor Green
try {
    $broker102After = Invoke-RestMethod -Uri 'http://localhost:8082/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 102 is now leader for:" -ForegroundColor Yellow
    $broker102After.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker102After.leaderPartitions.Count)" -ForegroundColor Cyan
    
    # Count how many partitions of the new topic this broker leads
    $newTopicLeaderCount = ($broker102After.leaderPartitions | Where-Object { $_.topic -eq 'broker-leader-verify' }).Count
    Write-Host "Broker 102 leads $newTopicLeaderCount partitions of 'broker-leader-verify'" -ForegroundColor Magenta
} catch {
    Write-Host "❌ Error querying broker 102: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 8: Delete the topic
Write-Host "=== Step 8: Deleting Topic 'broker-leader-verify' ===" -ForegroundColor Green
Invoke-RestMethod -Uri 'http://localhost:9091/api/v1/metadata/topics/broker-leader-verify' -Method DELETE
Write-Host "✅ Topic deleted" -ForegroundColor Green
Write-Host ""

# Step 9: Wait for metadata propagation
Write-Host "=== Step 9: Waiting for Deletion Metadata Propagation ===" -ForegroundColor Green
Write-Host "Waiting 3 seconds for brokers to receive deletion updates..." -ForegroundColor Yellow
Start-Sleep -Seconds 3
Write-Host ""

# Step 10: Query broker 101 metadata (after topic deletion)
Write-Host "=== Step 10: Broker 101 Metadata (After Topic Deletion) ===" -ForegroundColor Green
try {
    $broker101Final = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 101 is now leader for:" -ForegroundColor Yellow
    $broker101Final.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker101Final.leaderPartitions.Count)" -ForegroundColor Cyan
    
    # Check if any partitions of the deleted topic still exist
    $deletedTopicPartitions = ($broker101Final.leaderPartitions | Where-Object { $_.topic -eq 'broker-leader-verify' }).Count
    if ($deletedTopicPartitions -eq 0) {
        Write-Host "✅ VERIFIED: Broker 101 is no longer leader for 'broker-leader-verify' partitions" -ForegroundColor Green
    } else {
        Write-Host "❌ FAILED: Broker 101 still leads $deletedTopicPartitions partitions of deleted topic!" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Error querying broker 101: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 11: Query broker 102 metadata (after topic deletion)
Write-Host "=== Step 11: Broker 102 Metadata (After Topic Deletion) ===" -ForegroundColor Green
try {
    $broker102Final = Invoke-RestMethod -Uri 'http://localhost:8082/api/v1/storage/broker/metadata' -Method GET
    Write-Host "Broker 102 is now leader for:" -ForegroundColor Yellow
    $broker102Final.leaderPartitions | ConvertTo-Json -Depth 2
    Write-Host "Leader partition count: $($broker102Final.leaderPartitions.Count)" -ForegroundColor Cyan
    
    # Check if any partitions of the deleted topic still exist
    $deletedTopicPartitions = ($broker102Final.leaderPartitions | Where-Object { $_.topic -eq 'broker-leader-verify' }).Count
    if ($deletedTopicPartitions -eq 0) {
        Write-Host "✅ VERIFIED: Broker 102 is no longer leader for 'broker-leader-verify' partitions" -ForegroundColor Green
    } else {
        Write-Host "❌ FAILED: Broker 102 still leads $deletedTopicPartitions partitions of deleted topic!" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Error querying broker 102: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 12: Verify topic no longer exists in metadata service
Write-Host "=== Step 12: Verify Topic No Longer Exists ===" -ForegroundColor Green
try {
    Invoke-RestMethod -Uri 'http://localhost:9091/api/v1/metadata/topics/broker-leader-verify' -Method GET
    Write-Host "❌ FAILED: Topic still exists in metadata service!" -ForegroundColor Red
} catch {
    if ($_.Exception.Message -like "*404*") {
        Write-Host "✅ VERIFIED: Topic successfully removed from metadata service" -ForegroundColor Green
    } else {
        Write-Host "❌ Unexpected error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
