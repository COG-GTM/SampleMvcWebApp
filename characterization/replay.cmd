@echo off
rem Usage: characterization\replay.cmd [options]   (see characterization\README.md)
set PYTHONUTF8=1
pushd "%~dp0"
python -m chartool.cli replay %*
set RC=%ERRORLEVEL%
popd
exit /b %RC%
