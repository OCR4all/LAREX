import type { AvatarSettings, AvatarStyle } from '~/types/avatar'

export const DEFAULT_AVATAR_STYLE: AvatarStyle = 'GRADIENT'

export function useAvatarSettings() {
  const requestFetch = useRequestFetch()
  const defaultStyle = useState<AvatarStyle>('avatar.defaultStyle', () => DEFAULT_AVATAR_STYLE)
  const initialized = useState<boolean>('avatar.settingsInitialized', () => false)
  const pending = useState<boolean>('avatar.settingsPending', () => false)

  async function refresh() {
    pending.value = true
    try {
      const settings = await requestFetch<AvatarSettings>('/api/avatar-settings')
      defaultStyle.value = settings.defaultStyle
      initialized.value = true
    } catch (error) {
      defaultStyle.value = DEFAULT_AVATAR_STYLE
      console.warn('Failed to load avatar settings; using Gradient avatars:', error)
    } finally {
      pending.value = false
    }
  }

  async function initialize() {
    if (initialized.value || pending.value) return
    await refresh()
  }

  return {
    defaultStyle: readonly(defaultStyle),
    initialized: readonly(initialized),
    pending: readonly(pending),
    initialize,
    refresh
  }
}
