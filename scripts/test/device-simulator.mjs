#!/usr/bin/env node
/**
 * 设备模拟器（B 档自动化录入测试用）
 * ---------------------------------------------------------------------------
 * 用途：在没有真实硬件的情况下，把「设备那一端」假造出来，先把接入链路跑通。
 * 只依赖 Node 内置能力（fetch），不装任何第三方包。
 *
 * 用法：
 *   node device-simulator.mjs ping  TEMP-001 dev-key-temp-001
 *   node device-simulator.mjs temp  TEMP-001 dev-key-temp-001 CH-TEST-0001 25.5 [bizKey]
 *   node device-simulator.mjs burst TEMP-001 dev-key-temp-001 CH-TEST-0001 20 60   # 连推 20 条、间隔 60 秒
 *   node device-simulator.mjs entry RFID-001 dev-key-rfid-001 ET-DEMO-0001 BATCH-TEST-01
 *   node device-simulator.mjs racto READER-001 dev-key-reader-001 ET-DEMO-0001 SAMPLE-001 1
 *   node device-simulator.mjs sale  POS-001 dev-key-pos-001 QR-PORK-XXXX 38.5 0.75
 *   node device-simulator.mjs receipt PDA-001 dev-key-pda-001 CH-TEST-0001 示范一店 张三
 *
 * 环境变量：
 *   INGEST_BASE  接入网关地址，默认 http://localhost:8080/api/v1/ingest/device
 */

const BASE = process.env.INGEST_BASE || 'http://localhost:8080/api/v1/ingest/device'

/** 统一上报：所有通道共用一层信封，通道差异放在 data 里 */
export async function report(deviceKey, channel, bizKey, data, extra = {}) {
  const body = {
    channel,
    bizKey,
    reportTime: extra.reportTime || nowText(),
    sourceRef: extra.sourceRef,
    data,
  }
  const response = await fetch(`${BASE}/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': deviceKey },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    json = { code: response.status, message: text }
  }
  return { httpStatus: response.status, ...json }
}

export async function ping(deviceKey) {
  const response = await fetch(`${BASE}/ping`, { headers: { 'X-Device-Key': deviceKey } })
  return json(response)
}

async function json(response) {
  const text = await response.text()
  try {
    return { httpStatus: response.status, ...JSON.parse(text) }
  } catch {
    return { httpStatus: response.status, message: text }
  }
}

/** 设备侧时间格式必须与后端一致（yyyy-MM-dd HH:mm:ss），不要用 ISO 的 'T' */
export function nowText(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}

export function ago(seconds) {
  return nowText(new Date(Date.now() - seconds * 1000))
}

/** 生成设备侧唯一号：真机应由设备序号 + 时间戳构成，重发时必须保持不变 */
export function bizKey(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// --------------------------------------------------------------------------- CLI
// 只有直接运行本文件时才执行命令行分支；被测试脚本 import 时不触发

import { pathToFileURL } from 'node:url'

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
const [cmd, ...rest] = isMain ? process.argv.slice(2) : []

const help = () => {
  console.log(`设备模拟器 —— 用法：
  ping    <deviceNo> <deviceKey>
  temp    <deviceNo> <deviceKey> <transportNo> <temperature> [bizKey] [reportTime]
  burst   <deviceNo> <deviceKey> <transportNo> <count> <intervalSeconds>
  entry   <deviceNo> <deviceKey> <earTagNo> <batchNo> [quarantineCert] [vehicleNo] [weight]
  racto   <deviceNo> <deviceKey> <earTagNo> <sampleNo> <result 1阴性/0阳性> [batchNo]
  sale    <deviceNo> <deviceKey> <qrCode> [sellPrice] [sellWeightKg]
  receipt <deviceNo> <deviceKey> <transportNo> <storeName> <receiver> [tempValue]
  tag     <deviceNo> <deviceKey> <earTagNo> <farmName> [breed] [penNo]          # 佩戴即建档
  inject  <deviceNo> <deviceKey> <earTagNo> <vaccineName> <vaccineBatchNo> [dosage]
  inspect <deviceNo> <deviceKey> <earTagNo> <batchNo> <result 1/0> [veterinary] [inspectType]
  stamp   <deviceNo> <deviceKey> <earTagNo> <batchNo> <carcassNo> [veterinary]  # 需已有合格检验
  split   <deviceNo> <deviceKey> <parentBatchNo> <productName> [weightKg]`)
}

if (isMain) switch (cmd) {
  case 'ping':
    console.log(JSON.stringify(await ping(rest[1] || 'dev-key-temp-001'), null, 2))
    break
  case 'temp': {
    const [, key, transportNo, temperature, key0, time] = rest
    const res = await report(key, 'TEMPERATURE', key0 || bizKey('TEMP'), {
      transportNo, temperature: Number(temperature),
    }, { reportTime: time })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'burst': {
    const [, key, transportNo, countText, intervalText] = rest
    const count = Number(countText || 10)
    const interval = Number(intervalText || 1)
    for (let i = 0; i < count; i++) {
      const res = await report(key, 'TEMPERATURE', bizKey('TEMP'), {
        transportNo, temperature: Number((20 + Math.sin(i) * 2).toFixed(1)),
      })
      console.log(`#${i + 1} status=${res.data?.statusLabel} accepted=${res.data?.accepted} msg=${res.data?.message}`)
      if (i < count - 1) await sleep(interval * 1000)
    }
    break
  }
  case 'entry': {
    const [, key, earTagNo, batchNo, quarantineCert, vehicleNo, weight] = rest
    const res = await report(key, 'ENTRY', bizKey('ENTRY'), {
      earTagNo, batchNo,
      quarantineCert: quarantineCert || undefined,
      vehicleNo: vehicleNo || undefined,
      weight: weight ? Number(weight) : undefined,
      healthCheck: 1,
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'racto': {
    const [, key, earTagNo, sampleNo, result, batchNo] = rest
    const res = await report(key, 'RACTOPAMINE', bizKey('RT'), {
      earTagNo, sampleNo, batchNo, result: Number(result ?? 1), testMethod: '胶体金免疫层析法',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'sale': {
    const [, key, qrCode, sellPrice, sellWeightKg] = rest
    const res = await report(key, 'SALE', bizKey('POS'), {
      qrCode,
      sellPrice: sellPrice ? Number(sellPrice) : undefined,
      sellWeightKg: sellWeightKg ? Number(sellWeightKg) : undefined,
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'receipt': {
    const [, key, transportNo, storeName, receiver, tempValue] = rest
    const res = await report(key, 'RECEIPT', bizKey('PDA'), {
      transportNo, storeName, receiver,
      tempValue: tempValue ? Number(tempValue) : undefined,
      qtyCheck: 1, tempCheck: 1, packageIntact: 1,
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'tag': {
    // 养殖建档：耳标读写器，佩戴即建档
    const [, key, earTagNo, farmName, breed, penNo] = rest
    const res = await report(key, 'TAG', bizKey('TAG'), {
      earTagNo, farmName, breed: breed || '三元杂', penNo: penNo || '1号舍-03', gender: 1, origin: '自繁',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'inject': {
    // 免疫注射：智能连续注射器，疫苗批号必填
    const [, key, earTagNo, vaccineName, vaccineBatchNo, dosage] = rest
    const res = await report(key, 'VACCINE', bizKey('INJ'), {
      earTagNo, vaccineName, vaccineBatchNo, dosage: dosage || '2ml', injectSite: '耳后颈部',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'inspect': {
    // 屠宰检验工位终端：兽医判定 + 终端录入，结论与检验人必填
    const [, key, earTagNo, batchNo, result, veterinary, inspectType] = rest
    const res = await report(key, 'INSPECTION', bizKey('INSP'), {
      earTagNo, batchNo, result: Number(result ?? 1), veterinary: veterinary || '李官方兽医',
      inspectType: Number(inspectType ?? 2), conclusion: '体表、脏器无可见病变，判合格',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'stamp': {
    // 胴体自动盖章机：硬校验该猪已有合格检验记录
    const [, key, earTagNo, batchNo, carcassNo, veterinary] = rest
    const res = await report(key, 'STAMP', bizKey('STMP'), {
      earTagNo, batchNo, carcassNo, veterinary: veterinary || '李官方兽医',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  case 'split': {
    // 分割线扫码称重台：扫白条钩标签自动建分割批次，批次号系统生成
    const [, key, parentBatchNo, productName, weightKg] = rest
    const res = await report(key, 'SPLIT', bizKey('SPLT'), {
      parentBatchNo, productName, weightKg: Number(weightKg || 78.5),
      packageCount: 1, packageType: '真空袋', workshop: '分割车间一号线',
    })
    console.log(JSON.stringify(res, null, 2))
    break
  }
  default:
    help()
}
