// 端到端验证：追溯链是否把「已作废」如实标出来
// 用 CB-TEST-0001（含生猪 2）做样本：设备写一条瘦肉精记录 → 实名作废 → 查追溯
const BASE = 'http://localhost:8080/api/v1'

async function main() {
  const login = await (await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  })).json()
  const token = login.data.accessToken || login.data.token
  const h = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

  const rep = await (await fetch(`${BASE}/ingest/demo/report`, {
    method: 'POST',
    headers: h,
    body: JSON.stringify({
      deviceNo: 'READER-001',
      bizKey: `TRACE-VOID-${Date.now()}`,
      data: {
        earTagNo: 'ET-TEST-0001',
        batchNo: 'CB-TEST-0001',
        sampleNo: `TV-${Date.now()}`,
        result: 1,
        testTarget: '克伦特罗',
        samplePart: '尿液',
        // 设备 ≠ 人：检测记录必须落到具体检测员，缺人名只会转「待人工处理」，
        // 那样这条诊断就跑不到作废环节了，所以这里显式带上检测员。
        operator: '刘兽医',
      },
    }),
  })).json()
  console.log(`设备上报: code=${rep.code} accepted=${rep.data && rep.data.accepted} targetId=${rep.data && rep.data.targetId} ${rep.data && rep.data.message}`)
  const tid = rep.data && rep.data.targetId
  if (!tid) return

  const v = await (await fetch(`${BASE}/slaughter/ractopamine/${tid}/void`, {
    method: 'POST',
    headers: h,
    body: JSON.stringify({ reason: '追溯标注验证：作废一条设备写入的检测记录' }),
  })).json()
  console.log(`作废: code=${v.code} ${v.message}`)

  const t = await (await fetch(`${BASE}/trace/search?keyword=${encodeURIComponent('CB-TEST-0001')}`, { headers: h })).json()
  const full = t.data && t.data.product ? t.data.traceChain : t.data
  const voided = (full && full.voidedRecords) || []
  console.log(`追溯: code=${t.code} | 作废条数=${voided.length}`)
  console.log(`voidedSummary=${full && full.voidedSummary}`)
  console.log(`样例=${JSON.stringify(voided[0])}`)

  // 关键回归：作废记录不能出现在「当前有效记录」里
  const stillValid = (full && full.slaughter || []).some((row) =>
    (row.ractopamineTests || []).some((x) => Number(x.id) === Number(tid)))
  console.log(`作废记录是否仍在有效列表中: ${stillValid}（期望 false）`)
}

main().catch((e) => { console.error(e); process.exitCode = 1 })
