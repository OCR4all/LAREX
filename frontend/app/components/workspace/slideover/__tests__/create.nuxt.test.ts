import { mockNuxtImport, mountSuspended, registerEndpoint } from '@nuxt/test-utils/runtime'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import WorkspaceCreate from '../create.vue'

const { session, created } = vi.hoisted(() => ({
  session: { roles: ['GLOBAL_ADMIN'] as string[] },
  created: [] as Array<Record<string, unknown>>
}))

mockNuxtImport('useUserSession', () => () => ({ loggedIn: ref(true), user: ref({ roles: session.roles }) }))
mockNuxtImport('useWorkspaceStore', () => () => ({ refreshWorkspaces: vi.fn(), selectWorkspace: vi.fn() }))

registerEndpoint('/api/workspaces', {
  method: 'POST',
  handler: async (event) => {
    created.push(await event.web!.request!.json())
    return { id: 'workspace-1' }
  }
})

async function open() {
  await mountSuspended(WorkspaceCreate, { attrs: { open: true }, attachTo: document.body })
}

async function fill(label: string, value: string) {
  const field = [...document.querySelectorAll('label')].find(node => node.textContent?.trim() === label)
  const input = document.getElementById(field!.htmlFor) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await nextTick()
}

async function submit() {
  document.querySelector('form')!.dispatchEvent(new Event('submit'))
  await vi.waitFor(() => expect(created.length + document.querySelectorAll('[id$="-error"]').length).toBeGreaterThan(0))
}

describe('Create Workspace slideover', () => {
  beforeEach(() => {
    session.roles = ['GLOBAL_ADMIN']
    created.length = 0
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('sends a custom quota in bytes when a global admin sets one', async () => {
    await open()
    await fill('Name', 'Team')
    await fill('Quota Limit (GB)', '2.5')
    await submit()

    expect(created).toEqual([expect.objectContaining({ name: 'Team', quotaLimitBytes: 2_684_354_560 })])
  })

  it('uses the instance default when the quota is left blank', async () => {
    await open()
    await fill('Name', 'Team')
    await submit()

    expect(created).toEqual([expect.objectContaining({ name: 'Team' })])
    expect(created[0]).not.toHaveProperty('quotaLimitBytes')
  })

  it('rejects a negative quota without creating the workspace', async () => {
    await open()
    await fill('Name', 'Team')
    await fill('Quota Limit (GB)', '-1')
    await submit()

    expect(document.body.textContent).toContain('Quota must not be negative')
    expect(created).toEqual([])
  })

  it('does not offer a custom quota to global curators', async () => {
    session.roles = ['GLOBAL_CURATOR']
    await open()

    expect(document.body.textContent).toContain('Workspace Details')
    expect(document.body.textContent).not.toContain('Quota Limit (GB)')
  })
})
