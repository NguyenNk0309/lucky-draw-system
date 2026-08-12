param([string]$ComposeProject)

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$compose = @('compose')
if ($ComposeProject) { $compose += @('-p', $ComposeProject) }
$customerLogin = Invoke-RestMethod -Method POST -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username = 'customer'; password = 'customer123' } | ConvertTo-Json)
$sellerLogin = Invoke-RestMethod -Method POST -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username = 'seller'; password = 'seller123' } | ConvertTo-Json)
$customer = @{ Authorization = "Bearer $($customerLogin.token)" }
$seller = @{ Authorization = "Bearer $($sellerLogin.token)" }
$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$socket.ConnectAsync(
    [Uri]"ws://localhost:8080/ws/realtime?access_token=$([Uri]::EscapeDataString($customerLogin.token))",
    [Threading.CancellationToken]::None
).GetAwaiter().GetResult() | Out-Null

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

function Wait-Realtime($expectedTypes) {
    $seen = @{}
    while (@($expectedTypes | Where-Object { -not $seen[$_] }).Count) {
        $buffer = [byte[]]::new(4096)
        $timeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(10))
        $received = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $timeout.Token).GetAwaiter().GetResult()
        if ($received.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            throw 'Realtime WebSocket closed unexpectedly'
        }
        $message = [Text.Encoding]::UTF8.GetString($buffer, 0, $received.Count) | ConvertFrom-Json
        $seen[$message.type] = $true
    }
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

$entries = @(
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
Wait-Until 'entry notifications' {
    $items = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.message -match 'Ticket submitted'
    })
    if ($items.Count -eq 2) { ,$items }
} | Out-Null
Wait-Realtime @('NOTIFICATION')
Wait-Realtime @('NOTIFICATION')

$closed = Invoke-Json POST "/api/campaigns/$campaignId/end" $seller
if ($closed.status -ne 'ENDED') { throw 'Campaign did not stop at ENDED' }
if ($closed.snapshotHash.Length -ne 64) { throw 'Snapshot hash is missing after close' }

$scheduledCampaign = Invoke-Json POST '/api/campaigns' $seller @{
    name = "Scheduled Smoke $(Get-Date -Format s)"
    startAt = (Get-Date).ToUniversalTime().ToString('o')
    endAt = (Get-Date).ToUniversalTime().AddSeconds(5).ToString('o')
    maxEntriesPerUser = 1
    rewardType = 'PRODUCT'
    rewardReference = 'SCHEDULED-SMOKE'
}
Invoke-Json POST "/api/campaigns/$($scheduledCampaign.id)/activate" $seller | Out-Null
$scheduledClosed = Wait-Until 'scheduler campaign close' {
    $value = Invoke-Json GET "/api/campaigns/$($scheduledCampaign.id)" $seller
    if ($value.status -eq 'ENDED') { $value }
}
if ($scheduledClosed.snapshotHash.Length -ne 64) { throw 'Scheduler did not freeze and hash its snapshot' }

$draw = Invoke-Json POST "/api/campaigns/$campaignId/draw" $seller
if ($draw.winner.id -notin $entries.id) { throw 'Winner was not selected from submitted tickets' }
if ($draw.snapshotHash -ne $closed.snapshotHash) { throw 'Draw used a different snapshot' }
$replayedDraw = Invoke-Json POST "/api/campaigns/$campaignId/draw" $seller
if ($replayedDraw.winner.id -ne $draw.winner.id -or $replayedDraw.selectedIndex -ne $draw.selectedIndex) {
    throw 'Repeated draw selected another winner'
}

$winnerDetails = Invoke-Json GET "/api/customers/$($draw.winner.userId)" $seller
if ($winnerDetails.userId -ne $draw.winner.userId -or $winnerDetails.totalOrders -lt 3) {
    throw 'Seller could not inspect winner details'
}

Wait-Until 'winner projection' {
    $mine = Invoke-Json GET "/api/analytics/campaigns/$campaignId/me" $customer
    if ($mine.won -and $mine.reward.reference -eq 'SMOKE-50') { $mine }
} | Out-Null
Wait-Until 'winner notification' {
    $items = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.entryId -eq $draw.winner.id -and $_.message -match 'You won'
    })
    if ($items.Count -eq 1) { ,$items }
} | Out-Null
Wait-Until 'winner reward delivery' {
    $items = @((Invoke-Json GET '/api/rewards' $customer) | Where-Object {
        $_.campaignId -eq $campaignId -and $_.winnerEntryId -eq $draw.winner.id -and $_.deliveredAt
    })
    if ($items.Count -eq 1) { ,$items }
} | Out-Null
Wait-Realtime @('NOTIFICATION', 'REWARD')

docker @compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE outbox SET published_at=NULL WHERE aggregate_id='$campaignId' AND event_type IN ('EntrySubmitted','WinnerPicked')" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not replay outbox events' }
Start-Sleep -Seconds 2
$afterReplay = Invoke-Json GET "/api/analytics/campaigns/$campaignId/stats" $seller
$replayedNotifications = @((Invoke-Json GET '/api/notifications' $customer) | Where-Object { $_.campaignId -eq $campaignId })
$replayedRewards = @((Invoke-Json GET '/api/rewards' $customer) | Where-Object { $_.campaignId -eq $campaignId })
if ($afterReplay.totalEntries -ne $stats.totalEntries -or $replayedNotifications.Count -ne 3 -or $replayedRewards.Count -ne 1) {
    throw 'Duplicate Kafka delivery changed persisted results'
}
$socket.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done',
    [Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null

Write-Host 'Smoke test passed: order outbox, one ticket per order, transactional entry, seller/scheduler snapshot close, idempotent single-winner draw, winner details, async notifications/reward, WebSocket realtime, and replay deduplication.'
