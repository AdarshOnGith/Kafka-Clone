# Log Monitor
# Monitors service logs for errors and important events

Write-Host "📋 Monitoring Service Logs..." -ForegroundColor Green

# Configuration
$logFiles = @(
    @{ Service = "Metadata Service"; Path = "dmq-metadata-service\logs\dmq-metadata.log" },
    @{ Service = "Storage Service"; Path = "dmq-storage-service\logs\dmq-storage.log" }
)

$logPatterns = @{
    ERROR = @('ERROR', 'Exception', 'Failed', 'Error')
    WARN = @('WARN', 'Warning')
    INFO = @('INFO', 'Started', 'Connected', 'Initialized')
}

$monitorDuration = 60  # seconds
$checkInterval = 5     # seconds

Write-Host "Starting log monitoring for $monitorDuration seconds..." -ForegroundColor Yellow
Write-Host "Checking logs every $checkInterval seconds" -ForegroundColor White
Write-Host ""

# Initialize tracking
$logStats = @{}
$startTime = Get-Date
$lastCheck = $startTime

foreach ($logFile in $logFiles) {
    $logStats[$logFile.Service] = @{
        TotalLines = 0
        Errors = 0
        Warnings = 0
        Info = 0
        LastError = $null
        LastWarning = $null
        LastInfo = $null
    }
}

# Function to analyze log file
function Analyze-LogFile {
    param(
        [string]$filePath,
        [string]$serviceName,
        [datetime]$since
    )

    if (!(Test-Path $filePath)) {
        Write-Host "  ⚠️  Log file not found: $filePath" -ForegroundColor Yellow
        return
    }

    try {
        # Get new log lines since last check
        $newLines = Get-Content $filePath -Tail 1000 | Where-Object {
            # Simple timestamp parsing - adjust based on your log format
            $lineTime = $null
            if ($_ -match '(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})') {
                $lineTime = [datetime]::Parse($matches[1])
            }
            $lineTime -and $lineTime -gt $since
        }

        $stats = $logStats[$serviceName]

        foreach ($line in $newLines) {
            $stats.TotalLines++

            # Check for error patterns
            foreach ($errorPattern in $logPatterns.ERROR) {
                if ($line -match $errorPattern) {
                    $stats.Errors++
                    $stats.LastError = @{
                        Timestamp = Get-Date
                        Message = $line
                    }
                    break
                }
            }

            # Check for warning patterns
            foreach ($warnPattern in $logPatterns.WARN) {
                if ($line -match $warnPattern) {
                    $stats.Warnings++
                    $stats.LastWarning = @{
                        Timestamp = Get-Date
                        Message = $line
                    }
                    break
                }
            }

            # Check for info patterns
            foreach ($infoPattern in $logPatterns.INFO) {
                if ($line -match $infoPattern) {
                    $stats.Info++
                    $stats.LastInfo = @{
                        Timestamp = Get-Date
                        Message = $line
                    }
                    break
                }
            }
        }

        Write-Host "  📄 $serviceName log analyzed: $($newLines.Count) new lines" -ForegroundColor White

    } catch {
        Write-Host "  ❌ Error analyzing $serviceName log: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Monitoring loop
$iteration = 0
while ((Get-Date) - $startTime).TotalSeconds -lt $monitorDuration) {
    $iteration++
    $currentTime = Get-Date

    Write-Host "Check #$iteration at $($currentTime.ToString('HH:mm:ss'))" -ForegroundColor Cyan

    foreach ($logFile in $logFiles) {
        Analyze-LogFile -filePath $logFile.Path -serviceName $logFile.Service -since $lastCheck
    }

    $lastCheck = $currentTime

    # Display current stats
    Write-Host "  Current Statistics:" -ForegroundColor White
    foreach ($service in $logStats.Keys) {
        $stats = $logStats[$service]
        Write-Host "    $service - Lines: $($stats.TotalLines), Errors: $($stats.Errors), Warnings: $($stats.Warnings), Info: $($stats.Info)" -ForegroundColor White
    }

    # Wait for next check
    if ((Get-Date) - $startTime).TotalSeconds -lt $monitorDuration) {
        Write-Host "  Waiting $checkInterval seconds..." -ForegroundColor Gray
        Start-Sleep -Seconds $checkInterval
    }
}

# Final summary
Write-Host ""
Write-Host "📊 Log Monitoring Summary:" -ForegroundColor Cyan
Write-Host "Monitoring Duration: $([math]::Round(((Get-Date) - $startTime).TotalSeconds, 2)) seconds" -ForegroundColor White
Write-Host "Total Checks: $iteration" -ForegroundColor White
Write-Host ""

$totalErrors = 0
$totalWarnings = 0
$totalInfo = 0

foreach ($service in $logStats.Keys) {
    $stats = $logStats[$service]
    $totalErrors += $stats.Errors
    $totalWarnings += $stats.Warnings
    $totalInfo += $stats.Info

    Write-Host "$service Statistics:" -ForegroundColor Yellow
    Write-Host "  Total Lines Processed: $($stats.TotalLines)" -ForegroundColor White
    Write-Host "  Errors Found: $($stats.Errors)" -ForegroundColor $(if ($stats.Errors -gt 0) { "Red" } else { "Green" })
    Write-Host "  Warnings Found: $($stats.Warnings)" -ForegroundColor $(if ($stats.Warnings -gt 0) { "Yellow" } else { "Green" })
    Write-Host "  Info Messages: $($stats.Info)" -ForegroundColor Green

    if ($stats.LastError) {
        Write-Host "  Last Error: $($stats.LastError.Message)" -ForegroundColor Red
    }
    if ($stats.LastWarning) {
        Write-Host "  Last Warning: $($stats.LastWarning.Message)" -ForegroundColor Yellow
    }
    Write-Host ""
}

# Overall assessment
Write-Host "Overall Assessment:" -ForegroundColor Cyan
if ($totalErrors -gt 0) {
    Write-Host "❌ Issues detected: $totalErrors errors found" -ForegroundColor Red
    Write-Host "   Review the error details above and check service logs" -ForegroundColor Red
} elseif ($totalWarnings -gt 0) {
    Write-Host "⚠️  Warnings detected: $totalWarnings warnings found" -ForegroundColor Yellow
    Write-Host "   Monitor the warnings but services appear operational" -ForegroundColor Yellow
} else {
    Write-Host "✅ No errors or warnings detected during monitoring period" -ForegroundColor Green
    Write-Host "   Services appear to be running cleanly" -ForegroundColor Green
}

# Export detailed results
$exportPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\e2e-tests\log-monitoring-results.json"
$exportData = @{
    timestamp = $startTime.ToString('yyyy-MM-dd HH:mm:ss')
    duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 2)
    totalChecks = $iteration
    summary = @{
        totalErrors = $totalErrors
        totalWarnings = $totalWarnings
        totalInfo = $totalInfo
    }
    serviceStats = $logStats
} | ConvertTo-Json -Depth 10

$exportData | Out-File -FilePath $exportPath -Encoding UTF8
Write-Host ""
Write-Host "Detailed results exported to: $exportPath" -ForegroundColor White

Write-Host ""
Write-Host "🎉 Log monitoring complete!" -ForegroundColor Green
