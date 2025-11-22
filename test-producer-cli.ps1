# Producer CLI Test Script
# Tests all producer CLI functionality

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Producer CLI Test Suite" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$cliJar = "dmq-client\target\mycli.jar"
$testsPassed = 0
$testsFailed = 0

# Check if JAR exists
if (-not (Test-Path $cliJar)) {
    Write-Host "ERROR: CLI JAR not found at $cliJar" -ForegroundColor Red
    Write-Host "Run: mvn clean install -DskipTests -pl dmq-client -am" -ForegroundColor Yellow
    exit 1
}

Write-Host "Using CLI JAR: $cliJar" -ForegroundColor Gray
Write-Host ""

# Helper function to run CLI command
function Test-CliCommand {
    param(
        [string]$TestName,
        [string[]]$Arguments,
        [bool]$ShouldSucceed = $true
    )
    
    Write-Host "[$TestName]" -ForegroundColor Yellow -NoNewline
    Write-Host " Running: java -jar $cliJar $($Arguments -join ' ')" -ForegroundColor Gray
    
    $output = & java -jar $cliJar @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    
    if ($ShouldSucceed) {
        if ($exitCode -eq 0) {
            Write-Host "  ??? PASSED" -ForegroundColor Green
            $script:testsPassed++
            return $true
        } else {
            Write-Host "  ??? FAILED (exit code: $exitCode)" -ForegroundColor Red
            Write-Host "  Output: $output" -ForegroundColor Red
            $script:testsFailed++
            return $false
        }
    } else {
        if ($exitCode -ne 0) {
            Write-Host "  ??? PASSED (expected failure)" -ForegroundColor Green
            $script:testsPassed++
            return $true
        } else {
            Write-Host "  ??? FAILED (should have failed but succeeded)" -ForegroundColor Red
            $script:testsFailed++
            return $false
        }
    }
}

# Test 1: CLI Help
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 1: CLI Help & Info" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Test-CliCommand "Help Command" @("--help")
Test-CliCommand "Version Command" @("--version")
Test-CliCommand "No Arguments" @() -ShouldSucceed $false

Write-Host ""

# Test 2: Topic Creation
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 2: Topic Creation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Generate unique topic name
$timestamp = (Get-Date).ToString("MMddHHmmss")
$testTopic = "cli-test-topic-$timestamp-p5-rf2"

Write-Host "[Info] Creating test topic: $testTopic" -ForegroundColor Yellow

# Create topic first
$createOutput = & java -jar $cliJar create-topic --name $testTopic --partitions 5 --replication-factor 2 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "??? Topic created successfully" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "??? Topic creation failed: $createOutput" -ForegroundColor Red
    Write-Host "Continuing with tests assuming topic exists..." -ForegroundColor Yellow
    $testsFailed++
}

Write-Host ""

# Test 3: Message Production
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 3: Message Production" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Basic produce
Test-CliCommand "Produce Simple Message" @("produce", "--topic", $testTopic, "--key", "key1", "--value", "Hello from CLI!")

# Produce to specific partition
Test-CliCommand "Produce to Partition 0" @("produce", "--topic", $testTopic, "--key", "key2", "--value", "Message for partition 0", "--partition", "0")

# Produce with different acks
Test-CliCommand "Produce with acks=0 (fire-and-forget)" @("produce", "--topic", $testTopic, "--key", "key3", "--value", "Fire and forget", "--acks", "0")
Test-CliCommand "Produce with acks=1 (leader only)" @("produce", "--topic", $testTopic, "--key", "key4", "--value", "Leader ack", "--acks", "1")
Test-CliCommand "Produce with acks=-1 (all replicas)" @("produce", "--topic", $testTopic, "--key", "key5", "--value", "All replicas ack", "--acks", "-1")

# Produce with special characters
Test-CliCommand "Produce with special chars" @("produce", "--topic", $testTopic, "--key", "special-key", "--value", "Special: !@#$%^&*()_+-=[]{}|;:',.<>?/~``")

# Produce long message
$longValue = "A" * 500
Test-CliCommand "Produce long message (500 chars)" @("produce", "--topic", $testTopic, "--key", "long", "--value", $longValue)

# Produce with JSON-like value
Test-CliCommand "Produce JSON-like value" @("produce", "--topic", $testTopic, "--key", "json-key", "--value", '{"name":"John","age":30,"city":"NYC"}')

# Negative tests
Test-CliCommand "Produce without topic" @("produce", "--key", "key", "--value", "value") -ShouldSucceed $false
Test-CliCommand "Produce without key (key is optional)" @("produce", "--topic", $testTopic, "--value", "value") -ShouldSucceed $true
Test-CliCommand "Produce without value" @("produce", "--topic", $testTopic, "--key", "key") -ShouldSucceed $false

Write-Host ""

# Test 4: Edge Cases
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 4: Edge Cases" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Empty strings
Test-CliCommand "Produce with empty key" @("produce", "--topic", $testTopic, "--key", "", "--value", "value with empty key")

# Unicode characters
Test-CliCommand "Produce with Unicode" @("produce", "--topic", $testTopic, "--key", "unicode", "--value", "Hello ?????? ???? caf??")

# Numbers as strings
Test-CliCommand "Produce with numeric values" @("produce", "--topic", $testTopic, "--key", "123", "--value", "456.789")

# Whitespace
Test-CliCommand "Produce with whitespace value" @("produce", "--topic", $testTopic, "--key", "spaces", "--value", "  leading and trailing spaces  ")

# Non-existent topic (should fail if topic doesn't exist)
Test-CliCommand "Produce to non-existent topic" @("produce", "--topic", "non-existent-topic-xyz", "--key", "key", "--value", "value") -ShouldSucceed $false

Write-Host ""

# Test 5: Batch Production
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 5: Batch Production" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "[Batch Test] Producing 10 messages rapidly..." -ForegroundColor Yellow
$batchSuccess = 0
$batchFailed = 0

for ($i = 1; $i -le 10; $i++) {
    $result = & java -jar $cliJar produce --topic $testTopic --key "batch-key-$i" --value "Batch message $i" 2>&1
    if ($LASTEXITCODE -eq 0) {
        $batchSuccess++
        Write-Host "  Message $i sent successfully" -ForegroundColor Gray
    } else {
        $batchFailed++
        Write-Host "  Message $i failed: $result" -ForegroundColor Red
    }
}

Write-Host "  Batch Results: $batchSuccess succeeded, $batchFailed failed" -ForegroundColor Cyan
if ($batchFailed -eq 0) {
    Write-Host "  ??? PASSED" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  ??? FAILED" -ForegroundColor Red
    $testsFailed++
}

Write-Host ""

# Test 6: Performance Test (Optional)
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 6: Performance Test (50 messages)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$startTime = Get-Date
$perfSuccess = 0
$perfFailed = 0

for ($i = 1; $i -le 50; $i++) {
    $result = & java -jar $cliJar produce --topic $testTopic --key "perf-$i" --value "Performance test message $i" 2>&1
    if ($LASTEXITCODE -eq 0) {
        $perfSuccess++
    } else {
        $perfFailed++
    }
    
    # Progress indicator
    if ($i % 10 -eq 0) {
        Write-Host "  Progress: $i/50 messages sent" -ForegroundColor Gray
    }
}

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds
$throughput = [math]::Round(50 / $duration, 2)

Write-Host "  Results:" -ForegroundColor Cyan
Write-Host "    Success: $perfSuccess" -ForegroundColor Green
Write-Host "    Failed:  $perfFailed" -ForegroundColor Red
Write-Host "    Duration: $([math]::Round($duration, 2))s" -ForegroundColor Cyan
Write-Host "    Throughput: $throughput messages/sec" -ForegroundColor Cyan

if ($perfFailed -eq 0) {
    Write-Host "  ??? PASSED" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  ??? FAILED" -ForegroundColor Red
    $testsFailed++
}

Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total Tests: $($testsPassed + $testsFailed)" -ForegroundColor White
Write-Host "Passed:      $testsPassed" -ForegroundColor Green
Write-Host "Failed:      $testsFailed" -ForegroundColor Red

if ($testsFailed -eq 0) {
    Write-Host ""
    Write-Host "ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host ""
    exit 0
} else {
    Write-Host ""
    Write-Host "SOME TESTS FAILED" -ForegroundColor Red
    Write-Host ""
    exit 1
}
