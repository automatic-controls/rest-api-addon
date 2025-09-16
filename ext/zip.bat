if /i "%*" EQU "--help" (
  echo ZIP               Create PowerShell SDK ZIP archive.
  exit /b 0
)
if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
setlocal
echo Zipping PowerShell SDK...
del /F "%root%\webapp\docs\WebCTRLAPIClient.zip" >nul 2>nul
"%JDKBin%\jar.exe" -cMf "%root%\webapp\docs\WebCTRLAPIClient.zip" -C "%root%\webapp\docs\WebCTRLAPIClient" .
echo Zipping completed.
endlocal
exit /b 0