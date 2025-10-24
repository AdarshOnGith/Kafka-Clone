# Start Services
# This script starts both metadata and storage services

Write-Host "🚀 Starting DMQ Services..." -ForegroundColor Green

# Function to check if port is available
function Test-Port {
    param([int]$Port)
    $connection = New-Object System.Net.Sockets.TcpClient
    try {
        $connection.Connect("localhost", $Port)
        $connection.Close()
        return $true
    } catch {
        return $false
    }
}

# Check if ports are available
Write-Host "Checking port availability..." -ForegroundColor Yellow
if (Test-Port 9091) {
    Write-Host "❌ Port 9091 (metadata) is already in use" -ForegroundColor Red
    exit 1
}
if (Test-Port 8081) {
    Write-Host "❌ Port 8081 (storage) is already in use" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Ports 9091 and 8081 are available" -ForegroundColor Green

# Start metadata service in background
Write-Host "Starting metadata service on port 9091..." -ForegroundColor Yellow
$metadataJob = Start-Job -ScriptBlock {
    Set-Location "..\..\dmq-metadata-service"
    & mvn spring-boot:run
} -Name "MetadataService"

Start-Sleep -Seconds 2

# Start storage service in background
Write-Host "Starting storage service on port 8081..." -ForegroundColor Yellow
$storageJob = Start-Job -ScriptBlock {
    Set-Location "..\..\dmq-storage-service"
    & mvn spring-boot:run
} -Name "StorageService"

Start-Sleep -Seconds 5

# Wait for services to start
Write-Host "Waiting for services to initialize..." -ForegroundColor Yellow
$maxRetries = 30
$retryCount = 0

do {
    $metadataHealthy = $false
    $storageHealthy = $false

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9091/api/v1/metadata/controller" -TimeoutSec 5
        if ($response.StatusCode -eq 200) { $metadataHealthy = $true }
    } catch { }

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/storage/health" -TimeoutSec 5
        if ($response.StatusCode -eq 200) { $storageHealthy = $true }
    } catch { }

    if ($metadataHealthy -and $storageHealthy) {
        Write-Host "✅ Both services are healthy!" -ForegroundColor Green
        break
    }

    Write-Host "Waiting... Metadata: $(if ($metadataHealthy) {'✅'} else {'⏳'}), Storage: $(if ($storageHealthy) {'✅'} else {'⏳'})" -ForegroundColor Yellow
    Start-Sleep -Seconds 2
    $retryCount++
} while ($retryCount -lt $maxRetries)

if ($retryCount -eq $maxRetries) {
    Write-Host "❌ Services failed to start within timeout" -ForegroundColor Red
    Write-Host "Metadata Job Status:" $metadataJob.State -ForegroundColor Red
    Write-Host "Storage Job Status:" $storageJob.State -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🎉 Services started successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Cyan
Write-Host "  Metadata: http://localhost:9091" -ForegroundColor White
Write-Host "  Storage:  http://localhost:8081" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Run 03-init-test-data.ps1 to initialize brokers and topics"
Write-Host "2. Run individual flow tests from the respective directories"

# Keep jobs running
Write-Host ""
Write-Host "Press Ctrl+C to stop services..." -ForegroundColor Yellow
Wait-Job -Job $metadataJob, $storageJob
