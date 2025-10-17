@echo off
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║  Checking Friend's Storage Service for Messages              ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
echo Storage Service: https://qwmrnzsf-8082.inc1.devtunnels.ms
echo.
echo Testing different endpoints to retrieve messages...
echo.

echo [1] Checking health endpoint...
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/health
echo.
echo.

echo [2] Trying to GET messages from partition 0...
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/messages?topic=orders^&partition=0
echo.
echo.

echo [3] Trying alternate endpoint formats...
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/topics/orders/partitions/0/messages
echo.
echo.

echo [4] Trying to get all data...
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/data
echo.
echo.

echo [5] Checking for admin/debug endpoints...
curl https://qwmrnzsf-8082.inc1.devtunnels.ms/api/v1/storage/status
echo.
echo.

echo ═══════════════════════════════════════════════════════════════
echo If any of the above worked, you'll see the message data.
echo If not, check the service logs on friend's laptop directly.
echo ═══════════════════════════════════════════════════════════════
pause
