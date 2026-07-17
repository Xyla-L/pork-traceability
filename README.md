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
├── backend/           # Spring Boot 微服务（9个服务）
├── web-admin/         # Vue3 Web管理端
├── mini-app/          # Uni-app 消费者小程序
├── docs/              # 设计文档
│   ├── 业务需求文档摘要.md
│   ├── 设计文档.md
│   ├── 后端设计文档.md
│   ├── 前端设计文档.md
│   └── 任务分工文档.md
└── README.md
```

## 快速启动

详见各子目录下的 README。

## 团队

| 角色 | 人数 |
|------|:---:|
| 组长 | 1 |
| 前端 | 2 |
| 后端 | 1 |
