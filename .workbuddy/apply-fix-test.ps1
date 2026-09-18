$ErrorActionPreference = 'Continue'
$log = 'E:\pork-traceability\.workbuddy\apply-fix-test.out'
Set-Content -Path $log -Value "== apply re-submit test ==" -Encoding utf8
function W($m) { $m | Out-File -FilePath $log -Append -Encoding utf8 }

W (docker ps --filter "name=breeding-service" --format "{{.Names}} {{.Status}}")

$base = $null
$token = $null
foreach ($b in @('http://localhost:8080/api/v1', 'http://localhost:8080/api', 'http://localhost:8080')) {
  try {
    $r = Invoke-RestMethod -Uri "$b/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' -TimeoutSec 15
    $t = $null
    if ($r.data -and $r.data.token) { $t = $r.data.token } elseif ($r.token) { $t = $r.token }
    if ($t) { $base = $b; $token = $t; W "LOGIN OK base=$b tokenLen=$($t.Length)"; break }
    W "LOGIN no token via $b : $($r | ConvertTo-Json -Compress -Depth 5)"
  } catch { W "LOGIN fail via $b : $($_.Exception.Message)" }
}
if (-not $base) { W "ABORT: no token"; exit 1 }

$H = @{ Authorization = "Bearer $token" }

# 1) look for an existing rejected apply
$found = $null
try {
  $a = Invoke-RestMethod -Uri "$base/breeding/applies?pageNum=1&pageSize=100&approvalStatus=2" -Headers $H -TimeoutSec 20
  $recs = $a.data.records
  if (-not $recs) { $recs = $a.data.list }
  W "rejected applies count=$(@($recs).Count)"
  if ($recs -and @($recs).Count -gt 0) { $found = @($recs)[0] }
} catch { W "GET applies fail: $($_.Exception.Message)" }

if ($found) {
  W "use existing rejected apply id=$($found.id) pigId=$($found.pigId) applyNo=$($found.applyNo)"
  $pigId = $found.pigId
} else {
  W "no rejected apply found; create scenario"
  $p = Invoke-RestMethod -Uri "$base/breeding/pigs?pageNum=1&pageSize=100&status=1" -Headers $H -TimeoutSec 20
  $pr = $p.data.records; if (-not $pr) { $pr = $p.data.list }
  $ap = Invoke-RestMethod -Uri "$base/breeding/applies?pageNum=1&pageSize=200" -Headers $H -TimeoutSec 20
  $ar = $ap.data.records; if (-not $ar) { $ar = $ap.data.list }
  $used = @($ar | ForEach-Object { $_.pigId })
  $cand = @($pr | Where-Object { $used -notcontains $_.id })
  W "status=1 pigs=$(@($pr).Count) withApply=$($used.Count) candidates=$($cand.Count)"
  if ($cand.Count -eq 0) { W "ABORT: no candidate pig"; exit 1 }
  $pigId = @($cand)[0].id
  W "picked pigId=$pigId earTag=$(@($cand)[0].earTagNo)"
  $body = '{"targetSlaughterhouse":"AUTO-TEST","weightKg":100}'
  $c1 = Invoke-RestMethod -Uri "$base/breeding/pigs/$pigId/apply" -Method Post -ContentType 'application/json' -Headers $H -Body $body -TimeoutSec 20
  W "create apply #1 -> code=$($c1.code) msg=$($c1.message) id=$($c1.data)"
  $applyId = $c1.data
  $rj = Invoke-RestMethod -Uri "$base/breeding/applies/$applyId/approve" -Method Put -ContentType 'application/json' -Headers $H -Body '{"approved":false,"comment":"auto-test reject"}' -TimeoutSec 20
  W "reject -> code=$($rj.code) msg=$($rj.message)"
  $d = Invoke-RestMethod -Uri "$base/breeding/applies/$applyId" -Headers $H -TimeoutSec 20
  W "after reject detail: status=$($d.data.approvalStatus) reason=$($d.data.rejectReason)"
  $pig = Invoke-RestMethod -Uri "$base/breeding/pigs/$pigId" -Headers $H -TimeoutSec 20
  W "pig status after reject = $($pig.data.status) (expect 1)"
}

# 2) the actual fix test: re-submit after rejection
$body2 = '{"targetSlaughterhouse":"AUTO-TEST-2","weightKg":110}'
try {
  $c2 = Invoke-RestMethod -Uri "$base/breeding/pigs/$pigId/apply" -Method Post -ContentType 'application/json' -Headers $H -Body $body2 -TimeoutSec 20
  W "RESUBMIT -> code=$($c2.code) msg=$($c2.message) id=$($c2.data)"
  $d2 = Invoke-RestMethod -Uri "$base/breeding/applies/$($c2.data)" -Headers $H -TimeoutSec 20
  W "after resubmit: status=$($d2.data.approvalStatus) applyNo=$($d2.data.applyNo) reason=[$($d2.data.rejectReason)] approver=[$($d2.data.approver)]"
} catch { W "RESUBMIT fail: $($_.Exception.Message)" }

# 3) cleanup: reject again so demo data stays in a rejected state
try {
  $applyId2 = (Invoke-RestMethod -Uri "$base/breeding/applies?pageNum=1&pageSize=5&approvalStatus=0" -Headers $H -TimeoutSec 20).data.records
  $target = @($applyId2 | Where-Object { $_.pigId -eq $pigId })[0]
  if ($target) {
    $r2 = Invoke-RestMethod -Uri "$base/breeding/applies/$($target.id)/approve" -Method Put -ContentType 'application/json' -Headers $H -Body '{"approved":false,"comment":"auto-test cleanup"}' -TimeoutSec 20
    W "cleanup reject -> code=$($r2.code) msg=$($r2.message)"
  }
} catch { W "cleanup fail: $($_.Exception.Message)" }
W "== done =="
