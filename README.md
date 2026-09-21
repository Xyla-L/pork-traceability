# 猪肉产品质量安全溯源系统

基于区块链的猪肉产品质量安全溯源平台，覆盖**养殖免疫 → 屠宰检疫 → 分割配送 → 市场销售 → 应急追溯**全链路。

## 技术栈

| 层级 | 技术 |
|------|------|
| 区块链 | FISCO BCOS / Hyperledger Fabric（双链适配） |
| 后端 | Spring Boot 3.2 + Spring Cloud + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis + RabbitMQ |
| Web管理端 | Vue3 + Element Plus + ECharts |
| 消费者端 | Uni-app 微信小程序 |
| 部署 | Docker Compose 一键编排 |

## 项目结构

```
pork-traceability/
├── backend/           # Spring Boot 微服务（10个服务）
├── web-admin/         # Vue3 Web管理端
├── mini-app/          # Uni-app 消费者小程序
├── scripts/test/      # 设备模拟器 + 接入层验收测试
├── logs/              # 构建与测试留档
├── docs/              # 设计文档
│   ├── 业务需求文档摘要.md
│   ├── 设计文档.md
│   ├── 后端设计文档.md
│   ├── 前端设计文档.md
│   └── 任务分工文档.md
└── README.md
```

## 自动化录入（设备接入）

除人工表单录入外，系统支持设备自动采集：温度探头、入场 RFID/地磅、瘦肉精读数仪、
门店 POS、签收 PDA 五类设备通过统一入口上报，由 `ingest-service` 鉴权、校验、
去重后写入业务表，并标记 `source=DEVICE` 以区分机器与人工录入。

原则是「**自动采集 ≠ 自动生效**」：设备数据先入待确认队列，校验通过才落业务表；
拿不到的条件（检疫证核验、签收人）转人工确认，不会被默认放行。

验证方式：

```bash
# 端到端验收测试（12 个用例）
node scripts/test/ingest-acceptance-test.mjs

# 设备模拟器（单通道联调）
node scripts/test/device-simulator.mjs
```

详见 `docs/后端设计文档.md` 第十一章、`scripts/test/README.md`。

## 快速启动

详见各子目录下的 README。

## 团队

| 角色 | 人数 |
|------|:---:|
| 组长 | 1 |
| 前端 | 2 |
| 后端 | 1 |
