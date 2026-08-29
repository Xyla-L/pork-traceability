/**
 * Mock 数据
 * 后端 consumer API 未就绪前，前端使用本地 Mock 数据并行开发。
 * 数据结构完全对齐《后端设计文档》5.3 扫码溯源完整响应示例。
 *
 * 后端接口就绪后：将 USE_MOCK 置为 false，各 api 模块会自动切换为真实请求。
 */

export const USE_MOCK = true

/** 模拟网络延迟，方便观察骨架屏/加载态 */
export function delay(ms = 300) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 完整溯源数据（扫码 / 安心购共用） */
export const mockScanResult = {
  product: {
    name: '猪前腿肉（真空包装）',
    batchNo: 'SP2024071500101',
    weight: '0.5kg',
    packageDate: '2024-07-15',
    expireDate: '2024-07-22',
    qrCode: 'QR-PORK-2024071500101-A001',
  },
  traceChain: {
    farm: {
      name: 'XX市XX养殖专业合作社',
      licenseNo: 'SCXK-2024-00123',
      earTagNo: 'ET20240601001',
      breed: '长白猪',
    },
    vaccines: [
      { name: '猪瘟活疫苗', batchNo: 'CSF-20240601', time: '2024-06-05' },
      { name: '口蹄疫O型灭活疫苗', batchNo: 'FMD-20240615', time: '2024-06-20' },
    ],
    quarantineCert: {
      certNo: 'QC2024070100123',
      issueOrg: 'XX县动物卫生监督所',
      issueTime: '2024-07-01 10:30',
      inspector: '官方兽医-李建国',
      caVerified: true,
    },
    slaughter: {
      slaughterhouse: 'XX市定点屠宰场',
      licenseNo: 'SDZ-2024-0088',
      entryTime: '2024-07-02 06:30',
      inspectResult: '合格',
      ractopamine: '阴性',
      stampNo: 'ST2024070200123',
      veterinary: '官方兽医-王建国',
    },
    splitWorkshop: {
      name: 'XX食品加工有限公司分割车间',
      splitTime: '2024-07-15 08:30',
      workshopTemp: '10.5℃',
      productName: '猪前腿肉',
      packageType: '真空包装',
    },
    transport: {
      transportNo: 'TR2024071500008',
      vehicleNo: '京A·12345',
      vehicleType: '4.2m冷藏车',
      departTime: '2024-07-15 09:00',
      arriveTime: '2024-07-15 11:30',
      temperatureLog: [
        { time: '2024-07-15 09:00', temp: -2.5 },
        { time: '2024-07-15 10:00', temp: -1.8 },
        { time: '2024-07-15 11:30', temp: -2.1 },
      ],
      avgTemp: -2.2,
      abnormalCount: 0,
    },
    storeReceipt: {
      storeName: 'XX生鲜超市（朝阳路店）',
      receiptTime: '2024-07-15 11:45',
      receiver: '店长-赵六',
      tempAtReceipt: '-2.1℃',
      packageIntact: '完好',
    },
    blockchain: {
      verified: true,
      recordCount: 5,
      records: [
        { type: '产地检疫', txHash: '0x7a3b...', blockNumber: 1234567 },
        { type: '屠宰双检', txHash: '0x8c4d...', blockNumber: 1234890 },
        { type: '批次分割', txHash: '0x9e5f...', blockNumber: 1235100 },
        { type: '门店签收', txHash: '0xa1b2...', blockNumber: 1235300 },
        { type: '销售激活', txHash: '0xb3c4...', blockNumber: 1235500 },
      ],
    },
  },
}

/** 安心购：全链条检疫章 + 检测报告摘要 + 区块链徽章 */
export const mockSafeBuy = {
  qrCode: 'QR-PORK-2024071500101-A001',
  blockchain: { verified: true, recordCount: 5 },
  certChain: [
    { type: '产地检疫合格证明', certNo: 'QC2024070100123', issueOrg: 'XX县动物卫生监督所', issueTime: '2024-07-01 10:30', photo: '' },
    { type: '动物检疫合格证明（动物B）', certNo: 'QB2024070200101', issueOrg: 'XX市动物卫生监督所', issueTime: '2024-07-02 08:00', photo: '' },
    { type: '肉品品质检验合格证', certNo: 'RP2024070200202', issueOrg: 'XX市定点屠宰场', issueTime: '2024-07-02 12:00', photo: '' },
    { type: '瘦肉精检测报告', certNo: 'RT2024070200303', issueOrg: 'XX市农产品检测中心', issueTime: '2024-07-02 11:00', photo: '' },
  ],
  reports: [
    {
      title: '产地检疫',
      summary: '该批次生猪经官方兽医现场检疫，临床检查健康，具备产地检疫合格条件。',
      detail: '检疫编号 QC2024070100123；检查项目：临床检查、耳标核对、免疫档案查验，结果全部合格。',
      pass: true,
    },
    {
      title: '宰前检验',
      summary: '宰前12小时静养观察，群体健康，无异常临床表现。',
      detail: '宰前检验项目：群体观察、个体检查，未发现可疑病畜。',
      pass: true,
    },
    {
      title: '宰后检验',
      summary: '胴体及内脏同步检疫，淋巴结、脏器检查未见异常。',
      detail: '心脏、肝脏、肺脏、肾脏、淋巴结等器官检查均正常。',
      pass: true,
    },
    {
      title: '瘦肉精检测',
      summary: '盐酸克伦特罗、莱克多巴胺、沙丁胺醇检测均为阴性。',
      detail: '采用胶体金试纸条法，采样部位为尿液，检测结果阴性。',
      pass: true,
    },
  ],
  chainRecords: [
    { time: '2024-07-01 10:30', desc: '产地检疫证明上链', txHash: '0x7a3b...', blockNumber: 1234567 },
    { time: '2024-07-02 12:00', desc: '屠宰双检报告上链', txHash: '0x8c4d...', blockNumber: 1234890 },
    { time: '2024-07-15 08:30', desc: '批次分割信息上链', txHash: '0x9e5f...', blockNumber: 1235100 },
    { time: '2024-07-15 11:45', desc: '门店签收信息上链', txHash: '0xa1b2...', blockNumber: 1235300 },
    { time: '2024-07-15 12:00', desc: '销售激活信息上链', txHash: '0xb3c4...', blockNumber: 1235500 },
  ],
}

/** 一键验真结果 */
export const mockVerifyResult = {
  verified: true,
  chainStatus: '✅ 区块链已验证',
  detail: '全部 5 条存证记录与链上哈希一致',
  details: [
    { type: '产地检疫', localHash: 'a3f5c8e2', chainHash: 'a3f5c8e2', match: true },
    { type: '屠宰双检', localHash: 'b7d1e9a4', chainHash: 'b7d1e9a4', match: true },
    { type: '批次分割', localHash: 'c9e2f0b6', chainHash: 'c9e2f0b6', match: true },
    { type: '门店签收', localHash: 'd4a8b1c3', chainHash: 'd4a8b1c3', match: true },
    { type: '销售激活', localHash: 'e5b9c2d7', chainHash: 'e5b9c2d7', match: true },
  ],
}

/** 举报列表 */
export const mockComplaints = [
  {
    id: 1,
    reportNo: 'CP202407150001',
    reporterName: '张先生',
    reporterPhone: '138****1234',
    targetQrCode: 'QR-PORK-2024071500101-A001',
    targetBatch: 'SP2024071500101',
    complaintText: '购买后发现包装有异味，怀疑产品质量有问题。',
    fileIds: [],
    status: 0,
    statusLabel: '待处理',
    createTime: '2024-07-15 14:20',
    reply: '',
    replyTime: '',
  },
  {
    id: 2,
    reportNo: 'CP202407120002',
    reporterName: '匿名',
    reporterPhone: '',
    targetQrCode: 'QR-PORK-2024071200102-B003',
    targetBatch: 'SP2024071200102',
    complaintText: '扫码后溯源信息不完整，缺少运输环节。',
    fileIds: [],
    status: 1,
    statusLabel: '已受理',
    createTime: '2024-07-12 09:10',
    reply: '',
    replyTime: '',
  },
  {
    id: 3,
    reportNo: 'CP202407080003',
    reporterName: '李女士',
    reporterPhone: '139****5678',
    targetQrCode: 'QR-PORK-2024070800103-C001',
    targetBatch: 'SP2024070800103',
    complaintText: '商品过期预警信息未及时展示。',
    fileIds: [],
    status: 2,
    statusLabel: '已办结',
    createTime: '2024-07-08 16:40',
    reply: '感谢您的反馈，相关问题已核实并处理完毕，商品已下架。',
    replyTime: '2024-07-09 10:00',
  },
]

/** 最近扫码记录（首页展示用） */
export const mockRecentScans = [
  { qrCode: 'QR-PORK-2024071500101-A001', name: '猪前腿肉（真空包装）', time: '2024-07-16 10:20' },
  { qrCode: 'QR-PORK-2024071200102-B003', name: '猪里脊肉', time: '2024-07-13 18:05' },
  { qrCode: 'QR-PORK-2024070800103-C001', name: '五花肉', time: '2024-07-09 12:30' },
]
