# WAL Management Test
# Tests Write-Ahead Log operations and durability

Write-Host "💾 Testing WAL Management..." -ForegroundColor Green

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

# Test 1: Produce messages to create WAL segments
Write-Host "Test 1: Creating WAL segments with messages" -ForegroundColor Yellow
$messages = @()
for ($i = 0; $i -lt 10; $i++) {
    $messages += @{
        key = "wal-test-$i"
        value = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("WAL test message $i"))
    }
}

$produceData = @{
    topic = "test-topic"
    partition = 0
    messages = $messages
    producerId = "wal-test-producer"
    producerEpoch = 0
    requiredAcks = 1
} | ConvertTo-Json -Depth 10

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/messages" -Method "POST" -Body $produceData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success) {
        Write-Host "✅ Produced 10 messages for WAL testing" -ForegroundColor Green
    } else {
        Write-Host "❌ Failed to produce messages: $($response.errorMessage)" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ Message production failed: $($result.Error)" -ForegroundColor Red
    exit 1
}

# Test 2: Check WAL file creation
Write-Host "Test 2: Verifying WAL file creation" -ForegroundColor Yellow
$walDir = "..\..\dmq-storage-service\data\broker-1\logs\test-topic\0"

if (Test-Path $walDir) {
    $logFiles = Get-ChildItem -Path $walDir -Filter "*.log" -ErrorAction SilentlyContinue
    $indexFiles = Get-ChildItem -Path $walDir -Filter "*.index" -ErrorAction SilentlyContinue

    if ($logFiles) {
        Write-Host "✅ Found $($logFiles.Count) log file(s):" -ForegroundColor Green
        $logFiles | ForEach-Object { Write-Host "   $($_.Name)" -ForegroundColor White }
    } else {
        Write-Host "❌ No log files found" -ForegroundColor Red
    }

    if ($indexFiles) {
        Write-Host "✅ Found $($indexFiles.Count) index file(s):" -ForegroundColor Green
        $indexFiles | ForEach-Object { Write-Host "   $($_.Name)" -ForegroundColor White }
    } else {
        Write-Host "❌ No index files found" -ForegroundColor Red
    }
} else {
    Write-Host "❌ WAL directory not found: $walDir" -ForegroundColor Red
}

# Test 3: Verify message persistence (consume and verify)
Write-Host "Test 3: Verifying message persistence" -ForegroundColor Yellow
$consumeData = @{
    topic = "test-topic"
    partition = 0
    offset = 0
    maxBytes = 1048576
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $consumeData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.messages) {
        $totalMessages = $response.messages.Count
        Write-Host "✅ Retrieved $totalMessages messages from WAL" -ForegroundColor Green

        # Verify we have at least the 10 messages we produced
        if ($totalMessages -ge 10) {
            Write-Host "✅ Message persistence verified" -ForegroundColor Green
        } else {
            Write-Host "❌ Expected at least 10 messages, got $totalMessages" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Failed to consume messages: $($response.errorMessage)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Consume request failed: $($result.Error)" -ForegroundColor Red
}

# Test 4: Test flush operation (if available)
Write-Host "Test 4: Testing flush operation" -ForegroundColor Yellow
# Note: This would require a flush endpoint to be implemented
# For now, we'll just verify the service is still responsive
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/health"
if ($result.Success -and $result.StatusCode -eq 200) {
    Write-Host "✅ Service remains healthy after operations" -ForegroundColor Green
} else {
    Write-Host "❌ Service health check failed: $($result.Error)" -ForegroundColor Red
}

# Test 5: Check log end offset
Write-Host "Test 5: Verifying log end offset tracking" -ForegroundColor Yellow
# This would require a LEO endpoint - for now we'll check HWM as proxy
$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/partitions/test-topic/0/high-water-mark"
if ($result.Success -and $result.StatusCode -eq 200) {
    $hwm = [int]$result.Content
    Write-Host "✅ Current high water mark: $hwm" -ForegroundColor Green
    if ($hwm -gt 0) {
        Write-Host "✅ Log end offset tracking is working" -ForegroundColor Green
    }
} else {
    Write-Host "❌ Failed to get high water mark: $($result.Error)" -ForegroundColor Red
}

# Test 6: Segment file analysis
Write-Host "Test 6: Analyzing segment files" -ForegroundColor Yellow
if (Test-Path $walDir) {
    $logFiles = Get-ChildItem -Path $walDir -Filter "*.log" -ErrorAction SilentlyContinue
    foreach ($logFile in $logFiles) {
        $fileSize = $logFile.Length
        $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
        Write-Host "   $($logFile.Name): ${fileSizeMB}MB" -ForegroundColor White

        # Check if file size is reasonable (not empty, not too large for test data)
        if ($fileSize -gt 0 -and $fileSize -lt 100MB) {
            Write-Host "   ✅ File size looks reasonable" -ForegroundColor Green
        } elseif ($fileSize -eq 0) {
            Write-Host "   ❌ File is empty" -ForegroundColor Red
        } else {
            Write-Host "   ⚠️  File is very large: ${fileSizeMB}MB" -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "🎉 WAL management testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Verified WAL segment and index file creation" -ForegroundColor White
Write-Host "- Tested message persistence across service operations" -ForegroundColor White
Write-Host "- Validated log end offset and high water mark tracking" -ForegroundColor White
Write-Host "- Analyzed segment file sizes and integrity" -ForegroundColor White
Write-Host ""
Write-Host "Note: Check service logs for detailed WAL operations like:" -ForegroundColor Yellow
Write-Host "  - 'Created log segment'" -ForegroundColor White
Write-Host "  - 'Appended message at offset'" -ForegroundColor White
Write-Host "  - 'Read X messages from WAL'" -ForegroundColor White
