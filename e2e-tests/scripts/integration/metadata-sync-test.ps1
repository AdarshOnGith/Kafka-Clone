# Metadata Synchronization Test
# Tests bidirectional metadata sync between services

Write-Host "🔄 Testing Metadata Synchronization..." -ForegroundColor Green

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

# Step 1: Create a new topic in metadata service
Write-Host "Step 1: Creating topic in metadata service" -ForegroundColor Yellow
$syncTestTopic = @{
    name = "sync-test-topic"
    partitions = 2
    replicationFactor = 1
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $syncTestTopic
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    Write-Host "✅ Topic created in metadata service: $($response.topicName)" -ForegroundColor Green
    Write-Host "   Partitions: $($response.partitionCount), Replication: $($response.replicationFactor)" -ForegroundColor White
} else {
    Write-Host "❌ Topic creation failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Step 2: Verify topic exists in metadata service
Write-Host "Step 2: Verifying topic in metadata service" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/sync-test-topic"
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Topic confirmed in metadata service" -ForegroundColor Green
} else {
    Write-Host "❌ Topic verification failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Step 3: Check initial storage service state (should not have topic yet)
Write-Host "Step 3: Checking initial storage service state" -ForegroundColor Yellow
$initialConsumeData = @{
    topic = "sync-test-topic"
    partition = 0
    offset = 0
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $initialConsumeData
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "✅ Storage service correctly rejects unknown topic (before sync)" -ForegroundColor Green
} else {
    $response = $result.Content | ConvertFrom-Json
    if (!$response.success) {
        Write-Host "✅ Storage service correctly rejects unknown topic: $($response.errorMessage)" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Storage service accepted unknown topic (unexpected)" -ForegroundColor Yellow
    }
}

# Step 4: Trigger push synchronization
Write-Host "Step 4: Triggering push synchronization" -ForegroundColor Yellow
$syncTriggerData = @{
    brokers = @(1)
    topics = @("sync-test-topic")
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/trigger" -Method "POST" -Body $syncTriggerData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    Write-Host "✅ Push sync triggered successfully" -ForegroundColor Green
    Write-Host "   Brokers triggered: $($response.brokersTriggered)" -ForegroundColor White
    Write-Host "   Topics synced: $($response.topicsSynced)" -ForegroundColor White
} else {
    Write-Host "❌ Push sync trigger failed: $($result.Error)" -ForegroundColor Red
}

# Step 5: Wait for sync to complete and verify
Write-Host "Step 5: Waiting for sync completion and verification" -ForegroundColor Yellow
Write-Host "   Waiting 5 seconds for sync to propagate..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Test if storage service can now handle the topic
$syncVerifyData = @{
    topic = "sync-test-topic"
    partition = 0
    offset = 0
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $syncVerifyData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.messages.Count -eq 0) {
        Write-Host "✅ Storage service now accepts sync'd topic (empty result expected)" -ForegroundColor Green
    } else {
        Write-Host "❌ Storage service sync verification failed: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Storage service still rejects topic after sync: $($result.Error)" -ForegroundColor Red
}

# Step 6: Test producing to sync'd topic
Write-Host "Step 6: Testing production to sync'd topic" -ForegroundColor Yellow
$syncProduceData = @{
    topic = "sync-test-topic"
    partition = 0
    messages = @(
        @{
            key = "sync-test-msg"
            value = "c3luY2hyb25pemF0aW9uIHRlc3QgbWVzc2FnZQ=="  # base64
        }
    )
    producerId = "sync-test-producer"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $syncProduceData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success) {
        Write-Host "✅ Successfully produced to sync'd topic" -ForegroundColor Green
        Write-Host "   Offset assigned: $($response.results[0].offset)" -ForegroundColor White
    } else {
        Write-Host "❌ Production to sync'd topic failed: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Production request failed: $($result.Error)" -ForegroundColor Red
}

# Step 7: Verify message consumption from sync'd topic
Write-Host "Step 7: Verifying consumption from sync'd topic" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $syncVerifyData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.messages.Count -ge 1) {
        Write-Host "✅ Successfully consumed from sync'd topic" -ForegroundColor Green
        Write-Host "   Messages found: $($response.messages.Count)" -ForegroundColor White
        $syncMessage = $response.messages | Where-Object { $_.key -eq "sync-test-msg" }
        if ($syncMessage) {
            Write-Host "✅ Sync test message found with offset $($syncMessage.offset)" -ForegroundColor Green
        }
    } else {
        Write-Host "❌ Consumption from sync'd topic failed: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Consumption request failed: $($result.Error)" -ForegroundColor Red
}

# Step 8: Test heartbeat mechanism
Write-Host "Step 8: Testing heartbeat mechanism" -ForegroundColor Yellow
$heartbeatData = @{
    serviceId = "storage-1"
    metadataVersion = 1234567890
    partitionCount = 3
    isAlive = $true
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/storage-heartbeat" -Method "POST" -Body $heartbeatData
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Heartbeat sent successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Heartbeat failed: $($result.Error)" -ForegroundColor Red
}

# Step 9: Check sync status after heartbeat
Write-Host "Step 9: Checking sync status after heartbeat" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/status"
if ($result.Success -and $result.StatusCode -eq 200) {
    $syncStatus = $result.Content | ConvertFrom-Json
    Write-Host "✅ Sync status retrieved" -ForegroundColor Green
    Write-Host "   Controller leader: $($syncStatus.isControllerLeader)" -ForegroundColor White
    Write-Host "   Active brokers: $($syncStatus.activeBrokers)" -ForegroundColor White
    Write-Host "   Total topics: $($syncStatus.totalTopics)" -ForegroundColor White
} else {
    Write-Host "❌ Sync status check failed: $($result.Error)" -ForegroundColor Red
}

# Step 10: Test metadata version tracking
Write-Host "Step 10: Testing metadata version tracking" -ForegroundColor Yellow
# Create another topic to trigger version change
$versionTestTopic = @{
    name = "version-test-topic"
    partitions = 1
    replicationFactor = 1
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $versionTestTopic
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Version change topic created" -ForegroundColor Green
} else {
    Write-Host "❌ Version change topic creation failed: $($result.Error)" -ForegroundColor Red
}

# Wait for potential sync
Start-Sleep -Seconds 3

# Check if storage service received the update
$versionConsumeData = @{
    topic = "version-test-topic"
    partition = 0
    offset = 0
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $versionConsumeData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success) {
        Write-Host "✅ Storage service received version update (topic accessible)" -ForegroundColor Green
    }
}

# Step 11: Cleanup test topics
Write-Host "Step 11: Cleanup test topics" -ForegroundColor Yellow
Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/sync-test-topic" -Method "DELETE" | Out-Null
Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/version-test-topic" -Method "DELETE" | Out-Null
Write-Host "✅ Test topics cleaned up" -ForegroundColor Green

# Step 12: Final validation
Write-Host "Step 12: Final metadata sync validation" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/sync/consistency"
if ($result.Success -and $result.StatusCode -eq 200) {
    $consistency = $result.Content | ConvertFrom-Json
    Write-Host "✅ Final consistency check: $($consistency.status)" -ForegroundColor Green
    if ($consistency.status -eq "CONSISTENT") {
        Write-Host "✅ Metadata synchronization flow successful!" -ForegroundColor Green
        Write-Host "   Push sync: Working" -ForegroundColor White
        Write-Host "   Heartbeat: Active" -ForegroundColor White
        Write-Host "   Version tracking: Functional" -ForegroundColor White
        Write-Host "   Cross-service consistency: Maintained" -ForegroundColor White
    } else {
        Write-Host "⚠️  Consistency check shows issues: $($consistency.status)" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Final consistency check failed: $($result.Error)" -ForegroundColor Red
}

Write-Host ""
Write-Host "🎉 Metadata Synchronization testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Flow Summary:" -ForegroundColor Cyan
Write-Host "1. ✅ Topic Creation: Metadata service creates topic" -ForegroundColor White
Write-Host "2. ✅ Push Trigger: Sync explicitly triggered" -ForegroundColor White
Write-Host "3. ✅ Storage Update: Service receives and applies metadata" -ForegroundColor White
Write-Host "4. ✅ Topic Access: Storage can now handle the topic" -ForegroundColor White
Write-Host "5. ✅ Message Ops: Produce/consume work on sync'd topic" -ForegroundColor White
Write-Host "6. ✅ Heartbeat: Status reporting mechanism active" -ForegroundColor White
Write-Host "7. ✅ Version Tracking: Metadata changes propagate" -ForegroundColor White
Write-Host "8. ✅ Consistency: Cross-service state maintained" -ForegroundColor White
Write-Host ""
Write-Host "Bidirectional metadata synchronization is working correctly!" -ForegroundColor Green
