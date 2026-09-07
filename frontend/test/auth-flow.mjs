// Run after pnpm build: node test/auth-flow.mjs [path/to/server/index.mjs]
// Uses an isolated application server, encrypted cookies, and a local OAuth stub.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { createServer } from 'node:http'
import { resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { sealSession, unsealSession } from 'h3'

const jwt = exp => `header.${Buffer.from(JSON.stringify({ exp })).toString('base64url')}.signature`
const password = crypto.randomUUID()
const sessionConfig = { name: 'nuxt-session', password }
const user = { id: 'auth-smoke-user', name: 'Auth Smoke User', login: 'auth-smoke', email: '', roles: [] }
const exchanges = new Map()
let outage = false
const upstream = createServer(async (req, res) => {
  res.setHeader('content-type', 'application/json')
  if (req.url.endsWith('/token')) {
    let body = ''
    for await (const chunk of req) body += chunk
    const form = new URLSearchParams(body)
    const token = form.get('refresh_token')
    exchanges.set(token, (exchanges.get(token) || 0) + 1)
    if (token === 'timeout') return
    if (outage) {
      res.writeHead(503).end(JSON.stringify({ error: 'temporarily_unavailable' }))
    } else if (token?.startsWith('expired') || form.get('code')) {
      res.writeHead(400).end(JSON.stringify({ error: 'invalid_grant' }))
    } else {
      await delay(50)
      res.end(JSON.stringify({ access_token: jwt(Math.floor(Date.now() / 1000) + 300), refresh_token: `${token}-rotated`, expires_in: 300 }))
    }
  } else {
    res.end(JSON.stringify(req.url === '/api/workspaces' ? [] : {}))
  }
})
upstream.listen(0, '127.0.0.1')
await once(upstream, 'listening')
const upstreamUrl = `http://127.0.0.1:${upstream.address().port}`
const reservation = createServer()
reservation.listen(0, '127.0.0.1')
await once(reservation, 'listening')
const port = reservation.address().port
await new Promise(r => reservation.close(r))
const base = `http://127.0.0.1:${port}`
const server = spawn(process.execPath, [resolve(process.argv[2] || '.output/server/index.mjs')], {
  env: {
    ...process.env,
    NODE_ENV: 'production', NITRO_HOST: '127.0.0.1', NITRO_PORT: String(port),
    NUXT_SESSION_PASSWORD: password,
    NUXT_OAUTH_KEYCLOAK_SERVER_URL: upstreamUrl,
    NUXT_OAUTH_KEYCLOAK_SERVER_URL_INTERNAL: upstreamUrl,
    NUXT_OAUTH_KEYCLOAK_CLIENT_SECRET: 'test-secret',
    NUXT_OAUTH_KEYCLOAK_REDIRECT_URL: `${base}/auth/keycloak`,
    NUXT_API_BASE_INTERNAL: `${upstreamUrl}/api`
  },
  stdio: ['ignore', 'pipe', 'pipe']
})
let logs = ''
server.stdout.on('data', (chunk) => {
  logs += chunk
})
server.stderr.on('data', (chunk) => {
  logs += chunk
})
const request = (path, cookie, method = 'GET') => fetch(`${base}${path}`, {
  method, redirect: 'manual', headers: cookie ? { cookie } : {}, signal: AbortSignal.timeout(15000)
})
const makeCookie = async (refreshToken) => {
  const sealed = await sealSession({ context: { sessions: { 'nuxt-session': {
    id: crypto.randomUUID(), createdAt: Date.now(),
    data: { user, secure: { accessToken: jwt(1), refreshToken, accessTokenExpires: 1000 } }
  } } } }, sessionConfig)
  return `nuxt-session=${sealed}`
}
const cookieFrom = response => response.headers.getSetCookie().find(c => c.startsWith('nuxt-session='))?.split(';')[0]
try {
  for (let i = 0; i < 100; i++) {
    if (server.exitCode !== null) throw new Error(`Application server exited: ${logs}`)
    if (await request('/api/_auth/session').then(r => r.ok).catch(() => false)) break
    await delay(100)
  }

  const original = await makeCookie('ssr')
  const page = await request('/', original)
  assert.equal(page.status, 200, 'successful refresh renders the protected page')
  assert.match(page.headers.get('cache-control'), /no-store/)
  const rotated = cookieFrom(page)
  assert.ok(rotated && rotated !== original, 'SSR sends the refreshed cookie to the browser')
  const decoded = await unsealSession({}, sessionConfig, decodeURIComponent(rotated.slice('nuxt-session='.length)))
  assert.equal(decoded.data.secure.refreshToken, 'ssr-rotated')
  const html = await page.text()
  assert.ok(html.includes(user.id), 'SSR sees the authenticated user')
  assert.ok(!html.includes(decoded.data.secure.accessToken), 'access token is absent from HTML')
  assert.ok(!html.includes('ssr-rotated'), 'refresh token is absent from HTML')
  const sessionResponse = await request('/api/_auth/session', rotated)
  assert.equal((await sessionResponse.json()).user.id, user.id)
  assert.equal(exchanges.get('ssr'), 1, 'SSR and subsequent fetches do not repeat refresh')
  console.log('PASS: SSR refresh, browser cookie persistence, no token leakage')

  const expired = await request('/', await makeCookie('expired-ssr'))
  assert.equal(expired.status, 302)
  assert.ok(expired.headers.get('location').startsWith('/auth/keycloak'))
  assert.equal(cookieFrom(expired), 'nuxt-session=')
  assert.ok(!(await expired.text()).includes(user.id))
  console.log('PASS: expired session redirects before rendering protected content')

  const concurrentCookie = await makeCookie('concurrent')
  const results = await Promise.all([
    request('/api/auth/check', concurrentCookie),
    request('/api/auth/jwt/refresh', concurrentCookie, 'POST'),
    request('/api/_auth/session', concurrentCookie)
  ])
  for (const result of results) {
    assert.equal(result.status, 200)
    assert.ok(cookieFrom(result), 'each concurrent response receives the rotated cookie')
  }
  assert.equal(exchanges.get('concurrent'), 1)
  console.log('PASS: concurrent automatic and forced refresh, including expired access tokens')

  outage = true
  const retryCookie = await makeCookie('retry')
  for (const [path, method] of [['/', 'GET'], ['/api/auth/check', 'GET'], ['/api/auth/jwt/refresh', 'POST'], ['/api/workspaces', 'GET'], ['/api/upload-proxy/profile/image', 'POST']]) {
    const response = await request(path, retryCookie, method)
    assert.equal(response.status, 503, `${path} reports temporary unavailability`)
    assert.equal(cookieFrom(response), undefined, `${path} preserves the cookie`)
    assert.equal(response.headers.get('location'), null, `${path} does not loop through login`)
    assert.ok(!(await response.text()).includes(user.id))
  }
  const csr = await request('/api/_auth/session', retryCookie)
  const csrData = await csr.json()
  assert.equal(csrData.authUnavailable, true)
  assert.equal(csrData.user, undefined)
  assert.equal(cookieFrom(csr), undefined)
  outage = false
  const recovered = await request('/', retryCookie)
  assert.equal(recovered.status, 200)
  assert.ok(cookieFrom(recovered))
  console.log('PASS: outage blocks content, preserves sessions, and recovers without login')

  const timedOut = await request('/api/auth/check', await makeCookie('timeout'))
  assert.equal(timedOut.status, 503)
  assert.equal(cookieFrom(timedOut), undefined)
  assert.equal(exchanges.get('timeout'), 1, 'timed-out exchanges are not retried')
  console.log('PASS: unresponsive auth service times out without clearing the session')

  const callback = await request('/auth/keycloak?code=used-code')
  assert.equal(callback.status, 302)
  assert.equal(callback.headers.get('location'), '/auth/keycloak?redirectTo=%2F')
  const denied = await request('/auth/keycloak?error=access_denied')
  assert.equal(denied.status, 401)
  assert.equal(denied.headers.get('location'), null, 'failed login does not redirect in a loop')
  console.log('PASS: rejected callback restarts login')
} catch (error) {
  console.error(logs.slice(-6000))
  throw error
} finally {
  server.kill('SIGTERM')
  upstream.closeAllConnections()
  await new Promise(r => upstream.close(r))
}
