import type { H3Event } from 'h3'

const AUTH_REDIRECT_COOKIE = 'larex-auth-redirect'
const AUTH_REDIRECT_MAX_AGE_SECONDS = 10 * 60
const VALIDATION_ORIGIN = 'http://larex.local'

export function resolveAuthRedirect(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string' || value.length === 0 || value.length > 2048 || !value.startsWith('/')) {
    return fallback
  }

  try {
    const url = new URL(value, VALIDATION_ORIGIN)

    if (url.origin !== VALIDATION_ORIGIN || url.pathname.startsWith('/auth/keycloak')) {
      return fallback
    }

    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return fallback
  }
}

function cookieOptions(event: H3Event) {
  return {
    httpOnly: true,
    maxAge: AUTH_REDIRECT_MAX_AGE_SECONDS,
    path: '/auth/keycloak',
    sameSite: 'lax' as const,
    secure: getRequestURL(event).protocol === 'https:'
  }
}

export function storeAuthRedirect(event: H3Event, value: unknown): void {
  const redirectTo = resolveAuthRedirect(value, '')

  if (!redirectTo) {
    deleteCookie(event, AUTH_REDIRECT_COOKIE, cookieOptions(event))
    return
  }

  setCookie(event, AUTH_REDIRECT_COOKIE, redirectTo, cookieOptions(event))
}

export function consumeAuthRedirect(event: H3Event): string {
  const redirectTo = resolveAuthRedirect(getCookie(event, AUTH_REDIRECT_COOKIE))
  deleteCookie(event, AUTH_REDIRECT_COOKIE, cookieOptions(event))
  return redirectTo
}
