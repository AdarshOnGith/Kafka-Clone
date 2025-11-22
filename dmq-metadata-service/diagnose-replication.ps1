# Replication Diagnosis Script

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "  Raft Replication Diagnosis" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

$nodes = @(
    @{Id=1; Port=9091; Url="http://localhost:9091"},
    @{Id=2; Port=9092; Url="http://localhost:9092"},
    @{Id=3; Port=9093; Url="http://localhost:9093"}
)

# Step 1: Check Raft Status
Write-Host "`n[1] Raft Status Check" -ForegroundColor Yellow
foreach ($node in $nodes) {
    try {
        $status = Invoke-RestMethod -Uri "$($node.Url)/api/v1/raft/status" -Method GET -ErrorAction Stop
        $role = if ($status.isLeader) { "LEADER" } else { "FOLLOWER" }
        Write-Host "  Node $($node.Id): $role | Term: $($status.currentTerm) | Commit: $($status.commitIndex) | LastApplied: $($status.lastApplied)" -ForegroundColor $(if ($status.isLeader) {"Green"} else {"White"})
    } catch {
        Write-Host "  Node $($node.Id): OFFLINE or ERROR" -ForegroundColor Red
    }
}

# Step 2: Check Topic Count on Each Node
Write-Host "`n[2] Topic Count on Each Node (In-Memory State)" -ForegroundColor Yellow
foreach ($node in $nodes) {
    try {
        $topics = Invoke-RestMethod -Uri "$($node.Url)/api/v1/metadata/topics" -Method GET -ErrorAction Stop
        Write-Host "  Node $($node.Id): $($topics.Count) topics" -ForegroundColor White
        if ($topics.Count -gt 0) {
            foreach ($topic in $topics) {
                Write-Host "    - $($topic.topicName) (Partitions: $($topic.partitionCount))" -ForegroundColor Gray
            }
        }
    } catch {
        Write-Host "  Node $($node.Id): Failed to fetch topics - $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Step 3: Find Leader
Write-Host "`n[3] Identifying Leader" -ForegroundColor Yellow
$leader = $null
foreach ($node in $nodes) {
    try {
        $status = Invoke-RestMethod -Uri "$($node.Url)/api/v1/raft/status" -Method GET -ErrorAction Stop
        if ($status.isLeader) {
            $leader = $node
            Write-Host "  Leader: Node $($node.Id) (Port $($node.Port))" -ForegroundColor Green
            break
        }
    } catch { }
}

if ($null -eq $leader) {
    Write-Host "  ERROR: No leader found!" -ForegroundColor Red
    exit 1
}

# Step 4: Test Topic Creation on Leader
Write-Host "`n[4] Creating Test Topic on Leader" -ForegroundColor Yellow
$testTopicName = "diagnostic-test-$(Get-Random -Minimum 1000 -Maximum 9999)"
$createBody = @{
    topicName = $testTopicName
    partitionCount = 2
    replicationFactor = 1
} | ConvertTo-Json

try {
    Write-Host "  Creating topic: $testTopicName" -ForegroundColor White
    $response = Invoke-RestMethod -Uri "$($leader.Url)/api/v1/metadata/topics" `
        -Method POST -ContentType "application/json" -Body $createBody -ErrorAction Stop
    Write-Host "  ✓ Topic created successfully on leader!" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Failed to create topic: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Wait for replication
Write-Host "`n  Waiting 3 seconds for replication..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# Step 5: Check Replication Status
Write-Host "`n[5] Checking Replication (After 3 seconds)" -ForegroundColor Yellow
$replicationSuccess = $true

foreach ($node in $nodes) {
    try {
        $topics = Invoke-RestMethod -Uri "$($node.Url)/api/v1/metadata/topics" -Method GET -ErrorAction Stop
        $found = $topics | Where-Object { $_.topicName -eq $testTopicName }
        
        if ($found) {
            Write-Host "  ✓ Node $($node.Id): Topic '$testTopicName' found!" -ForegroundColor Green
        } else {
            Write-Host "  ✗ Node $($node.Id): Topic '$testTopicName' NOT found!" -ForegroundColor Red
            $replicationSuccess = $false
        }
    } catch {
        Write-Host "  ✗ Node $($node.Id): Error checking topics" -ForegroundColor Red
        $replicationSuccess = $false
    }
}

# Step 6: Re-check Raft Status
Write-Host "`n[6] Re-checking Raft Status (After Topic Creation)" -ForegroundColor Yellow
foreach ($node in $nodes) {
    try {
        $status = Invoke-RestMethod -Uri "$($node.Url)/api/v1/raft/status" -Method GET -ErrorAction Stop
        $role = if ($status.isLeader) { "LEADER" } else { "FOLLOWER" }
        Write-Host "  Node $($node.Id): $role | Term: $($status.currentTerm) | Commit: $($status.commitIndex) | LastApplied: $($status.lastApplied)" -ForegroundColor $(if ($status.isLeader) {"Green"} else {"White"})
    } catch {
        Write-Host "  Node $($node.Id): ERROR" -ForegroundColor Red
    }
}

# Summary
Write-Host "`n===============================================" -ForegroundColor Cyan
if ($replicationSuccess) {
    Write-Host "  ✓ REPLICATION WORKING!" -ForegroundColor Green
} else {
    Write-Host "  ✗ REPLICATION FAILED!" -ForegroundColor Red
    Write-Host "`n  TROUBLESHOOTING STEPS:" -ForegroundColor Yellow
    Write-Host "  1. Check if followers' commitIndex increased" -ForegroundColor White
    Write-Host "  2. Check if followers' lastApplied increased" -ForegroundColor White
    Write-Host "  3. Look for errors in terminal logs:" -ForegroundColor White
    Write-Host "     - Leader: Look for 'Sending AppendEntries' messages" -ForegroundColor Gray
    Write-Host "     - Followers: Look for 'Received AppendEntries' messages" -ForegroundColor Gray
    Write-Host "  4. Check if RaftApiController endpoints are working" -ForegroundColor White
    Write-Host "  5. Check network connectivity between nodes" -ForegroundColor White
}
Write-Host "===============================================" -ForegroundColor Cyan
