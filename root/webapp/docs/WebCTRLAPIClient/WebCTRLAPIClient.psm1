class WebCTRLAPIClient {
  [string]$_url
  [string]$_publicApiKey
  [string]$_privateApiKey
  [hashtable]$config = @{
    retryCount = 3
    retryDelay = 30000  # 30 seconds
    timeout    = 30000  # 30 seconds
  }
  WebCTRLAPIClient([string]$baseApiServerUrl, [string]$publicApiKey, [string]$privateApiKey) {
    if ($baseApiServerUrl) {
      $baseApiServerUrl = $baseApiServerUrl -replace '/+$', '' -ireplace '/api$', '' -ireplace '/RestAPI$', ''
    }else{
      throw [System.ArgumentException]::new("Invalid base API server URL.")
    }
    $this._url = "$baseApiServerUrl/RestAPI/api"
    if ($publicApiKey -and $privateApiKey) {
      $this._publicApiKey = $publicApiKey
      $this._privateApiKey = $privateApiKey
    }else{
      throw [System.ArgumentException]::new("Invalid API keys.")
    }
  }
  [hashtable] SendRequest([string]$endpoint) {
    return $this.SendRequest($endpoint, @{})
  }
  [hashtable] SendRequest([string]$endpoint, [hashtable]$data) {
    $endpoint = $endpoint -replace '/+$', '' -replace '^/+', ''
    $stat = 0
    $ret = $null
    for ($i = 0; $i -lt $this.config.retryCount; $i++) {
      $response = $null
      try {
        if ($stat -eq 429) {
          Start-Sleep -Milliseconds $this.config.retryDelay
        }
        $jwt = $this.BuildJWT($endpoint, $data)
        $httpClient = [System.Net.Http.HttpClient]::new()
        $httpClient.Timeout = [System.TimeSpan]::FromMilliseconds($this.config.timeout)
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$($this._url)/$endpoint")
        $content = [System.Net.Http.StringContent]::new($jwt.body, [System.Text.Encoding]::UTF8, 'application/json')
        $request.Content = $content
        $request.Headers.Add("Authorization", "Bearer $($jwt.signature)")
        $request.Headers.Add("Accept", "application/json")
        $response = $httpClient.SendAsync($request).Result
        $stat = $response.StatusCode.value__
        $ret = $response.Content.ReadAsStringAsync().Result | ConvertFrom-Json
      }catch{
        Write-Error $_
        $ret = $null
      }
      if ($stat -ne 409 -and $stat -ne 429) {
        break
      }
    }
    return @{
      status   = $stat
      response = $ret
    }
  }
  [hashtable] BuildJWT([string]$endpoint, [hashtable]$data) {
    $body = @{
      iss  = $this._publicApiKey
      aud  = $endpoint
      jti  = [System.Guid]::NewGuid().ToString()
      iat  = [long][System.Math]::Floor((New-TimeSpan -Start (New-Object System.DateTime(1970, 1, 1, 0, 0, 0, [System.DateTimeKind]::Utc)) -End ((Get-Date).ToUniversalTime())).TotalSeconds)
      data = $data
    } | ConvertTo-Json -Compress -Depth 20
    $signature = $this.SignWithHMACSHA256($body)
    return @{
      body      = $body
      signature = $signature
    }
  }
  [string] SignWithHMACSHA256([string]$data) {
    $keyBytes = [System.Text.Encoding]::UTF8.GetBytes($this._privateApiKey)
    $dataBytes = [System.Text.Encoding]::UTF8.GetBytes($data)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList @(,$keyBytes)
    $hashBytes = $hmac.ComputeHash($dataBytes)
    $signature = [System.Convert]::ToBase64String($hashBytes)
    return $this.Base64UrlEncode($signature)
  }
  [string] Base64UrlEncode([string]$str) {
    return $str -replace '\+', '-' -replace '/', '_' -replace '=+$', ''
  }
}