# How to Verify Messages on Friend's Storage Service

## Option 1: Check Service Logs
Look for the terminal/console window where the storage service is running.
You should see log entries like:

```
[INFO] Received message for topic: orders, partition: 0
[INFO] Message key: user123
[INFO] Message value: [base64 encoded data]
[INFO] Assigned offset: 1
```

## Option 2: Check the Data Directory
The storage service likely stores messages in a local directory. Common locations:

### On Windows:
- `C:\Users\[username]\kafka-data\`
- `C:\Users\[username]\dmq-data\`
- Project directory: `dmq-storage-service\data\`

### Look for files like:
- `orders-0.log` (topic: orders, partition: 0)
- `orders-0.index`
- Or JSON files with message data

## Option 3: Use HTTP API to Query Messages

If the storage service has a GET endpoint to retrieve messages:

```powershell
# Get messages from partition 0
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/messages?topic=orders&partition=0

# Or with offset
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/messages?topic=orders&partition=0&offset=0
```

## Option 4: Check Python Script Output (if using Python)

If friend's storage service is Python-based, look for:
- Terminal window running `python storage_service.py`
- Check for print statements showing received messages

## What to Look For:

### Message 1 (Offset 1):
- Key: `user123`
- Value (Base64): `SGVsbG8gV29ybGQgZnJvbSBWaXJhbCdzIFByb2R1Y2VyIQ==`
- Value (Decoded): `Hello World from Viral's Producer!`
- Timestamp: 1760727066685

### Message 2 (Offset 2):
- Key: `user456`
- Value (Base64): `U2Vjb25kIG1lc3NhZ2UgZnJvbSBWaXJhbCE=`
- Value (Decoded): `Second message from Viral!`
- Timestamp: 1760727067866

## Quick Verification Commands:

### If service has logs in a file:
```powershell
# On Windows PowerShell
Get-Content "path\to\storage-service.log" -Tail 50
```

### If running in terminal:
- Look for the PowerShell/CMD window with the service
- Should show recent POST requests to /api/v1/storage/messages

### If data is in JSON file:
```powershell
Get-Content "path\to\orders-partition-0.json" | ConvertFrom-Json
```
