const MANAGED_PROFILE_IMAGE_PATH_PREFIX = '/api/profile/images/'

type AvatarIdentity = {
  firstName?: string | null
  lastName?: string | null
  name?: string | null
  username?: string | null
  login?: string | null
  email?: string | null
}

function getInitial(value?: string | null): string | undefined {
  const normalized = value?.trim()
  if (!normalized) return undefined
  return normalized.charAt(0).toUpperCase()
}

export function resolveManagedProfileAvatarSrc(avatar?: string | null): string | undefined {
  const normalized = avatar?.trim()
  if (!normalized) return undefined

  try {
    const parsed = new URL(normalized, 'http://localhost')
    if (!parsed.pathname.startsWith(MANAGED_PROFILE_IMAGE_PATH_PREFIX)) {
      return undefined
    }

    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    return undefined
  }
}

export function resolveAvailableManagedProfileAvatarSrc(
  avatar: string | null | undefined,
  invalidSources: readonly string[]
): string | undefined {
  const managedSrc = resolveManagedProfileAvatarSrc(avatar)
  return managedSrc && !invalidSources.includes(managedSrc) ? managedSrc : undefined
}

export function getAvatarInitials(identity: AvatarIdentity, fallback = 'U'): string {
  const firstNameInitial = getInitial(identity.firstName)
  const lastNameInitial = getInitial(identity.lastName)

  if (firstNameInitial && lastNameInitial) {
    return `${firstNameInitial}${lastNameInitial}`
  }

  if (firstNameInitial) {
    return firstNameInitial
  }

  const normalizedName = identity.name?.trim()
  if (normalizedName) {
    const nameParts = normalizedName.split(/\s+/).filter(Boolean)
    if (nameParts.length >= 2) {
      const first = nameParts[0]?.charAt(0).toUpperCase() || ''
      const second = nameParts[1]?.charAt(0).toUpperCase() || ''
      return `${first}${second}` || fallback
    }
    return nameParts[0]?.charAt(0).toUpperCase() || fallback
  }

  return getInitial(identity.username)
    || getInitial(identity.login)
    || getInitial(identity.email)
    || fallback
}
