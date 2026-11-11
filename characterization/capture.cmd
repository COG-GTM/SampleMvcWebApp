@echo off
rem Usage: characterization\capture.cmd [options]   (see characterization\README.md)
set PYTHONUTF8=1
pushd "%~dp0"
python -m chartool.cli capture %*
set RC=%ERRORLEVEL%
popd
exit /b %RC%
