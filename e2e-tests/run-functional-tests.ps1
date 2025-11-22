# Run Functional Tests Only
# Orchestrates execution of functional tests (services assumed to be running)

Write-Host "🚀 Starting Functional Test Suite..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor White

$testSuiteStart = Get-Date
$testResults = @()
$failedTests = @()

# Test configuration
$testTimeout = 300  # 5 minutes per test
$baseDir = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\e2e-tests"

# Function to run a test script
function Run-TestScript {
    param(
        [string]$scriptPath,
        [string]$testName,
        [string]$description
    )

    Write-Host ""
    Write-Host "Running: $testName" -ForegroundColor Cyan
    Write-Host "Description: $description" -ForegroundColor White
    Write-Host "Script: $scriptPath" -ForegroundColor Gray

    $testStart = Get-Date
    $testResult = @{
        Name = $testName
        Description = $description
        Script = $scriptPath
        StartTime = $testStart
        EndTime = $null
        Duration = $null
        ExitCode = $null
        Success = $false
        Output = $null
        Error = $null
    }

    try {
        # Run the test script with timeout
        $job = Start-Job -ScriptBlock {
            param($path)
            & $path
        } -ArgumentList $scriptPath

        $job | Wait-Job -Timeout $testTimeout | Out-Null

        if ($job.State -eq "Completed") {
            $testResult.ExitCode = $job.ChildJobs[0].ExitCode
            $testResult.Success = $testResult.ExitCode -eq 0
            $testResult.Output = $job.ChildJobs[0].Output
        } else {
            # Timeout occurred
            $job | Stop-Job
            $testResult.Success = $false
            $testResult.Error = "Test timed out after $testTimeout seconds"
        }

        $job | Remove-Job

    } catch {
        $testResult.Success = $false
        $testResult.Error = $_.Exception.Message
    }

    $testResult.EndTime = Get-Date
    $testResult.Duration = [math]::Round(($testResult.EndTime - $testStart).TotalSeconds, 2)

    if ($testResult.Success) {
        Write-Host "✅ PASSED ($($testResult.Duration)s)" -ForegroundColor Green
    } else {
        Write-Host "❌ FAILED ($($testResult.Duration)s)" -ForegroundColor Red
        if ($testResult.Error) {
            Write-Host "   Error: $($testResult.Error)" -ForegroundColor Red
        }
        if ($testResult.ExitCode -ne $null) {
            Write-Host "   Exit Code: $($testResult.ExitCode)" -ForegroundColor Red
        }
        $failedTests += $testResult
    }

    return $testResult
}

# Functional test definitions (skip setup tests)
$testScripts = @(
    # Storage service tests
    @{
        Path = "$baseDir\scripts\storage\producer-flow-test.ps1"
        Name = "Producer Flow Test"
        Description = "Validate message production with offset assignment"
    },
    @{
        Path = "$baseDir\scripts\storage\consumer-flow-test-simple.ps1"
        Name = "Consumer Flow Test"
        Description = "Validate message consumption with offset-based retrieval"
    },
    @{
        Path = "$baseDir\scripts\storage\wal-management-test.ps1"
        Name = "WAL Management Test"
        Description = "Test durable log operations and file management"
    },
    @{
        Path = "$baseDir\scripts\storage\partition-status-test.ps1"
        Name = "Partition Status Test"
        Description = "Test partition metrics and status reporting"
    },

    # Metadata service tests
    @{
        Path = "$baseDir\scripts\metadata\topic-management-test.ps1"
        Name = "Topic Management Test"
        Description = "Test topic CRUD operations and validation"
    },
    @{
        Path = "$baseDir\scripts\metadata\broker-management-test.ps1"
        Name = "Broker Management Test"
        Description = "Test broker registration and tracking"
    },
    @{
        Path = "$baseDir\scripts\metadata\kraft-consensus-test.ps1"
        Name = "KRaft Consensus Test"
        Description = "Test distributed consensus and leader election"
    },
    @{
        Path = "$baseDir\scripts\metadata\service-discovery-test.ps1"
        Name = "Service Discovery Test"
        Description = "Test centralized configuration and URL resolution"
    },

    # Integration tests
    @{
        Path = "$baseDir\scripts\integration\producer-e2e-test.ps1"
        Name = "Producer E2E Test"
        Description = "End-to-end producer flow validation"
    },
    @{
        Path = "$baseDir\scripts\integration\consumer-e2e-test.ps1"
        Name = "Consumer E2E Test"
        Description = "End-to-end consumer flow validation"
    },
    @{
        Path = "$baseDir\scripts\integration\metadata-sync-test.ps1"
        Name = "Metadata Sync Test"
        Description = "Test bidirectional metadata sync between services"
    },
    @{
        Path = "$baseDir\scripts\integration\heartbeat-flow-test.ps1"
        Name = "Heartbeat Flow Test"
        Description = "Test periodic heartbeat mechanism between services"
    },
    @{
        Path = "$baseDir\scripts\integration\replication-flow-test.ps1"
        Name = "Replication Flow Test"
        Description = "Test message replication across multiple brokers"
    }
)

# Run all functional tests
Write-Host "Functional Test Suite Configuration:" -ForegroundColor Yellow
Write-Host "  Total Tests: $($testScripts.Count)" -ForegroundColor White
Write-Host "  Test Timeout: $testTimeout seconds" -ForegroundColor White
Write-Host "  Base Directory: $baseDir" -ForegroundColor White
Write-Host ""

$passedTests = 0
$testCount = 0

foreach ($test in $testScripts) {
    $testCount++
    Write-Host "[$testCount/$($testScripts.Count)]" -ForegroundColor Gray -NoNewline

    if (!(Test-Path $test.Path)) {
        Write-Host ""
        Write-Host "⚠️  Test script not found: $($test.Path)" -ForegroundColor Yellow
        $failedTests += @{
            Name = $test.Name
            Description = $test.Description
            Script = $test.Path
            Success = $false
            Error = "Script file not found"
        }
        continue
    }

    $result = Run-TestScript -scriptPath $test.Path -testName $test.Name -description $test.Description
    $testResults += $result

    if ($result.Success) {
        $passedTests++
    }
}

# Run monitoring tests (non-blocking)
Write-Host ""
Write-Host "Running Monitoring Scripts..." -ForegroundColor Cyan

$monitoringScripts = @(
    @{
        Path = "$baseDir\scripts\monitoring\health-check.ps1"
        Name = "Health Check Monitor"
        Description = "Monitor service health and availability"
    },
    @{
        Path = "$baseDir\scripts\monitoring\log-monitor.ps1"
        Name = "Log Monitor"
        Description = "Monitor service logs for errors and events"
    },
    @{
        Path = "$baseDir\scripts\monitoring\metrics-collector.ps1"
        Name = "Metrics Collector"
        Description = "Collect and analyze performance metrics"
    }
)

foreach ($monitor in $monitoringScripts) {
    if (Test-Path $monitor.Path) {
        Write-Host "  Starting $($monitor.Name)..." -ForegroundColor White
        # Run monitoring scripts in background
        Start-Job -ScriptBlock {
            param($path)
            & $path
        } -ArgumentList $monitor.Path | Out-Null
    } else {
        Write-Host "  ⚠️  Monitoring script not found: $($monitor.Path)" -ForegroundColor Yellow
    }
}

# Final summary
$suiteEnd = Get-Date
$suiteDuration = [math]::Round(($suiteEnd - $testSuiteStart).TotalSeconds, 2)

Write-Host ""
Write-Host "==========================================" -ForegroundColor White
Write-Host "🏁 Functional Test Suite Complete!" -ForegroundColor Green
Write-Host ""

Write-Host "Results Summary:" -ForegroundColor Cyan
Write-Host "  Total Tests: $($testScripts.Count)" -ForegroundColor White
Write-Host "  Passed: $passedTests" -ForegroundColor Green
Write-Host "  Failed: $($testScripts.Count - $passedTests)" -ForegroundColor $(if ($passedTests -eq $testScripts.Count) { "Green" } else { "Red" })
Write-Host "  Success Rate: $([math]::Round(($passedTests / $testScripts.Count) * 100, 2))%" -ForegroundColor $(if ($passedTests -eq $testScripts.Count) { "Green" } elseif ($passedTests -gt ($testScripts.Count * 0.8)) { "Yellow" } else { "Red" })
Write-Host "  Total Duration: $($suiteDuration)s" -ForegroundColor White
Write-Host "  Average Test Time: $([math]::Round($suiteDuration / $testScripts.Count, 2))s" -ForegroundColor White

# Detailed failure report
if ($failedTests.Count -gt 0) {
    Write-Host ""
    Write-Host "Failed Tests:" -ForegroundColor Red
    foreach ($failure in $failedTests) {
        Write-Host "  ❌ $($failure.Name)" -ForegroundColor Red
        Write-Host "    Description: $($failure.Description)" -ForegroundColor White
        Write-Host "    Script: $($failure.Script)" -ForegroundColor Gray
        if ($failure.Error) {
            Write-Host "    Error: $($failure.Error)" -ForegroundColor Red
        }
        if ($failure.ExitCode -ne $null) {
            Write-Host "    Exit Code: $($failure.ExitCode)" -ForegroundColor Red
        }
        Write-Host ""
    }
}

# Export detailed results
$exportPath = "$baseDir\functional-test-suite-results.json"
$exportData = @{
    suiteStart = $testSuiteStart.ToString('yyyy-MM-dd HH:mm:ss')
    suiteEnd = $suiteEnd.ToString('yyyy-MM-dd HH:mm:ss')
    duration = $suiteDuration
    totalTests = $testScripts.Count
    passedTests = $passedTests
    failedTests = $testScripts.Count - $passedTests
    successRate = [math]::Round(($passedTests / $testScripts.Count) * 100, 2)
    testResults = $testResults
    failedTestDetails = $failedTests
} | ConvertTo-Json -Depth 10

$exportData | Out-File -FilePath $exportPath -Encoding UTF8
Write-Host "Detailed results exported to: $exportPath" -ForegroundColor White

# Final status
Write-Host ""
if ($passedTests -eq $testScripts.Count) {
    Write-Host "🎉 ALL FUNCTIONAL TESTS PASSED! System is fully functional." -ForegroundColor Green
    exit 0
} elseif ($passedTests -gt ($testScripts.Count * 0.8)) {
    Write-Host "⚠️  MOST TESTS PASSED. System is mostly functional but has some issues." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "❌ MANY TESTS FAILED. System requires attention." -ForegroundColor Red
    exit 2
}