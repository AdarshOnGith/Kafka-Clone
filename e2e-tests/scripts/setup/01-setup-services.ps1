# Setup Services Configuration
# This script configures the service mappings for testing

Write-Host " Setting up service configurations..." -ForegroundColor Green

# Create config directory if it doesn''t exist
$configDir = "..\..\config"
if (!(Test-Path $configDir)) {
    New-Item -ItemType Directory -Path $configDir -Force
}

# Create services.json configuration
$servicesConfig = @'
{
  "metadata-services": [
    {
      "id": 1,
      "host": "localhost",
      "port": 9091,
      "paired-storage": 1
    }
  ],
  "storage-services": [
    {
      "id": 1,
      "host": "localhost",
      "port": 8081,
      "paired-metadata": 1
    }
  ]
}
'@

$servicesConfig | Out-File -FilePath "$configDir\services.json" -Encoding UTF8

Write-Host " Created $configDir\services.json" -ForegroundColor Green

# Verify storage service application.yml
$storageAppYml = "..\..\dmq-storage-service\src\main\resources\application.yml"
if (Test-Path $storageAppYml) {
    Write-Host " Storage service application.yml exists" -ForegroundColor Green
} else {
    Write-Host "  Storage service application.yml not found" -ForegroundColor Yellow
}

# Verify metadata service application.yml
$metadataAppYml = "..\..\dmq-metadata-service\src\main\resources\application.yml"
if (Test-Path $metadataAppYml) {
    Write-Host " Metadata service application.yml exists" -ForegroundColor Green
} else {
    Write-Host "  Metadata service application.yml not found" -ForegroundColor Yellow
}

Write-Host " Service configuration setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Run 02-start-services.ps1 to start the services"
Write-Host "2. Run 03-init-test-data.ps1 to initialize test data"
