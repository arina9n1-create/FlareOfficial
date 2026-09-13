# =====================================================================
# FlareOfficial — Secure Media Gateway Deployment Script
# Deploys the B2 upload/download Edge Functions to your Supabase project.
#
# USAGE (run once from anywhere):
#   powershell -ExecutionPolicy Bypass -File "c:\Users\Feelings Of Hearts\AndroidStudioProjects\version9\deploy-media-functions.ps1"
#
# The FIRST run opens a browser for `supabase login`; the session is
# cached afterwards. Secrets (B2_*) are already set on the project.
# =====================================================================

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "== FlareOfficial secure media gateway deployment ==" -ForegroundColor Cyan

# 0. Sanity check
if (-not (Get-Command supabase -ErrorAction SilentlyContinue)) {
    Write-Error "Supabase CLI not found. Install it first: npm i -g supabase"
    exit 1
}

# 1. Login (browser popup on first run; no-op when already logged in)
Write-Host "`n[1/4] Checking Supabase login..." -ForegroundColor Yellow
supabase login
if ($LASTEXITCODE -ne 0) { Write-Error "Login failed"; exit 1 }

# 2. Link this project folder (ref matches Backend.URL)
Write-Host "`n[2/4] Linking project..." -ForegroundColor Yellow
supabase link --project-ref crlrjkpoxlkbpjfqnyyr
if ($LASTEXITCODE -ne 0) { Write-Error "Link failed"; exit 1 }

# 3. Deploy the media functions
Write-Host "`n[3/4] Deploying Edge Functions..." -ForegroundColor Yellow
supabase functions deploy b2-upload
if ($LASTEXITCODE -ne 0) { Write-Error "b2-upload deploy failed"; exit 1 }
supabase functions deploy b2-download
if ($LASTEXITCODE -ne 0) { Write-Error "b2-download deploy failed"; exit 1 }
supabase functions deploy b2-delete
if ($LASTEXITCODE -ne 0) { Write-Error "b2-delete deploy failed"; exit 1 }

# 4. Smoke test: b2-download must exist and reject an unauthenticated probe
Write-Host "`n[4/4] Smoke testing b2-download..." -ForegroundColor Yellow
$outFile = Join-Path $env:TEMP "flareofficial_b2_download_probe.json"
$code = & curl.exe -s -o $outFile -w "%{http_code}" --max-time 30 `
    "https://crlrjkpoxlkbpjfqnyyr.supabase.co/functions/v1/b2-download?path=users/probe/profile/x.jpg"
$body = ""
if (Test-Path $outFile) { $body = [string](Get-Content $outFile -Raw -ErrorAction SilentlyContinue) }
if ($code -in @('400', '401')) {
    Write-Host "OK -> b2-download is LIVE and rejects unauthenticated calls (HTTP $body // $code)." -ForegroundColor Green
} elseif ($body -like '*not found*') {
    Write-Warning "Function not deployed yet (HTTP $code): $body"
} else {
    Write-Host "Probe returned HTTP $code : $body" -ForegroundColor DarkYellow
}

Write-Host "`n== DEPLOY COMPLETE ==" -ForegroundColor Green
Write-Host @"

Next manual steps:
 1) Supabase Dashboard -> SQL Editor -> run:
       supabase/migrations/20260827010000_private_media_gateway_rewrite.sql
 2) Android Studio -> Rebuild + install the app
 3) Open profile page -> avatar/cover/photos will render through the gateway
"@ -ForegroundColor White
