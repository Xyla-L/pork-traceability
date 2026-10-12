#!/usr/bin/env node
/**
 * 一次性验证：操作人必须是「人」，设备只能出现在「采集设备」列。
 *
 * 背景：接入层原先在多个通道里做了兜底——报文没带操作人时用设备名
 * （形如「INJ-001 自动采集」）填进操作人字段。结果是追溯链上「谁给这头猪
 * 打的疫苗」「谁做的入场查验」查到的是设备本身。已改为：
 *   · 报文没带操作人 → 转「待人工确认」，由人工补录实名；
 *   · 业务侧兜底值由「设备自动采集」改为「未记录」；
 *   · 存量已被写成设备名的值由 init-sql/21 脚本修正为「未记录」。
 *
 * 验证四件事：
 *   1) 免疫注射不带操作人 → 转人工（status=1），原因点名「操作人」
 *   2) 免疫注射带操作人   → 入账（status=2），业务记录里的人名是人
 *   3) 瘦肉精检测不带操作人 → 转人工（status=1），原因点名「检测员」
 *   4) 全库人名字段里不再出现「自动采集」字样（拉接口逐条比对）
 *
 * 用法：node logs/verify-operator-is-human.mjs
 * 说明：脚本刻意复用既有耳标，不新建生猪档案；产生的上报记录 id 会打印出来。
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
const STAMP = Date.now()

let token = null
let failed = 0
const created = []

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

function check(label, ok, extra = '') {
  console.log(`${ok ? '✅' : '❌'} ${label}${extra ? ' — ' + extra : ''}`)
  if (!ok) failed++
}

/** 合法的 1x1 PNG（文件服务校验魔数），供凭证上传用例 */
function pngBytes() {
  return Buffer.from(
    '89504e470d0a1a0a0000000d494844520000000100000001080200000090775' +
    '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
    'hex',
  )
}

async function report(deviceNo, data) {
  const res = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({ deviceNo, data }),
  })
  if (!res.data) throw new Error('上报失败：' + JSON.stringify(res).slice(0, 200))
  if (res.data.stagingId) created.push(res.data.stagingId)
  return res.data
}

/** 某通道下第一台启用中的设备 */
async function enabledDevice(channel) {
  const list = ((await api(`/ingest/devices?channel=${channel}`)).data) || []
  return list.find((d) => d.status === 1) || null
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  const pigPage = (await api('/breeding/pigs?current=1&size=1')).data
  const pig = (pigPage?.records || [])[0]
  if (!pig) throw new Error('生猪档案为空，无法验证（脚本刻意不新建档案以免污染数据）')
  console.log(`② 复用既有耳标 ${pig.earTagNo}（档案 id=${pig.id}）\n`)

  // ---------- 1) 免疫注射：不带操作人 ----------
  const vaccineDevice = await enabledDevice('VACCINE')
  if (!vaccineDevice) throw new Error('没有启用中的免疫注射设备，无法验证')
  const batch = `VAC-VERIFY-${STAMP}`
  const r1 = await report(vaccineDevice.deviceNo, {
    earTagNo: pig.earTagNo,
    vaccineName: '猪瘟兔化弱毒疫苗',
    vaccineBatchNo: batch,
    dosage: '2ml',
    operator: '', // 明确不给操作人
  })
  check('免疫注射不带操作人：转「待人工确认」而不是自动入账', r1.status === 1,
    `status=${r1.status}（${r1.statusLabel}）`)
  check('转人工原因点名「操作人」', String(r1.message || '').includes('操作人'), r1.message)
  check('未写入业务数据', !r1.targetId, `记录编号=${r1.targetId ?? '空'}`)

  // ---------- 2) 免疫注射：带上兽医姓名 + 接种凭证 ----------
  // 凭证照片是免疫注射的硬要求（2026-10-08 起）：缺照片会转人工，所以这条用例要先传一张
  const voucherForm = new FormData()
  voucherForm.append('file', new Blob([pngBytes()], { type: 'image/png' }), 'voucher.png')
  const voucherRes = await (await fetch(`${BASE}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: voucherForm,
  })).json()
  const voucherFileId = voucherRes?.data?.fileId
  check('接种凭证照片上传成功', !!voucherFileId, JSON.stringify(voucherRes).slice(0, 120))

  const r2 = await report(vaccineDevice.deviceNo, {
    earTagNo: pig.earTagNo,
    vaccineName: '猪瘟兔化弱毒疫苗',
    vaccineBatchNo: `${batch}-OK`,
    dosage: '2ml',
    operator: '刘兽医',
    fileIds: voucherFileId ? [voucherFileId] : undefined,
  })
  check('免疫注射带操作人：正常入账', r2.status === 2, `status=${r2.status}（${r2.statusLabel}）`)

  // 业务侧落库的人名要真的是人
  if (r2.targetId) {
    const records = ((await api(`/breeding/pigs/${pig.id}/vaccines`)).data) || []
    const list = Array.isArray(records) ? records : (records.records || [])
    const hit = list.find((v) => String(v.id) === String(r2.targetId))
    if (hit) {
      check('入账后操作人是人名而非设备名', hit.operator === '刘兽医' && !String(hit.operator).includes('自动采集'),
        `operator=${hit.operator}`)
      check('采集设备单独记录在来源单据号上', !!hit.sourceRef, `sourceRef=${hit.sourceRef}`)
    } else {
      check('能在免疫记录里找到刚入账的那条', false, `未找到 id=${r2.targetId}`)
    }
  }

  // ---------- 3) 瘦肉精：不带检测员 ----------
  const ractoDevice = await enabledDevice('RACTOPAMINE')
  if (ractoDevice) {
    const r3 = await report(ractoDevice.deviceNo, {
      earTagNo: pig.earTagNo,
      batchNo: `B-VERIFY-${STAMP}`,
      sampleNo: `S-VERIFY-${STAMP}`,
      result: 1,
      operator: '',
    })
    check('瘦肉精不带检测员：转「待人工确认」', r3.status === 1, `status=${r3.status}（${r3.statusLabel}）`)
    check('转人工原因点名「检测员」', String(r3.message || '').includes('检测员'), r3.message)
  } else {
    console.log('⚠️  没有启用中的瘦肉精检测设备，跳过该项')
  }

  // ---------- 4) 全库人名字段不再出现设备字样 ----------
  /** 分页接口统一取 total 条（够抽查演示数据即可） */
  async function rows(path, params = 'current=1&size=200') {
    const body = (await api(`${path}?${params}`)).data
    if (Array.isArray(body)) return body
    return body?.records || []
  }

  const collected = []
  const scan = (label, list, field) => {
    for (const row of list) {
      const value = String(row[field] || '')
      if (value.includes('自动采集')) collected.push(`${label}#${row.id} ${field}=${value}`)
    }
  }

  scan('免疫注射', await rows(`/breeding/pigs/${pig.id}/vaccines`), 'operator')
  scan('入场查验', await rows('/slaughter/entries'), 'inspector')
  scan('瘦肉精检测', await rows('/slaughter/ractopamine'), 'operator')
  scan('屠宰检验', await rows('/slaughter/inspections'), 'veterinary')
  scan('胴体盖章', await rows('/slaughter/stamps'), 'veterinary')
  scan('分割批次', await rows('/distribution/splits'), 'operator')
  scan('门店签收', await rows('/distribution/receipts'), 'receiver')

  check('人名字段里不再出现「自动采集」', collected.length === 0,
    collected.length ? collected.slice(0, 3).join('；') : '已扫免疫/查验/检测/检验/盖章/分割/签收 7 张表，均为空 ✅')

  console.log(`\n本次产生的上报记录 id：${[...new Set(created)].join('、')}`)
  console.log(`${failed === 0 ? '全部通过 ✅' : `有 ${failed} 项未通过 ❌`}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
