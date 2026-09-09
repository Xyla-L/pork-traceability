# 后端服务

本目录包含 9 个 Spring Boot 微服务及 4 个公共模块。开发环境默认使用 MySQL、Redis、RabbitMQ、Nacos 和数据库本地账本适配器。

## 环境要求

- JDK 17
- Maven 3.9+
- Docker Engine + Docker Compose（完整环境启动）

## 编译

```powershell
mvn -B package -DskipTests
```

## Docker Compose 启动

1. 复制 `.env.example` 为 `.env`，至少修改 MySQL、Redis、RabbitMQ、JWT 和管理员密码。
2. 在本目录执行：

```powershell
docker compose up --build -d
docker compose ps
```

网关地址为 `http://localhost:8080/api/v1`，RabbitMQ 管理端口为 `15672`，Nacos 控制台端口为 `8848`。

默认开发账本通过 `BLOCKCHAIN_TYPE=local` 启用。FISCO BCOS 或 Fabric 接入需要对应网络证书、节点地址、已部署合约地址及 SDK 配置，未提供这些参数时不得切换适配器类型。

## 数据库

首次创建 MySQL 数据卷时，`init-sql` 中的脚本会按文件名顺序执行。`07-demo-data.sql` 提供可重复执行的全链路演示数据，演示二维码为 `QR-PORK-DEMO-0001`。

已有数据库不会自动重新执行初始化脚本。表结构发生变化时应使用迁移工具或重新创建专用开发数据卷，禁止直接覆盖生产数据。

## 常用接口

- Swagger UI：`http://localhost:8080/doc.html`
- 健康检查：`http://localhost:8080/actuator/health`
- 登录：`POST /api/v1/auth/login`
- 扫码追溯：`GET /api/v1/consumer/scan/QR-PORK-DEMO-0001`
