import type { Plugin } from 'vite'
import type { IncomingMessage, ServerResponse } from 'node:http'

// ========== 模拟账号数据（开发期 mock，后端 auth-service 就绪后删除） ==========
interface MockUser {
  id: number
  username: string
  password: string
  realName: string
  phone: string
  orgId: number
  orgName: string
  role: string
  status: number
  lastLoginTime: string
  permissions: string[]
}

const MOCK_USERS: MockUser[] = [
  {
    id: 1,
    username: 'admin',
    password: 'admin123',
    realName: '系统管理员',
    phone: '13800000001',
    orgId: 1,
    orgName: '集团总部',
    role: 'ADMIN',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
  {
    id: 2,
    username: 'farmer',
    password: 'farmer123',
    realName: '张养殖',
    phone: '13800000002',
    orgId: 2,
    orgName: '东升养殖场',
    role: 'FARMER',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
  {
    id: 3,
    username: 'slaughter',
    password: 'slaughter123',
    realName: '李屠宰',
    phone: '13800000003',
    orgId: 3,
    orgName: '华康屠宰场',
    role: 'SLAUGHTER_OP',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
  {
    id: 4,
    username: 'distributor',
    password: 'dist123',
    realName: '王配送',
    phone: '13800000004',
    orgId: 4,
    orgName: '鲜达冷链物流',
    role: 'DISTRIBUTOR',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
  {
    id: 5,
    username: 'retailer',
    password: 'retail123',
    realName: '赵零售',
    phone: '13800000005',
    orgId: 5,
    orgName: '惠民生鲜超市',
    role: 'RETAILER',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
  {
    id: 6,
    username: 'supervisor',
    password: 'super123',
    realName: '刘监管',
    phone: '13800000006',
    orgId: 6,
    orgName: '市农业农村局',
    role: 'SUPERVISOR',
    status: 1,
    lastLoginTime: '',
    permissions: [],
  },
]

// 去掉 password，返回给前端的用户对象
function publicUser(u: MockUser) {
  const { password: _pwd, ...rest } = u
  return rest
}

function ok(data: unknown) {
  return { code: 200, message: 'ok', data }
}
function fail(message: string, code = 400) {
  return { code, message, data: null }
}

// token 格式：mock.token.<username>.<timestamp>，用户名不含 `.`，可安全反解
function makeToken(username: string) {
  return `mock.token.${username}.${Date.now()}`
}
function makeRefresh(username: string) {
  return `mock.refresh.${username}.${Date.now()}`
}
function parseUsername(token: string): string | null {
  const parts = token.split('.')
  return parts.length === 4 ? parts[2] : null
}

function readBody(req: IncomingMessage): Promise<Record<string, any>> {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

function send(res: ServerResponse, body: unknown) {
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify(body))
}

/**
 * 处理 /api/v1/auth/* 的 mock 请求（挂载点为 /api/v1/auth，req.url 为挂载点之后的路径）
 */
async function handleAuth(req: IncomingMessage, res: ServerResponse, next: () => void) {
  const url = (req.url || '').split('?')[0].replace(/\/$/, '')
  const method = (req.method || 'GET').toUpperCase()

  // POST /auth/login
  if (url === '/login' && method === 'POST') {
    const body = await readBody(req)
    const user = MOCK_USERS.find(
      (u) => u.username === body.username && u.password === body.password,
    )
    if (!user) return send(res, fail('用户名或密码错误'))
    return send(res, ok({ token: makeToken(user.username), refreshToken: makeRefresh(user.username), user: publicUser(user) }))
  }

  // GET /auth/me
  if (url === '/me' && method === 'GET') {
    const auth = req.headers.authorization || ''
    const token = auth.replace(/^Bearer\s+/i, '')
    const username = parseUsername(token)
    const user = MOCK_USERS.find((u) => u.username === username)
    if (!user) return send(res, fail('未登录或登录已过期', 401))
    return send(res, ok(publicUser(user)))
  }

  // POST /auth/refresh
  if (url === '/refresh' && method === 'POST') {
    const body = await readBody(req)
    const username = parseUsername(body.refreshToken || '')
    const user = MOCK_USERS.find((u) => u.username === username)
    if (!user) return send(res, fail('刷新令牌无效', 401))
    return send(res, ok({ token: makeToken(user.username), refreshToken: makeRefresh(user.username) }))
  }

  // POST /auth/logout
  if (url === '/logout' && method === 'POST') {
    return send(res, ok(null))
  }

  // 非 auth 接口交给后续中间件（含 vite 代理）
  next()
}

/**
 * Vite 插件：拦截 /api/v1/auth/* 返回 mock 数据，替代尚未实现的后端 auth-service
 */
export function mockAuthPlugin(): Plugin {
  return {
    name: 'mock-auth-api',
    configureServer(server) {
      server.middlewares.use('/api/v1/auth', handleAuth as any)
    },
  }
}
