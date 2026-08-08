$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$customer = @{ 'X-Demo-User' = 'customer-1'; 'X-Demo-Role' = 'CUSTOMER' }
$seller = @{ 'X-Demo-User' = 'seller-1'; 'X-Demo-Role' = 'SELLER' }

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
    $response = $errorRecord.Exception.Response
    if ($response) {
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    return $errorRecord.Exception.Message
}

$tickets = Wait-Until 'seed tickets' { $items = Invoke-Json GET '/api/write/tickets' $customer; if ($items.Count -ge 2) { ,$items } }
$issued = @($tickets | Where-Object status -eq 'ISSUED')
if ($issued.Count -lt 2) { throw 'Expected two available seed tickets' }

Invoke-Json POST '/api/write/campaigns/demo-campaign/entries' $customer @{ ticketId = $issued[0].id } | Out-Null
Invoke-Json POST '/api/write/campaigns/demo-campaign/entries' $customer @{ ticketId = $issued[1].id } | Out-Null
Invoke-Json POST '/api/orders' $customer @{ total = 1300000 } | Out-Null
$third = Wait-Until 'third ticket' {
    $items = Invoke-Json GET '/api/write/tickets' $customer
    $available = @($items | Where-Object status -eq 'ISSUED')
    if ($available.Count -gt 0) { $available[0] }
}

try {
    Invoke-Json POST '/api/write/campaigns/demo-campaign/entries' $customer @{ ticketId = $third.id } | Out-Null
    throw 'Third entry unexpectedly succeeded'
} catch {
    if ((Get-ErrorBody $_) -notmatch 'ENTRY_QUOTA_REACHED') { throw }
}
try {
    Invoke-Json POST '/api/write/campaigns/demo-campaign/entries' $customer @{ ticketId = $issued[0].id } | Out-Null
    throw 'Consumed ticket unexpectedly succeeded'
} catch {
    if ((Get-ErrorBody $_) -notmatch 'TICKET_UNUSABLE') { throw }
}

$stats = Wait-Until 'analytics projection' {
    $value = Invoke-Json GET '/api/analytics/campaigns/demo-campaign/stats' $seller
    if ($value.totalEntries -eq 2) { $value }
}
Invoke-Json POST '/api/write/campaigns/demo-campaign/end' $seller | Out-Null
$firstDraw = Invoke-Json POST '/api/write/campaigns/demo-campaign/draw' $seller
$secondDraw = Invoke-Json POST '/api/write/campaigns/demo-campaign/draw' $seller
if ($firstDraw.winner.id -ne $secondDraw.winner.id) { throw 'Second draw changed the winner' }
if ($firstDraw.snapshotHash.Length -ne 64) { throw 'Snapshot hash is missing' }

docker compose exec -T mysql mysql -ulucky -plucky luckydraw -e "UPDATE outbox SET published_at=NULL WHERE event_type='EntrySubmitted' ORDER BY created_at LIMIT 1" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not replay the outbox event' }
Start-Sleep -Seconds 2
$afterReplay = Invoke-Json GET '/api/analytics/campaigns/demo-campaign/stats' $seller
if ($afterReplay.totalEntries -ne $stats.totalEntries) { throw 'Duplicate event changed analytics counters' }

Wait-Until 'notification' { $items = Invoke-Json GET '/api/notifications' $customer; if ($items.Count -eq 1) { ,$items } } | Out-Null
Wait-Until 'reward' { $items = Invoke-Json GET '/api/rewards' $customer; if ($items.Count -eq 1 -and $items[0].deliveredAt) { ,$items } } | Out-Null

Write-Host 'Smoke test passed: quota, ticket reuse, projection, replay dedup, draw idempotency, notification, and reward.'
