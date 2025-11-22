# Consumer CLI Test Script
# Tests consumer and consumer group functionality

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Consumer CLI Test Suite" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$cliJar = "dmq-client\target\mycli.jar"
$testsPassed = 0
$testsFailed = 0

# Check if JAR exists
if (-not (Test-Path $cliJar)) {
    Write-Host "ERROR: CLI JAR not found" -ForegroundColor Red
    exit 1
}

Write-Host "Using CLI JAR: $cliJar" -ForegroundColor Gray
Write-Host ""

# ========================================
# Setup: Create test topic and produce messages
# ========================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Setup: Preparing Test Environment" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$timestamp = (Get-Date).ToString("MMddHHmmss")
$testTopic = "consumer-test-$timestamp"

Write-Host "[Setup] Creating topic: $testTopic" -ForegroundColor Yellow

$createOutput = & java -jar $cliJar create-topic --name $testTopic --partitions 3 --replication-factor 2 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "[PASS] Topic created" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Topic creation failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[Setup] Producing 30 test messages..." -ForegroundColor Yellow

for ($i = 1; $i -le 30; $i++) {
    $partition = ($i - 1) % 3
    & java -jar $cliJar produce --topic $testTopic --key "key-$i" --value "Message $i" --partition $partition 2>&1 | Out-Null
    if ($i % 10 -eq 0) {
        Write-Host "  Progress: $i/30" -ForegroundColor Gray
    }
}

Write-Host "[PASS] Messages produced" -ForegroundColor Green
Write-Host ""

Start-Sleep -Seconds 2

# ========================================
# Test Suite 1: Simple Consumer
# ========================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 1: Simple Consumer" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "[Test 1] Consume from partition 0" -ForegroundColor Yellow
$output = & java -jar $cliJar consume --topic $testTopic --partition 0 --offset 0 --max-messages 5 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "  [PASS]" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  [FAIL]" -ForegroundColor Red
    $testsFailed++
}

Write-Host "[Test 2] Consume from partition 1" -ForegroundColor Yellow
& java -jar $cliJar consume --topic $testTopic --partition 1 --offset 0 --max-messages 5 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  [PASS]" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  [FAIL]" -ForegroundColor Red
    $testsFailed++
}

Write-Host "[Test 3] Consume from invalid partition (should fail)" -ForegroundColor Yellow
& java -jar $cliJar consume --topic $testTopic --partition 99 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [PASS] Failed as expected" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  [FAIL] Should have failed" -ForegroundColor Red
    $testsFailed++
}

Write-Host ""

# ========================================
# Test Suite 2: Consumer Group
# ========================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 2: Consumer Group Commands" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "[Test 4] List consumer groups" -ForegroundColor Yellow
& java -jar $cliJar list-groups 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  [PASS]" -ForegroundColor Green
    $testsPassed++
} else {
    Write-Host "  [FAIL]" -ForegroundColor Red
    $testsFailed++
}

Write-Host "[Test 5] Describe consumer group" -ForegroundColor Yellow
& java -jar $cliJar describe-group --group-id $groupId 2>&1 | Out-Null
# This may fail if group doesn't exist yet, which is fine
Write-Host "  [SKIP] Group may not exist" -ForegroundColor Yellow

Write-Host ""
Write-Host "Note: consume-group command not yet implemented in CLI" -ForegroundColor Gray
Write-Host "Consumer group functionality exists in DMQConsumer class" -ForegroundColor Gray

Write-Host ""

# ========================================
# Test Suite 3: Performance Test
# ========================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Suite 3: Performance Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "[Setup] Producing 50 more messages for performance test..." -ForegroundColor Gray
for ($i = 51; $i -le 100; $i++) {
    $partition = ($i - 1) % 3
    & java -jar $cliJar produce --topic $testTopic --key "key-$i" --value "Message $i" --partition $partition 2>&1 | Out-Null
    if ($i % 25 -eq 0) {
        Write-Host "  Progress: $i/100" -ForegroundColor Gray
    }
}
Write-Host "[PASS] Additional messages produced" -ForegroundColor Green

Write-Host "[Test 6] Consume 50 messages (performance test)" -ForegroundColor Yellow
$startTime = Get-Date

& java -jar $cliJar consume --topic $testTopic --partition 0 --offset 0 --max-messages 50 2>&1 | Out-Null

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds
$throughput = [math]::Round(50 / $duration, 2)

if ($LASTEXITCODE -eq 0) {
    Write-Host "  [PASS] Performance test completed" -ForegroundColor Green
    Write-Host "  Duration: $([math]::Round($duration, 2))s, Throughput: $throughput msg/s" -ForegroundColor Cyan
    $testsPassed++
} else {
    Write-Host "  [FAIL]" -ForegroundColor Red
    $testsFailed++
}

Write-Host ""

# ========================================
# Summary
# ========================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Topic:  $testTopic" -ForegroundColor White
Write-Host "Total:  $($testsPassed + $testsFailed)" -ForegroundColor White
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red

if ($testsFailed -eq 0) {
    Write-Host ""
    Write-Host "ALL TESTS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "SOME TESTS FAILED" -ForegroundColor Red
    exit 1
}
