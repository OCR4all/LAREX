import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { isUserSettingsSection, useUserSettings } from '../use-user-settings'

describe('user settings dialog', () => {
  beforeEach(() => {
    const state = new Map<string, Ref>()
    vi.stubGlobal('useState', (key: string, init: () => unknown) => {
      if (!state.has(key)) state.set(key, ref(init()))
      return state.get(key)
    })
  })

  it('shares the selected section and open state between entry points', () => {
    const menu = useUserSettings()
    const dialog = useUserSettings()
    menu.openSettings('notifications')
    expect(dialog.isSettingsOpen.value).toBe(true)
    expect(dialog.settingsSection.value).toBe('notifications')
    dialog.closeSettings()
    expect(menu.isSettingsOpen.value).toBe(false)
    menu.openSettings()
    expect(dialog.settingsSection.value).toBe('profile')
  })

  it('validates sections used by bookmarks and security callbacks', () => {
    expect(isUserSettingsSection('security')).toBe(true)
    expect(isUserSettingsSection('transfers')).toBe(true)
    expect(isUserSettingsSection('appearance')).toBe(true)
    expect(isUserSettingsSection('unknown')).toBe(false)
    expect(isUserSettingsSection(['security'])).toBe(false)
  })
})
