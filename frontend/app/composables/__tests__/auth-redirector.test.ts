import { afterEach, expect, it, vi } from 'vitest'
import { useAuthRedirector } from '../use-auth-redirector'

afterEach(() => vi.unstubAllGlobals())

it('recovers locally without revoking the remembered Keycloak session', async () => {
  const clear = vi.fn().mockResolvedValue(undefined)
  const remoteFetch = vi.fn()
  const clearWorkspace = vi.fn()
  const navigate = vi.fn()
  const initialized = { value: true }
  vi.stubGlobal('useUserSession', () => ({ loggedIn: { value: true }, clear }))
  vi.stubGlobal('useNuxtApp', () => ({ runWithContext: (action: () => unknown) => action() }))
  vi.stubGlobal('useRoute', () => ({ fullPath: '/project/123?tab=pages#page-7' }))
  vi.stubGlobal('useWorkspaceStore', () => ({ clearState: clearWorkspace }))
  vi.stubGlobal('useState', () => initialized)
  vi.stubGlobal('navigateToAuth', navigate)
  vi.stubGlobal('$fetch', remoteFetch)

  await useAuthRedirector().handleAuthError()

  expect(clear).toHaveBeenCalledOnce()
  expect(remoteFetch).not.toHaveBeenCalled()
  expect(clearWorkspace).toHaveBeenCalledOnce()
  expect(initialized.value).toBe(false)
  expect(navigate).toHaveBeenCalledWith({ redirectTo: '/project/123?tab=pages#page-7', replace: true })
  expect(clear.mock.invocationCallOrder[0]).toBeLessThan(navigate.mock.invocationCallOrder[0]!)
})
