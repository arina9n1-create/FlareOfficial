$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybHJqa3BveGxrYnBqZnFueXlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMTY5MTQsImV4cCI6MjEwMTY5MjkxNH0.khdA6Dm5RinWa00YdKKrQY9sJ1r553GEMxIll5i6Wdk'
$base = 'https://crlrjkpoxlkbpjfqnyyr.supabase.co'
$h = @{ 'apikey' = $key; 'Content-Type' = 'application/json' }
$conv = 'd165c205-16f4-472b-8558-c186a8450e8d'
$o = (Invoke-WebRequest -Uri "$base/auth/v1/token?grant_type=refresh_token" -Method Post -Headers $h -Body '{"refresh_token":"nvycoz3uxziq"}' -UseBasicParsing).Content | ConvertFrom-Json
$ah = @{ 'apikey' = $key; 'Authorization' = 'Bearer ' + $o.access_token; 'Content-Type' = 'application/json' }
$body = '{"p_conversation_id":"' + $conv + '"}'
$raw = (Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_messages" -Method Post -Headers $ah -Body $body -UseBasicParsing).Content
$msgs = $raw | ConvertFrom-Json
$i = 1
foreach ($m in $msgs) {
    $codes = ($m.message_text.ToCharArray() | ForEach-Object { ('U+{0:X4}' -f [int]$_) }) -join ' '
    Write-Output ("msg" + $i + " id=" + $m.id)
    Write-Output ("msg" + $i + " text_ascii=" + (($m.message_text -replace '[^\x20-\x7E]', '?')))
    Write-Output ("msg" + $i + " unicode=" + $codes)
    $i++
}