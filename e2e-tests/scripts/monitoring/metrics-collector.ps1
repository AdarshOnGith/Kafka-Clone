# Metrics Collector
# Collects and analyzes performance metrics from services

Write-Host "📊 Collecting Service Metrics..." -ForegroundColor Green

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

# Metrics endpoints
$metricsEndpoints = @(
    @{ Service = "Metadata Service"; Url = "http://localhost:9091/api/v1/metadata/metrics" },
    @{ Service = "Storage Service"; Url = "http://localhost:8081/api/v1/storage/metrics" }
)

$metricsResults = @()
$startTime = Get-Date

Write-Host "Collecting metrics from services..." -ForegroundColor Yellow

foreach ($endpoint in $metricsEndpoints) {
    Write-Host "  Collecting from $($endpoint.Service)..." -ForegroundColor White
    $result = Invoke-HttpRequest -Uri $endpoint.Url

    if ($result.Success -and $result.StatusCode -eq 200) {
        try {
            $metrics = $result.Content | ConvertFrom-Json
            $metricsResults += @{
                Service = $endpoint.Service
                Timestamp = Get-Date
                Success = $true
                Data = $metrics
            }
            Write-Host "    ✅ Metrics collected successfully" -ForegroundColor Green
        } catch {
            $metricsResults += @{
                Service = $endpoint.Service
                Timestamp = Get-Date
                Success = $false
                Error = "Failed to parse JSON: $($_.Exception.Message)"
            }
            Write-Host "    ❌ Failed to parse metrics JSON: $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        $metricsResults += @{
            Service = $endpoint.Service
            Timestamp = Get-Date
            Success = $false
            Error = $result.Error
        }
        Write-Host "    ❌ Failed to collect metrics: $($result.Error)" -ForegroundColor Red
    }
}

# Analyze collected metrics
Write-Host ""
Write-Host "Analyzing collected metrics..." -ForegroundColor Yellow

$analysis = @{
    Timestamp = Get-Date
    ServicesAnalyzed = $metricsResults.Count
    SuccessfulCollections = ($metricsResults | Where-Object { $_.Success }).Count
    FailedCollections = ($metricsResults | Where-Object { -not $_.Success }).Count
    ServiceMetrics = @()
}

foreach ($result in $metricsResults) {
    $serviceAnalysis = @{
        Service = $result.Service
        Success = $result.Success
        Timestamp = $result.Timestamp
    }

    if ($result.Success) {
        $metrics = $result.Data

        # Analyze metadata service metrics
        if ($result.Service -eq "Metadata Service") {
            $serviceAnalysis.Metrics = @{
                Topics = @{
                    Total = $metrics.topics.total
                    Active = $metrics.topics.active
                    Deleted = $metrics.topics.deleted
                }
                Brokers = @{
                    Total = $metrics.brokers.total
                    Active = $metrics.brokers.active
                    Offline = $metrics.brokers.offline
                }
                Partitions = @{
                    Total = $metrics.partitions.total
                    Leaders = $metrics.partitions.leaders
                    Replicas = $metrics.partitions.replicas
                }
                Requests = @{
                    Total = $metrics.requests.total
                    Successful = $metrics.requests.successful
                    Failed = $metrics.requests.failed
                    AverageResponseTime = $metrics.requests.averageResponseTime
                }
                Memory = @{
                    Used = $metrics.memory.used
                    Free = $metrics.memory.free
                    Total = $metrics.memory.total
                }
            }
        }

        # Analyze storage service metrics
        if ($result.Service -eq "Storage Service") {
            $serviceAnalysis.Metrics = @{
                Messages = @{
                    Produced = $metrics.messages.produced
                    Consumed = $metrics.messages.consumed
                    Stored = $metrics.messages.stored
                }
                Topics = @{
                    Total = $metrics.topics.total
                    Active = $metrics.topics.active
                }
                Partitions = @{
                    Total = $metrics.partitions.total
                    Leaders = $metrics.partitions.leaders
                }
                WAL = @{
                    Segments = $metrics.wal.segments
                    Size = $metrics.wal.size
                    Operations = $metrics.wal.operations
                }
                Requests = @{
                    Total = $metrics.requests.total
                    Successful = $metrics.requests.successful
                    Failed = $metrics.requests.failed
                    AverageResponseTime = $metrics.requests.averageResponseTime
                }
                Storage = @{
                    Used = $metrics.storage.used
                    Free = $metrics.storage.free
                    Total = $metrics.storage.total
                }
            }
        }

        Write-Host "  📈 $($result.Service) Metrics:" -ForegroundColor Green
        if ($result.Service -eq "Metadata Service") {
            Write-Host "    Topics: $($serviceAnalysis.Metrics.Topics.Total) total, $($serviceAnalysis.Metrics.Topics.Active) active" -ForegroundColor White
            Write-Host "    Brokers: $($serviceAnalysis.Metrics.Brokers.Total) total, $($serviceAnalysis.Metrics.Brokers.Active) active" -ForegroundColor White
            Write-Host "    Requests: $($serviceAnalysis.Metrics.Requests.Total) total, $([math]::Round($serviceAnalysis.Metrics.Requests.AverageResponseTime, 2))ms avg" -ForegroundColor White
            Write-Host "    Memory: $([math]::Round($serviceAnalysis.Metrics.Memory.Used / 1MB, 2))MB used, $([math]::Round($serviceAnalysis.Metrics.Memory.Free / 1MB, 2))MB free" -ForegroundColor White
        } elseif ($result.Service -eq "Storage Service") {
            Write-Host "    Messages: $($serviceAnalysis.Metrics.Messages.Produced) produced, $($serviceAnalysis.Metrics.Messages.Consumed) consumed" -ForegroundColor White
            Write-Host "    WAL: $($serviceAnalysis.Metrics.WAL.Segments) segments, $([math]::Round($serviceAnalysis.Metrics.WAL.Size / 1MB, 2))MB size" -ForegroundColor White
            Write-Host "    Requests: $($serviceAnalysis.Metrics.Requests.Total) total, $([math]::Round($serviceAnalysis.Metrics.Requests.AverageResponseTime, 2))ms avg" -ForegroundColor White
            Write-Host "    Storage: $([math]::Round($serviceAnalysis.Metrics.Storage.Used / 1MB, 2))MB used, $([math]::Round($serviceAnalysis.Metrics.Storage.Free / 1MB, 2))MB free" -ForegroundColor White
        }
    } else {
        Write-Host "  ❌ $($result.Service): Failed to collect metrics" -ForegroundColor Red
        Write-Host "    Error: $($result.Error)" -ForegroundColor Red
    }

    Write-Host ""
    $analysis.ServiceMetrics += $serviceAnalysis
}

# Performance analysis
Write-Host "Performance Analysis:" -ForegroundColor Cyan

# Calculate system-wide metrics
$totalRequests = 0
$totalSuccessfulRequests = 0
$avgResponseTime = 0
$responseTimeCount = 0

foreach ($service in $analysis.ServiceMetrics) {
    if ($service.Success -and $service.Metrics.Requests) {
        $totalRequests += $service.Metrics.Requests.Total
        $totalSuccessfulRequests += $service.Metrics.Requests.Successful
        if ($service.Metrics.Requests.AverageResponseTime -gt 0) {
            $avgResponseTime += $service.Metrics.Requests.AverageResponseTime
            $responseTimeCount++
        }
    }
}

if ($responseTimeCount -gt 0) {
    $avgResponseTime = $avgResponseTime / $responseTimeCount
}

$successRate = if ($totalRequests -gt 0) { [math]::Round(($totalSuccessfulRequests / $totalRequests) * 100, 2) } else { 0 }

Write-Host "  Total Requests: $totalRequests" -ForegroundColor White
Write-Host "  Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 95) { "Green" } elseif ($successRate -ge 90) { "Yellow" } else { "Red" })
Write-Host "  Average Response Time: $([math]::Round($avgResponseTime, 2))ms" -ForegroundColor $(if ($avgResponseTime -le 100) { "Green" } elseif ($avgResponseTime -le 500) { "Yellow" } else { "Red" })

# Health assessment
$healthScore = 0
$maxScore = 100

if ($analysis.SuccessfulCollections -eq $analysis.ServicesAnalyzed) { $healthScore += 30 } # All services responding
if ($successRate -ge 95) { $healthScore += 30 } # High success rate
if ($avgResponseTime -le 100) { $healthScore += 20 } # Fast response times
if ($avgResponseTime -le 500) { $healthScore += 10 } # Acceptable response times
if ($totalRequests -gt 0) { $healthScore += 10 } # System is active

$healthStatus = switch {
    ($healthScore -ge 90) { "EXCELLENT" }
    ($healthScore -ge 75) { "GOOD" }
    ($healthScore -ge 60) { "FAIR" }
    ($healthScore -ge 40) { "POOR" }
    default { "CRITICAL" }
}

Write-Host "  Health Score: $healthScore/100 ($healthStatus)" -ForegroundColor $(switch ($healthStatus) {
    "EXCELLENT" { "Green" }
    "GOOD" { "Green" }
    "FAIR" { "Yellow" }
    "POOR" { "Red" }
    "CRITICAL" { "Red" }
})

# Export results
$exportPath = "c:\Users\123ad\Downloads\Distri_Kafka\Kafka-Clone\e2e-tests\metrics-collection-results.json"
$exportData = $analysis | ConvertTo-Json -Depth 10

$exportData | Out-File -FilePath $exportPath -Encoding UTF8
Write-Host ""
Write-Host "Detailed metrics exported to: $exportPath" -ForegroundColor White

Write-Host ""
Write-Host "🎉 Metrics collection complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Services Analyzed: $($analysis.ServicesAnalyzed)" -ForegroundColor White
Write-Host "  Successful Collections: $($analysis.SuccessfulCollections)" -ForegroundColor White
Write-Host "  Failed Collections: $($analysis.FailedCollections)" -ForegroundColor White
Write-Host "  System Health: $healthStatus ($healthScore/100)" -ForegroundColor $(switch ($healthStatus) {
    "EXCELLENT" { "Green" }
    "GOOD" { "Green" }
    "FAIR" { "Yellow" }
    "POOR" { "Red" }
    "CRITICAL" { "Red" }
})
