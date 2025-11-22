# Cleanup Test Data
# This script cleans up test data and resets the environment

Write-Host "🧹 Cleaning up test data..." -ForegroundColor Green

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

# Stop Java processes
Write-Host "Stopping Java processes..." -ForegroundColor Yellow
$javaProcesses = Get-Process java -ErrorAction SilentlyContinue
if ($javaProcesses) {
    $javaProcesses | Stop-Process -Force
    Write-Host "✅ Stopped $($javaProcesses.Count) Java processes" -ForegroundColor Green
} else {
    Write-Host "ℹ️  No Java processes found" -ForegroundColor Blue
}

# Clean data directories
Write-Host "Cleaning data directories..." -ForegroundColor Yellow

$storageDataDir = "..\..\dmq-storage-service\data"
if (Test-Path $storageDataDir) {
    Remove-Item -Recurse -Force $storageDataDir\*
    Write-Host "✅ Cleaned storage service data directory" -ForegroundColor Green
}

$metadataDataDir = "..\..\dmq-metadata-service\data"
if (Test-Path $metadataDataDir) {
    Remove-Item -Recurse -Force $metadataDataDir\*
    Write-Host "✅ Cleaned metadata service data directory" -ForegroundColor Green
}

# Reset config files
Write-Host "Resetting configuration files..." -ForegroundColor Yellow

$configDir = "..\..\config"
if (Test-Path "$configDir\services.json") {
    # Backup original if it exists
    if (Test-Path "$configDir\services.json.backup") {
        Copy-Item "$configDir\services.json.backup" "$configDir\services.json" -Force
        Write-Host "✅ Restored original services.json" -ForegroundColor Green
    } else {
        Write-Host "ℹ️  No backup found for services.json" -ForegroundColor Blue
    }
}

# Clean Maven artifacts
Write-Host "Cleaning Maven artifacts..." -ForegroundColor Yellow
Push-Location "..\..\dmq-storage-service"
& mvn clean -q
Pop-Location

Push-Location "..\..\dmq-metadata-service"
& mvn clean -q
Pop-Location

Write-Host "✅ Maven artifacts cleaned" -ForegroundColor Green

# Check for remaining processes on ports
Write-Host "Checking for processes on test ports..." -ForegroundColor Yellow
$ports = @(8080, 8082)
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        Write-Host "⚠️  Port $port still has connections:" -ForegroundColor Yellow
        $connections | ForEach-Object {
            Write-Host "   PID: $($_.OwningProcess), State: $($_.State)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "✅ Port $port is free" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "🎉 Cleanup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Environment has been reset. You can now:" -ForegroundColor Cyan
Write-Host "1. Run setup scripts again: .\01-setup-services.ps1"
Write-Host "2. Start fresh testing: .\02-start-services.ps1"
