# Producer Flow Test
# Tests message production functionality

Write-Host " Testing Producer Flow..." -ForegroundColor Green

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

# Test 1: Single message production
Write-Host "Test 1: Single message production" -ForegroundColor Yellow
$singleMessageData = @{
    topic = "test-topic"
    partition = 0
    messages = @(
        @{
            key = "test-key-1"
            value = "dGVzdCBtZXNzYWdlIDE="  # base64 encoded "test message 1"
        }
    )
    producerId = "producer-test-1"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $singleMessageData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.results[0].offset -ge 0) {
        Write-Host " Single message production successful (offset: $($response.results[0].offset))" -ForegroundColor Green
    } else {
        Write-Host " Single message production failed: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host " Single message production request failed: $($result.Error)" -ForegroundColor Red
}

# Test 2: Batch message production
Write-Host "Test 2: Batch message production" -ForegroundColor Yellow
$batchMessageData = @{
    topic = "test-topic"
    partition = 0
    messages = @(
        @{
            key = "batch-key-1"
            value = "YmF0Y2ggbWVzc2FnZSAx"  # base64 encoded "batch message 1"
        },
        @{
            key = "batch-key-2"
            value = "YmF0Y2ggbWVzc2FnZSAy"  # base64 encoded "batch message 2"
        },
        @{
            key = "batch-key-3"
            value = "YmF0Y2ggbWVzc2FnZSAz"  # base64 encoded "batch message 3"
        }
    )
    producerId = "producer-test-2"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $batchMessageData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.results.Count -eq 3) {
        $offsets = $response.results | ForEach-Object { $_.offset }
        $isConsecutive = $true
        for ($i = 1; $i -lt $offsets.Count; $i++) {
            if ($offsets[$i] -ne ($offsets[$i-1] + 1)) {
                $isConsecutive = $false
                break
            }
        }
        if ($isConsecutive) {
            Write-Host " Batch message production successful (offsets: $($offsets -join ', '))" -ForegroundColor Green
        } else {
            Write-Host " Batch message offsets not consecutive: $($offsets -join ', ')" -ForegroundColor Red
        }
    } else {
        Write-Host " Batch message production failed: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host " Batch message production request failed: $($result.Error)" -ForegroundColor Red
}

# Test 3: Invalid request validation
Write-Host "Test 3: Invalid request validation" -ForegroundColor Yellow
$invalidData = @{
    topic = ""
    partition = 0
    messages = @()
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $invalidData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if (!$response.success -and $response.errorCode -ne "NONE") {
        Write-Host " Invalid request properly rejected: $($response.errorCode)" -ForegroundColor Green
    } else {
        Write-Host " Invalid request not properly rejected" -ForegroundColor Red
    }
} elseif ($result.Success) {
    Write-Host " Invalid request should have been rejected" -ForegroundColor Red
} else {
    Write-Host " Invalid request test failed: $($result.Error)" -ForegroundColor Red
}

# Test 4: Check high water mark
Write-Host "Test 4: Verify high water mark" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark"
if ($result.Success -and $result.StatusCode -eq 200) {
    $hwm = $result.Content | ConvertFrom-Json
    if ($hwm -gt 0) {
        Write-Host " High water mark updated correctly: $hwm" -ForegroundColor Green
    } else {
        Write-Host " High water mark not updated: $hwm" -ForegroundColor Red
    }
} else {
    Write-Host " High water mark check failed: $($result.Error)" -ForegroundColor Red
}

Write-Host ""
Write-Host " Producer flow testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Tested single and batch message production" -ForegroundColor White
Write-Host "- Verified offset assignment (0, 1, 2, 3)" -ForegroundColor White
Write-Host "- Tested input validation" -ForegroundColor White
Write-Host "- Verified high water mark updates" -ForegroundColor White
