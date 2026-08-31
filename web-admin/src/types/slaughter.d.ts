// ========== 屠宰相关类型 ==========
// 以 Web 管理端页面实际字段为准（面向批次/胴体、中文状态值）

export interface EntryInspection {
  id: number
  batchNo: string // 批次号
  earTagNo: string // 耳标号
  sourceFarm: string // 来源养殖场
  arrivalTime: string // 入场时间 yyyy-MM-dd HH:mm:ss
  weight: number // 重量(kg)
  quarantineCert: string // 检疫证明编号
  inspector: string // 查验员
  status: string // 待查验 | 合格 | 不合格
  remark?: string // 查验意见
  createTime?: string
}

export interface SlaughterInspection {
  id: number
  inspectNo: string // 检验编号
  batchNo: string // 批次号
  earTagNo: string // 耳标号
  inspectType: string // 宰前检验 | 宰后检验 | 同步检验
  inspectTime: string // 检验时间
  inspector: string // 检验员
  temperature: number // 体温(°C)
  result: string // 合格 | 不合格 | 待检验
  status: string // 待检验 | 合格 | 不合格
  organCheck?: string // 脏器检查结果（页面为字符串）
  conclusion?: string // 检验结论
  createTime?: string
}

export interface RactopamineTest {
  id: number
  testNo: string // 检测编号
  batchNo: string // 批次号
  sampleNo: string // 样本编号
  testType: string // 瘦肉精检测 | 克伦特罗检测 | 莱克多巴胺检测 | 沙丁胺醇检测
  testMethod?: string // 胶体金法 | ELISA法 | LC-MS/MS法
  sampleSite?: string // 尿液 | 肝脏 | 肌肉
  testTime: string // 检测时间
  tester: string // 检测员
  result: string // 阴性 | 阳性 | 待检测
  status: string // 待检测 | 检测中 | 已完成
  reportUrl?: string // 检测报告链接
  remark?: string
  createTime?: string
}

export interface CarcassStamp {
  id: number
  stampNo: string // 盖章编号
  batchNo: string // 批次号
  carcassNo: string // 胴体编号
  stampType: string // 检疫合格章 | 检验合格章 | 无害化处理章 | 高温处理章
  stampTime: string // 盖章时间
  inspector: string // 检疫员（表单字段）
  veterinary?: string // 检疫员（列表展示字段，与 inspector 同义）
  isVerified: boolean // 是否已上链
  blockchainTxHash?: string // 区块链交易哈希
  verifyTime?: string // 上链时间
  status: string // 待盖章 | 已盖章 | 已作废
  remark?: string
  createTime?: string
}

export interface Slaughterhouse {
  id: number
  name: string
}
