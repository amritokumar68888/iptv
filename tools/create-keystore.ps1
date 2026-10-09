# create-keystore.ps1
# -----------------------------------------------------------------------------
#  Run this ONCE. It creates your app's signing key (keystore).
#
#  WARNING - keep this key and its password safe:
#    - Every release MUST be signed with the SAME key, otherwise the app
#      will not install as an update over the previous version.
#    - If you lose this key you can never update customer devices again.
#
#  How to run (needs Java / keytool):
#     powershell -ExecutionPolicy Bypass -File tools\create-keystore.ps1
#
#  NOTE: This file is intentionally ASCII-only. A .ps1 containing characters
#  like the arrow or em-dash gets mis-decoded by PowerShell 5.1 (UTF-8 without
#  BOM is read as ANSI, and those bytes map to smart quotes which PowerShell
#  treats as string delimiters) and then fails to parse.
# -----------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $repoRoot "skyott.jks"
$b64Out   = Join-Path $repoRoot "keystore-base64.txt"
$alias    = "skyott"

Write-Host ""
Write-Host "=== Sky OTT - create signing keystore ===" -ForegroundColor Cyan
Write-Host ""

# -- keytool available? -------------------------------------------------------
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    Write-Host "[X] keytool not found. Install a JDK first:" -ForegroundColor Red
    Write-Host "      winget install EclipseAdoptium.Temurin.17.JDK"
    Write-Host "    Then open a NEW terminal and run this script again."
    exit 1
}
Write-Host "keytool: $keytool"

# -- do not silently overwrite an existing key --------------------------------
if (Test-Path $keystore) {
    Write-Host ""
    Write-Host "[!] $keystore already exists." -ForegroundColor Yellow
    Write-Host "    Overwriting it LOSES the old key and breaks app updates forever."
    $ans = Read-Host "    Create a new key anyway? (type yes)"
    if ($ans -ne 'yes') { Write-Host "Cancelled."; exit 0 }
}

# -- generate (keytool prompts for the password itself) -----------------------
Write-Host ""
Write-Host "You will now be asked for a keystore password (twice)." -ForegroundColor Cyan
Write-Host "REMEMBER IT - you must add the same password as a GitHub Secret." -ForegroundColor Yellow
Write-Host ""

& keytool -genkeypair -v `
    -keystore $keystore `
    -alias $alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname "CN=Sky OTT, OU=App, O=SkyOTT, L=Dhaka, ST=Dhaka, C=BD"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[X] keytool failed (exit $LASTEXITCODE)" -ForegroundColor Red
    exit 1
}

# -- base64 (to paste into a GitHub Secret) -----------------------------------
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
Set-Content -Path $b64Out -Value $b64 -Encoding ascii

Write-Host ""
Write-Host "[OK] keystore created: $keystore" -ForegroundColor Green
Write-Host "[OK] base64 written to: $b64Out" -ForegroundColor Green
Write-Host ""
Write-Host "=== Next steps =================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Open $b64Out and copy the whole line."
Write-Host ""
Write-Host "2. GitHub, your repo, Settings, Secrets and variables, Actions"
Write-Host "   then New repository secret, and add these four:"
Write-Host ""
Write-Host "     KEYSTORE_BASE64  = the whole content of keystore-base64.txt"
Write-Host "     KEYSTORE_PASS    = the password you just typed"
Write-Host "     KEY_ALIAS        = skyott"
Write-Host "     KEY_PASS         = the same password"
Write-Host ""
Write-Host "3. Then delete the base64 file:"
Write-Host "     Remove-Item `"$b64Out`""
Write-Host ""
Write-Host "WARNING: back up skyott.jks somewhere safe (USB drive / private cloud)."
Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
