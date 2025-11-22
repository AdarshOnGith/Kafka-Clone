# Consumer Flow Test
# Tests message consumption functionality

Write-Host "Testing Consumer Flow..." -ForegroundColor Green

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

# Test 1: Consume messages from offset 0
Write-Host "Test 1: Consume from offset 0" -ForegroundColor Yellow
$consumeData = @{
    topic = "test-topic"
    partition = 0
    offset = 0
    maxBytes = 1048576
} | ConvertTo-Json

$result = Invoke-HttpRequest -Uri "http://localhost:8081/api/v1/storage/consume" -Method "POST" -Body $consumeData
if ($result.Success -and $result.StatusCode -eq 200) {
    $response = $result.Content | ConvertFrom-Json
    if ($response.success -and $response.messages.Count -gt 0) {
        Write-Host "Consumed $($response.messages.Count) messages successfully" -ForegroundColor Green
    } else {
        Write-Host "Consumption failed or no messages found" -ForegroundColor Red
    }
} else {
    Write-Host "Consume request failed: $($result.Error)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Consumer flow testing complete!" -ForegroundColor Green
