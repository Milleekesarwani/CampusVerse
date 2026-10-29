$baseUrl = "http://localhost:8080/campusverse-lite"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Write-Host "Verifying data persistence after Tomcat server restart..." -ForegroundColor Cyan

# Get fresh CSRF
$homeResp = Invoke-WebRequest -Uri "$baseUrl/home" -WebSession $session -UseBasicParsing
$csrf = if ($homeResp.Content -match 'name="csrfToken"\s+value="([^"]+)"') { $Matches[1] } else { "" }

# Login with password
$loginResp = Invoke-WebRequest -Uri "$baseUrl/student" -Method Post -WebSession $session -UseBasicParsing `
    -Headers @{ "Accept" = "application/json" } `
    -Body @{ action = "login"; nickname = "Aman_Sharma"; password = "Campus@123"; csrfToken = $csrf }

$loginJson = $loginResp.Content | ConvertFrom-Json
if ($loginJson.csrfToken) { $csrf = $loginJson.csrfToken }

# 1. Profile persistence
$prof = (Invoke-WebRequest -Uri "$baseUrl/student?json=true" -WebSession $session -UseBasicParsing).Content | ConvertFrom-Json
Write-Host "Student Profile: id=$($prof.id), nickname=$($prof.nickname)" -ForegroundColor Yellow
if ($prof.nickname -ne "Aman_Sharma" -or -not $prof.loggedIn) { throw "Student profile did not survive restart" }

# 2. GhostRooms persistence
$ghosts = (Invoke-WebRequest -Uri "$baseUrl/ghostrooms?json=true" -WebSession $session -UseBasicParsing).Content | ConvertFrom-Json
$ourGhost = $ghosts | Where-Object { $_.title -eq "LiveE2EGhostRoom" -or $_.type -eq "GHOST" }
Write-Host "GhostRoom Found: $($ourGhost[0].title), topic=$($ourGhost[0].topic), subject=$($ourGhost[0].subject), expires=$($ourGhost[0].expires)" -ForegroundColor Yellow
if (-not $ourGhost) { throw "GhostRoom did not survive restart" }

# 3. Doubts persistence
$doubts = (Invoke-WebRequest -Uri "$baseUrl/doubts?json=true" -WebSession $session -UseBasicParsing).Content | ConvertFrom-Json
$ourDoubt = $doubts | Where-Object { $_.title -like "*Concurrency Question*" -or $_.subject -eq "Java" }
Write-Host "Doubt Found: $($ourDoubt[0].title), author=$($ourDoubt[0].author), subject=$($ourDoubt[0].subject)" -ForegroundColor Yellow
if (-not $ourDoubt) { throw "Doubt did not survive restart" }

# 4. Chat Messages persistence (>100 messages)
$chunk1 = ((Invoke-WebRequest -Uri "$baseUrl/api/messages?roomId=1&afterId=0" -WebSession $session -UseBasicParsing).Content | ConvertFrom-Json)
$cursor = $chunk1[$chunk1.Count - 1].id
$chunk2 = ((Invoke-WebRequest -Uri "$baseUrl/api/messages?roomId=1&afterId=$cursor" -WebSession $session -UseBasicParsing).Content | ConvertFrom-Json)
$totalMsgs = $chunk1.Count + $chunk2.Count
Write-Host "Chat Messages after restart in Room 1: Chunk1=$($chunk1.Count), Chunk2=$($chunk2.Count), Total=$totalMsgs" -ForegroundColor Yellow
if ($chunk1.Count -ne 100 -or $chunk2.Count -lt 10) { throw "Chat messages did not survive restart or pagination failed" }

Write-Host "`n[SUCCESS] ALL DATA SURVIVED SERVER RESTART!" -ForegroundColor Green
