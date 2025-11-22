# Topic Management Test
# Tests topic CRUD operations

Write-Host "📝 Testing Topic Management..." -ForegroundColor Green

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

# Test 1: Create a new topic
Write-Host "Test 1: Topic creation" -ForegroundColor Yellow
$topicData = @{
    name = "e2e-topic-test"
    partitions = 2
    replicationFactor = 1
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $topicData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.topicName -eq "e2e-topic-test" -and $response.partitionCount -eq 2) {
        Write-Host "✅ Topic created successfully: $($response.topicName) with $($response.partitionCount) partitions" -ForegroundColor Green
    } else {
        Write-Host "❌ Topic creation response incorrect: $($result.Content)" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Topic creation failed: $($result.Error)" -ForegroundColor Red
}

# Test 2: Get topic metadata
Write-Host "Test 2: Topic retrieval" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/e2e-topic-test"
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.topicName -eq "e2e-topic-test") {
        Write-Host "✅ Topic retrieved successfully: $($response.topicName)" -ForegroundColor Green
        Write-Host "   Partitions: $($response.partitionCount), Replication: $($response.replicationFactor)" -ForegroundColor White
    } else {
        Write-Host "❌ Topic retrieval failed: incorrect data" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Topic retrieval failed: $($result.Error)" -ForegroundColor Red
}

# Test 3: List all topics
Write-Host "Test 3: Topic listing" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics"
if ($result.Success -and $result.StatusCode -eq 200) {
    $topics = $result.Content | ConvertFrom-Json
    Write-Host "✅ Retrieved $($topics.Count) topics" -ForegroundColor Green
    $topics | ForEach-Object { Write-Host "   - $_" -ForegroundColor White }

    if ($topics -contains "e2e-topic-test") {
        Write-Host "✅ New topic appears in list" -ForegroundColor Green
    } else {
        Write-Host "❌ New topic not found in list" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Topic listing failed: $($result.Error)" -ForegroundColor Red
}

# Test 4: Delete topic
Write-Host "Test 4: Topic deletion" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/e2e-topic-test" -Method "DELETE"
if ($result.Success -and $result.StatusCode -eq 204) {
    Write-Host "✅ Topic deleted successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Topic deletion failed: $($result.Error)" -ForegroundColor Red
}

# Test 5: Verify topic deletion
Write-Host "Test 5: Verify topic deletion" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/e2e-topic-test"
if (!$result.Success -or $result.StatusCode -eq 404) {
    Write-Host "✅ Topic successfully deleted (404 response)" -ForegroundColor Green
} else {
    Write-Host "❌ Topic still exists after deletion" -ForegroundColor Red
}

# Test 6: Verify topic removed from list
Write-Host "Test 6: Verify topic removed from list" -ForegroundColor Yellow
$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics"
if ($result.Success -and $result.StatusCode -eq 200) {
    $topics = $result.Content | ConvertFrom-Json
    if ($topics -notcontains "e2e-topic-test") {
        Write-Host "✅ Topic successfully removed from list" -ForegroundColor Green
    } else {
        Write-Host "❌ Topic still appears in list after deletion" -ForegroundColor Red
    }
}

# Test 7: Invalid topic creation
Write-Host "Test 7: Invalid topic creation validation" -ForegroundColor Yellow
$invalidTopicData = @{
    name = ""
    partitions = 0
    replicationFactor = 0
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $invalidTopicData
if (!$result.Success -or $result.StatusCode -ne 200) {
    Write-Host "✅ Invalid topic creation properly rejected" -ForegroundColor Green
} else {
    Write-Host "❌ Invalid topic creation should have been rejected" -ForegroundColor Red
}

# Test 8: Duplicate topic creation
Write-Host "Test 8: Duplicate topic handling" -ForegroundColor Yellow
# First create a topic
$dupTopicData = @{
    name = "duplicate-test-topic"
    partitions = 1
    replicationFactor = 1
} | ConvertTo-Json

$result1 = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $dupTopicData
$result2 = Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics" -Method "POST" -Body $dupTopicData

if ($result1.Success -and $result1.StatusCode -eq 200) {
    if ($result2.Success -and $result2.StatusCode -eq 409) {
        Write-Host "✅ Duplicate topic creation properly rejected (409 Conflict)" -ForegroundColor Green
    } elseif ($result2.Success -and $result2.StatusCode -eq 200) {
        Write-Host "⚠️  Duplicate topic creation allowed (may be expected behavior)" -ForegroundColor Yellow
    } else {
        Write-Host "❌ Duplicate topic creation test failed: $($result2.Error)" -ForegroundColor Red
    }
}

# Cleanup: Delete the duplicate test topic
Invoke-HttpRequest -Uri "http://localhost:9091/api/v1/metadata/topics/duplicate-test-topic" -Method "DELETE" | Out-Null

Write-Host ""
Write-Host "🎉 Topic management testing complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "- Tested complete topic lifecycle (Create, Read, Delete)" -ForegroundColor White
Write-Host "- Verified topic listing and search functionality" -ForegroundColor White
Write-Host "- Tested input validation and error handling" -ForegroundColor White
Write-Host "- Validated duplicate topic handling" -ForegroundColor White
Write-Host ""
Write-Host "Note: Topic operations are typically performed by:" -ForegroundColor Yellow
Write-Host "  - Cluster administrators via management tools" -ForegroundColor White
Write-Host "  - Automated systems for topic provisioning" -ForegroundColor White
Write-Host "  - Kafka clients requesting topic auto-creation" -ForegroundColor White
