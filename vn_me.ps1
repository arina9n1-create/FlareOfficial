$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybHJqa3BveGxrYnBqZnFueXlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMTY5MTQsImV4cCI6MjEwMTY5MjkxNH0.khdA6Dm5RinWa00YdKKrQY9sJ1r553GEMxIll5i6Wdk'
$base = 'https://crlrjkpoxlkbpjfqnyyr.supabase.co'
$h = @{ 'apikey' = $key; 'Content-Type' = 'application/json' }
$o = (Invoke-WebRequest -Uri "$base/auth/v1/token?grant_type=refresh_token" -Method Post -Headers $h -Body '{"refresh_token":"nvycoz3uxziq"}' -UseBasicParsing).Content | ConvertFrom-Json
$ah = @{ 'apikey' = $key; 'Authorization' = 'Bearer ' + $o.access_token; 'Content-Type' = 'application/json' }
# Current session identity
try {
    $me = (Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_activate_identity" -Method Post -Headers $ah -Body '{}' -UseBasicParsing).Content | ConvertFrom-Json
    Write-Output ("MY_IDENTITY=" + $me.identity_id + " phone=" + $me.phone)
} catch {
    Write-Output ("activate err: " + $_.ErrorDetails.Message)
}
# Peer identity for +8801732968837
try {
    $p = (Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_search_number" -Method Post -Headers $ah -Body '{"p_phone":"+8801732968837"}' -UseBasicParsing).Content | ConvertFrom-Json
    Write-Output ("PEER_SEARCH=" + ($p | ConvertTo-Json -Compress))
} catch {
    Write-Output ("search err: " + $_.ErrorDetails.Message)
}