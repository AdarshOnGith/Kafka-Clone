# Guide: How to Verify Messages on Friend's Laptop

## Method 1: Check Service Console/Logs (EASIEST)

1. Look for the terminal/command window where the storage service is running
2. You should see log entries showing:
   ```
   [INFO] POST /api/v1/storage/messages - 200 OK
   [INFO] Topic: orders, Partition: 0, Offset: 1
   [INFO] Topic: orders, Partition: 0, Offset: 2
   ```

3. If using Python, logs might show:
   ```python
   Received message: {'topic': 'orders', 'partition': 0, 'key': 'user123', ...}
   Stored at offset: 1
   ```

## Method 2: Check Data Files on Disk

### Find the project directory:
Look for a folder like:
- `dmq-storage-service/`
- `kafka-clone/dmq-storage-service/`
- Or wherever friend's code is located

### Common data storage locations:
```
dmq-storage-service/
├── data/
│   ├── orders/
│   │   ├── 0/                    ← Partition 0
│   │   │   ├── messages.log      ← Your messages should be here!
│   │   │   ├── messages.json
│   │   │   └── index.dat
│   │   └── 1/                    ← Partition 1
│   └── test-topic/
├── logs/
│   └── storage-service.log       ← Check this for request logs
└── storage.db                     ← Or might use SQLite database
```

### On Windows - Open File Explorer and navigate to:
```
C:\Users\[friend's-username]\dmq-storage-service\data\orders\0\
```

Or search for files:
```
*.log
*.json
messages.*
```

## Method 3: Check Database (if using SQLite/other DB)

### If using SQLite:
```powershell
# Find the database file
Get-ChildItem -Path "C:\Users\[username]\" -Recurse -Filter "*.db" | Where-Object {$_.Name -like "*storage*" -or $_.Name -like "*dmq*"}
```

### Open with SQLite browser:
- Download: https://sqlitebrowser.org/
- Open the `.db` file
- Look for tables like: `messages`, `orders_partition_0`, etc.

## Method 4: Check Python Service Code

1. Find the storage service Python file (probably `storage_service.py` or similar)
2. Look for where it saves messages:
   ```python
   # Look for code like:
   with open(f'data/{topic}/partition_{partition}/messages.log', 'a') as f:
       f.write(json.dumps(message))
   ```
3. Go to that directory and open the file

## What You Should See:

### In messages.log or similar:
```json
{"key": "user123", "value": "SGVsbG8gV29ybGQgZnJvbSBWaXJhbCdzIFByb2R1Y2VyIQ==", "offset": 1, "timestamp": 1760727066685}
{"key": "user456", "value": "U2Vjb25kIG1lc3NhZ2UgZnJvbSBWaXJhbCE=", "offset": 2, "timestamp": 1760727067866}
```

### Decode the Base64 values:
- `SGVsbG8gV29ybGQgZnJvbSBWaXJhbCdzIFByb2R1Y2VyIQ==` = `Hello World from Viral's Producer!`
- `U2Vjb25kIG1lc3NhZ2UgZnJvbSBWaXJhbCE=` = `Second message from Viral!`

## Quick PowerShell Commands (Run on Friend's Laptop):

### Search for data files:
```powershell
Get-ChildItem -Path "C:\Users\" -Recurse -Filter "*.log" -ErrorAction SilentlyContinue | Where-Object {$_.FullName -like "*storage*" -or $_.FullName -like "*dmq*" -or $_.FullName -like "*orders*"}
```

### Search for JSON files with "orders":
```powershell
Get-ChildItem -Path "C:\Users\" -Recurse -Filter "*.json" -ErrorAction SilentlyContinue | Where-Object {$_.FullName -like "*orders*"}
```

### View recent files modified (your messages were just written):
```powershell
Get-ChildItem -Path "C:\Users\[friend's-username]\" -Recurse -File | Where-Object {$_.LastWriteTime -gt (Get-Date).AddMinutes(-10)} | Sort-Object LastWriteTime -Descending | Select-Object FullName, LastWriteTime -First 20
```

## Expected Confirmation:

✅ **Offset 1**: Message from user123 with value about "Viral's Producer"
✅ **Offset 2**: Message from user456 with "Second message"
✅ **Both in**: Topic `orders`, Partition `0`
✅ **Timestamps**: Around 1760727066685 and 1760727067866

---

**If you can't find the data files, look at the storage service code to see where it's configured to save data!**
