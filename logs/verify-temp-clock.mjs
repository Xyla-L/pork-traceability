/**
 * 冷链温度「时间轴闸门」回归：设备时钟跑到未来，必须当场拒绝。
 *
 * 为什么这一条要拦（而不是记录后交给人工）：
 * 未来的记录会成为该运单的「最近一条」，之后所有真实温度都被当成「补传的历史数据」
 * （recordTime 早于上一条 → 不判断档），这一趟的断档从此永远报不出来 ——
 * 而「这一车到底断没断冷链」正是门店拒收与责任划分的唯一依据。
 *
 * 同时盯住「不许误伤」的一侧：补传历史数据（早于发车、甚至早于建单）是这条通道的合法能力，
 * 车载终端先缓存、进站后整段上传是常态，所以闸门只卡「未来」，不往回卡。
 *
 * 自持：本脚本自建一张一次性运单做主体（status=2 运输中），用完连同温度记录一起删掉，
 * 不借用演示台账里的任何在途运单。
 */
import { execSync } from 'node:child_process'

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const RUN = Date.now()
const PASS = `CLOCK${RUN}`
const PLATE = `临${String(RUN).slice(-5)}`
const MYSQL = 'docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e'

let pass = 0
let fail = 0
function check(name, ok, detail = '') {
  if (ok) { pass++; console.log(`  ✅ ${name}`) } else { fail++; console.log(`  ❌ ${name} ${detail}`) }
}
function sql(stmt) {
  // 折成一行：mysql -e 对多行多语句的解析不稳定，压成单行后语义不变
  const flat = stmt.replace(/\s+/g, ' ')
  const cmd = `${MYSQL} "${flat.replace(/"/g, '\\"')}" 2>&1 | grep -v Warning || true`
  return execSync(cmd, { encoding: 'utf8' }).trim()
}
function sqlOne(stmt) {
  return sql(stmt).split('\n')[0].trim()
}
function pad(n) { return String(n).padStart(2, '0') }
function timeText(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------------------------------------------------------------- 登录
const loginRes = await fetch(`${BASE}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const loginBody = await loginRes.json()
const token = loginBody?.data?.token || loginBody?.data?.accessToken
if (!token) { console.error('登录失败，无法继续'); process.exit(1) }

async function report(deviceNo, bizKey, data, reportTime) {
  const res = await fetch(`${BASE}/ingest/demo/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ deviceNo, channel: 'TEMPERATURE', bizKey, reportTime, data }),
  })
  let body = null
  try { body = await res.json() } catch { /* 空响应体 */ }
  const d = body?.data
  return {
    httpStatus: res.status,
    status: d?.status,
    accepted: d?.accepted,
    stagingId: d?.stagingId,
    message: d?.message || d?.reason || d?.msg || body?.msg || '',
  }
}

const transportNo = `TR-${PASS}`
/** 本脚本自己那几条温度（按上报时间精确查）：定时采集器每 300s 也会给在途运单落一条，
 *  所以不能拿「这张运单有几条温度」当断言，只能盯「我报的那一条有没有被写进去」。 */
const logAt = (t) => Number(sqlOne(
  `USE db_distribution; SELECT COUNT(*) FROM temperature_log WHERE transport_id=(SELECT id FROM cold_chain_transport WHERE transport_no='${transportNo}') AND record_time='${timeText(t)}';`))

try {
  // ---------------------------------------------------------------- 0 一次性主体
  console.log('\n▶ 0 自建一趟「运输中」的运单（不借演示台账的在途车）')
  const splitBatchId = sqlOne('USE db_distribution; SELECT IFNULL(MAX(id),0) FROM split_batch;')
  const depart = new Date(Date.now() - 2 * 3600 * 1000)
  sql(`USE db_distribution; INSERT INTO cold_chain_transport
       (transport_no, split_batch_id, vehicle_no, driver_source, driver_confirmed, stop_count, status, depart_time, origin, destination, source)
       VALUES ('${transportNo}', ${splitBatchId}, '${PLATE}', 'MANUAL', 0, 1, 2, '${timeText(depart)}', '测试产地', '测试门店', 'MANUAL');`)
  const tid = sqlOne(`USE db_distribution; SELECT id FROM cold_chain_transport WHERE transport_no='${transportNo}';`)
  check('0 一次性运单已建立（status=2 运输中，2 小时前发车）', !!tid && tid !== '', `tid=${tid}`)

  // ---------------------------------------------------------------- 1 时钟跑到未来
  console.log('\n▶ 1 车载终端时钟错乱：记录时间跑到未来 → 拒绝（不是转人工）')
  const future = new Date(Date.now() + 2 * 24 * 3600 * 1000)
  const r1 = await report('TEMP-001', `${PASS}-FUTURE`,
    { transportNo, temperature: 4.1, recorder: '冷机1号' }, timeText(future))
  check('1a 状态 = 已拒绝(3)', r1.status === 3, `status=${r1.status} msg=${r1.message}`)
  check('1b 回执说清「晚于当前时间」', /晚于当前时间/.test(r1.message), r1.message)
  check('1c 没有写进温度台账（拒绝就是不入账）', logAt(future) === 0, `该时刻已有 ${logAt(future)} 条`)

  // ---------------------------------------------------------------- 2 不许误伤：预冷
  console.log('\n▶ 2 装车前预冷（发车之前）照常入账 —— 冷藏车预冷是常态，闸门只卡「未来」')
  const precool = new Date(depart.getTime() - 30 * 60 * 1000)
  const r2 = await report('TEMP-001', `${PASS}-PRECOOL`,
    { transportNo, temperature: 2.6, recorder: '冷机1号' }, timeText(precool))
  check('2a 预冷入账成功', r2.status === 2, `status=${r2.status} msg=${r2.message}`)
  check('2b 预冷记录已落库', logAt(precool) === 1, `该时刻 ${logAt(precool)} 条`)

  // ---------------------------------------------------------------- 3 不许误伤：整段补传
  console.log('\n▶ 3 整段补传（比发车更早）照常入账 —— 「这条温度属于哪一趟」由报文里的运单号归属，不靠时间去猜')
  const backfill = new Date(depart.getTime() - 4 * 3600 * 1000)
  const r3 = await report('TEMP-001', `${PASS}-BACKFILL`,
    { transportNo, temperature: 3.2, recorder: '冷机1号' }, timeText(backfill))
  check('3a 补传入账成功', r3.status === 2, `status=${r3.status} msg=${r3.message}`)
  check('3b 补传记录已落库（时间取设备给的时刻，不是「现在」）', logAt(backfill) === 1,
    `该时刻 ${logAt(backfill)} 条`)

  // ---------------------------------------------------------------- 4 正常在途
  console.log('\n▶ 4 在途实时温度照常入账')
  const inside = new Date(depart.getTime() + 20 * 60 * 1000)
  const r4 = await report('TEMP-001', `${PASS}-INSIDE`,
    { transportNo, temperature: 4.0, recorder: '冷机1号' }, timeText(inside))
  check('4a 在途温度入账成功', r4.status === 2, `status=${r4.status} msg=${r4.message}`)
  check('4b 在途记录已落库', logAt(inside) === 1, `该时刻 ${logAt(inside)} 条`)
} finally {
  // ---------------------------------------------------------------- 清理
  console.log('\n▶ 5 清理本脚本自建的数据')
  sql(`USE db_distribution; DELETE FROM temperature_log WHERE transport_id IN
       (SELECT id FROM cold_chain_transport WHERE transport_no='${transportNo}');
       DELETE FROM cold_chain_transport WHERE transport_no='${transportNo}';
       USE db_ingest; DELETE FROM ingest_staging WHERE channel='TEMPERATURE' AND biz_key LIKE '${PASS}-%';`)
  const left = sqlOne(`USE db_distribution; SELECT COUNT(*) FROM cold_chain_transport WHERE transport_no='${transportNo}';`)
  const leftStaging = sqlOne(`USE db_ingest; SELECT COUNT(*) FROM ingest_staging WHERE channel='TEMPERATURE' AND biz_key LIKE '${PASS}-%';`)
  check('5 一次性运单已删除（不留残骸）', String(left) === '0', `剩余 ${left}`)
  check('5b 本轮的报文流水已删除（被拒绝的流水也要清）', String(leftStaging) === '0', `剩余 ${leftStaging}`)
  console.log(`\n结果：通过 ${pass} / 失败 ${fail}`)
  process.exit(fail === 0 ? 0 : 1)
}
