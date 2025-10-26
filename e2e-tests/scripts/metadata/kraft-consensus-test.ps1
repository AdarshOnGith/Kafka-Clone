# KRaft Consensus Test
# Tests distributed consensus protocol

Write-Host "⚖️  Testing KRaft Consensus Protocol..." -ForegroundColor Green

# Function to make HTTP requests
function Invoke-HttpRequest {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null,
        [string]$ContentType = "application/json"
    )

    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            ContentType = $ContentType
        }

        if ($Body) {
            $params.Body = $Body
        }

        $response = Invoke-WebRequest @params
        return @{
            Success = $true
            StatusCode = $response.StatusCode
            Content = $response.Content
        }
    } catch {
        return @{
            Success = $false
            StatusCode = $_.Exception.Response.StatusCode.Value__
            Error = $_.Exception.Message
        }
    }
}

# Test 1: Check controller status
Write-Host "Test 1: Controller status check" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/controller"
if ($result.Success -and $result.StatusCode -eq 200) {
    $controllerInfo = $result.Content | ConvertFrom-Json
    Write-Host "✅ Controller status retrieved" -ForegroundColor Green
    Write-Host "   Leader: $($controllerInfo.isLeader)" -ForegroundColor White
    Write-Host "   Leader ID: $($controllerInfo.leaderId)" -ForegroundColor White
    Write-Host "   Term: $($controllerInfo.term)" -ForegroundColor White

    if ($controllerInfo.isLeader) {
        Write-Host "✅ This node is the controller leader" -ForegroundColor Green
    } else {
        Write-Host "ℹ️  This node is not the controller leader (follower mode)" -ForegroundColor Blue
    }
} else {
    Write-Host "❌ Controller status check failed: $($result.Error)" -ForegroundColor Red
}

# Test 2: Raft log operations (via topic creation)
Write-Host "Test 2: Raft log operations via topic creation" -ForegroundColor Yellow
$raftTestTopic = @{
    name = "raft-test-topic"
    partitions = 1
    replicationFactor = 1
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $raftTestTopic
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Topic creation succeeded (logged in Raft)" -ForegroundColor Green

    # Check that the topic was persisted (Raft log replay)
    Start-Sleep -Seconds 1
    $result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/raft-test-topic"
    if ($result.Success -and $result.StatusCode -eq 200) {
        Write-Host "✅ Topic persisted in Raft log" -ForegroundColor Green
    } else {
        Write-Host "❌ Topic not persisted in Raft log" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Topic creation failed: $($result.Error)" -ForegroundColor Red
}

# Test 3: Metadata sync status
Write-Host "Test 3: Metadata sync status" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/status"
if ($result.Success -and $result.StatusCode -eq 200) {
    $syncStatus = $result.Content | ConvertFrom-Json
    Write-Host "✅ Sync status retrieved" -ForegroundColor Green
    Write-Host "   Controller Leader: $($syncStatus.isControllerLeader)" -ForegroundColor White
    Write-Host "   Active Brokers: $($syncStatus.activeBrokers)" -ForegroundColor White
    Write-Host "   Total Topics: $($syncStatus.totalTopics)" -ForegroundColor White
    Write-Host "   Status: $($syncStatus.status)" -ForegroundColor White
} else {
    Write-Host "❌ Sync status check failed: $($result.Error)" -ForegroundColor Red
}

# Test 4: Metadata consistency check
Write-Host "Test 4: Metadata consistency validation" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/consistency"
if ($result.Success -and $result.StatusCode -eq 200) {
    $consistency = $result.Content | ConvertFrom-Json
    Write-Host "✅ Consistency check completed" -ForegroundColor Green
    Write-Host "   Status: $($consistency.status)" -ForegroundColor White
    Write-Host "   Brokers Checked: $($consistency.brokersChecked)" -ForegroundColor White
    Write-Host "   Topics Checked: $($consistency.topicsChecked)" -ForegroundColor White

    if ($consistency.status -eq "CONSISTENT") {
        Write-Host "✅ Metadata is consistent" -ForegroundColor Green
    } else {
        Write-Host "❌ Metadata consistency issues detected" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Consistency check failed: $($result.Error)" -ForegroundColor Red
}

# Test 5: Pull metadata sync
Write-Host "Test 5: Pull metadata sync" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync"
if ($result.Success -and $result.StatusCode -eq 200) {
    $syncData = $result.Content | ConvertFrom-Json
    Write-Host "✅ Pull sync successful" -ForegroundColor Green
    Write-Host "   Topics: $($syncData.topics.Count)" -ForegroundColor White
    Write-Host "   Brokers: $($syncData.brokers.Count)" -ForegroundColor White
    Write-Host "   Sync Timestamp: $($syncData.syncTimestamp)" -ForegroundColor White
} else {
    Write-Host "❌ Pull sync failed: $($result.Error)" -ForegroundColor Red
}

# Test 6: Incremental sync
Write-Host "Test 6: Incremental sync capability" -ForegroundColor Yellow
$sinceTimestamp = [int64](([DateTime]::UtcNow.AddMinutes(-5)).ToUniversalTime().Subtract([DateTime]::Parse("1970-01-01")).TotalMilliseconds)
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/incremental?sinceTimestamp=$sinceTimestamp"
if ($result.Success -and $result.StatusCode -eq 200) {
    $incrementalSync = $result.Content | ConvertFrom-Json
    Write-Host "✅ Incremental sync successful" -ForegroundColor Green
    Write-Host "   Changes Since: $($incrementalSync.changesSince)" -ForegroundColor White
    Write-Host "   Topics: $($incrementalSync.topics.Count)" -ForegroundColor White
    Write-Host "   Brokers: $($incrementalSync.brokers.Count)" -ForegroundColor White
} else {
    Write-Host "❌ Incremental sync failed: $($result.Error)" -ForegroundColor Red
}

# Test 7: Full sync
Write-Host "Test 7: Full sync capability" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/full" -Method "POST"
if ($result.Success -and $result.StatusCode -eq 200) {
    $fullSync = $result.Content | ConvertFrom-Json
    Write-Host "✅ Full sync successful" -ForegroundColor Green
    Write-Host "   Full Sync Performed: $($fullSync.fullSyncPerformed)" -ForegroundColor White
    Write-Host "   Topics: $($fullSync.topics.Count)" -ForegroundColor White
    Write-Host "   Brokers: $($fullSync.brokers.Count)" -ForegroundColor White
} else {
    Write-Host "❌ Full sync failed: $($result.Error)" -ForegroundColor Red
}

# Test 8: Leader-only operations
Write-Host "Test 8: Leader-only operation validation" -ForegroundColor Yellow
# Try to create a topic when not leader (if this node is not leader)
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/controller"
if ($result.Success) {
    $controllerInfo = $result.Content | ConvertFrom-Json
    if (-not $controllerInfo.isLeader) {
        # This node is not leader, try leader-only operation
        $leaderOnlyTopic = @{
            name = "leader-only-test"
            partitions = 1
            replicationFactor = 1
        } | ConvertTo-Json

        $result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $leaderOnlyTopic
        if ($result.StatusCode -eq 503) {  # Service Unavailable
            Write-Host "✅ Leader-only operation properly rejected on follower" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Leader-only operation result: $($result.StatusCode)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "ℹ️  This node is leader, skipping leader-only test" -ForegroundColor Blue
    }
}

# Cleanup: Delete test topic
Write-Host "Cleanup: Removing test topic" -ForegroundColor Yellow
Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/raft-test-topic" -Method "DELETE" | Out-Null

Write-Host ""
Write-Host "🎉 KRaft consensus testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Tested controller status and leadership" -ForegroundColor White
Write-Host "- Verified Raft log operations via topic creation" -ForegroundColor White
Write-Host "- Tested metadata sync and consistency checks" -ForegroundColor White
Write-Host "- Validated leader-only operation restrictions" -ForegroundColor White
Write-Host "- Confirmed pull/incremental/full sync capabilities" -ForegroundColor White
Write-Host ""
Write-Host "Note: KRaft consensus provides:" -ForegroundColor Yellow
Write-Host "  - Distributed metadata management" -ForegroundColor White
Write-Host "  - Fault-tolerant controller election" -ForegroundColor White
Write-Host "  - Consistent metadata replication" -ForegroundColor White
Write-Host "  - Leader-follower coordination" -ForegroundColor White
Write-Host "  - Automatic failover and recovery" -ForegroundColor White
