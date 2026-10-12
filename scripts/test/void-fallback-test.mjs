#!/usr/bin/env node
/**
 * 作废留痕 + 人工兜底解锁 · 验收测试（设备独占版）
 * ---------------------------------------------------------------------------
 * 在「数据采集类只留设备自动写入」之后，验证剩下的三条规矩有没有被削弱：
 *   1) 唯一保留的人工采集口（产地检疫出证）必须实名 + 申请兜底解锁才放行
 *   2) 结论字段禁止原地改，只能作废 + 重录；非结论字段可改且留痕
 *   3) 作废与新增同级受控（实名 + 理由 + 票据 + 上链），堵死「用作废绕开管控」
 *   4) 追溯统一取「当前有效记录」，作废记录不物理删、带标注保留
 *
 * 用法：
 *   node scripts/test/void-fallback-test.mjs
 *   node scripts/test/void-fallback-test.mjs --base http://localhost:8080/api/v1
 *
 * 覆盖用例：
 *   1  策略接口     —— 人工采集入口只剩 1 个「设备优先」环节（产地检疫出证）
 *   2  申请理由过短 —— 少于 4 个字被拒
 *   3  设备独占环节 —— 不可申请兜底解锁（该环节人工入口已整条删除）
 *   4  正常申请     —— 返回票据 / 失效时间 / 申请人
 *   5  解锁记录列表 —— 能看到刚申请的那条，状态为「生效中」
 *   6  无票据出证   —— 唯一人工入口：无票据被拒（【兜底管控】）
 *   7  伪造票据     —— 签名对不上，被拒
 *   8  带票据出证   —— 放行
 *   9  作废理由过短 —— 被拒
 *  10  无票据作废   —— 设备优先环节的作废同样被拒（与新增同级受控）
 *  11  带票据作废   —— 放行
 *  12  作废后不可见 —— 不再作为当前有效检疫证返回
 *  13  结论字段修改 —— 设备写入的疫苗记录改「疫苗批号」被拒（【结论保护】）
 *  14  说明性字段修改 —— 只改附件放行
 *  15  设备独占作废 —— 实名 + 理由可作废（设备录错的数据必须能清掉，且不需票据）
 *  16  追溯作废标注 —— /slaughter/internal/pigs 返回 voided，含环节/单号/理由/操作人
 *  17  关闭解锁     —— 解锁记录可提前关闭并留痕
 *
 * 副作用：本脚本会写入 1 张产地检疫证并作废它、由设备通道写入 1 条疫苗记录与 1 条瘦肉精记录、
 * 并留下若干条兜底解锁申请记录。这些都是「留痕」的一部分，不构成脏数据；
 * 检疫证、瘦肉精记录均通过正常的作废流程处理，不会被物理删除。
 */

const args = process.argv.slice(2)
const argValue = (name, fallback) => {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback
}
const BASE = argValue('--base', 'http://localhost:8080/api/v1')
const ADMIN = { username: argValue('--user', 'admin'), password: argValue('--pass', '123456') }

function nowText(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}

const results = []
let token = null

function record(no, name, passed, detail) {
  results.push({ no, name, passed, detail })
  console.log(`${passed ? '✅ PASS' : '❌ FAIL'}  [${no}] ${name}\n        ${detail}`)
}

function skip(no, name, detail) {
  results.push({ no, name, passed: true, skipped: true, detail })
  console.log(`⚪ SKIP  [${no}] ${name}\n        ${detail}`)
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  const text = await response.text()
  try {
    return { httpStatus: response.status, ...JSON.parse(text) }
  } catch {
    return { httpStatus: response.status, message: text }
  }
}

async function login() {
  const res = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  const data = res.data || {}
  token = data.accessToken || data.token || data.access_token
  if (!token) throw new Error(`登录失败：${res.message || JSON.stringify(res).slice(0, 200)}`)
  console.log(`登录成功（${ADMIN.username}）\n`)
}

/** 三类管控各自有独立的提示前缀，判定必须分开，否则会把 A 的拦截误当成 B 生效 */
const blockedBy = (res, keyword) =>
  Number(res?.code) === 2003 && String(res?.message || '').includes(keyword)
const isFallbackBlocked = (res) => blockedBy(res, '兜底管控')
const isConclusionBlocked = (res) => blockedBy(res, '结论保护')

async function firstPig() {
  const res = await api('/breeding/pigs?current=1&size=5')
  return (res.data?.records || res.data?.list || [])[0] || null
}

async function vaccineRows(pigId) {
  const res = await api(`/breeding/pigs/${pigId}/vaccines/list`)
  return Array.isArray(res.data) ? res.data : []
}

async function unlockFor(stage, reason) {
  return api('/ingest/fallback/unlock', {
    method: 'POST',
    body: JSON.stringify({ stage, reason }),
  })
}

/** 合法的产地检疫出证报文（字段必须齐全，否则拿到的只是参数校验失败） */
function certBody(pig) {
  return {
    pigId: pig.id,
    certNo: `VOID-FB-QC-${Date.now()}`,
    issueOrg: '验收测试签发机构',
    issueTime: nowText(),
    validUntil: '2027-12-31',
    inspector: '验收测试官方兽医',
    fileId: 'void-fb-test-file',
    caSignature: 'Vk9JRC1GQi1URVNU',
  }
}

async function main() {
  console.log('='.repeat(78))
  console.log('作废留痕 + 人工兜底解锁 · 验收测试（设备独占版）')
  console.log('='.repeat(78))
  await login()

  // ---------------------------------------------------------------- 用例 1
  const policyRes = await api('/ingest/source-policy')
  const policies = Array.isArray(policyRes.data) ? policyRes.data : []
  const preferred = policies.filter((p) => p.policy === 'DEVICE_PREFERRED')
  record(1, '人工采集入口已收敛到唯一一个「设备优先」环节',
    preferred.length === 1 && preferred[0].code === 'QUARANTINE',
    `需兜底解锁的环节：${preferred.map((p) => `${p.label}(${p.code})`).join('、') || '无'}`)

  const pig = await firstPig()
  if (!pig) {
    for (let n = 2; n <= 17; n += 1) skip(n, '依赖生猪档案的用例', '库中没有生猪档案')
  } else {
    // -------------------------------------------------------------- 用例 2
    const tooShort = await unlockFor('QUARANTINE', '出证')
    record(2, '兜底解锁：理由过短被拒',
      Number(tooShort.code) === 1002 || Number(tooShort.code) === 1001,
      `提交理由「出证」（2 字）：code=${tooShort.code}，message=${tooShort.message}`)

    // -------------------------------------------------------------- 用例 3
    // TAG 现在是设备独占：该环节人工入口已整条删除，申请兜底解锁也没有意义，应被明确拒绝
    const blockedStage = await unlockFor('TAG', '设备故障需要人工补建一头生猪档案')
    record(3, '兜底解锁：设备独占环节不可申请（该环节人工入口已删除）',
      Number(blockedStage.code) !== 200 && String(blockedStage.message || '').includes('设备独占'),
      `提交 TAG：code=${blockedStage.code}，message=${blockedStage.message}`)

    // -------------------------------------------------------------- 用例 4
    const unlock = await unlockFor('QUARANTINE', `兜底解锁验收测试：官方兽医终端故障，需人工出具检疫证 ${Date.now()}`)
    const ticket = unlock.data?.ticket
    record(4, '兜底解锁：申请成功并签发限时票据',
      Number(unlock.code) === 200 && !!ticket && String(ticket).startsWith('v1|'),
      `id=${unlock.data?.id}，环节=${unlock.data?.stageLabel}，申请人=${unlock.data?.applicant}，` +
      `失效时间=${unlock.data?.expireTime}，票据前缀=${String(ticket || '').slice(0, 24)}...`)

    // -------------------------------------------------------------- 用例 5
    const listRes = await api('/ingest/fallback/unlocks?stage=QUARANTINE')
    const mine = (Array.isArray(listRes.data) ? listRes.data : []).find((r) => r.id === unlock.data?.id)
    record(5, '解锁记录列表：申请过程完整留档',
      !!mine && mine.active === true && !!mine.reason && !!mine.applicant,
      mine ? `状态=${mine.statusLabel}，理由=${String(mine.reason).slice(0, 30)}...，申请人=${mine.applicant}` : '未在列表中查到该记录')

    // -------------------------------------------------------------- 用例 6
    const noTicketCert = await api(`/breeding/pigs/${pig.id}/quarantine-cert`, {
      method: 'POST',
      body: JSON.stringify(certBody(pig)),
    })
    record(6, '唯一人工入口：无票据出证被拒',
      isFallbackBlocked(noTicketCert),
      `code=${noTicketCert.code}，message=${noTicketCert.message}`)

    // -------------------------------------------------------------- 用例 7
    // 伪造票据：结构像模像样，但签名对不上——服务端必须靠 HMAC 校验识破，而不是只看格式
    const forged = `v1|QUARANTINE|${Date.now() + 3600_000}|1|admin|FORGED-SIGNATURE`
    const forgedRes = await api(`/breeding/pigs/${pig.id}/quarantine-cert`, {
      method: 'POST',
      headers: { 'X-Fallback-Ticket': forged },
      body: JSON.stringify({ ...certBody(pig), certNo: `VOID-FB-QC-FORGED-${Date.now()}` }),
    })
    record(7, '伪造签名票据被拒',
      isFallbackBlocked(forgedRes),
      `code=${forgedRes.code}，message=${forgedRes.message}`)

    // -------------------------------------------------------------- 用例 8
    const body = certBody(pig)
    const withTicket = await api(`/breeding/pigs/${pig.id}/quarantine-cert`, {
      method: 'POST',
      headers: { 'X-Fallback-Ticket': ticket },
      body: JSON.stringify(body),
    })
    let cert = null
    if (Number(withTicket.code) === 200) {
      const got = await api(`/breeding/pigs/${pig.id}/quarantine-cert`)
      cert = got.data || null
    }
    record(8, '唯一人工入口：带票据出证放行',
      Number(withTicket.code) === 200 && !!cert?.id,
      `code=${withTicket.code}；检疫证 id=${cert?.id}，编号=${cert?.certNo}，状态=${cert?.statusLabel ?? cert?.status}`)

    // 后续用例都依赖这张证
    if (!cert?.id) {
      for (let n = 9; n <= 12; n += 1) skip(n, '依赖新增检疫证', '用例 8 未成功出具检疫证')
    } else {
      // ------------------------------------------------------------ 用例 9
      const shortReasonVoid = await api(`/breeding/pigs/${pig.id}/quarantine-cert/${cert.id}/void`, {
        method: 'POST',
        headers: { 'X-Fallback-Ticket': ticket },
        body: JSON.stringify({ reason: '错' }),
      })
      record(9, '作废：理由过短被拒',
        Number(shortReasonVoid.code) === 1001 || Number(shortReasonVoid.code) === 1002,
        `提交理由「错」（1 字）：code=${shortReasonVoid.code}，message=${shortReasonVoid.message}`)

      // ------------------------------------------------------------ 用例 10
      const noTicketVoid = await api(`/breeding/pigs/${pig.id}/quarantine-cert/${cert.id}/void`, {
        method: 'POST',
        body: JSON.stringify({ reason: '验收测试：作废这张测试检疫证' }),
      })
      record(10, '作废与新增同级受控：设备优先环节无票据作废被拒',
        isFallbackBlocked(noTicketVoid),
        `code=${noTicketVoid.code}，message=${noTicketVoid.message}`)

      // ------------------------------------------------------------ 用例 11
      const voided = await api(`/breeding/pigs/${pig.id}/quarantine-cert/${cert.id}/void`, {
        method: 'POST',
        headers: { 'X-Fallback-Ticket': ticket },
        body: JSON.stringify({ reason: '验收测试：作废这张测试检疫证，验证作废留痕' }),
      })
      record(11, '设备优先环节：带票据作废放行',
        Number(voided.code) === 200,
        `code=${voided.code}，message=${voided.message || '已作废'}`)

      // ------------------------------------------------------------ 用例 12
      const afterVoid = await api(`/breeding/pigs/${pig.id}/quarantine-cert`)
      const stillCurrent = afterVoid.data && Number(afterVoid.data.id) === Number(cert.id)
      record(12, '作废后不再作为当前有效检疫证返回（追溯统一取当前有效记录）',
        !stillCurrent,
        stillCurrent
          ? `作废的检疫证 id=${cert.id} 仍然被当作有效记录返回，说明取数没走有效记录`
          : `当前有效检疫证已不含 id=${cert.id}（记录仍在库中，未物理删除）`)
    }

    // -------------------------------------------------------------- 用例 13~14
    // 结论字段保护与说明性字段放行，落在疫苗记录上：
    // 人工新增入口虽然删了，但「设备写入的记录可以补充说明性信息」这条通道必须保留，
    // 同时结论字段（批号/剂量/接种人等）必须仍然改不动。
    const deviceBatch = `VOID-FB-DEV-${Date.now()}`
    const demoVac = await api('/ingest/demo/report', {
      method: 'POST',
      body: JSON.stringify({
        deviceNo: 'INJ-001',
        bizKey: deviceBatch,
        data: {
          earTagNo: pig.earTagNo,
          vaccineName: '设备写入的验收测试疫苗',
          vaccineBatchNo: deviceBatch,
          injectTime: nowText(),
          dosage: '1ml',
          operator: '智能注射器',
        },
      }),
    })
    const vacId = demoVac.data?.targetId
    const vacRow = vacId ? (await vaccineRows(pig.id)).find((r) => Number(r.id) === Number(vacId)) : null
    if (!demoVac.data?.accepted || !vacRow) {
      skip(13, '结论字段禁止原地修改', `设备上报未直接入账（status=${demoVac.data?.status}：${demoVac.data?.message}），跳过`)
      skip(14, '说明性字段允许修改并留痕', '无设备写入的疫苗记录可改，跳过')
    } else {
      const conclusionEdit = await api(`/breeding/pigs/${pig.id}/vaccines/${vacRow.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          id: vacRow.id,
          pigId: pig.id,
          vaccineName: vacRow.vaccineName,
          batchNo: 'VOID-FB-TAMPERED', // 结论字段：改批号
          injectTime: vacRow.injectTime,
          dosage: vacRow.dosage,
          operator: vacRow.operator,
          fileIds: [],
        }),
      })
      record(13, '结论字段禁止原地修改（改疫苗批号被拒）',
        isConclusionBlocked(conclusionEdit),
        `code=${conclusionEdit.code}，message=${conclusionEdit.message}`)

      const noteEdit = await api(`/breeding/pigs/${pig.id}/vaccines/${vacRow.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          id: vacRow.id,
          pigId: pig.id,
          vaccineName: vacRow.vaccineName,
          batchNo: vacRow.batchNo,
          injectTime: vacRow.injectTime,
          dosage: vacRow.dosage,
          operator: vacRow.operator,
          fileIds: ['acceptance-test-attachment'],
        }),
      })
      record(14, '说明性字段允许修改并留痕（只改附件放行）',
        Number(noteEdit.code) === 200,
        `code=${noteEdit.code}，message=${noteEdit.message || '已放行'}`)
    }

    // -------------------------------------------------------------- 用例 15~16
    // 用设备通道写入一条瘦肉精记录（设备独占环节，人工新增已删除），再用实名 + 理由作废它，
    // 最后从追溯内部接口确认 voided 标注确实带出来了。
    const demo = await api('/ingest/demo/report', {
      method: 'POST',
      body: JSON.stringify({
        deviceNo: 'READER-001',
        bizKey: `VOID-FB-${Date.now()}`,
        data: {
          earTagNo: pig.earTagNo,
          batchNo: 'VOID-FB-TEST',
          sampleNo: `VOID-S-${Date.now()}`,
          result: 1,
          testTarget: '克伦特罗',
          samplePart: '尿液',
        },
      }),
    })
    const testId = demo.data?.targetId
    if (!demo.data?.accepted || !testId) {
      skip(15, '设备独占环节实名可作废', `设备上报未直接入账（status=${demo.data?.status}：${demo.data?.message}），跳过`)
      skip(16, '追溯返回作废标注', '无作废记录可查，跳过')
    } else {
      // 设备独占环节的作废：不要求票据（人工入口本就关闭，无从"绕开"），
      // 但实名 + 理由必须落实——否则设备录错的数据就永远清不掉了。
      const voidTest = await api(`/slaughter/ractopamine/${testId}/void`, {
        method: 'POST',
        body: JSON.stringify({ reason: '验收测试：样本编号录入有误，作废后重录' }),
      })
      record(15, '设备独占环节（瘦肉精）实名 + 理由可作废（不需票据）',
        Number(voidTest.code) === 200,
        `作废设备写入的记录 id=${testId}：code=${voidTest.code}，message=${voidTest.message || '已作废'}`)

      const internal = await api(`/slaughter/internal/pigs?ids=${pig.id}`)
      const row = (Array.isArray(internal.data) ? internal.data : []).find((r) => Number(r.pigId) === Number(pig.id))
      const voidedRows = Array.isArray(row?.voided) ? row.voided : []
      const hit = voidedRows.find((v) => Number(v.id) === Number(testId))
      record(16, '追溯接口返回作废标注（环节 / 单号 / 理由 / 操作人 / 时间）',
        !!hit && !!hit.voidReason && !!hit.voidOperator,
        hit
          ? `voided 命中：环节=${hit.stageLabel}，单号=${hit.bizNo}，理由=${hit.voidReason}，操作人=${hit.voidOperator}，时间=${hit.voidTime}`
          : `voided 未命中 id=${testId}（该生猪当前作废记录 ${voidedRows.length} 条）`)
    }

    // -------------------------------------------------------------- 用例 17
    const closeRes = await api(`/ingest/fallback/unlock/${unlock.data?.id}/close?reason=${encodeURIComponent('验收测试：用完即关')}`, {
      method: 'POST',
    })
    const afterClose = await api('/ingest/fallback/unlocks?stage=QUARANTINE')
    const closedRow = (Array.isArray(afterClose.data) ? afterClose.data : []).find((r) => r.id === unlock.data?.id)
    record(17, '兜底解锁可提前关闭并留痕',
      Number(closeRes.code) === 200 && closedRow?.closed === true && !!closedRow?.closeReason,
      `code=${closeRes.code}；状态=${closedRow?.statusLabel}，关闭人=${closedRow?.closeOperator}，关闭理由=${closedRow?.closeReason}`)
  }

  // ---------------------------------------------------------------- 汇总
  const failed = results.filter((r) => !r.passed)
  const skipped = results.filter((r) => r.skipped)
  console.log('\n' + '='.repeat(78))
  console.log(`结果：通过 ${results.length - failed.length - skipped.length} / ${results.length - skipped.length}` +
    (skipped.length ? `（跳过 ${skipped.length}）` : ''))
  if (failed.length) {
    console.log('失败用例：')
    failed.forEach((r) => console.log(`  - [${r.no}] ${r.name}：${r.detail}`))
    process.exitCode = 1
  } else {
    console.log('人工采集只余产地检疫出证且须先申请；结论只能公开作废重录；追溯只取当前有效记录并如实标注作废。')
  }
  console.log('='.repeat(78))
}

main().catch((error) => {
  console.error('\n验收脚本执行失败：', error.message)
  if (String(error.message).includes('fetch failed')) {
    console.error('请确认后端已启动：cd backend && docker compose ps（网关 8080 应为 healthy）')
  }
  process.exitCode = 1
})
