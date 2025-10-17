@echo off
cd /d "C:\Users\viral\OneDrive\Desktop\Kafka-Clone\dmq-client"
set JAVA_HOME=C:\Program Files\Java\jdk-11
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║  Sending Message to Friend's Dev Tunnel Storage Service       ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
echo Make sure Mock Metadata Server is running on port 8081
echo Friend's storage service: https://qwmrnzsf-8082.inc1.devtunnels.ms
echo.
pause
echo.
mvn compile exec:java "-Dexec.mainClass=com.distributedmq.client.test.TestSendToFriendStorage" "-Dexec.cleanupDaemonThreads=false"
pause
