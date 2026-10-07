export const USER_SETTINGS_SECTIONS = ['profile', 'appearance', 'invitations', 'transfers', 'notifications', 'security', 'about'] as const
export type UserSettingsSection = typeof USER_SETTINGS_SECTIONS[number]

export function isUserSettingsSection(value: unknown): value is UserSettingsSection {
  return typeof value === 'string' && USER_SETTINGS_SECTIONS.includes(value as UserSettingsSection)
}

export function useUserSettings() {
  const isSettingsOpen = useState('user-settings-open', () => false)
  const settingsSection = useState<UserSettingsSection>('user-settings-section', () => 'profile')

  function openSettings(section: UserSettingsSection = 'profile') {
    settingsSection.value = section
    isSettingsOpen.value = true
  }

  function closeSettings() {
    isSettingsOpen.value = false
  }

  return { isSettingsOpen, settingsSection, openSettings, closeSettings }
}
