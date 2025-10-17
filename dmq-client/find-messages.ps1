# Run this script on your friend's laptop to find stored messages
# Save as: find-messages.ps1

Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Searching for Stored Messages on This Computer              ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 1. Find recently modified files (messages were just written)
Write-Host "[1] Finding recently modified files (last 30 minutes)..." -ForegroundColor Yellow
$recentFiles = Get-ChildItem -Path $env:USERPROFILE -Recurse -File -ErrorAction SilentlyContinue | 
    Where-Object {$_.LastWriteTime -gt (Get-Date).AddMinutes(-30)} | 
    Where-Object {$_.Name -like "*.log" -or $_.Name -like "*.json" -or $_.Name -like "*message*"} |
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 10

if ($recentFiles) {
    Write-Host "✅ Found recently modified files:" -ForegroundColor Green
    $recentFiles | ForEach-Object {
        Write-Host "   📁 $($_.FullName)" -ForegroundColor White
        Write-Host "      Modified: $($_.LastWriteTime)" -ForegroundColor Gray
    }
} else {
    Write-Host "❌ No recently modified files found" -ForegroundColor Red
}
Write-Host ""

# 2. Search for storage service directories
Write-Host "[2] Searching for storage service directories..." -ForegroundColor Yellow
$storageDirs = Get-ChildItem -Path $env:USERPROFILE -Directory -Recurse -ErrorAction SilentlyContinue | 
    Where-Object {$_.Name -like "*storage*" -or $_.Name -like "*dmq*" -or $_.Name -like "*kafka*"} |
    Select-Object -First 10

if ($storageDirs) {
    Write-Host "✅ Found storage directories:" -ForegroundColor Green
    $storageDirs | ForEach-Object {
        Write-Host "   📂 $($_.FullName)" -ForegroundColor White
        
        # Check for data subdirectories
        $dataFiles = Get-ChildItem -Path $_.FullName -Recurse -File -ErrorAction SilentlyContinue | 
            Where-Object {$_.Name -like "*.log" -or $_.Name -like "*.json" -or $_.Name -like "*message*"} |
            Select-Object -First 3
        
        if ($dataFiles) {
            $dataFiles | ForEach-Object {
                Write-Host "      📄 $($_.Name)" -ForegroundColor Cyan
            }
        }
    }
} else {
    Write-Host "❌ No storage directories found" -ForegroundColor Red
}
Write-Host ""

# 3. Search for files containing "orders" (our topic name)
Write-Host "[3] Searching for files related to 'orders' topic..." -ForegroundColor Yellow
$ordersFiles = Get-ChildItem -Path $env:USERPROFILE -Recurse -File -ErrorAction SilentlyContinue | 
    Where-Object {$_.Name -like "*orders*" -or $_.DirectoryName -like "*orders*"} |
    Where-Object {$_.Extension -in @('.log', '.json', '.txt', '.dat')} |
    Select-Object -First 10

if ($ordersFiles) {
    Write-Host "✅ Found files related to 'orders':" -ForegroundColor Green
    $ordersFiles | ForEach-Object {
        Write-Host "   📄 $($_.FullName)" -ForegroundColor White
        Write-Host "      Size: $($_.Length) bytes, Modified: $($_.LastWriteTime)" -ForegroundColor Gray
        
        # Try to read first few lines if it's a text file
        if ($_.Length -lt 10KB -and $_.Extension -in @('.log', '.json', '.txt')) {
            try {
                $content = Get-Content $_.FullName -TotalCount 3 -ErrorAction SilentlyContinue
                if ($content) {
                    Write-Host "      Preview:" -ForegroundColor Gray
                    $content | ForEach-Object { Write-Host "         $_" -ForegroundColor DarkGray }
                }
            } catch {}
        }
    }
} else {
    Write-Host "❌ No files related to 'orders' found" -ForegroundColor Red
}
Write-Host ""

# 4. Search for database files
Write-Host "[4] Searching for database files..." -ForegroundColor Yellow
$dbFiles = Get-ChildItem -Path $env:USERPROFILE -Recurse -File -ErrorAction SilentlyContinue | 
    Where-Object {$_.Extension -in @('.db', '.sqlite', '.sqlite3')} |
    Where-Object {$_.Name -like "*storage*" -or $_.Name -like "*dmq*" -or $_.Name -like "*message*"} |
    Select-Object -First 5

if ($dbFiles) {
    Write-Host "✅ Found database files:" -ForegroundColor Green
    $dbFiles | ForEach-Object {
        Write-Host "   🗄️  $($_.FullName)" -ForegroundColor White
        Write-Host "      Size: $([math]::Round($_.Length/1KB, 2)) KB" -ForegroundColor Gray
    }
} else {
    Write-Host "❌ No database files found" -ForegroundColor Red
}
Write-Host ""

# 5. Check for running Python processes (storage service)
Write-Host "[5] Checking for running storage service processes..." -ForegroundColor Yellow
$pythonProcesses = Get-Process | Where-Object {$_.ProcessName -like "*python*"}

if ($pythonProcesses) {
    Write-Host "✅ Found Python processes:" -ForegroundColor Green
    $pythonProcesses | ForEach-Object {
        Write-Host "   🐍 $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor White
    }
    Write-Host "   💡 Check the console window for these processes to see logs!" -ForegroundColor Yellow
} else {
    Write-Host "❌ No Python processes found" -ForegroundColor Red
}
Write-Host ""

# Summary
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "✅ Search Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Expected message content (Base64 encoded):" -ForegroundColor Yellow
Write-Host "  Offset 1: SGVsbG8gV29ybGQgZnJvbSBWaXJhbCdzIFByb2R1Y2VyIQ==" -ForegroundColor Gray
Write-Host "  Offset 2: U2Vjb25kIG1lc3NhZ2UgZnJvbSBWaXJhbCE=" -ForegroundColor Gray
Write-Host ""
Write-Host "Decoded values:" -ForegroundColor Yellow
Write-Host "  Offset 1: Hello World from Viral's Producer!" -ForegroundColor Gray
Write-Host "  Offset 2: Second message from Viral!" -ForegroundColor Gray
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan

# Pause
Read-Host "`nPress Enter to exit"
