import { randomBytes } from 'node:crypto'
import { createError } from 'h3'
import type { PublicReleaseKind } from './public-release-download'

interface PublicReleaseBrowserSession {
  kind: PublicReleaseKind
  sharePublicId: string
  authorizationHeader: string
  issuedToIp: string | null
  issuedToUserAgent: string | null
  expiresAt: number
}

interface FailedAttemptWindow {
  failures: number
  windowStartedAt: number
  blockedUntil: number
}

const SESSION_TTL_MS = 60_000
const MAX_SESSION_COUNT = 2048

const FAILURE_WINDOW_MS = 10 * 60_000
const BLOCK_DURATION_MS = 10 * 60_000
const MAX_FAILURES_PER_WINDOW = 8
const MAX_ATTEMPT_KEYS = 10_000

const browserSessions = new Map<string, PublicReleaseBrowserSession>()
const failedAttempts = new Map<string, FailedAttemptWindow>()

function cleanupExpiredSessions(now: number): void {
  if (browserSessions.size > MAX_SESSION_COUNT) {
    const sessions = [...browserSessions.entries()]
      .sort((a, b) => a[1].expiresAt - b[1].expiresAt)

    for (const [token] of sessions.slice(0, sessions.length - MAX_SESSION_COUNT)) {
      browserSessions.delete(token)
    }
  }

  for (const [token, session] of browserSessions.entries()) {
    if (session.expiresAt <= now) {
      browserSessions.delete(token)
    }
  }
}

function cleanupFailedAttempts(now: number): void {
  if (failedAttempts.size > MAX_ATTEMPT_KEYS) {
    const attempts = [...failedAttempts.entries()]
      .sort((a, b) => {
        const aRecency = Math.max(a[1].windowStartedAt, a[1].blockedUntil)
        const bRecency = Math.max(b[1].windowStartedAt, b[1].blockedUntil)
        return aRecency - bRecency
      })
    for (const [key] of attempts.slice(0, attempts.length - MAX_ATTEMPT_KEYS)) {
      failedAttempts.delete(key)
    }
  }

  for (const [key, state] of failedAttempts.entries()) {
    const windowExpired = now - state.windowStartedAt > FAILURE_WINDOW_MS
    const blockExpired = state.blockedUntil !== 0 && state.blockedUntil <= now
    if (windowExpired && blockExpired) {
      failedAttempts.delete(key)
    }
  }
}

export function buildPublicReleaseAttemptKey(
  sharePublicId: string,
  requesterIp: string | null,
  requesterUserAgent: string | null
): string {
  const ip = requesterIp || 'unknown-ip'
  const userAgent = requesterUserAgent || 'unknown-ua'
  return `${sharePublicId}:${ip}:${userAgent}`
}

export function assertPublicReleaseAttemptAllowed(attemptKey: string, now = Date.now()): void {
  cleanupFailedAttempts(now)
  const state = failedAttempts.get(attemptKey)
  if (!state) {
    return
  }
  if (state.blockedUntil > now) {
    throw createError({
      statusCode: 429,
      statusMessage: 'Too many failed attempts. Please try again later.'
    })
  }
}

export function registerPublicReleaseAttemptResult(attemptKey: string, success: boolean, now = Date.now()): void {
  cleanupFailedAttempts(now)

  if (success) {
    failedAttempts.delete(attemptKey)
    return
  }

  const existing = failedAttempts.get(attemptKey)
  if (!existing || now - existing.windowStartedAt > FAILURE_WINDOW_MS) {
    failedAttempts.set(attemptKey, {
      failures: 1,
      windowStartedAt: now,
      blockedUntil: 0
    })
    return
  }

  const failures = existing.failures + 1
  const blockedUntil = failures >= MAX_FAILURES_PER_WINDOW
    ? now + BLOCK_DURATION_MS
    : existing.blockedUntil

  failedAttempts.set(attemptKey, {
    failures,
    windowStartedAt: existing.windowStartedAt,
    blockedUntil
  })
}

export function createPublicReleaseBrowserSession(
  kind: PublicReleaseKind,
  sharePublicId: string,
  authorizationHeader: string,
  requesterIp: string | null,
  requesterUserAgent: string | null,
  now = Date.now()
): string {
  cleanupExpiredSessions(now)

  const token = randomBytes(24).toString('base64url')
  browserSessions.set(token, {
    kind,
    sharePublicId,
    authorizationHeader,
    issuedToIp: requesterIp,
    issuedToUserAgent: requesterUserAgent,
    expiresAt: now + SESSION_TTL_MS
  })
  return token
}

export function consumePublicReleaseBrowserSession(
  token: string,
  requesterIp: string | null,
  requesterUserAgent: string | null,
  now = Date.now()
): PublicReleaseBrowserSession | null {
  cleanupExpiredSessions(now)

  const session = browserSessions.get(token)
  if (!session) {
    return null
  }

  browserSessions.delete(token)
  if (session.expiresAt <= now) {
    return null
  }

  if (session.issuedToIp && requesterIp && session.issuedToIp !== requesterIp) {
    return null
  }

  if (session.issuedToUserAgent && requesterUserAgent && session.issuedToUserAgent !== requesterUserAgent) {
    return null
  }

  return session
}
