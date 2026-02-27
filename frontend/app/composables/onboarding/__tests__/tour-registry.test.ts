import { describe, expect, it, vi } from 'vitest'

vi.mock('../tour-utils', () => ({
  clickAndWait: async () => {},
  dispatchOnboardingEvent: () => {},
  ensureDashboardSidebarVisible: async () => {},
  ensureSidebarSectionExpanded: async () => {},
  ensureEditorMode: async () => {},
  waitForElement: async () => document.body,
  waitForVisibleElement: async () => document.body,
  withHookAction: (callback: () => void | Promise<void>) => callback,
  withNextAction: (callback: () => void | Promise<void>) => callback
}))

const { resolveContextTourId } = await import('../tour-registry')

describe('resolveContextTourId', () => {
  it('resolves static dashboard routes', () => {
    expect(resolveContextTourId('/')).toBe('global-intro')
    expect(resolveContextTourId('/tasks')).toBe('tasks-index')
    expect(resolveContextTourId('/labels')).toBe('labels-index')
  })

  it('resolves dynamic builder routes', () => {
    expect(resolveContextTourId('/labels/new')).toBe('labels-builder')
    expect(resolveContextTourId('/labels/abc')).toBe('labels-builder')
    expect(resolveContextTourId('/tag-sets/new')).toBe('tag-sets-builder')
    expect(resolveContextTourId('/virtual-keyboard/keyboard-1')).toBe('virtual-keyboards-builder')
    expect(resolveContextTourId('/codecs/new')).toBe('codecs-builder')
  })

  it('normalizes query and hash fragments', () => {
    expect(resolveContextTourId('/tasks?status=open')).toBe('tasks-index')
    expect(resolveContextTourId('/settings#profile')).toBe('settings-profile')
  })

  it('supports trailing slash routes', () => {
    expect(resolveContextTourId('/tasks/')).toBe('tasks-index')
    expect(resolveContextTourId('/labels/new/')).toBe('labels-builder')
    expect(resolveContextTourId('/workspace/settings/members/')).toBe('workspace-members')
  })

  it('resolves editor tours by mode context', () => {
    expect(resolveContextTourId('/editor')).toBe('editor-layout')
    expect(resolveContextTourId('/editor', { editorMode: 'layout' })).toBe('editor-layout')
    expect(resolveContextTourId('/editor', { editorMode: 'text' })).toBe('editor-text')
  })

  it('returns null for unknown routes', () => {
    expect(resolveContextTourId('/unknown')).toBeNull()
  })
})
