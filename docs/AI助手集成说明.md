# AI 助手集成说明（内嵌实验室 Dify / 千问）

> 落地时间：2026-11-16
> 目标：在 pork-traceability 里内嵌实验室部署的 Dify（Qwen3.8-27B），
> 做两个角色的助手：消费者看"人话解读"，监管生成"专业简报"。

---

## 一、整体链路

```
小程序 / 管理后台
      │  fetch + ReadableStream（SSE）
      ▼
gateway-service  /api/v1/ai/**   →  StripPrefix=2
      ▼
ai-service (8089)
      │  ├─ TraceDataClient ──→ trace-service (8086)  取溯源数据
      │  └─ PromptFactory   ──→ 角色化 Prompt
      ▼
Dify /v1/chat-messages  (streaming)
      ▲
      │  SSH 隧道：本机 9090 → frp-use.com → 172.17.102.191:80
      └─ 实验室服务器
```

**为什么单独起一个 ai-service**
模型能力被隔离在一个服务里。将来换 Dify、换成 OpenAI 兼容网关、或者加本地 vLLM，
其他微服务一行都不用改。这是微服务里"变化点隔离"的典型用法。

---

## 二、连接信息

| 项 | 值 |
|---|---|
| Dify 控制台 | http://127.0.0.1:9090 （需先开隧道） |
| 隧道命令 | 双击桌面 `实验室Dify.bat`，或 `ssh -N NodeQW1` |
| Dify 账号 | `root@123.com` / `qwer@136` |
| 应用 | `Qwen3.8-27B`，mode = `advanced-chat`（Chatflow） |
| API Key | 不再写在文档和代码里，见下方「密钥怎么配」 |
| 应用 ID | `561d7b15-94f9-432c-b9cf-0d1bd75caf3e` |

### 密钥怎么配（改过了，别再写回 yml）

`application.yml` 里已经去掉了默认 Key，改成 `${DIFY_API_KEY:}`。有两个注入方式：

**本地开发（推荐）**：把 Key 写进 `backend/ai-service/src/main/resources/application-local.yml`，
这个文件已在 `.gitignore` 里，不会进 Git。主配置默认激活 `local` profile，所以直接启动就能用。

**生产 / CI**：设环境变量 `DIFY_API_KEY`，并把 `SPRING_PROFILES_ACTIVE` 设为 `prod`：

```powershell
$env:DIFY_API_KEY = "app-xxxx"
$env:SPRING_PROFILES_ACTIVE = "prod"
```

Key 为空时启动日志会打一条 WARN 提示，不会静默失败。


### 端口约定（重要）

- **9090** = 本机访问 Dify 的隧道端口
- **8080** = gateway-service
- 原来隧道占 8080，会和网关冲突，已改到 9090

改了这两个文件（都有 `.bak` 备份）：
- `C:\Users\Cheun\.ssh\config` → `LocalForward 9090 localhost:80`
- `桌面\实验室Dify.bat` → 自动打开 `http://localhost:9090`

---

## 三、后端结构

```
backend/ai-service/
├── config/
│   ├── DifyProperties.java         配置绑定（base-url / api-key / 超时）
│   ├── DifyClientConfig.java       两个 WebClient：difyWebClient（流式）、plainWebClient（取数）
│   ├── AdminSecurityProperties.java 监管接口角色校验开关
│   └── WebMvcConfig.java           把拦截器挂到 /ai/admin/**
├── client/
│   ├── DifyClient.java             封装 /v1/chat-messages，blocking + streaming
│   └── TraceDataClient.java        调 trace-service 取数（含参数编码与故障区分）
├── prompt/
│   └── PromptFactory.java          双角色 Prompt 模板 ★效果好坏在这一层
├── service/
│   └── AiAssistService.java        编排：取数 → 组 Prompt → 调 Dify
├── security/
│   └── AdminRoleInterceptor.java   校验 X-User-Role ∈ {SUPERVISOR, ADMIN}
├── exception/
│   └── TraceDataUnavailableException.java  下游取数失败（区别于「确实没数据」）
├── controller/
│   └── AiAssistantController.java   SSE 端点 + Reactor 到 SseEmitter 的桥接
└── dto/                            请求/事件载体
```

### 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/ai/consumer/interpret?qrCode=` | 匿名 | 消费者一键解读（SSE） |
| GET | `/ai/consumer/ask?qrCode=&q=&conversationId=` | 匿名 | 消费者追问（SSE），带上轮会话 ID |
| GET | `/ai/admin/report` | **需 SUPERVISOR/ADMIN** | 监管简报（SSE） |
| GET | `/ai/health` | 匿名 | 探活；依赖不可达时返回 **HTTP 503** |

经网关：`/api/v1/ai/consumer/interpret` 等。

**参数上限**（超过直接返回 `error` 事件，不会丢给 Tomcat 出 HTML 错误页）：
`qrCode` ≤ 128 且只允许 `[A-Za-z0-9._-]`；`q` ≤ 500；`user` ≤ 64 且只允许 `[A-Za-z0-9_-]`。

### SSE 事件协议

```
event: delta
data: {"text":"这块肉"}

event: delta
data: {"text":"可以放心购买"}

event: done
data: {"conversationId":"44ba271a-...","chars":243}

event: error
data: {"message":"..."}
```

前端收到 `delta` 就追加文本，收到 `done` 结束。

**所有失败都是 `error` 事件，不会再出现「HTTP 200 返回 JSON」或「400 空 body」的情况**——
包括参数校验失败、trace-service 宕机、Dify 不可达。前端只要处理一种响应形态即可。
`error.message` 是脱敏后的用户文案，不含内网地址；原始异常只进服务端日志。

---

## 四、双角色怎么实现的

实验室那个 Chatflow 的 `user_input_form` 是空的，**没法通过 `inputs` 传角色**。
所以角色差异放在后端 Prompt 里，Dify 只当模型网关。

消费者 Prompt 的三条要点：
1. 先说受众："对方看不懂检疫证号、哈希值"
2. 给禁止项："数据里没有的就别说，缺了就说'暂未记录'"
3. 给格式："一句话结论 + 3~5 条短句 + 200~300 字"

监管 Prompt 的三条要点：
1. 受众是执法人员，不科普
2. 固定四段结构 + Markdown 表格
3. 硬约束："数字必须来自数据，不足就写'建议补充采集'"

**实测效果**（数据为空时）：
- 消费者端："这块肉目前无法确认能否放心吃，因为缺少具体的溯源数据…"（没有编造）
- 监管端："当前无异常数据…数据不足，建议补充采集"（守住了约束）

---

## 五、启动方式

```powershell
# 1. 先开 Dify 隧道
双击桌面 实验室Dify.bat

# 2. 起 Nacos（如果没有常驻）
# 3. 起 trace-service（AI 要取它的数据）
# 4. 起 ai-service
cd D:\pork-traceability\backend
mvn -pl ai-service spring-boot:run
```

> 注意：父 pom 里 `spring-boot-maven-plugin` 是 `skip=true`，
> ai-service 的 pom 里已经显式覆盖成 `skip=false`，所以能直接 `spring-boot:run`。
> 其他服务想在命令行跑，同样需要覆盖，或者在 IDEA 里直接 Run 主类。

快速验证：

```powershell
curl http://localhost:8089/ai/health
# 正常：{"code":200,...,"data":{"difyReachable":true}}
# Dify 挂了：HTTP 503 + {"code":503,"message":"AI 依赖（Dify）不可达"}
```

> health 现在会返回 503，方便探针判断。响应里不再回显 baseUrl。

---

## 五之二、安全设计（评审后补的，别删）

### 1. 溯源码必须经过白名单校验 + URL 编码

`TraceDataClient.encodePathSegment()` 先按 `^[A-Za-z0-9._-]{1,128}$` 校验，再 `UriUtils.encodePathSegment`。

**为什么**：原来直接字符串拼 URL，传 `qrCode=../../dashboard/overview` 会让
`GET /trace/safe-buy/../../dashboard/overview` 被 Tomcat 归一化成 `GET /dashboard/overview`，
把监管看板的汇总数据当"这块肉"的溯源结果喂给模型，实测模型会输出「这块肉可以放心购买」。
这是越权读 + 业务结论错误，属于最严重的一类问题。

### 2. 取数失败 ≠ 没有数据

`TraceDataClient.get()` 严格区分三态：

| 情况 | 处理 |
|---|---|
| 网络失败 / 超时 / 5xx | 抛 `TraceDataUnavailableException` → 前端 `error` 事件 |
| 业务码非 200 | 抛 `TraceDataUnavailableException` → 前端 `error` 事件 |
| 业务码 200 + data 为空 | 正常返回空 Map → 模型输出"这项信息暂未记录" |

**为什么**：原来用 `onErrorResume` 把一切失败都变成空 Map，
trace-service 抖一下，消费者就会看到「溯源信息完全缺失，建议您暂时不要购买」——
商家会来投诉，而且系统里没有任何告警。

### 3. 监管接口需要角色

`/ai/admin/report` 会真的烧一次 GPU 推理，必须有门槛：

- 网关 `AuthGlobalFilter`：`/api/v1/ai/consumer` 和 `/api/v1/ai/health` 放行，
  `/api/v1/ai/admin/**` 必须带有效 JWT，解析后注入 `X-User-Id` / `X-User-Role`
- `ai-service` 侧 `AdminRoleInterceptor`：只放行 `SUPERVISOR` / `ADMIN`

本地用 curl 直连 8089 调试时没有网关的头，可以临时：

```powershell
$env:AI_ROLE_CHECK = "false"
```

⚠️ **前提**：ai-service 不能直接暴露公网。否则攻击者自带
`X-User-Role: ADMIN` 就能绕过。部署时用安全组限制 8089，只让网关访问。

### 4. 部署 Nginx 时必须关缓冲

`backend/nginx.conf` 里给 `/api/v1/ai/` 单独加了：

```nginx
proxy_buffering off;
proxy_cache off;
chunked_transfer_encoding on;
proxy_read_timeout 180s;
```

不关的话，Nginx 会收完整段响应再转发，18 秒的监管简报就变成"卡 18 秒然后一次性全出来"，
SSE 的流式体验完全失效。

---

## 六、前端

| 端 | 文件 | 说明 |
|---|---|---|
| 小程序 | `mini-app/src/api/modules/ai.js` | 流式请求，按平台条件编译 |
| 小程序 | `mini-app/src/components/AiInterpretCard.vue` | 解读卡片 + 追问输入框 |
| 小程序 | `pages/safe-buy/safe-buy.vue` | 已嵌入，`auto-start` 进页面就解读 |
| 后台 | `web-admin/src/api/modules/ai.ts` | fetch + ReadableStream 解析 SSE |
| 后台 | `web-admin/src/components/ai/AiReportPanel.vue` | 简报面板，含轻量 Markdown 渲染 |
| 后台 | `views/dashboard/DashboardView.vue` | 已嵌入统计卡片下方 |

### 两个平台坑（已处理）

1. **小程序没有 `TextDecoder`**
   `ai.js` 里手写了 UTF-8 解码器，并且处理了"一个中文字符被切在两块之间"的情况
   （不完整的字节留到下一块），否则中文会乱码。

2. **小程序必须开 `enableChunked`**
   `uni.request` 不开 chunked 拿不到流式分块，只能等全部返回。
   H5 端则走 fetch + ReadableStream，用条件编译分开写。

---

## 七、后续可以怎么扩

1. **在 Dify 里加变量**：给 Chatflow 的 LLM 节点加一个 `role` 输入变量，
   然后 `DifyChatCommand.inputs` 传 `{"role":"consumer"}`，
   Prompt 就能从后端搬到 Dify 里，改话术不用发版。
2. **拆成两个 App**：消费者 / 监管各建一个 Chatflow，各自调模型参数，
   `DifyProperties` 扩成 `Map<String, String> apiKeys`。
3. **自动报告**：用 `@Scheduled` 每天定时跑 `adminReport`，
   结果落库推送给监管端，不需要人点按钮。
4. **多轮记忆**：前端把 `done` 事件里的 `conversationId` 存下来，
   下次请求带上，Dify 会维持上下文。

---

## 八、已知限制

- `application.yml` 里的 `api-key` 是明文，**提交 Git 前建议移到 Nacos 配置中心或环境变量**。
- Dify 的登录接口要 base64 编码密码（`btoa(utf8(password))`），不是 RSA，别被报错信息误导。
- 数据为空时 AI 会如实说"无数据"，这是 Prompt 刻意约束的结果，不是 bug。

---

## 九、相关文档

- **《知识图谱与自然语言操作.md》** —— 2026-11-19 新增的能力：把系统架构扫成知识图谱，
  用一句话定位接口、补参数、带用户身份执行。本文档里的 Dify 隧道、API Key 配置对它同样适用，
  区别只是它用的是 blocking 模式（要结构化 JSON），不是 SSE。
- **《AI接口文档.md》** —— 消费者/监管问答的接口协议（SSE 事件格式）。
  自然语言操作走的是普通 JSON，不走 SSE，协议见知识图谱文档第三节。

