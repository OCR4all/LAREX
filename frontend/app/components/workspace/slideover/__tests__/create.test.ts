import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as z from 'zod'

const { descriptor } = parse(readFileSync(new URL('../create.vue', import.meta.url), 'utf8'))
const compiled = transpileModule(compileScript(descriptor, { id: 'workspace-create-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
interface FormData {
  name: string
  description?: string
  quotaInGB?: number
}
interface Setup {
  schema: z.ZodType<FormData>
  state: Partial<FormData>
  canSetQuota: Vue.ComputedRef<boolean>
  invitedUsers: Vue.Ref<Array<{ userId: string, username: string, role: 'EDITOR' | 'CURATOR' }>>
  onSubmit: (event: { data: FormData }) => Promise<void>
}
const component: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', compiled)((name: string) => {
  if (name === 'zod') return z
  if (name === 'vue') return Vue
  throw new Error(`Unexpected import ${name}`)
}, component)

describe('Workspace creation quota', () => {
  const fetch = vi.fn()
  const user = Vue.ref({ roles: ['GLOBAL_ADMIN'] })
  let scope: Vue.EffectScope
  let setup: Setup

  beforeEach(() => {
    vi.clearAllMocks()
    user.value = { roles: ['GLOBAL_ADMIN'] }
    for (const name of ['ref', 'reactive', 'computed', 'watch'] as const) vi.stubGlobal(name, Vue[name])
    vi.stubGlobal('useWorkspaceStore', () => ({ refreshWorkspaces: vi.fn(), selectWorkspace: vi.fn() }))
    vi.stubGlobal('useUserSession', () => ({ user }))
    vi.stubGlobal('useDebounceFn', (fn: unknown) => fn)
    vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
    vi.stubGlobal('useId', () => 'workspace-form')
    vi.stubGlobal('globalKey', (...parts: string[]) => parts.join(':'))
    vi.stubGlobal('refreshNuxtData', vi.fn())
    vi.stubGlobal('$fetch', fetch.mockResolvedValue({ id: 'workspace-1' }))
    scope = Vue.effectScope()
    setup = scope.run(() => component.default!.setup({}, { expose: () => {}, emit: vi.fn() }))!
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('sends a fractional GB quota as bytes alongside the initial invitations', async () => {
    setup.invitedUsers.value = [{ userId: 'member-1', username: 'member', role: 'EDITOR' }]
    await setup.onSubmit({ data: setup.schema.parse({ name: 'Team', quotaInGB: 2.5 }) })

    expect(fetch).toHaveBeenCalledWith('/api/workspaces', {
      method: 'POST',
      body: {
        name: 'Team', quotaLimitBytes: 2_684_354_560,
        initialInvites: [{ userId: 'member-1', role: 'EDITOR' }]
      }
    })
  })

  it.each([undefined, '', null])('uses the default quota for an empty value (%s)', async (quotaInGB) => {
    await setup.onSubmit({ data: setup.schema.parse({ name: 'Team', quotaInGB }) })

    expect(fetch.mock.calls[0]?.[1].body.quotaLimitBytes).toBeUndefined()
  })

  it('preserves an explicit zero quota', async () => {
    await setup.onSubmit({ data: setup.schema.parse({ name: 'Team', quotaInGB: 0 }) })

    expect(fetch.mock.calls[0]?.[1].body.quotaLimitBytes).toBe(0)
  })

  it.each([-1, NaN, Infinity, Number.MAX_SAFE_INTEGER])('rejects an invalid quota (%s)', (quotaInGB) => {
    expect(setup.schema.safeParse({ name: 'Team', quotaInGB }).success).toBe(false)
  })

  it('hides and omits custom quota for global curators', async () => {
    user.value = { roles: ['GLOBAL_CURATOR'] }
    expect(setup.canSetQuota.value).toBe(false)
    await setup.onSubmit({ data: { name: 'Team', quotaInGB: 5 } })

    expect(fetch.mock.calls[0]?.[1].body.quotaLimitBytes).toBeUndefined()
  })
})
