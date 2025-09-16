@echo off
cd /d "E:\University\3rd_Years\OS\ProcessThreadProject"

echo Compiling .java files...
javac *.java
if errorlevel 1 (
  echo.
  echo COMPILATION FAILED. Check errors above.
  pause
  goto :eof
)

echo.
echo Starting PubSub and Nodes...

start "Broker" cmd /k "java Broker"

timeout /t 1 >nul

start "Node 1" cmd /k "java Node 1"
start "Node 2" cmd /k "java Node 2"
start "Node 3" cmd /k "java Node 3"

exit /b
