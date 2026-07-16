import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildAuthUrl } from '../auth-url'

describe('auth URL utils', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('includes the requested post-login route', () => {
    vi.stubGlobal('useColorMode', () => ({ value: 'light' }))

    expect(buildAuthUrl('/project/123?tab=pages#page-7'))
      .toBe('/auth/keycloak?dark=false&redirectTo=%2Fproject%2F123%3Ftab%3Dpages%23page-7')
  })
})
