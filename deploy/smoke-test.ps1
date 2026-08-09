param([string]$ComposeProject)

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$compose = @('compose')
if ($ComposeProject) { $compose += @('-p', $ComposeProject) }
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

$spins = @(
    Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[0].id }
    Invoke-Json POST "/api/campaigns/$campaignId/entries" $customer @{ ticketId = $tickets[1].id }
)
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

docker @compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE entries SET reward_pending=TRUE,wheel_segment=1 WHERE id IN ('$($spins[0].id)','$($spins[1].id)')" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not guarantee two reward outcomes for deterministic smoke verification' }

$pending = @((Invoke-Json GET "/api/campaigns/$campaignId/rewards/pending" $seller))
if ($pending.Count -ne 2) { throw 'Seller could not list pending customer rewards' }
$canceledEntryId = $spins[0].id
$deliveredEntryId = $spins[1].id
$canceled = Invoke-Json POST "/api/campaigns/$campaignId/rewards/cancel" $seller @{ entryIds = @($canceledEntryId) }
if (@($canceled.canceledEntryIds) -notcontains $canceledEntryId) { throw 'Seller could not cancel selected reward' }
$pendingAfterCancel = @((Invoke-Json GET "/api/campaigns/$campaignId/rewards/pending" $seller))
if ($pendingAfterCancel.Count -ne 1 -or $pendingAfterCancel[0].entryId -ne $deliveredEntryId) {
    throw 'Canceled reward remained pending'
}

Wait-Until 'canceled notification' {
    $items = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.entryId -eq $canceledEntryId -and $_.message -match 'CANCELED'
    })
    if ($items.Count -eq 1) { ,$items }
} | Out-Null

$closed = Invoke-Json POST "/api/campaigns/$campaignId/end" $seller
if ($closed.status -ne 'DRAWN') { throw 'Campaign close did not release pending rewards' }
if ($closed.snapshotHash.Length -ne 64) { throw 'Snapshot hash is missing' }
try {
    Invoke-Json POST "/api/campaigns/$campaignId/rewards/cancel" $seller @{ entryIds = @($deliveredEntryId) } | Out-Null
    throw 'Reward cancellation unexpectedly succeeded after campaign close'
} catch { if ((Get-ErrorBody $_) -notmatch 'REWARD_NOT_CANCELABLE') { throw } }

Wait-Until 'winner reward projection' {
    $mine = Invoke-Json GET "/api/analytics/campaigns/$campaignId/me" $customer
    if ($mine.won -and $mine.rewardStatus -eq 'DELIVERING' -and
            $mine.canceledRewards -eq 1 -and $mine.releasedRewards -eq 1 -and
            $mine.reward.reference -eq 'SMOKE-50') { $mine }
} | Out-Null

docker @compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE outbox SET published_at=NULL WHERE aggregate_id='$campaignId' AND event_type='EntrySubmitted' ORDER BY created_at LIMIT 1" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not replay outbox event' }
Start-Sleep -Seconds 2
$afterReplay = Invoke-Json GET "/api/analytics/campaigns/$campaignId/stats" $seller
if ($afterReplay.totalEntries -ne $stats.totalEntries) { throw 'Duplicate event changed analytics counters' }

Wait-Until 'delivery notification' {
    $items = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.entryId -eq $deliveredEntryId -and
        $_.message -match 'SMOKE-50.*being delivered'
    })
    if ($items.Count -eq 1) { ,$items }
} | Out-Null
Wait-Until 'only uncanceled reward delivery' {
    $items = @((Invoke-Json GET '/api/rewards' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.reference -eq 'SMOKE-50'
    })
    if ($items.Count -eq 1 -and $items[0].winnerEntryId -eq $deliveredEntryId -and $items[0].deliveredAt) {
        ,$items
    }
} | Out-Null

docker @compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE outbox SET published_at=NULL WHERE aggregate_id='$campaignId' AND event_type='WinnerPicked' ORDER BY created_at LIMIT 1" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not replay winner event' }
Start-Sleep -Seconds 2
$afterWinnerReplay = @((Invoke-Json GET '/api/rewards' $customer) | Where-Object { $_.campaignId -eq $campaignId })
if ($afterWinnerReplay.Count -ne 1 -or $afterWinnerReplay[0].winnerEntryId -ne $deliveredEntryId) {
    throw 'Duplicate or canceled winner event created another reward'
}

Write-Host 'Smoke test passed: pending reward listing, seller multi-cancel API, CANCELED notification, close race guard, uncanceled delivery only, and replay deduplication.'
