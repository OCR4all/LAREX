import { beforeAll, describe, expect, it, vi } from 'vitest'

vi.mock('nuxt/app', () => ({
  useAppConfig: () => ({})
}))

let resolveEditorCustomCursorPreset: typeof import('../use-editor-custom-cursor').resolveEditorCustomCursorPreset

beforeAll(async () => {
  const module = await import('../use-editor-custom-cursor')
  resolveEditorCustomCursorPreset = module.resolveEditorCustomCursorPreset
})

describe('use-editor-custom-cursor', () => {
  it('resolves the active editor tool cursor preset', () => {
    expect(resolveEditorCustomCursorPreset({ actionWandActive: true })).toBe('actionWand')
    expect(resolveEditorCustomCursorPreset({ actionWandActive: false })).toBeNull()
  })
})
