@echo off
cd /d "C:\Users\viral\OneDrive\Desktop\Kafka-Clone\dmq-client"
set JAVA_HOME=C:\Program Files\Java\jdk-11
echo Running Integration Test with Friend's Storage Service...
echo.
mvn compile exec:java "-Dexec.mainClass=com.distributedmq.client.test.TestIntegrationWithFriendStorage" "-Dexec.cleanupDaemonThreads=false"
pause
