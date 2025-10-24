# Run Functional Tests Sequentially
# Runs functional tests one by one (services assumed to be running)

Write-Host "🚀 Starting Sequential Functional Test Suite..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor White

$testSuiteStart = Get-Date
$testResults = @()
$failedTests = @()

# Test configuration
$testTimeout = 120  # 2 minutes per test
$baseDir = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\e2e-tests"

# Function to run a test script sequentially
function Run-TestScriptSequential {
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
        $process = Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"$scriptPath`"" -NoNewWindow -PassThru -RedirectStandardOutput "temp_output.txt" -RedirectStandardError "temp_error.txt"

        # Wait for the process with timeout
        $process | Wait-Process -Timeout $testTimeout -ErrorAction SilentlyContinue

        if (!$process.HasExited) {
            # Process timed out, kill it
            $process.Kill()
            $testResult.Success = $false
            $testResult.Error = "Test timed out after $testTimeout seconds"
        } else {
            $testResult.ExitCode = $process.ExitCode
            $testResult.Success = $process.ExitCode -eq 0
        }

        if (Test-Path "temp_output.txt") {
            $testResult.Output = Get-Content "temp_output.txt" -Raw
            Remove-Item "temp_output.txt" -ErrorAction SilentlyContinue
        }

        if (Test-Path "temp_error.txt") {
            $errorContent = Get-Content "temp_error.txt" -Raw
            if ($errorContent) {
                $testResult.Error = $errorContent
            }
            Remove-Item "temp_error.txt" -ErrorAction SilentlyContinue
        }

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

# Functional test definitions
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

# Run all functional tests sequentially
Write-Host "Sequential Functional Test Suite Configuration:" -ForegroundColor Yellow
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

    $result = Run-TestScriptSequential -scriptPath $test.Path -testName $test.Name -description $test.Description
    $testResults += $result

    if ($result.Success) {
        $passedTests++
    }
}

# Final summary
$suiteEnd = Get-Date
$suiteDuration = [math]::Round(($suiteEnd - $testSuiteStart).TotalSeconds, 2)

Write-Host ""
Write-Host "==========================================" -ForegroundColor White
Write-Host "🏁 Sequential Functional Test Suite Complete!" -ForegroundColor Green
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

# Export detailed results (simplified)
$exportPath = "$baseDir\sequential-test-suite-results.json"
$simpleResults = $testResults | Select-Object Name, Description, Success, Duration, Error | ConvertTo-Json -Depth 3

$simpleResults | Out-File -FilePath $exportPath -Encoding UTF8
Write-Host "Detailed results exported to: $exportPath" -ForegroundColor White

# Final status
Write-Host ""
if ($passedTests -eq $testScripts.Count) {
    Write-Host "🎉 ALL FUNCTIONAL TESTS PASSED! System is fully functional." -ForegroundColor Green
    return 0
} elseif ($passedTests -gt ($testScripts.Count * 0.8)) {
    Write-Host "⚠️  MOST TESTS PASSED. System is mostly functional but has some issues." -ForegroundColor Yellow
    return 1
} else {
    Write-Host "❌ MANY TESTS FAILED. System requires attention." -ForegroundColor Red
    return 2
}