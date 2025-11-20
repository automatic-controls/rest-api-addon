# Create a function which computes a password hash compatible with WebCTRL (salted Sha512 or PBKDF2)
# WebCTRL8.0+ is compatible with SSHA512
# WebCTRL10.0+ is compatible with PBKDF2
function Get-PasswordHash {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Password,
    [Parameter(Mandatory = $false)]
    [ValidateSet('SSHA512', 'PBKDF2')]
    [string]$HashType = 'SSHA512'
  )
  
  # Generate 8 bytes of random salt
  $Salt = New-Object byte[] 8
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($Salt)
  $rng.Dispose()
  
  if ($HashType -eq 'PBKDF2') {
    # PBKDF2WithHmacSHA512: 210000 iterations, 512-bit hash
    $pbkdf2 = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($Password, $Salt, 210000, [System.Security.Cryptography.HashAlgorithmName]::SHA512)
    $hashBytes = $pbkdf2.GetBytes(64)  # 512 bits = 64 bytes
    $pbkdf2.Dispose()
    
    # Concatenate hash and salt
    $result = New-Object byte[] ($hashBytes.Length + $Salt.Length)
    [System.Buffer]::BlockCopy($hashBytes, 0, $result, 0, $hashBytes.Length)
    [System.Buffer]::BlockCopy($Salt, 0, $result, $hashBytes.Length, $Salt.Length)
    
    # Convert to base64
    $base64 = [System.Convert]::ToBase64String($result)
    
    # Return with {PBKDF2} prefix
    return '{PBKDF2}' + $base64
  }
  else {
    # Convert password to UTF-8 bytes
    $passwordBytes = [System.Text.Encoding]::UTF8.GetBytes($Password)
    
    # Concatenate password and salt
    $data = New-Object byte[] ($passwordBytes.Length + $Salt.Length)
    [System.Buffer]::BlockCopy($passwordBytes, 0, $data, 0, $passwordBytes.Length)
    [System.Buffer]::BlockCopy($Salt, 0, $data, $passwordBytes.Length, $Salt.Length)
    
    # Compute SHA-512 hash
    $sha512 = [System.Security.Cryptography.SHA512]::Create()
    $hashBytes = $sha512.ComputeHash($data)
    $sha512.Dispose()
    
    # Concatenate hash and salt
    $result = New-Object byte[] ($hashBytes.Length + $Salt.Length)
    [System.Buffer]::BlockCopy($hashBytes, 0, $result, 0, $hashBytes.Length)
    [System.Buffer]::BlockCopy($Salt, 0, $result, $hashBytes.Length, $Salt.Length)
    
    # Convert to base64
    $base64 = [System.Convert]::ToBase64String($result)
    
    # Return with {SSHA512} prefix
    return '{SSHA512}' + $base64
  }
}

# Create a function to validate a given password against the stored hash value
function Confirm-PasswordHash {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Password,
    [Parameter(Mandatory = $true)]
    [string]$Hash
  )
  
  # Detect hash type based on prefix
  if ($Hash.StartsWith('{PBKDF2}')) {
    # Remove the {PBKDF2} prefix and decode base64
    $base64Data = $Hash.Substring(8)  # Remove '{PBKDF2}' prefix (8 characters)
    $data = [System.Convert]::FromBase64String($base64Data)
    
    # Extract salt (last 8 bytes) and hash (first len-8 bytes)
    $len = $data.Length
    $salt = New-Object byte[] 8
    $storedHash = New-Object byte[] ($len - 8)
    
    # Use BlockCopy to extract salt (last 8 bytes)
    [System.Buffer]::BlockCopy($data, $len - 8, $salt, 0, 8)
    # Use BlockCopy to extract hash (first len-8 bytes)
    [System.Buffer]::BlockCopy($data, 0, $storedHash, 0, $len - 8)
    
    # Compute PBKDF2 hash with same parameters
    $pbkdf2 = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($Password, $salt, 210000, [System.Security.Cryptography.HashAlgorithmName]::SHA512)
    $computedHash = $pbkdf2.GetBytes(64)  # 512 bits = 64 bytes
    $pbkdf2.Dispose()
    
    # Compare the computed hash with the stored hash
    return Compare-ByteArrays $computedHash $storedHash
  }
  elseif ($Hash.StartsWith('{SSHA512}')) {
    # Remove the {SSHA512} prefix and decode base64
    $base64Data = $Hash.Substring(9)  # Remove '{SSHA512}' prefix (9 characters)
    $data = [System.Convert]::FromBase64String($base64Data)
    
    # Extract salt (last 8 bytes) and hash (first len-8 bytes)
    $len = $data.Length
    $salt = New-Object byte[] 8
    $storedHash = New-Object byte[] ($len - 8)
    
    # Use BlockCopy to extract salt (last 8 bytes)
    [System.Buffer]::BlockCopy($data, $len - 8, $salt, 0, 8)
    # Use BlockCopy to extract hash (first len-8 bytes)
    [System.Buffer]::BlockCopy($data, 0, $storedHash, 0, $len - 8)
    
    # Convert password to UTF-8 bytes
    $passwordBytes = [System.Text.Encoding]::UTF8.GetBytes($Password)
    
    # Concatenate password and salt
    $dataToHash = New-Object byte[] ($passwordBytes.Length + $salt.Length)
    [System.Buffer]::BlockCopy($passwordBytes, 0, $dataToHash, 0, $passwordBytes.Length)
    [System.Buffer]::BlockCopy($salt, 0, $dataToHash, $passwordBytes.Length, $salt.Length)
    
    # Compute SHA-512 hash
    $sha512 = [System.Security.Cryptography.SHA512]::Create()
    $computedHash = $sha512.ComputeHash($dataToHash)
    $sha512.Dispose()
    
    # Compare the computed hash with the stored hash
    return Compare-ByteArrays $computedHash $storedHash
  }
  else {
    throw "Unsupported hash format. Expected {SSHA512} or {PBKDF2} prefix."
  }
}

# Helper function to compare two byte arrays
function Compare-ByteArrays {
  param(
    [byte[]]$Array1,
    [byte[]]$Array2
  )
  if ($Array1.Length -ne $Array2.Length) {
    return $false
  }
  for ($i = 0; $i -lt $Array1.Length; $i++) {
    if ($Array1[$i] -ne $Array2[$i]) {
      return $false
    }
  }
  return $true
}