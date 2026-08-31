$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybHJqa3BveGxrYnBqZnFueXlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMTY5MTQsImV4cCI6MjEwMTY5MjkxNH0.khdA6Dm5RinWa00YdKKrQY9sJ1r553GEMxIll5i6Wdk'
$base = 'https://crlrjkpoxlkbpjfqnyyr.supabase.co'
$h = @{ 'apikey' = $key; 'Content-Type' = 'application/json' }
$o = (Invoke-WebRequest -Uri "$base/auth/v1/token?grant_type=refresh_token" -Method Post -Headers $h -Body '{"refresh_token":"nvycoz3uxziq"}' -UseBasicParsing).Content | ConvertFrom-Json
$ah = @{ 'apikey' = $key; 'Authorization' = 'Bearer ' + $o.access_token; 'Content-Type' = 'application/json' }
$conv = 'd165c205-16f4-472b-8558-c186a8450e8d'
$mid = 'b0d42c48-eeb5-4581-beac-839dd7ed0199'
$body = '{"p_conversation_id":"' + $conv + '","p_message_id":"' + $mid + '"}'
try {
    $r = Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_delete_message" -Method Post -Headers $ah -Body $body -UseBasicParsing
    Write-Output ("vn_delete_message OK -> status " + $r.StatusCode)
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Output ("vn_delete_message -> HTTP " + $code + " :: " + $_.ErrorDetails.Message)
}