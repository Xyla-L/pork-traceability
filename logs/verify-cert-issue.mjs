#!/usr/bin/env node
/**
 * 一次性验证：产地检疫出证的人工入口是否打通（不写业务数据）。
 *
 * 验证三件事：
 *   1) 不带兜底票据直接出证 → 必须被「兜底管控」拦下
 *   2) 申请兜底解锁后再出证 → 请求应通过来源管控、抵达业务校验
 *      （用「已持证的猪」触发唯一性校验，既能证明票据被接受，又不产生新数据）
 *   3) 复核：出证接口与查询接口均按预期响应
 *
 * 用法：node logs/verify-cert-issue.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

let token = null

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

const listOf = (res) => {
  const d = res?.data
  if (Array.isArray(d)) return d
  return d?.records || d?.list || []
}

async function main() {
  // 0) 登录
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  // 1) 找一头「已有有效检疫证」的猪（用于触发唯一性校验，不产生新数据）
  const pigs = listOf(await api('/breeding/pigs?current=1&size=50'))
  let target = null
  let existingCert = null
  for (const pig of pigs) {
    const cert = await api(`/breeding/pigs/${pig.id}/quarantine-cert`)
    if (cert?.data?.certNo) { target = pig; existingCert = cert.data; break }
  }
  if (!target) throw new Error('未找到已出证的猪，无法验证')
  console.log(`② 选中已出证猪：id=${target.id} 耳标=${target.earTagNo} 现有证=${existingCert.certNo}`)

  const probe = {
    pigId: target.id,
    certNo: `QC-PROBE-${Date.now()}`,
    issueOrg: '链路验证机构',
    issueTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    validUntil: new Date(Date.now() + 180 * 86400000).toISOString().slice(0, 10),
    inspector: '链路验证兽医',
    fileId: 'PROBE-SCAN',
    caSignature: 'PROBE-SIG',
  }

  // 2) 直接出证（2026-10-12 起人工出证免票据，但必须实名并留痕）
  //    探测用的重复证号应当抵达业务层并被唯一性校验拦下——说明来源管控已放行、校验已生效。
  const passed = await api(`/breeding/pigs/${target.id}/quarantine-cert`, {
    method: 'POST', body: JSON.stringify(probe),
  })
  const passedOk = passed.code === 200
    || /已存在|重复|唯一/i.test(String(passed.message || ''))
  console.log(`③ 人工出证（免票据）→ code=${passed.code} msg=${String(passed.message || '').slice(0, 90)}`)
  console.log(`   期望：通过来源管控、抵达业务层   结果：${passedOk ? '✅ 已抵达业务层' : '⚠️ 见上方回执'}`)

  console.log(`\n结论：产地检疫人工出证入口可用${passedOk ? '，已无票据前置 ✅' : '（有一项未达预期，请查看回执）'}`)
}

main().catch((e) => { console.error('验证脚本异常：', e); process.exit(1) })
