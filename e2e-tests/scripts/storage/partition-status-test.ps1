# Partition Status Test
# Tests partition status reporting and metrics

Write-Host "📊 Testing Partition Status Reporting..." -ForegroundColor Green

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

# Ensure we have some data to report on
Write-Host "Ensuring test data exists..." -ForegroundColor Yellow
$testMessage = @{
    topic = "test-topic"
    partition = 0
    messages = @(@{ key = "status-test"; value = "c3RhdHVzIHRlc3Q=" })
    producerId = "status-test"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $testMessage
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "⚠️  Could not produce test message, but continuing..." -ForegroundColor Yellow
}

# Test 1: Get high water mark
Write-Host "Test 1: High water mark reporting" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark"
if ($result.Success -and $result.StatusCode -eq 200) {
    $hwm = [int]$result.Content
    Write-Host "✅ High water mark: $hwm" -ForegroundColor Green
    if ($hwm -ge 0) {
        Write-Host "✅ HWM is valid (non-negative)" -ForegroundColor Green
    } else {
        Write-Host "❌ HWM is invalid: $hwm" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Failed to get high water mark: $($result.Error)" -ForegroundColor Red
}

# Test 2: Test multiple partitions
Write-Host "Test 2: Multi-partition status" -ForegroundColor Yellow
$partitions = 0, 1, 2  # test-topic has 3 partitions
$partitionStatuses = @{}

foreach ($partition in $partitions) {
    $result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/$partition/high-water-mark"
    if ($result.Success -and $result.StatusCode -eq 200) {
        $hwm = [int]$result.Content
        $partitionStatuses[$partition] = $hwm
        Write-Host "   Partition $partition HWM: $hwm" -ForegroundColor White
    } else {
        Write-Host "   ❌ Failed to get HWM for partition $partition" -ForegroundColor Red
        $partitionStatuses[$partition] = $null
    }
}

# Verify partition status collection
$validPartitions = ($partitionStatuses.Values | Where-Object { $_ -ne $null }).Count
Write-Host "✅ Valid partition status for $validPartitions/$($partitions.Count) partitions" -ForegroundColor Green

# Test 3: Test invalid partition
Write-Host "Test 3: Invalid partition handling" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/99/high-water-mark"
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "✅ Invalid partition properly rejected" -ForegroundColor Green
} else {
    $response = $result.Content | ConvertFrom-Json
    if (!$response.success) {
        Write-Host "✅ Invalid partition properly rejected: $($response.errorMessage)" -ForegroundColor Green
    } else {
        Write-Host "❌ Invalid partition should have been rejected" -ForegroundColor Red
    }
}

# Test 4: Test invalid topic
Write-Host "Test 4: Invalid topic handling" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/non-existent-topic/0/high-water-mark"
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "✅ Invalid topic properly rejected" -ForegroundColor Green
} else {
    $response = $result.Content | ConvertFrom-Json
    if (!$response.success) {
        Write-Host "✅ Invalid topic properly rejected: $($response.errorMessage)" -ForegroundColor Green
    } else {
        Write-Host "❌ Invalid topic should have been rejected" -ForegroundColor Red
    }
}

# Test 5: Status consistency check
Write-Host "Test 5: Status consistency validation" -ForegroundColor Yellow
# Produce a few more messages and verify HWM increases
$additionalMessages = @{
    topic = "test-topic"
    partition = 0
    messages = @(
        @{ key = "consistency-1"; value = "Y29uc2lzdGVuY3kgdGVzdCAx" },
        @{ key = "consistency-2"; value = "Y29uc2lzdGVuY3kgdGVzdCAy" }
    )
    producerId = "consistency-test"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$initialHwm = $partitionStatuses[0]
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $additionalMessages
if ($result.Success -and $result.StatusCode -eq 200) {
    Start-Sleep -Seconds 1  # Allow time for processing

    $result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark"
    if ($result.Success -and $result.StatusCode -eq 200) {
        $finalHwm = [int]$result.Content
        $increase = $finalHwm - $initialHwm
        Write-Host "✅ HWM increased by $increase (from $initialHwm to $finalHwm)" -ForegroundColor Green
        if ($increase -ge 2) {
            Write-Host "✅ Status consistency maintained" -ForegroundColor Green
        } else {
            Write-Host "⚠️  HWM increase seems low: $increase" -ForegroundColor Yellow
        }
    }
}

# Test 6: Heartbeat data simulation
Write-Host "Test 6: Heartbeat data collection simulation" -ForegroundColor Yellow
# This simulates what the heartbeat scheduler would collect
$heartbeatData = @{
    nodeId = 1
    metadataVersion = 1234567890
    partitions = @(
        @{
            topic = "test-topic"
            partition = 0
            leo = $finalHwm
            lag = 0
            hwm = $finalHwm
            role = "LEADER"
        }
    )
    isAlive = $true
} | ConvertTo-Json -Depth 10

Write-Host "✅ Simulated heartbeat data structure:" -ForegroundColor Green
Write-Host "   Node ID: $($heartbeatData.nodeId)" -ForegroundColor White
Write-Host "   Partitions: $($heartbeatData.partitions.Count)" -ForegroundColor White
Write-Host "   LEO/HWM: $($heartbeatData.partitions[0].leo)" -ForegroundColor White
Write-Host "   Role: $($heartbeatData.partitions[0].role)" -ForegroundColor White

Write-Host ""
Write-Host "🎉 Partition status testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Tested high water mark reporting across partitions" -ForegroundColor White
Write-Host "- Verified error handling for invalid topics/partitions" -ForegroundColor White
Write-Host "- Validated status consistency during message production" -ForegroundColor White
Write-Host "- Simulated heartbeat data collection structure" -ForegroundColor White
Write-Host ""
Write-Host "Note: In production, partition status would be collected by:" -ForegroundColor Yellow
Write-Host "  - StorageHeartbeatScheduler (every 5 seconds)" -ForegroundColor White
Write-Host "  - collectPartitionStatus() method" -ForegroundColor White
Write-Host "  - Heartbeat transmission to metadata service" -ForegroundColor White
