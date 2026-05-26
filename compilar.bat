@echo off
echo Compilando...
set CP=lib\lwjgl-3.3.4.jar;lib\lwjgl-glfw-3.3.4.jar;lib\lwjgl-opengl-3.3.4.jar
javac -cp "%CP%" -d out src\Main.java
if %ERRORLEVEL%==0 (
    echo Compilado correctamente. Ejecuta ejecutar.bat para iniciar.
) else (
    echo Error al compilar.
)
pause
