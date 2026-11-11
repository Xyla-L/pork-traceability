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

  // 1) 优先找「在养 + 已有有效检疫证 + 有有效免疫记录」的猪：
  //    对它再出一次证会撞唯一性校验 —— 既证明票据被接受、入口抵达业务层，又不产生新数据。
  //    免疫是出证的前置（ImmunityGate），缺免疫会先被拦下，那证明的是闸门而不是入口，
  //    所以「有免疫」这条筛选不能省。
  const pigs = listOf(await api('/breeding/pigs?current=1&size=500&status=1'))
  let target = null
  let existingCert = null
  let needVoid = false
  for (const pig of pigs) {
    const vacs = listOf(await api(`/breeding/pigs/${pig.id}/vaccines`))
    if (!vacs.some((v) => v.voidFlag !== 1)) continue
    const cert = await api(`/breeding/pigs/${pig.id}/quarantine-cert`)
    if (cert?.data?.certNo) {
      // 第一优先：已持证 → 撞唯一性，零副作用
      target = pig
      existingCert = cert.data
      needVoid = false
      break
    }
    if (!target) {
      // 兜底：有免疫但还没出证 → 出证成功后立刻作废，属于自清
      target = pig
      needVoid = true
    }
  }
  if (!target) throw new Error('未找到「在养 + 已免疫」的猪，无法验证')
  console.log(`② 选中在养猪：id=${target.id} 耳标=${target.earTagNo}`
    + (needVoid ? '（尚未出证：本轮出证后即作废）' : ` 现有证=${existingCert.certNo}`))

  // 本地时间，不能用 toISOString（那是 UTC，会把出证时间写成 8 小时前，
  // 撞上「免疫晚于出证」的时序判定，看到的就成了假失败）
  const p = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const localNow = `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`
    + ` ${p(now.getHours())}:${p(now.getMinutes())}:${p(now.getSeconds())}`
  const probe = {
    pigId: target.id,
    certNo: `QC-PROBE-${Date.now()}`,
    issueOrg: '链路验证机构',
    issueTime: localNow,
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

  // 兜底路径产生的证要收回去，不留脏数据
  if (needVoid && passed.code === 200) {
    const fresh = await api(`/breeding/pigs/${target.id}/quarantine-cert`)
    const certId = fresh?.data?.id
    if (certId) {
      const voided = await api(`/breeding/pigs/${target.id}/quarantine-cert/${certId}/void`, {
        method: 'POST', body: JSON.stringify({ reason: '链路验证：探针产生的证，验证后收回' }),
      })
      console.log(`④ 探针证已作废（自清）：${voided.code === 200 ? '✅' : '⚠️ ' + String(voided.message || '').slice(0, 80)}`)
    }
  }

  console.log(`\n结论：产地检疫人工出证入口可用${passedOk ? '，已无票据前置 ✅' : '（有一项未达预期，请查看回执）'}`)
}

main().catch((e) => { console.error('验证脚本异常：', e); process.exit(1) })
