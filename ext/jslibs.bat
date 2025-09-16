if /i "%*" EQU "--help" (
  echo JSLIBS            Download third-party JavaScript libraries.
  exit /b 0
)
if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
setlocal
echo Downloading third-party JavaScript libraries...
rmdir /S /Q "%root%\webapp\docs\lib" >nul 2>nul
mkdir "%root%\webapp\docs\lib" >nul 2>nul
curl --location --fail --silent --output-dir "%root%\webapp\docs\lib" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/css/all.min.css"
curl --location --fail --silent --output-dir "%root%\webapp\docs\lib" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/atom-one-dark.min.css"
curl --location --fail --silent --output-dir "%root%\webapp\docs\lib" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/highlight.min.js"
curl --location --fail --silent --output-dir "%root%\webapp\docs\lib" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/http.min.js"
curl --location --fail --silent --output-dir "%root%\webapp\docs\lib" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/powershell.min.js"
rmdir /S /Q "%root%\webapp\docs\webfonts" >nul 2>nul
mkdir "%root%\webapp\docs\webfonts" >nul 2>nul
curl --location --fail --silent --output-dir "%root%\webapp\docs\webfonts" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/webfonts/fa-brands-400.ttf"
curl --location --fail --silent --output-dir "%root%\webapp\docs\webfonts" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/webfonts/fa-brands-400.woff2"
curl --location --fail --silent --output-dir "%root%\webapp\docs\webfonts" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/webfonts/fa-solid-900.ttf"
curl --location --fail --silent --output-dir "%root%\webapp\docs\webfonts" --remote-name "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/webfonts/fa-solid-900.woff2"
echo Download completed.
endlocal
exit /b 0