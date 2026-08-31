$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybHJqa3BveGxrYnBqZnFueXlyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMTY5MTQsImV4cCI6MjEwMTY5MjkxNH0.khdA6Dm5RinWa00YdKKrQY9sJ1r553GEMxIll5i6Wdk'
$base = 'https://crlrjkpoxlkbpjfqnyyr.supabase.co'
$h = @{ 'apikey' = $key; 'Content-Type' = 'application/json' }
try {
  $o = (Invoke-WebRequest -Uri "$base/auth/v1/token?grant_type=refresh_token" -Method Post -Headers $h -Body '{"refresh_token":"nvycoz3uxziq"}' -UseBasicParsing).Content | ConvertFrom-Json
  $ah = @{ 'apikey' = $key; 'Authorization' = 'Bearer ' + $o.access_token; 'Content-Type' = 'application/json' }
  Write-Output '== INBOX =='
  $inbox = (Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_inbox" -Method Post -Headers $ah -Body '{}' -UseBasicParsing).Content | ConvertFrom-Json
  foreach ($row in $inbox) {
    Write-Output ("conv=" + $row.conversation_id + " | peer=" + $row.peer_phone + " | last=" + $row.last_message_preview)
  }
  Write-Output '== MESSAGE LIST =='
  foreach ($row in $inbox) {
    $msgBody = '{"p_conversation_id":"' + $row.conversation_id + '"}'
    try {
      $msgs = (Invoke-WebRequest -Uri "$base/rest/v1/rpc/vn_messages" -Method Post -Headers $ah -Body $msgBody -UseBasicParsing).Content | ConvertFrom-Json
      foreach ($m in $msgs) {
        Write-Output ("  msgId=" + $m.id + " | sender=" + $m.sender_identity_id + " | " + $m.message_text)
      }
    } catch {
      Write-Output ("  vn_messages ERR: " + $_.ErrorDetails.Message)
    }
  }
} catch {
  Write-Output ("FAILED: " + $_.Exception.Message)
}