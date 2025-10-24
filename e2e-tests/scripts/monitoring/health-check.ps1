# Health Check Monitor
# Monitors service health and availability

Write-Host "🏥 Monitoring Service Health..." -ForegroundColor Green

# Function to make HTTP requests
function Invoke-HttpRequest {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null,
        [string]$ContentType = "application/json",
        [int]$TimeoutSeconds = 10
    )

    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            ContentType = $ContentType
            TimeoutSec = $TimeoutSeconds
        }

        if ($Body) {
            $params.Body = $Body
        }

        $response = Invoke-WebRequest @params
        return @{
            Success = $true
            StatusCode = $response.StatusCode
            Content = $response.Content
            ResponseTime = $response.BaseResponse.ResponseTime.TotalMilliseconds
        }
    } catch {
        return @{
            Success = $false
            StatusCode = $_.Exception.Response.StatusCode.Value__
            Error = $_.Exception.Message
            ResponseTime = -1
        }
    }
}

# Service endpoints to monitor
$services = @(
    @{ Name = "Metadata Service"; Url = "http://localhost:9091/api/v1/metadata/controller" },
    @{ Name = "Storage Service"; Url = "http://localhost:8081/api/v1/storage/health" }
)

$healthResults = @()
$startTime = Get-Date

Write-Host "Checking service health..." -ForegroundColor Yellow

foreach ($service in $services) {
    Write-Host "  Testing $($service.Name)..." -ForegroundColor White
    $result = Invoke-HttpRequest -Uri $service.Url -TimeoutSeconds 5

    $healthStatus = @{
        Service = $service.Name
        Url = $service.Url
        Status = if ($result.Success -and $result.StatusCode -eq 200) { "HEALTHY" } else { "UNHEALTHY" }
        StatusCode = $result.StatusCode
        ResponseTime = $result.ResponseTime
        Error = if ($result.Success) { $null } else { $result.Error }
        Timestamp = Get-Date
    }

    $healthResults += $healthStatus

    if ($healthStatus.Status -eq "HEALTHY") {
        Write-Host "    ✅ $($service.Name): HEALTHY ($([math]::Round($result.ResponseTime, 2))ms)" -ForegroundColor Green
    } else {
        Write-Host "    ❌ $($service.Name): UNHEALTHY (Status: $($result.StatusCode))" -ForegroundColor Red
        if ($result.Error) {
            Write-Host "       Error: $($result.Error)" -ForegroundColor Red
        }
    }
}

# Calculate overall health
$healthyServices = ($healthResults | Where-Object { $_.Status -eq "HEALTHY" }).Count
$totalServices = $services.Count
$overallHealth = if ($healthyServices -eq $totalServices) { "ALL_HEALTHY" } elseif ($healthyServices -gt 0) { "PARTIAL_HEALTHY" } else { "ALL_UNHEALTHY" }

Write-Host ""
Write-Host "Health Summary:" -ForegroundColor Cyan
Write-Host "  Overall Status: $overallHealth" -ForegroundColor $(if ($overallHealth -eq "ALL_HEALTHY") { "Green" } elseif ($overallHealth -eq "PARTIAL_HEALTHY") { "Yellow" } else { "Red" })
Write-Host "  Healthy Services: $healthyServices/$totalServices" -ForegroundColor White
Write-Host "  Check Duration: $(([math]::Round(((Get-Date) - $startTime).TotalSeconds, 2))) seconds" -ForegroundColor White

# Detailed health information
Write-Host ""
Write-Host "Detailed Results:" -ForegroundColor Cyan
foreach ($result in $healthResults) {
    Write-Host "  $($result.Service):" -ForegroundColor White
    Write-Host "    Status: $($result.Status)" -ForegroundColor $(if ($result.Status -eq "HEALTHY") { "Green" } else { "Red" })
    Write-Host "    Response Time: $(if ($result.ResponseTime -ge 0) { "$([math]::Round($result.ResponseTime, 2))ms" } else { "N/A" })" -ForegroundColor White
    Write-Host "    Status Code: $($result.StatusCode)" -ForegroundColor White
    if ($result.Error) {
        Write-Host "    Error: $($result.Error)" -ForegroundColor Red
    }
    Write-Host "    Timestamp: $($result.Timestamp.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor White
    Write-Host ""
}

# Export results to JSON file
$exportPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\e2e-tests\health-check-results.json"
$exportData = @{
    timestamp = $startTime.ToString('yyyy-MM-dd HH:mm:ss')
    duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 2)
    overallHealth = $overallHealth
    healthyServices = $healthyServices
    totalServices = $totalServices
    results = $healthResults
} | ConvertTo-Json -Depth 10

$exportData | Out-File -FilePath $exportPath -Encoding UTF8
Write-Host "Results exported to: $exportPath" -ForegroundColor White

# Exit with appropriate code
if ($overallHealth -eq "ALL_HEALTHY") {
    Write-Host ""
    Write-Host "🎉 All services are healthy!" -ForegroundColor Green
    exit 0
} elseif ($overallHealth -eq "PARTIAL_HEALTHY") {
    Write-Host ""
    Write-Host "⚠️  Some services are unhealthy. Check the detailed results above." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host ""
    Write-Host "❌ All services are unhealthy. System may be down." -ForegroundColor Red
    exit 2
}
