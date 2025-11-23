@echo off
cd /d %~dp0
echo Starting DMQ GUI Client (Direct Class)...
java -cp "target/classes;../../dmq-common/target/classes" DMQGuiClientWithAuth
pause

@REM skips jar goof for test live changes