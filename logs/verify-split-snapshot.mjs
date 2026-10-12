/**
 * 分割车间监控快照回归（SPLIT_SNAPSHOT 通道 / workshop_snapshot 表）：
 *
 *   这条通道与其它所有通道的本质差别是「不产生溯源事实」——只落一张带时间戳的现场影像，
 *   不参与任何业务判定。所以断言的重点不是业务规则，而是三条工程口径：
 *
 *     1. 两段式上报能通：照片先经文件服务换成 fileId，报文只带 fileId；
 *        接入层调 /file/{fileId}/meta 核对真实性。
 *     2. 凭证防伪生效：编造的 fileId → 已拒绝（retryable=false，外部应删单重传）；
 *        缺 fileId → 结构性拒绝（validate 阶段）。
 *     3. 失败不进人工队列：照片是周期快照，服务抖动丢弃即可，转人工只会淹没队列。
 *     4. 绑定关系（2026-10-12）：报文中若带 batchNo（工位终端告知的「当前正在分割的白条
 *        属于哪个批次」），照片就归属到该批次；报了系统里不存在的批次号 = 编造绑定 → 已拒绝；
 *        不带 batchNo 仍照常入账（只是不落归属）。页面据此「换批次就看那一次分割的现场」。
 *
 *   另含「滚动窗口」（每台相机只留最近 N 张，默认 20）的端到端验证：
 *   连续上报到超过上限后，最老的一批必须已被淘汰、总数收敛到上限。
 *
 *   ⚠️ 数据安全：滚动窗口是**按设备**生效的（pruneOldSnapshots(deviceNo)），只要往一台
 *   相机写快照，写满上限就会挤掉它最老的那张 —— 哪怕那张是界面演示留下的真实数据。
 *   所以本脚本的写入必须打在一台**一次性测试相机**上，绝不能打在生产相机上。
 *      由 bash 侧（verify-split-snapshot-db.sh）先登记 CAM-SPLIT-TEST 再以
 *      SNAPSHOT_DEVICE=CAM-SPLIT-TEST 调起；不传则退化为只读检查（见下）。
 *
 *   运行：SNAPSHOT_DEVICE=CAM-SPLIT-TEST node logs/verify-split-snapshot.mjs
 *         （不传 SNAPSHOT_DEVICE 时，若目标相机已有快照则跳过全部写入节，只验接口可用）
 *   结束后打印清理用 ID（快照 id / 文件 fileId / 接入流水），由 bash 侧清理。
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
// 设备号可外部指定：写到一台一次性测试相机上，才不至于挤掉生产相机里既有的快照。
const DEVICE_NO = process.env.SNAPSHOT_DEVICE || 'CAM-SPLIT-001'
const RETENTION = Number(process.env.SNAPSHOT_RETENTION || 20)

let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  [PASS] ${name}`)
  } else {
    fail++
    console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`)
  }
}

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers: h,
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json().catch(() => null)
}

const pad = (n) => String(n).padStart(2, '0')
function nowText(offsetSeconds = 0) {
  const d = new Date(Date.now() + offsetSeconds * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const report = (data, extra = {}) =>
  api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo: DEVICE_NO, data, ...extra } })

const listSnapshots = async (limit = 50) => {
  const res = await api(`/distribution/split-snapshots?deviceNo=${DEVICE_NO}&limit=${limit}`, { token })
  return Array.isArray(res?.data) ? res.data : []
}

console.log('\n== 0) 探底：这台相机当前的快照数（决定能不能安全写入）==')
const baselineRows = await listSnapshots(50)
const baselineIds = new Set(baselineRows.map((r) => r.id))
check('快照查询接口可用', Array.isArray(baselineRows), `返回类型=${typeof baselineRows}`)
console.log(`     当前 ${baselineRows.length} 张`)

/**
 * 写入前的安全闸门。
 *
 * 滚动窗口按设备生效：这台相机只要写满上限，最老的一张就会被淘汰。如果探到这里
 * 已经有快照（很可能是界面演示留下的真实数据），继续写就是在删别人的数据 ——
 * 回归脚本没有这个权力。此时只报「接口可用」，把写入节整体跳过，并指明正确跑法。
 */
if (baselineRows.length > 0) {
  console.log(`\n⚠️  跳过写入节：相机 ${DEVICE_NO} 已有 ${baselineRows.length} 张快照。`)
  console.log('    滚动窗口按设备生效，再写就会挤掉这台相机里既有的数据（可能来自界面演示）。')
  console.log('    完整回归请打在一台一次性相机上（verify-split-snapshot-db.sh 会自行登记）：')
  console.log('      SNAPSHOT_DEVICE=CAM-SPLIT-TEST node logs/verify-split-snapshot.mjs')
  console.log(`\n===== 结果：${pass} 项通过，${fail} 项失败（写入节已跳过）=====`)
  console.log('#DEVICE ' + DEVICE_NO)
  console.log('#SKIPPED 1')
  process.exit(fail === 0 ? 0 : 1)
}

// 上传一张演示照片（两段式的第一段：图片走文件服务换 fileId）
const here = dirname(fileURLToPath(import.meta.url))
const demoPhoto = readFileSync(join(here, '..', 'web-admin', 'public', 'demo', 'vaccine-voucher.png'))
const uploadedFileIds = []
/**
 * 每张快照都上传一份照片，模拟真实「相机一次快门 = 一次上传」。
 * 注意：文件服务按内容哈希去重，演示图只有一张，所以多次上传拿到的是**同一个 fileId**。
 * 这恰好把「多张快照共用一份文件」这条真实可能发生的路径也覆盖到了 ——
 * 滚动淘汰时必须先确认「没有别的快照还指向这份文件」才删，否则会把还在用的照片删掉，
 * 后续上报随即被接入层的凭证核对判成「照片不存在（疑似伪造）」。
 */
async function uploadPhoto(name = 'split-snapshot.png') {
  const form = new FormData()
  form.append('file', new Blob([demoPhoto], { type: 'image/png' }), name)
  const res = await fetch(`${BASE}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  }).then((r) => r.json()).catch(() => null)
  const id = res?.data?.fileId
  if (id) uploadedFileIds.push(id)
  return id
}

const fileId = await uploadPhoto()
check('照片上传文件服务拿到 fileId（两段式的第一段）', !!fileId, 'fileId=' + fileId)

console.log('\n== 1) 正常上报：应入账，且落库的是「拍摄时刻 + 车间」==')
const captured = nowText(-5)
const first = await report({ fileId, capturedTime: captured, workshop: '分割一号线' })
const firstId = first?.data?.targetId
check('★ 快照上报已入账', first?.data?.accepted === true, JSON.stringify(first?.data).slice(0, 260))
check('★ 回执带出落库记录编号', firstId != null, `targetId=${firstId}`)
check('★ 目标表是 workshop_snapshot', first?.data?.targetTable === 'workshop_snapshot', `targetTable=${first?.data?.targetTable}`)

const rowsAfter = (await listSnapshots(50)).filter((r) => r.id === firstId)
const row = rowsAfter[0]
check('★ 查询接口能查到这张快照', !!row, `id=${firstId}`)
check('★ 拍摄时刻取的是报文时间（不是入库时刻）', String(row?.capturedTime || '').startsWith(captured), `capturedTime=${row?.capturedTime} 期望前缀=${captured}`)
check('★ 车间与 fileId 原样落库', row?.workshop === '分割一号线' && row?.fileId === fileId, `workshop=${row?.workshop} fileId=${row?.fileId}`)
check('★ 记录里没有操作人字段被设备名顶替（照片不产生责任）', row?.operator === undefined || row?.operator === null, `operator=${row?.operator}`)

console.log('\n== 2) 凭证防伪：编造的 fileId 必须被拒 ==')
const bogus = await report({ fileId: 'FAKE-FILE-ID-NOT-EXIST', capturedTime: nowText(), workshop: '分割一号线' })
check('★ 伪造 fileId → 已拒绝', bogus?.data?.accepted !== true && bogus?.data?.status === 3,
  `status=${bogus?.data?.status} message=${bogus?.data?.message}`)
check('★ 原因点明「疑似伪造凭证」', String(bogus?.data?.message || '').includes('伪造'),
  `message=${bogus?.data?.message}`)
check('★ 回执状态文案是「已拒绝」', bogus?.data?.statusLabel === '已拒绝',
  `statusLabel=${bogus?.data?.statusLabel}`)

console.log('\n== 3) 结构性错误：报文没带照片 → 直接拒绝（不进人工队列）==')
const noPhoto = await report({ capturedTime: nowText(), workshop: '分割一号线' })
check('★ 缺 fileId → 已拒绝', noPhoto?.data?.accepted !== true && noPhoto?.data?.status === 3,
  `status=${noPhoto?.data?.status} message=${noPhoto?.data?.message}`)
check('★ 原因写明缺少照片', String(noPhoto?.data?.message || '').includes('缺少照片'),
  `message=${noPhoto?.data?.message}`)
check('★ 不是「待人工处理」（照片空缺不需要人插手）', noPhoto?.data?.status !== 1,
  `status=${noPhoto?.data?.status}`)

let testIds = [firstId].filter(Boolean)

// 能走到这里，说明探底时相机是空的（有数据早在 0) 节就退出了），可以放心把张数顶过上限。
console.log(`\n== 4) ★ 滚动窗口：连续上报超过上限（${RETENTION} 张）后，最老的必须被淘汰 ==`)
const baselineMax = Math.max(0, ...(await listSnapshots(50)).map((r) => r.id))
// 复用同一张真照片（接入层只核对 fileId 是否存在，不核对内容），把张数顶过上限
const burst = RETENTION - testIds.length + 3
const created = []
for (let i = 0; i < burst; i++) {
  const photo = await uploadPhoto(`split-snapshot-${i}.png`)
  const r = await report({ fileId: photo, capturedTime: nowText(-60 + i), workshop: '分割一号线' })
  if (r?.data?.targetId) created.push(r.data.targetId)
}
testIds = testIds.concat(created)
check(`★ 突发上报 ${burst} 张全部入账`, created.length === burst, `入账=${created.length} 期望=${burst}`)

const burstRows = await listSnapshots(50)
check(`★ 总数收敛到上限 ${RETENTION}（滚动窗口生效，不是无限增长）`,
  burstRows.length === RETENTION, `实际=${burstRows.length} 期望=${RETENTION}`)
check('★ 上限之前的老快照已被淘汰（剩下的全部是新写入的）',
  burstRows.every((r) => r.id > baselineMax),
  `最老的剩余 id=${Math.min(...burstRows.map((r) => r.id))} 应 > ${baselineMax}`)

// 绑定用例放在**最后**跑：它们是最新的几张，不会被上面的滚动窗口淘汰掉，
// 这样 bash 侧才能在最干净的时点上对「库里的绑定列」下断言。
console.log('\n== 5) 绑定：照片归属到「这一次分割」（带批次号 / 不带 / 报假批次号）==')
// 取一个真实存在的胴体批次：绑定必须落在真批次上，编的批次号要被拒
const batchPage = await api('/distribution/batches?pageNum=1&pageSize=1', { token })
const realBatchNo = (batchPage?.data?.records || batchPage?.data?.list || batchPage?.data || [])[0]?.batchNo
check('拿到一个真实批次号用于绑定用例', !!realBatchNo, `batchNo=${realBatchNo}`)

if (realBatchNo) {
  // ① 带批次号 → 入账并绑定；查询接口按 batchNo 能过滤出来
  const bindPhoto = await uploadPhoto('split-snapshot-bind.png')
  const bindRes = await report({ fileId: bindPhoto, capturedTime: nowText(-3), workshop: '分割一号线', batchNo: realBatchNo })
  const bindId = bindRes?.data?.targetId
  check('★ 带批次号上报 → 已入账', bindRes?.data?.accepted === true, JSON.stringify(bindRes?.data).slice(0, 260))
  // 注意：成功回执的 message 一律是「已入账」，**不带业务附言**（绑定事实落业务表、由页面展示），
  // 所以这里不要去断回执文案 —— 绑定是否真的落库，看下面的查询断言。
  if (bindId) testIds.push(bindId)

  const byBatch = await api(`/distribution/split-snapshots?batchNo=${encodeURIComponent(realBatchNo)}&limit=50`, { token })
  const boundRow = (Array.isArray(byBatch?.data) ? byBatch.data : []).find((r) => r.id === bindId)
  check('★ 按批次查询能查到这张照片', !!boundRow, `id=${bindId} batchNo=${realBatchNo}`)
  check('★ 落库的批次号是规范写法（不是设备传来的原文）', boundRow?.batchNo === realBatchNo,
    `落库 batchNo=${boundRow?.batchNo} 期望=${realBatchNo}`)
  check('★ 同批次的照片能反查出这次分割的作业号（有作业时）',
    boundRow?.jobNo === undefined || boundRow?.jobNo === null || typeof boundRow.jobNo === 'string',
    `jobNo=${boundRow?.jobNo}`)

  // ② 报一个系统里不存在的批次号 → 编造绑定关系，必须拒绝（比不绑定更坏：页面会把它当某次分割的现场）
  const fake = await report({ fileId: bindPhoto, capturedTime: nowText(), workshop: '分割一号线', batchNo: 'CB-NOT-EXIST-19000101-999' })
  check('★ 编造批次号 → 已拒绝', fake?.data?.accepted !== true && fake?.data?.status === 3,
    `status=${fake?.data?.status} message=${fake?.data?.message}`)
  check('★ 原因写明「查不到该批次」', String(fake?.data?.message || '').includes('查不到该批次'),
    `message=${fake?.data?.message}`)
  check('★ 编造绑定不是「待人工处理」（照片不进人工队列）', fake?.data?.status !== 1,
    `status=${fake?.data?.status}`)
}

// ③ 不带批次号 → 仍然入账，只是不落归属（相机没有工位信息时的正常路径）
const noBindPhoto = await uploadPhoto('split-snapshot-nobind.png')
const noBind = await report({ fileId: noBindPhoto, capturedTime: nowText(-2), workshop: '分割一号线' })
check('★ 不带批次号 → 照常入账（照片只是现场影像）', noBind?.data?.accepted === true,
  JSON.stringify(noBind?.data).slice(0, 200))
if (noBind?.data?.targetId) testIds.push(noBind.data.targetId)

// 绑定节又写了两张：滚动窗口依旧是「谁新留谁」，总数必须还停在上限
const finalRows = await listSnapshots(50)
check(`★ 加写绑定用例后总数仍收敛在上限 ${RETENTION}`,
  finalRows.length === RETENTION, `实际=${finalRows.length} 期望=${RETENTION}`)
check('★ 最新的一张是「未绑定」的那张（不因为别人绑了批次就蹭上归属）',
  finalRows[0]?.batchNo == null, `最新 id=${finalRows[0]?.id} batchNo=${finalRows[0]?.batchNo}`)
check('★ 带批次号的那张确实绑上了（查得到）',
  finalRows.some((r) => r.batchNo && r.batchNo === realBatchNo),
  `批次数=${finalRows.filter((r) => r.batchNo).length} 期望批次=${realBatchNo}`)

// 淘汰掉的 id 也要报告出来（它们已被服务端删除，bash 侧的清理只需处理剩下的）
console.log(`     淘汰的 id（服务端已删）：${testIds.filter((id) => !finalRows.some((r) => r.id === id)).join(',')}`)

console.log(`\n===== 结果：${pass} 项通过，${fail} 项失败 =====`)
console.log('#DEVICE ' + DEVICE_NO)
console.log('#FILEID ' + (fileId || ''))
console.log('#ALL_FILES ' + uploadedFileIds.join(','))
console.log('#SNAPSHOT_IDS ' + testIds.join(','))
console.log('#BOUND_BATCH ' + (realBatchNo || ''))
process.exit(fail === 0 ? 0 : 1)
