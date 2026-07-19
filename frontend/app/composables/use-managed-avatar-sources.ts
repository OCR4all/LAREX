const MAX_INVALID_SOURCES = 100

export const useManagedAvatarSources = () => {
  const invalidSources = useState<string[]>('avatar.invalidManagedSources', () => [])

  const resolve = (avatar?: string | null): string | undefined => {
    return resolveAvailableManagedProfileAvatarSrc(avatar, invalidSources.value)
  }

  const invalidate = (avatar?: string | null) => {
    const managedSrc = resolveManagedProfileAvatarSrc(avatar)
    if (!managedSrc || invalidSources.value.includes(managedSrc)) return

    invalidSources.value = [
      ...invalidSources.value.slice(-(MAX_INVALID_SOURCES - 1)),
      managedSrc
    ]
  }

  return {
    invalidSources: readonly(invalidSources),
    resolve,
    invalidate
  }
}
