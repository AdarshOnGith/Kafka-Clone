# Broker Management Test
# Tests broker registration and tracking

Write-Host "🖥️  Testing Broker Management..." -ForegroundColor Green

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

# Test 1: Register a new broker
Write-Host "Test 1: Broker registration" -ForegroundColor Yellow
$brokerData = @{
    id = 2
    host = "localhost"
    port = 8083
    rack = "rack2"
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers" -Method "POST" -Body $brokerData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.id -eq 2 -and $response.host -eq "localhost") {
        Write-Host "✅ Broker registered successfully: ID $($response.id), Host $($response.host):$($response.port)" -ForegroundColor Green
    } else {
        Write-Host "❌ Broker registration response incorrect: $($result.Content)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Broker registration failed: $($result.Error)" -ForegroundColor Red
}

# Test 2: Get broker information
Write-Host "Test 2: Broker retrieval" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers/2"
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.id -eq 2) {
        Write-Host "✅ Broker retrieved successfully: $($response.id)@$($response.host):$($response.port)" -ForegroundColor Green
    } else {
        Write-Host "❌ Broker retrieval failed: incorrect data" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Broker retrieval failed: $($result.Error)" -ForegroundColor Red
}

# Test 3: List all brokers
Write-Host "Test 3: Broker listing" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers"
if ($result.Success -and $result.StatusCode -eq 200) {
    $brokers = $result.Content | ConvertFrom-Json
    Write-Host "✅ Retrieved $($brokers.Count) brokers" -ForegroundColor Green
    $brokers | ForEach-Object { Write-Host "   - ID: $($_.id), Host: $($_.host):$($_.port), Rack: $($_.rack)" -ForegroundColor White }

    $brokerIds = $brokers | ForEach-Object { $_.id }
    if ($brokerIds -contains 1 -and $brokerIds -contains 2) {
        Write-Host "✅ Both brokers (1 and 2) appear in list" -ForegroundColor Green
    } else {
        Write-Host "❌ Expected brokers not found in list: $($brokerIds -join ', ')" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Broker listing failed: $($result.Error)" -ForegroundColor Red
}

# Test 4: Register another broker
Write-Host "Test 4: Additional broker registration" -ForegroundColor Yellow
$broker3Data = @{
    id = 3
    host = "localhost"
    port = 8084
    rack = "rack1"
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers" -Method "POST" -Body $broker3Data
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Third broker registered successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Third broker registration failed: $($result.Error)" -ForegroundColor Red
}

# Test 5: Verify broker count increased
Write-Host "Test 5: Verify broker count" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers"
if ($result.Success -and $result.StatusCode -eq 200) {
    $brokers = $result.Content | ConvertFrom-Json
    if ($brokers.Count -ge 3) {
        Write-Host "✅ Broker count correct: $($brokers.Count) brokers registered" -ForegroundColor Green
    } else {
        Write-Host "❌ Broker count incorrect: $($brokers.Count) (expected >= 3)" -ForegroundColor Red
    }
}

# Test 6: Invalid broker registration
Write-Host "Test 6: Invalid broker registration validation" -ForegroundColor Yellow
$invalidBrokerData = @{
    id = -1
    host = ""
    port = 0
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers" -Method "POST" -Body $invalidBrokerData
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "✅ Invalid broker registration properly rejected" -ForegroundColor Green
} else {
    Write-Host "❌ Invalid broker registration should have been rejected" -ForegroundColor Red
}

# Test 7: Duplicate broker registration
Write-Host "Test 7: Duplicate broker handling" -ForegroundColor Yellow
$duplicateBrokerData = @{
    id = 2
    host = "localhost"
    port = 8083
    rack = "rack2"
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers" -Method "POST" -Body $duplicateBrokerData
if ($result.Success -and $result.StatusCode -eq 409) {
    Write-Host "✅ Duplicate broker registration properly rejected (409 Conflict)" -ForegroundColor Green
} elseif ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "⚠️  Duplicate broker registration allowed (may be expected behavior)" -ForegroundColor Yellow
} else {
    Write-Host "❌ Duplicate broker registration test failed: $($result.Error)" -ForegroundColor Red
}

# Test 8: Non-existent broker retrieval
Write-Host "Test 8: Non-existent broker retrieval" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers/99"
if (!$result.Success -or $result.StatusCode -eq 404) {
    Write-Host "✅ Non-existent broker properly returns 404" -ForegroundColor Green
} else {
    Write-Host "❌ Non-existent broker should return 404" -ForegroundColor Red
}

# Test 9: Broker status tracking (via heartbeats)
Write-Host "Test 9: Broker status tracking" -ForegroundColor Yellow
# Send a simple heartbeat to update broker status
$heartbeatData = @{
    brokerId = 2
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/heartbeat" -Method "POST" -Body $heartbeatData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.acknowledged) {
        Write-Host "✅ Broker heartbeat acknowledged" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "🎉 Broker management testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Tested broker registration and retrieval" -ForegroundColor White
Write-Host "- Verified broker listing and counting" -ForegroundColor White
Write-Host "- Tested input validation and duplicate handling" -ForegroundColor White
Write-Host "- Validated error responses for invalid operations" -ForegroundColor White
Write-Host "- Tested basic heartbeat/status tracking" -ForegroundColor White
Write-Host ""
Write-Host "Note: In production, brokers are typically:" -ForegroundColor Yellow
Write-Host "  - Auto-registered on startup" -ForegroundColor White
Write-Host "  - Monitored via periodic heartbeats" -ForegroundColor White
Write-Host "  - Tracked for leader election and partition assignment" -ForegroundColor White
Write-Host "  - Deregistered on graceful shutdown or failure detection" -ForegroundColor White
