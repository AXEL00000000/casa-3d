@echo off
set MVN=%USERPROFILE%\.vscode\extensions\oracle.oracle-java-25.1.0\nbcode\java\maven\bin\mvn.cmd
"%MVN%" compile exec:exec ^
  -Dexec.executable=java ^
  "-Dexec.args=-cp %%classpath%% -Djava.library.path=natives\windows-amd64 practica.cuatro.pkg3d.CasaFinal" ^
  -f pom.xml -q
pause
