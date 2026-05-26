@echo off
set MVN=%USERPROFILE%\.vscode\extensions\oracle.oracle-java-25.1.0\nbcode\java\maven\bin\mvn.cmd
"%MVN%" exec:java -Dexec.mainClass="Main" -q
pause
