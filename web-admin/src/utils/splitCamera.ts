import { fileApi } from '@/api/modules/file'
import { ingestApi } from '@/api/modules/ingest'

/**
 * 分割车间监控「相机模拟器」。
 *
 * <h3>为什么需要它</h3>
 * 分割操作页的监控区要展示的是「<b>这一次分割</b>的现场照片」。演示环境里没有真相机，
 * 而「去设备接入页手动点两次、一次只传一张」既不真实（现实里相机是自动的），
 * 又和分割动作脱节（新做一次分割不会产生新照片，页面看到的永远是上一张）。
 * 所以把相机放在这里：**分割线扫码称重台一上报，相机就开拍**，拍到的照片带着这次分割的批次号。
 *
 * <h3>为什么照片是画出来的</h3>
 * 演示图库里没有车间实拍图。用 canvas 现画一张「车间工位示意图」有两个好处：
 *   1. **每张都不一样** —— 时间戳精确到秒、色调随序号变、传送带上的白条位置逐张错开，
 *      所以三张照片肉眼可辨，不会出现「传了三张、看着还是一张」；
 *   2. **能一眼看出归属** —— 画面上直接写着批次号与序号（第 N/3 张），
 *      演示时不需要旁白解释「这张是哪次分割的」。
 * 这不是「假数据」：照片本来就不参与任何业务判定（不进溯源链、不进门槛），
 * 它要证明的是「两段式上报这条路能通、且照片归属正确」。
 *
 * <h3>节奏（演示值，可调）</h3>
 * 现在的 2 秒 / 3 张是**演示节奏**，为了让人坐着看完一次连拍。
 * 真实口径是「采样间隔由设备自己定」—— 见 `ingest_device.sample_interval_seconds`
 * （这台相机台账里写的是 60 秒），系统不轮询。改正式值只需要动下面两个常量
 * （或后续把它接到设备台账上）。
 */

/** 连拍间隔（毫秒）。演示取 2 秒，真实相机是 60 秒一拍（设备台账 sample_interval_seconds）。 */
export const SNAPSHOT_INTERVAL_MS = 2000

/** 每次分割连拍张数。演示取 3 张。 */
export const SNAPSHOT_COUNT = 3

/** 分割车间监控相机的通道名（等价于 IngestChannel.SPLIT_SNAPSHOT） */
export const SNAPSHOT_CHANNEL = 'SPLIT_SNAPSHOT'

const SHOT_W = 640
const SHOT_H = 360

function pad(n: number) {
  return String(n).padStart(2, '0')
}

/** 与演示表单同一口径的本地时间串（yyyy-MM-dd HH:mm:ss） */
export function nowText(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export interface ShotOptions {
  /** 本次分割所属胴体批次号（画在画面上，也是上报时的绑定依据） */
  batchNo: string
  /** 第几张（从 1 开始） */
  index: number
  /** 共几张 */
  total: number
  workshop?: string
  deviceNo?: string
}

/**
 * 画一张「分割车间现场」示意图。
 *
 * 画面构成刻意做成监控视角：顶部信息条（相机号 + 车间）、中部传送带与白条、
 * 底部时间戳与序号 —— 让人一眼看出「这是相机拍的现场」，而不是一张装饰图。
 */
export function renderWorkshopShot(opts: ShotOptions): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = SHOT_W
  canvas.height = SHOT_H
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.reject(new Error('浏览器不支持 canvas，无法生成现场照片'))

  // 每次分割色调错开：把批次号与序号揉进色相，保证「不同分割、不同张」颜色都不同
  const seed = hash(`${opts.batchNo}#${opts.index}#${Date.now()}`)
  const hue = seed % 360

  // ---- 背景：车间冷色渐变 + 顶灯 ----
  const bg = ctx.createLinearGradient(0, 0, 0, SHOT_H)
  bg.addColorStop(0, `hsl(${hue}, 18%, 26%)`)
  bg.addColorStop(0.55, `hsl(${hue}, 14%, 18%)`)
  bg.addColorStop(1, `hsl(${hue}, 12%, 12%)`)
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, SHOT_W, SHOT_H)

  // 顶灯（两个柔光斑）
  for (const lx of [SHOT_W * 0.28, SHOT_W * 0.74]) {
    const lamp = ctx.createRadialGradient(lx, -20, 10, lx, -20, 240)
    lamp.addColorStop(0, `hsla(${hue}, 40%, 92%, 0.30)`)
    lamp.addColorStop(1, 'hsla(0, 0%, 100%, 0)')
    ctx.fillStyle = lamp
    ctx.fillRect(0, 0, SHOT_W, SHOT_H)
  }

  // ---- 墙上的挂轨 + 悬挂的白条（数量与位置随序号错开，逐张不同） ----
  const railY = 96
  ctx.fillStyle = 'rgba(255,255,255,0.16)'
  ctx.fillRect(40, railY, SHOT_W - 80, 4)

  const hanging = 5
  for (let i = 0; i < hanging; i++) {
    // 位置跟着「序号」平移，所以三张照片里白条所在的位置都不一样
    const x = 62 + i * 104 + ((opts.index * 17) % 40)
    const h = 118 + ((seed >> (i + 1)) % 26)
    const w = 44
    // 挂钩
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(x + w / 2, railY + 4)
    ctx.lineTo(x + w / 2, railY + 22)
    ctx.stroke()
    // 白条（肉色，受光面偏亮）
    const grad = ctx.createLinearGradient(x, railY + 22, x + w, railY + 22 + h)
    grad.addColorStop(0, `hsl(${12 + (i * 4)}, 42%, 78%)`)
    grad.addColorStop(1, `hsl(${8 + (i * 4)}, 34%, 58%)`)
    ctx.fillStyle = grad
    roundRect(ctx, x, railY + 22, w, h, 8)
    ctx.fill()
    ctx.strokeStyle = 'rgba(0,0,0,0.18)'
    ctx.lineWidth = 1
    ctx.stroke()
  }

  // ---- 传送带（下半部分） ----
  const beltY = 262
  ctx.fillStyle = 'rgba(20,24,28,0.85)'
  ctx.fillRect(0, beltY, SHOT_W, 62)
  ctx.fillStyle = 'rgba(255,255,255,0.10)'
  for (let x = -40; x < SHOT_W; x += 28) {
    // 带纹随序号滚动，也能体现「这是不同时刻的画面」
    const offset = (opts.index * 11) % 28
    ctx.fillRect(x + offset, beltY + 30, 14, 3)
  }
  ctx.fillStyle = 'rgba(255,255,255,0.06)'
  ctx.fillRect(0, beltY, SHOT_W, 2)

  // ---- 噪点/扫描线：让它更像监控画面，而不是矢量插画 ----
  ctx.globalAlpha = 0.045
  for (let i = 0; i < 900; i++) {
    const nx = (hash(`${seed}-${i}`) % SHOT_W)
    const ny = (hash(`${seed}-${i}-y`) % SHOT_H)
    ctx.fillStyle = i % 2 ? '#ffffff' : '#000000'
    ctx.fillRect(nx, ny, 2, 2)
  }
  ctx.globalAlpha = 1

  // ---- 顶部信息条 ----
  ctx.fillStyle = 'rgba(0,0,0,0.42)'
  ctx.fillRect(0, 0, SHOT_W, 34)
  ctx.fillStyle = '#e8f0f5'
  ctx.font = '14px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
  ctx.textBaseline = 'middle'
  ctx.textAlign = 'left'
  ctx.fillText(opts.deviceNo || 'CAM-SPLIT-001', 14, 17)
  ctx.textAlign = 'right'
  ctx.fillText(opts.workshop || '分割车间', SHOT_W - 14, 17)

  // 录制点
  ctx.fillStyle = '#ff5b5b'
  ctx.beginPath()
  ctx.arc(SHOT_W / 2, 17, 5, 0, Math.PI * 2)
  ctx.fill()

  // ---- 中央：批次号（这一张属于哪次分割，直接写在画面上） ----
  ctx.textAlign = 'center'
  ctx.fillStyle = 'rgba(0,0,0,0.35)'
  roundRect(ctx, SHOT_W / 2 - 150, 52, 300, 34, 6)
  ctx.fill()
  ctx.fillStyle = '#ffffff'
  ctx.font = '20px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
  ctx.fillText(opts.batchNo || '未绑定批次', SHOT_W / 2, 70)

  // ---- 底部时间戳 + 序号 ----
  const stamp = nowText()
  ctx.font = '16px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
  const label = `${stamp}   第 ${opts.index}/${opts.total} 张`
  const tw = ctx.measureText(label).width
  ctx.fillStyle = 'rgba(0,0,0,0.55)'
  roundRect(ctx, 12, SHOT_H - 36, tw + 24, 26, 5)
  ctx.fill()
  ctx.fillStyle = '#f2f6f9'
  ctx.textAlign = 'left'
  ctx.fillText(label, 24, SHOT_H - 22)

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('现场照片生成失败（canvas 导出为空）'))
    }, 'image/png')
  })
}

/** 32 位字符串散列 —— 只用于配色/噪点，不做任何安全用途 */
function hash(input: string): number {
  let h = 2166136261
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return Math.abs(h)
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

export interface AutoShootOptions {
  /** 相机设备号（走 SPLIT_SNAPSHOT 通道的那台） */
  cameraDeviceNo: string
  /** 这次分割所属的胴体批次号 —— 照片绑定的依据 */
  batchNo: string
  workshop?: string
  /** 进度回调：已完成张数 / 总张数 */
  onProgress?: (done: number, total: number) => void
}

export interface AutoShootResult {
  uploaded: number
  failed: number
  total: number
  /** 第一张的失败原因（用于向人解释为什么一张都没拍上） */
  firstError?: string
}

/**
 * 连拍并逐张上报：一次分割 = 一组现场照片。
 *
 * 顺序是「先传文件换 fileId、再报 fileId + 拍摄时刻 + 批次号」——
 * 与真实相机的两段式上报完全一致，接入层会核对照片真实存在。
 *
 * 单张失败不中断（现场网络抖一下就整组放弃，比丢一张更糟）：
 * 失败张数会回给调用方，由页面决定怎么提示。
 */
export async function autoShootSplitSnapshots(opts: AutoShootOptions): Promise<AutoShootResult> {
  const total = SNAPSHOT_COUNT
  let uploaded = 0
  let failed = 0
  let firstError: string | undefined

  for (let i = 0; i < total; i++) {
    // 第一张立即拍（分割已经开始），之后按间隔连拍
    if (i > 0) await sleep(SNAPSHOT_INTERVAL_MS)
    const shotIndex = i + 1
    try {
      const blob = await renderWorkshopShot({
        batchNo: opts.batchNo,
        index: shotIndex,
        total,
        workshop: opts.workshop,
        deviceNo: opts.cameraDeviceNo,
      })
      // 文件名带批次号与序号：演示时在「待人工处理」或文件列表里也能一眼看出归属
      const name = `split-snapshot-${opts.batchNo}-${shotIndex}.png`
      const info: any = await fileApi.upload(new File([blob], name, { type: 'image/png' }))
      const fileId = info?.fileId
      if (!fileId) throw new Error('文件服务未返回 fileId')

      const res: any = await ingestApi.demoReport({
        deviceNo: opts.cameraDeviceNo,
        channel: SNAPSHOT_CHANNEL,
        data: {
          fileId,
          capturedTime: nowText(),
          workshop: opts.workshop,
          // 绑定依据：工位终端告知相机「当前正在分割的白条属于哪个批次」
          batchNo: opts.batchNo,
        },
      })
      if (res?.accepted) uploaded++
      else {
        failed++
        firstError = firstError || (res?.message || '快照未入账')
      }
    } catch (e: any) {
      failed++
      firstError = firstError || (e?.message || '快照上传失败')
    } finally {
      opts.onProgress?.(shotIndex, total)
    }
  }

  return { uploaded, failed, total, firstError }
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
