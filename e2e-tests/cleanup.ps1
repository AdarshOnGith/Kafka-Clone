# Cleanup Test Environment
# Removes test data and resets services

Write-Host "🧹 Cleaning up test environment..." -ForegroundColor Green

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

# Step 1: Stop running services
Write-Host "Step 1: Stopping running services" -ForegroundColor Yellow

# Check if services are running and stop them
$services = @(
    @{ Name = "Metadata Service"; ProcessName = "dmq-metadata-service"; Port = 8080 },
    @{ Name = "Storage Service"; ProcessName = "dmq-storage-service"; Port = 8082 }
)

$stoppedServices = 0
foreach ($service in $services) {
    Write-Host "  Checking $($service.Name)..." -ForegroundColor White

    # Check if port is in use
    $portCheck = Get-NetTCPConnection -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($portCheck) {
        Write-Host "    Port $($service.Port) is in use, attempting to stop service..." -ForegroundColor Yellow

        # Try graceful shutdown via API
        $result = Invoke-HttpRequest -Uri "http://localhost:$($service.Port)/api/v1/$($service.ProcessName.Split('-')[1])/shutdown" -Method "POST"
        if ($result.Success -and $result.StatusCode -eq 200) {
            Write-Host "    ✅ $($service.Name) shutdown gracefully" -ForegroundColor Green
            $stoppedServices++
        } else {
            Write-Host "    ⚠️  Graceful shutdown failed, killing process..." -ForegroundColor Yellow

            # Force kill process
            $processes = Get-Process -Name "*$($service.ProcessName)*" -ErrorAction SilentlyContinue
            if ($processes) {
                $processes | Stop-Process -Force
                Write-Host "    ✅ $($service.Name) process killed" -ForegroundColor Green
                $stoppedServices++
            } else {
                Write-Host "    ❌ No $($service.Name) process found" -ForegroundColor Red
            }
        }
    } else {
        Write-Host "    ✅ $($service.Name) not running" -ForegroundColor Green
        $stoppedServices++
    }
}

Write-Host "✅ Stopped $stoppedServices/$($services.Count) services" -ForegroundColor Green

# Step 2: Clean up test topics
Write-Host "Step 2: Cleaning up test topics" -ForegroundColor Yellow

# List of test topics to clean up
$testTopics = @(
    "test-topic",
    "producer-test-topic",
    "consumer-test-topic",
    "wal-test-topic",
    "partition-test-topic",
    "topic-mgmt-test",
    "broker-mgmt-test",
    "kraft-test-topic",
    "discovery-test-topic",
    "sync-test-topic",
    "version-test-topic",
    "replication-test-topic",
    "heartbeat-test-topic"
)

$deletedTopics = 0
foreach ($topic in $testTopics) {
    Write-Host "  Deleting topic: $topic" -ForegroundColor White

    $result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/$topic" -Method "DELETE"
    if ($result.Success -and $result.StatusCode -eq 200) {
        Write-Host "    ✅ Topic '$topic' deleted" -ForegroundColor Green
        $deletedTopics++
    } elseif ($result.StatusCode -eq 404) {
        Write-Host "    ℹ️  Topic '$topic' not found (already deleted)" -ForegroundColor Gray
        $deletedTopics++
    } else {
        Write-Host "    ❌ Failed to delete topic '$topic': $($result.Error)" -ForegroundColor Red
    }
}

Write-Host "✅ Cleaned up $deletedTopics/$($testTopics.Count) test topics" -ForegroundColor Green

# Step 3: Clean up test brokers
Write-Host "Step 3: Cleaning up test brokers" -ForegroundColor Yellow

# List of test broker IDs to clean up
$testBrokerIds = @(100, 101, 102, 201, 202, 203)

$deletedBrokers = 0
foreach ($brokerId in $testBrokerIds) {
    Write-Host "  Deleting broker: $brokerId" -ForegroundColor White

    $result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers/$brokerId" -Method "DELETE"
    if ($result.Success -and $result.StatusCode -eq 200) {
        Write-Host "    ✅ Broker $brokerId deleted" -ForegroundColor Green
        $deletedBrokers++
    } elseif ($result.StatusCode -eq 404) {
        Write-Host "    ℹ️  Broker $brokerId not found (already deleted)" -ForegroundColor Gray
        $deletedBrokers++
    } else {
        Write-Host "    ❌ Failed to delete broker $brokerId: $($result.Error)" -ForegroundColor Red
    }
}

Write-Host "✅ Cleaned up $deletedBrokers/$($testBrokerIds.Count) test brokers" -ForegroundColor Green

# Step 4: Clean up log files
Write-Host "Step 4: Cleaning up log files" -ForegroundColor Yellow

$logFiles = @(
    "dmq-metadata-service\logs\dmq-metadata.log",
    "dmq-storage-service\logs\dmq-storage.log",
    "e2e-tests\health-check-results.json",
    "e2e-tests\log-monitoring-results.json",
    "e2e-tests\metrics-collection-results.json",
    "e2e-tests\test-suite-results.json"
)

$cleanedLogs = 0
foreach ($logFile in $logFiles) {
    $fullPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\$logFile"
    if (Test-Path $fullPath) {
        try {
            Remove-Item $fullPath -Force
            Write-Host "    ✅ Cleaned up: $logFile" -ForegroundColor Green
            $cleanedLogs++
        } catch {
            Write-Host "    ❌ Failed to clean up: $logFile ($($_.Exception.Message))" -ForegroundColor Red
        }
    } else {
        Write-Host "    ℹ️  File not found: $logFile" -ForegroundColor Gray
        $cleanedLogs++
    }
}

Write-Host "✅ Cleaned up $cleanedLogs/$($logFiles.Count) log/result files" -ForegroundColor Green

# Step 5: Clean up temporary files
Write-Host "Step 5: Cleaning up temporary files" -ForegroundColor Yellow

$tempDirs = @(
    "dmq-metadata-service\target",
    "dmq-storage-service\target"
)

$cleanedTemp = 0
foreach ($tempDir in $tempDirs) {
    $fullPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\$tempDir"
    if (Test-Path $fullPath) {
        try {
            # Remove only temporary build artifacts, keep essential files
            Get-ChildItem $fullPath -Recurse -File | Where-Object {
                $_.Name -like "*.tmp" -or
                $_.Name -like "*.log" -or
                $_.Name -like "*test*.class"
            } | Remove-Item -Force

            Write-Host "    ✅ Cleaned temporary files in: $tempDir" -ForegroundColor Green
            $cleanedTemp++
        } catch {
            Write-Host "    ❌ Failed to clean temp files in: $tempDir ($($_.Exception.Message))" -ForegroundColor Red
        }
    } else {
        Write-Host "    ℹ️  Directory not found: $tempDir" -ForegroundColor Gray
        $cleanedTemp++
    }
}

Write-Host "✅ Cleaned up $cleanedTemp/$($tempDirs.Count) temporary directories" -ForegroundColor Green

# Step 6: Reset configuration files
Write-Host "Step 6: Resetting configuration files" -ForegroundColor Yellow

$configFiles = @(
    "e2e-tests\config\services.json"
)

$resetConfigs = 0
foreach ($configFile in $configFiles) {
    $fullPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\$configFile"
    if (Test-Path $fullPath) {
        try {
            # Reset to default configuration
            $defaultConfig = @{
                metadataService = @{
                    url = "http://localhost:9091"
                    endpoints = @{
                        health = "/api/v1/metadata/controller"
                        topics = "/api/v1/metadata/topics"
                        brokers = "/api/v1/metadata/brokers"
                        sync = "/api/v1/metadata/sync"
                        heartbeat = "/api/v1/metadata/storage-heartbeat"
                    }
                }
                storageService = @{
                    url = "http://localhost:8081"
                    endpoints = @{
                        health = "/api/v1/storage/health"
                        messages = "/api/v1/storage/messages"
                        consume = "/api/v1/storage/consume"
                        partitions = "/api/v1/storage/partitions"
                        wal = "/api/v1/storage/wal"
                    }
                }
            } | ConvertTo-Json -Depth 10

            $defaultConfig | Out-File -FilePath $fullPath -Encoding UTF8
            Write-Host "    ✅ Reset configuration: $configFile" -ForegroundColor Green
            $resetConfigs++
        } catch {
            Write-Host "    ❌ Failed to reset config: $configFile ($($_.Exception.Message))" -ForegroundColor Red
        }
    } else {
        Write-Host "    ℹ️  Config file not found: $configFile" -ForegroundColor Gray
        $resetConfigs++
    }
}

Write-Host "✅ Reset $resetConfigs/$($configFiles.Count) configuration files" -ForegroundColor Green

# Step 7: Final verification
Write-Host "Step 7: Final cleanup verification" -ForegroundColor Yellow

$verificationPassed = 0
$verificationTotal = 3

# Check if services are stopped
$runningServices = 0
foreach ($service in $services) {
    $portCheck = Get-NetTCPConnection -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($portCheck) {
        $runningServices++
    }
}

if ($runningServices -eq 0) {
    Write-Host "✅ All services stopped" -ForegroundColor Green
    $verificationPassed++
} else {
    Write-Host "⚠️  $runningServices services still running" -ForegroundColor Yellow
}

# Check if test topics are cleaned up
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics"
$remainingTopics = 0
if ($result.Success -and $result.StatusCode -eq 200) {
    $topics = $result.Content | ConvertFrom-Json
    $remainingTopics = ($topics | Where-Object { $testTopics -contains $_.name }).Count
}

if ($remainingTopics -eq 0) {
    Write-Host "✅ All test topics cleaned up" -ForegroundColor Green
    $verificationPassed++
} else {
    Write-Host "⚠️  $remainingTopics test topics still exist" -ForegroundColor Yellow
}

# Check if test brokers are cleaned up
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/brokers"
$remainingBrokers = 0
if ($result.Success -and $result.StatusCode -eq 200) {
    $brokers = $result.Content | ConvertFrom-Json
    $remainingBrokers = ($brokers | Where-Object { $testBrokerIds -contains $_.id }).Count
}

if ($remainingBrokers -eq 0) {
    Write-Host "✅ All test brokers cleaned up" -ForegroundColor Green
    $verificationPassed++
} else {
    Write-Host "⚠️  $remainingBrokers test brokers still exist" -ForegroundColor Yellow
}

Write-Host "✅ Cleanup verification: $verificationPassed/$verificationTotal checks passed" -ForegroundColor Green

# Step 8: Summary
Write-Host "Step 8: Cleanup summary" -ForegroundColor Yellow

Write-Host ""
Write-Host "🧹 Test Environment Cleanup Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Cleanup Summary:" -ForegroundColor Cyan
Write-Host "✅ Services Stopped: $stoppedServices/$($services.Count)" -ForegroundColor White
Write-Host "✅ Topics Deleted: $deletedTopics/$($testTopics.Count)" -ForegroundColor White
Write-Host "✅ Brokers Removed: $deletedBrokers/$($testBrokerIds.Count)" -ForegroundColor White
Write-Host "✅ Logs Cleaned: $cleanedLogs/$($logFiles.Count)" -ForegroundColor White
Write-Host "✅ Temp Files: $cleanedTemp/$($tempDirs.Count)" -ForegroundColor White
Write-Host "✅ Config Reset: $resetConfigs/$($configFiles.Count)" -ForegroundColor White
Write-Host "✅ Verification: $verificationPassed/$verificationTotal" -ForegroundColor White
Write-Host ""

$overallSuccess = ($stoppedServices -eq $services.Count) -and
                  ($deletedTopics -eq $testTopics.Count) -and
                  ($deletedBrokers -eq $testBrokerIds.Count) -and
                  ($verificationPassed -eq $verificationTotal)

if ($overallSuccess) {
    Write-Host "🎉 Cleanup successful! Environment is ready for next test run." -ForegroundColor Green
    exit 0
} else {
    Write-Host "⚠️  Cleanup completed with some issues. Manual cleanup may be needed." -ForegroundColor Yellow
    exit 1
}
