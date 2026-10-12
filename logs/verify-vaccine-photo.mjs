/**
 * 免疫注射凭证照片链路回归（完整方案）：
 *   1. 能拍照的终端带凭证上报 → 自动入账
 *   2. 缺凭证上报 → 不拒绝，转「待人工确认」（等人工补传）
 *   3. 编造 fileId 上传 → 直接拒绝（假凭证不得入库）
 *   4. 人工确认入账时补「操作人姓名 + 凭证照片」→ 入账后记录落实名与照片，备注留痕
 *   5. 同报文重发 → 判重复，不新建记录
 *
 * 说明：本环境的 node 禁止 child_process，落库断言（vaccine_record.file_ids /
 * 原始报文不可变）不在此脚本内，脚本会把 targetId 打出来，由
 * logs/verify-vaccine-photo-db.sh 用 docker exec mysql 完成核验。
 *
 * 运行：node logs/verify-vaccine-photo.mjs
 */

const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

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

async function api(path, { method = 'GET', token, body, file } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (file) {
    const fd = new FormData()
    fd.append('file', new Blob([file.buffer], { type: 'image/png' }), file.name)
    payload = fd
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(BASE + path, { method, headers, body: payload })
  const json = await res.json().catch(() => ({}))
  return json
}

function pngBuffer() {
  // 1x1 红色像素的合法 PNG（文件服务校验魔数）
  return {
    name: 'voucher.png',
    buffer: Buffer.from(
      '89504e470d0a1a0a0000000d494844520000000100000001080200000090775' +
      '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
      'hex',
    ),
  }
}

async function main() {
  console.log('== 免疫注射凭证照片链路 ==')

  // 0. 登录
  const login = await api('/auth/login', {
    method: 'POST',
    body: { username: 'admin', password: '123456' },
  })
  const token = login?.data?.token || login?.data?.accessToken
  check('管理员登录', !!token, JSON.stringify(login).slice(0, 120))
  if (!token) return

  // 1. 上传真实凭证照片
  const up = await api('/file/upload', { method: 'POST', token, file: pngBuffer() })
  const fileId = up?.data?.fileId
  check('凭证照片上传成功并返回 fileId', !!fileId, JSON.stringify(up).slice(0, 120))
  if (!fileId) return

  // 2. 元信息接口可核验
  const meta = await api(`/file/${fileId}/meta`, { token })
  check('文件元信息接口返回同一 fileId', meta?.data?.fileId === fileId, JSON.stringify(meta).slice(0, 120))

  // 3. 取一台免疫注射设备与一头在养生猪
  const devices = (await api('/ingest/devices', { token }))?.data || []
  const device = devices.find((d) => d.channel === 'VACCINE' && d.status === 1)
  check('存在启用的免疫注射设备', !!device, 'VACCINE 设备未登记或已停用')
  if (!device) return

  const pigs = (await api('/breeding/pigs?status=1&pageNum=1&pageSize=1', { token }))?.data?.records || []
  const pig = pigs[0]
  const earTagNo = pig?.earTagNo
  check('存在在养生猪可供演示', !!earTagNo, JSON.stringify(pigs).slice(0, 100))
  if (!earTagNo) return

  // 4. 缺凭证：不拒绝，转人工
  const noPhoto = await api('/ingest/demo/report', {
    method: 'POST',
    token,
    body: {
      deviceNo: device.deviceNo,
      data: {
        earTagNo,
        vaccineName: '回归-缺凭证疫苗',
        vaccineBatchNo: 'VF-NOPHOTO-' + Date.now(),
        dosage: '2ml',
        operator: '刘兽医',
      },
    },
  })
  check('缺凭证上报转「待人工确认」', noPhoto?.data?.status === 1, JSON.stringify(noPhoto).slice(0, 160))
  check('转人工原因提到凭证', /凭证/.test(noPhoto?.data?.message || ''), noPhoto?.data?.message)

  // 5. 带真实 fileId：自动入账
  const batchNo = 'VF-OK-' + Date.now()
  const ok = await api('/ingest/demo/report', {
    method: 'POST',
    token,
    body: {
      deviceNo: device.deviceNo,
      data: {
        earTagNo,
        vaccineName: '回归-带凭证疫苗',
        vaccineBatchNo: batchNo,
        dosage: '2ml',
        operator: '刘兽医',
        fileIds: [fileId],
      },
    },
  })
  check('带真实 fileId 上报自动入账', ok?.data?.accepted === true, JSON.stringify(ok).slice(0, 160))
  const okStagingId = ok?.data?.stagingId
  const okTargetId = ok?.data?.targetId
  // 落库断言（file_ids / 操作人）由 logs/verify-vaccine-photo-db.sh 完成
  if (okTargetId) console.log(`  [INFO] 已入账记录编号（vaccine_record.id）= ${okTargetId}，凭证 fileId = ${fileId}`)

  // 7. 编造 fileId：拒绝
  const fake = await api('/ingest/demo/report', {
    method: 'POST',
    token,
    body: {
      deviceNo: device.deviceNo,
      data: {
        earTagNo,
        vaccineName: '回归-伪造凭证',
        vaccineBatchNo: 'VF-FAKE-' + Date.now(),
        operator: '刘兽医',
        fileIds: ['deadbeefdeadbeefdeadbeefdeadbeef'],
      },
    },
  })
  check('伪造 fileId 被拒绝', fake?.data?.status === 3, JSON.stringify(fake).slice(0, 160))
  check('拒绝原因说明凭证不存在', /不存在/.test(fake?.data?.message || ''), fake?.data?.message)

  // 8. 人工确认入账时补齐「操作人 + 凭证」
  const manual = await api('/ingest/demo/report', {
    method: 'POST',
    token,
    body: {
      deviceNo: device.deviceNo,
      data: {
        earTagNo,
        vaccineName: '回归-人工补录疫苗',
        vaccineBatchNo: 'VF-MANUAL-' + Date.now(),
        dosage: '2ml',
        // 故意不带 operator 与 fileIds：模拟「只有注射器」的终端形态
      },
    },
  })
  check('缺人缺照片转「待人工确认」', manual?.data?.status === 1, JSON.stringify(manual).slice(0, 160))
  const manualId = manual?.data?.stagingId
  if (manualId) {
    const approve = await api(`/ingest/staging/${manualId}/approve`, {
      method: 'POST',
      token,
      body: {
        remark: '现场核对无误，补录接种人与照片',
        payload: { operator: '王复核', fileIds: [fileId] },
      },
    })
    check('人工补齐后确认入账', approve?.data?.accepted === true, JSON.stringify(approve).slice(0, 200))
    // 备注 / 实名 / 照片的落库断言在 logs/verify-vaccine-photo-db.sh（设备回执不含 remark 字段）
    const targetId = approve?.data?.targetId
    if (targetId) {
      console.log(`  [INFO] 人工补齐入账记录编号（vaccine_record.id）= ${targetId}，应落操作人「王复核」且凭证含 ${fileId}`)
      console.log(`  [INFO] 该上报 staging.id = ${manualId}`)
    }
  }

  // 10. 幂等不受影响：同报文重发返回既有记录
  if (okStagingId) {
    const again = await api('/ingest/demo/report', {
      method: 'POST',
      token,
      body: {
        deviceNo: device.deviceNo,
        data: {
          earTagNo,
          vaccineName: '回归-带凭证疫苗',
          vaccineBatchNo: batchNo,
          dosage: '2ml',
          operator: '刘兽医',
          fileIds: [fileId],
        },
      },
    })
    check('同报文重发判重复且不新建', again?.data?.duplicate === true, JSON.stringify(again).slice(0, 120))
  }

  console.log(`\n结果：${pass} 通过，${fail} 失败`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本执行失败：', e)
  process.exit(1)
})
