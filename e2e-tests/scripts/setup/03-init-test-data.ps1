# Initialize Test Data
# This script sets up brokers and topics for testing

Write-Host "ðŸ Initializing test data..." -ForegroundColor Green

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

# Check if metadata service is available
Write-Host "Checking metadata service availability..." -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/controller"
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host " Metadata service not available: $($result.Error)" -ForegroundColor Red
    exit 1
}
Write-Host " Metadata service is available" -ForegroundColor Green

# Register broker
Write-Host "Registering storage broker..." -ForegroundColor Yellow
$brokerData = @{
    id = 101
    host = "localhost"
    port = 8081
    rack = "rack1"
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers" -Method "POST" -Body $brokerData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.id -eq 101) {
        Write-Host "✅ Broker registered successfully (ID: $($response.id))" -ForegroundColor Green
    } else {
        Write-Host "❌ Broker registration failed: $($response.errorMessage)" -ForegroundColor Red
        exit 1
    }
} elseif ($result.StatusCode -eq 409) {
    Write-Host "ℹ️  Broker already registered (ID: 101)" -ForegroundColor Yellow
} else {
    Write-Host "❌ Broker registration request failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Create test topic
Write-Host "Creating test topic..." -ForegroundColor Yellow
$topicData = @{
    topicName = "test-topic"
    partitionCount = 3
    replicationFactor = 1
    config = @{
        "retention.ms" = "604800000"
        "segment.bytes" = "1073741824"
    }
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $topicData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.topicName -eq "test-topic") {
        Write-Host "✅ Topic created successfully: $($response.topicName)" -ForegroundColor Green
    } else {
        Write-Host "❌ Topic creation failed: $($response.errorMessage)" -ForegroundColor Red
        exit 1
    }
} elseif ($result.StatusCode -eq 409) {
    Write-Host "ℹ️  Topic already exists: test-topic" -ForegroundColor Yellow
} else {
    Write-Host "❌ Topic creation request failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Create sync test topic
Write-Host "Creating sync test topic..." -ForegroundColor Yellow
$syncTopicData = @{
    topicName = "sync-test-topic"
    partitionCount = 1
    replicationFactor = 1
    config = @{
        "retention.ms" = "604800000"
        "segment.bytes" = "1073741824"
    }
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $syncTopicData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.topicName -eq "sync-test-topic") {
        Write-Host "✅ Sync topic created successfully: $($response.topicName)" -ForegroundColor Green
    } else {
        Write-Host "❌ Sync topic creation failed: $($response.errorMessage)" -ForegroundColor Red
        exit 1
    }
} elseif ($result.StatusCode -eq 409) {
    Write-Host "ℹ️  Sync topic already exists: sync-test-topic" -ForegroundColor Yellow
} else {
    Write-Host "❌ Sync topic creation request failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Verify topics and brokers
Write-Host "Verifying created data..." -ForegroundColor Yellow

# Check topics
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics"
if ($result.Success -and $result.StatusCode -eq 200) {
    $topics = $result.Content | ConvertFrom-Json
    Write-Host " Found $($topics.Count) topics" -ForegroundColor Green
} else {
    Write-Host " Failed to list topics: $($result.Error)" -ForegroundColor Red
}

# Check brokers
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers"
if ($result.Success -and $result.StatusCode -eq 200) {
    $brokers = $result.Content | ConvertFrom-Json
    Write-Host " Found $($brokers.Count) brokers" -ForegroundColor Green
} else {
    Write-Host " Failed to list brokers: $($result.Error)" -ForegroundColor Red
}

Write-Host ""
Write-Host "ðŸŽ Test data initialization complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Available test topics:" -ForegroundColor Cyan
Write-Host "  - test-topic (3 partitions)" -ForegroundColor White
Write-Host "  - sync-test-topic (1 partition)" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Run producer flow tests: ..\storage\producer-flow-test.ps1"
Write-Host "2. Run consumer flow tests: ..\storage\consumer-flow-test.ps1"
Write-Host "3. Run integration tests: ..\integration\producer-e2e-test.ps1"
