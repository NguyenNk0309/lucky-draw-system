$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$customerLogin = Invoke-RestMethod -Method POST -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username = 'customer'; password = 'customer123' } | ConvertTo-Json)
$sellerLogin = Invoke-RestMethod -Method POST -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username = 'seller'; password = 'seller123' } | ConvertTo-Json)
$customer = @{ Authorization = "Bearer $($customerLogin.token)" }
$seller = @{ Authorization = "Bearer $($sellerLogin.token)" }

function Invoke-Json($method, $path, $headers, $body = $null) {
    $arguments = @{ Method = $method; Uri = "$base$path"; Headers = $headers; ContentType = 'application/json' }
    if ($null -ne $body) { $arguments.Body = ($body | ConvertTo-Json) }
    Invoke-RestMethod @arguments
}

function Wait-Until($description, $test) {
    foreach ($attempt in 1..40) {
        try { $value = & $test } catch { $value = $null }
        if ($value) { return $value }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $description"
}

function Get-ErrorBody($errorRecord) {
    if ($errorRecord.ErrorDetails.Message) { return $errorRecord.ErrorDetails.Message }
    return $errorRecord.Exception.Message
}

$campaign = Invoke-Json POST '/api/campaigns' $seller @{
    name = "Smoke Draw $(Get-Date -Format s)"
    startAt = (Get-Date).ToUniversalTime().ToString('o')
    endAt = (Get-Date).ToUniversalTime().AddMinutes(30).ToString('o')
    maxEntriesPerUser = 2
    rewardType = 'COUPON'
    rewardReference = 'SMOKE-50'
}
$campaignId = $campaign.id
Invoke-Json POST "/api/campaigns/$campaignId/activate" $seller | Out-Null

$orderIds = @(1..3 | ForEach-Object { (Invoke-Json POST '/api/orders' $customer @{ total = 1300000 + $_ }).id })
$tickets = Wait-Until 'three purchase tickets' {
    $items = @((Invoke-Json GET '/api/tickets' $customer) | Where-Object { $_.status -eq 'ISSUED' -and $_.orderId -in $orderIds })
    if ($items.Count -eq 3) { ,$items }
}

Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[0].id } | Out-Null
Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[1].id } | Out-Null
try {
    Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[2].id } | Out-Null
    throw 'Third entry unexpectedly succeeded'
} catch { if ((Get-ErrorBody $_) -notmatch 'ENTRY_QUOTA_REACHED') { throw } }
try {
    Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[0].id } | Out-Null
    throw 'Consumed ticket unexpectedly succeeded'
} catch { if ((Get-ErrorBody $_) -notmatch 'TICKET_UNUSABLE') { throw } }

$stats = Wait-Until 'analytics projection' {
    $value = Invoke-Json GET "/api/analytics/campaigns/$campaignId/stats" $seller
    if ($value.totalEntries -eq 2) { $value }
}
Invoke-Json POST "/api/campaigns/$campaignId/end" $seller | Out-Null
$firstDraw = Invoke-Json POST "/api/campaigns/$campaignId/draw" $seller
$secondDraw = Invoke-Json POST "/api/campaigns/$campaignId/draw" $seller
if ($firstDraw.winner.id -ne $secondDraw.winner.id) { throw 'Second draw changed the winner' }
if ($firstDraw.snapshotHash.Length -ne 64) { throw 'Snapshot hash is missing' }

docker compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE outbox SET published_at=NULL WHERE aggregate_id='$campaignId' AND event_type='EntrySubmitted' ORDER BY created_at LIMIT 1" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not replay outbox event' }
Start-Sleep -Seconds 2
$afterReplay = Invoke-Json GET "/api/analytics/campaigns/$campaignId/stats" $seller
if ($afterReplay.totalEntries -ne $stats.totalEntries) { throw 'Duplicate event changed analytics counters' }

Wait-Until 'notification' { $items = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object { $_.campaignId -eq $campaignId }); if ($items.Count -eq 1) { ,$items } } | Out-Null
Wait-Until 'reward' { $items = @((Invoke-Json GET '/api/rewards' $customer) | Where-Object { $_.campaignId -eq $campaignId }); if ($items.Count -eq 1 -and $items[0].deliveredAt) { ,$items } } | Out-Null

Write-Host 'Smoke test passed: gateway auth/routing, campaign service, order tickets, quota, wheel entry, projection, replay dedup, draw idempotency, notification, and reward.'
