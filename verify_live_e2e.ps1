# Live End-to-End Verification Script for CampusVerse Lite on Tomcat 10.1

$baseUrl = "http://localhost:8080/campusverse-lite"
$results = [ordered]@{}

function Report-Pass($name, $details) {
    Write-Host "[PASS] $name - $details" -ForegroundColor Green
    $results[$name] = "PASS: $details"
}

function Report-Fail($name, $details) {
    Write-Host "[FAIL] $name - $details" -ForegroundColor Red
    $results[$name] = "FAIL: $details"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Starting Live CampusVerse Lite Verification Suite" -ForegroundColor Cyan
Write-Host "Target URL: $baseUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# Check 1: HomeServlet unauthenticated state (no auto-login)
# -----------------------------------------------------------------------------
try {
    $guestSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $homeResp = Invoke-WebRequest -Uri "$baseUrl/home" -WebSession $guestSession -UseBasicParsing
    if ($homeResp.StatusCode -eq 200 -and $homeResp.Content -like "*Enter your campus corner*" -and $homeResp.Content -notlike "*Milee Kesarwani*") {
        Report-Pass "Check 1: HomeServlet Unauthenticated" "Shows nickname entry prompt without auto-logging in visitor"
    } else {
        Report-Fail "Check 1: HomeServlet Unauthenticated" "HomeServlet response did not show clean nickname entry prompt"
    }
} catch {
    Report-Fail "Check 1: HomeServlet Unauthenticated" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Check 2: Two separate student sessions
# -----------------------------------------------------------------------------
try {
    $sessionA = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $sessionB = New-Object Microsoft.PowerShell.Commands.WebRequestSession

    # Get CSRF tokens from home page
    $homeA = Invoke-WebRequest -Uri "$baseUrl/home" -WebSession $sessionA -UseBasicParsing
    $csrfA = if ($homeA.Content -match 'name="csrfToken"\s+value="([^"]+)"') { $Matches[1] } else { "" }

    $homeB = Invoke-WebRequest -Uri "$baseUrl/home" -WebSession $sessionB -UseBasicParsing
    $csrfB = if ($homeB.Content -match 'name="csrfToken"\s+value="([^"]+)"') { $Matches[1] } else { "" }

    # Login Student A
    $loginRespA = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $sessionA -UseBasicParsing `
        -Headers @{ "Accept" = "application/json" } `
        -Body @{ action = "login"; nickname = "Student_Alpha_E2E"; csrfToken = $csrfA }

    $jsonLoginA = $loginRespA.Content | ConvertFrom-Json
    if ($jsonLoginA.csrfToken) { $csrfA = $jsonLoginA.csrfToken }

    # Login Student B
    $loginRespB = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $sessionB -UseBasicParsing `
        -Headers @{ "Accept" = "application/json" } `
        -Body @{ action = "login"; nickname = "Student_Beta_E2E"; csrfToken = $csrfB }

    $jsonLoginB = $loginRespB.Content | ConvertFrom-Json
    if ($jsonLoginB.csrfToken) { $csrfB = $jsonLoginB.csrfToken }

    $profA = (Invoke-WebRequest -Uri "$baseUrl/student?json=true" -WebSession $sessionA -UseBasicParsing).Content | ConvertFrom-Json
    $profB = (Invoke-WebRequest -Uri "$baseUrl/student?json=true" -WebSession $sessionB -UseBasicParsing).Content | ConvertFrom-Json

    if ($profA.loggedIn -and $profB.loggedIn -and $profA.id -ne $profB.id -and $profA.nickname -eq "Student_Alpha_E2E" -and $profB.nickname -eq "Student_Beta_E2E") {
        Report-Pass "Check 2: Separate Student Sessions" "Student A (ID $($profA.id): $($profA.nickname)) and Student B (ID $($profB.id): $($profB.nickname)) have independent sessions"
    } else {
        Report-Fail "Check 2: Separate Student Sessions" "Sessions were not independent or nicknames mismatched"
    }
} catch {
    Report-Fail "Check 2: Separate Student Sessions" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Check 3: Profile and interest persistence after reload & clearing all interests
# -----------------------------------------------------------------------------
try {
    # Update profile for Student A with 2 interests using action=saveProfile
    $saveResp = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $sessionA -UseBasicParsing `
        -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfA } `
        -Body "action=saveProfile&nickname=Student_Alpha_Updated&interests=DSA&interests=Java&csrfToken=$csrfA" `
        -ContentType "application/x-www-form-urlencoded"

    # Reload profile (simulating page reload)
    $reloadA = (Invoke-WebRequest -Uri "$baseUrl/student?json=true" -WebSession $sessionA -UseBasicParsing).Content | ConvertFrom-Json

    $hasInterests = ($reloadA.nickname -eq "Student_Alpha_Updated" -and $reloadA.interests.Count -ge 2 -and $reloadA.interests -contains "DSA" -and $reloadA.interests -contains "Java")

    # Clear all interests using action=update_profile without interests parameter
    $clearResp = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $sessionA -UseBasicParsing `
        -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfA } `
        -Body "action=update_profile&nickname=Student_Alpha_Updated&csrfToken=$csrfA" `
        -ContentType "application/x-www-form-urlencoded"

    # Reload again to verify empty interests
    $reloadEmpty = (Invoke-WebRequest -Uri "$baseUrl/student?json=true" -WebSession $sessionA -UseBasicParsing).Content | ConvertFrom-Json
    $clearedOk = ($reloadEmpty.interests.Count -eq 0)

    if ($hasInterests -and $clearedOk) {
        Report-Pass "Check 3: Profile & Interest Persistence" "Persisted nickname and interests on reload, and successfully cleared all interests when omitted"
    } else {
        Report-Fail "Check 3: Profile & Interest Persistence" "Interests or nickname failed to persist or clear properly"
    }
} catch {
    Report-Fail "Check 3: Profile & Interest Persistence" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Check 4: GhostRoom creation and correct expiry
# -----------------------------------------------------------------------------
try {
    # Send title and hours (frontend fields)
    $createGhostResp = Invoke-WebRequest -Uri "$baseUrl/ghostrooms" -Method Post -WebSession $sessionA -UseBasicParsing `
        -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfA } `
        -Body "action=create&title=LiveE2EGhostRoom&hours=2&topic=Graph+Algorithms&subject=DSA&csrfToken=$csrfA" `
        -ContentType "application/x-www-form-urlencoded"

    $ghostJson = $createGhostResp.Content | ConvertFrom-Json
    $ghostId = $ghostJson.roomId
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $diffHours = ($ghostJson.expires - $nowMs) / (1000 * 3600)

    # Verify expiry is ~2 hours (between 1.8 and 2.1 hours)
    if ($ghostJson.success -and $ghostJson.title -eq "LiveE2EGhostRoom" -and $diffHours -gt 1.8 -and $diffHours -lt 2.2) {
        Report-Pass "Check 4: GhostRoom Creation & Expiry" "Created GhostRoom ID $ghostId with 2-hour duration (expires in $([Math]::Round($diffHours, 2))h)"
    } else {
        Report-Fail "Check 4: GhostRoom Creation & Expiry" "GhostRoom duration was not parsed as 2 hours (calculated: $diffHours hrs)"
    }
} catch {
    Report-Fail "Check 4: GhostRoom Creation & Expiry" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Check 5: Failed requests never showing success (403 non-author, 409 conflict)
# -----------------------------------------------------------------------------
try {
    # 1. Student A posts a doubt
    $doubtResp = Invoke-WebRequest -Uri "$baseUrl/doubts" -Method Post -WebSession $sessionA -UseBasicParsing `
        -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfA } `
        -Body "action=create&subject=Java&title=Concurrency+Question&description=Explaining+volatile+vs+synchronized&csrfToken=$csrfA" `
        -ContentType "application/x-www-form-urlencoded"

    $doubtJson = $doubtResp.Content | ConvertFrom-Json
    $doubtId = $doubtJson.id

    # 2. Student B attempts to resolve Student A's doubt (must fail 403)
    $resolveFailedWith403 = $false
    try {
        $nonAuthorResp = Invoke-WebRequest -Uri "$baseUrl/doubts" -Method Post -WebSession $sessionB -UseBasicParsing `
            -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfB } `
            -Body "action=resolve&doubtId=$doubtId&csrfToken=$csrfB" `
            -ContentType "application/x-www-form-urlencoded"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 403) {
            $resolveFailedWith403 = $true
        }
    }

    # 3. Student B attempts to claim Student A's nickname (must fail 409)
    $nickConflict409 = $false
    try {
        $conflictResp = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $sessionB -UseBasicParsing `
            -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfB } `
            -Body "action=saveProfile&nickname=Student_Alpha_Updated&csrfToken=$csrfB" `
            -ContentType "application/x-www-form-urlencoded"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 409) {
            $nickConflict409 = $true
        }
    }

    if ($resolveFailedWith403 -and $nickConflict409) {
        Report-Pass "Check 5: Failed Requests Validation" "Non-author resolve returned 403 Forbidden; nickname conflict returned 409 Conflict. Success never shown on error."
    } else {
        Report-Fail "Check 5: Failed Requests Validation" "Failed requests did not return expected HTTP error codes (resolve403: $resolveFailedWith403, nick409: $nickConflict409)"
    }
} catch {
    Report-Fail "Check 5: Failed Requests Validation" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Check 6: More than 100 chat messages & pagination
# -----------------------------------------------------------------------------
try {
    # Study room 1: DSA
    $roomId = 1
    Write-Host "[INFO] Seeding 110 chat messages into Room $roomId to test pagination beyond 100..." -ForegroundColor DarkGray

    for ($i = 1; $i -le 110; $i++) {
        $msgPost = Invoke-WebRequest -Uri "$baseUrl/messages" -Method Post -WebSession $sessionA -UseBasicParsing `
            -Headers @{ "Accept" = "application/json"; "X-CSRF-TOKEN" = $csrfA } `
            -Body "roomId=$roomId&content=Live+E2E+Message+Sequence+$i&csrfToken=$csrfA" `
            -ContentType "application/x-www-form-urlencoded"
    }

    # First fetch: afterId = 0
    $chunk1Raw = (Invoke-WebRequest -Uri "$baseUrl/api/messages?roomId=$roomId&afterId=0" -WebSession $sessionA -UseBasicParsing).Content
    $chunk1 = $chunk1Raw | ConvertFrom-Json
    $chunk1Count = $chunk1.Count

    # Read cursor from chunk 1
    $cursor = $chunk1[$chunk1Count - 1].id

    # Second fetch: afterId = cursor
    $chunk2Raw = (Invoke-WebRequest -Uri "$baseUrl/api/messages?roomId=$roomId&afterId=$cursor" -WebSession $sessionA -UseBasicParsing).Content
    $chunk2 = $chunk2Raw | ConvertFrom-Json
    $chunk2Count = $chunk2.Count

    if ($chunk1Count -eq 100 -and $chunk2Count -ge 10) {
        Report-Pass "Check 6: >100 Messages Pagination" "Chunk 1 returned exactly 100 messages; Chunk 2 after ID $cursor returned $chunk2Count additional messages"
    } else {
        Report-Fail "Check 6: >100 Messages Pagination" "Pagination did not return expected chunk counts (chunk1: $chunk1Count, chunk2: $chunk2Count)"
    }
} catch {
    Report-Fail "Check 6: >100 Messages Pagination" $_.Exception.Message
}

# -----------------------------------------------------------------------------
# Summary Report
# -----------------------------------------------------------------------------
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "Live E2E Verification Results Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
foreach ($k in $results.Keys) {
    Write-Host "$k : $($results[$k])"
}
