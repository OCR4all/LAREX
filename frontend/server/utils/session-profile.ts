import { jwtDecode } from 'jwt-decode'
import type { H3Event } from 'h3'

type RoleClaims = {
  realm_access?: {
    roles?: string[]
  }
  roles?: string[]
}

type IdentityClaims = RoleClaims & {
  sub?: string
  id?: string
  preferred_username?: string
  name?: string
  given_name?: string
  family_name?: string
  email?: string
}

type ProfileResponse = {
  username?: string | null
  email?: string | null
  firstName?: string | null
  lastName?: string | null
  avatar?: string | null
}

type SessionUser = {
  id: string
  login: string
  name: string
  email: string
  avatar?: string
  roles: string[]
}

const MANAGED_PROFILE_IMAGE_PATH_PREFIX = '/api/profile/images/'

function resolveManagedProfileAvatarSrc(avatar?: string | null): string | undefined {
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

function resolveRolesFromClaims(identity: RoleClaims): string[] {
  if (Array.isArray(identity.realm_access?.roles)) {
    return identity.realm_access.roles
  }
  if (Array.isArray(identity.roles)) {
    return identity.roles
  }
  return []
}

function decodeRoleClaims(accessToken: string): RoleClaims | null {
  try {
    return jwtDecode<RoleClaims>(accessToken)
  } catch {
    return null
  }
}

function resolveRoles(accessToken: string): string[] {
  const tokenClaims = decodeRoleClaims(accessToken)
  if (tokenClaims) {
    return resolveRolesFromClaims(tokenClaims)
  }

  return []
}

function resolveDisplayName(identity: IdentityClaims, profile: ProfileResponse | null, login: string): string {
  const firstName = profile?.firstName?.trim() || identity.given_name?.trim() || ''
  const lastName = profile?.lastName?.trim() || identity.family_name?.trim() || ''
  const fullName = [firstName, lastName].filter(Boolean).join(' ').trim()

  if (fullName) {
    return fullName
  }

  return identity.name?.trim() || login
}

async function fetchProfile(event: H3Event, accessToken: string): Promise<ProfileResponse | null> {
  const config = useRuntimeConfig(event)
  const profileUrl = `${config.apiBaseInternal}/profile`

  try {
    return await $fetch<ProfileResponse>(profileUrl, {
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    })
  } catch (error) {
    console.warn('[auth] Failed to load backend profile for session avatar:', error)
    return null
  }
}

export async function buildSessionUser(
  event: H3Event,
  identity: IdentityClaims,
  accessToken: string
): Promise<SessionUser> {
  const profile = await fetchProfile(event, accessToken)

  const login = profile?.username?.trim()
    || identity.preferred_username?.trim()
    || identity.id?.trim()
    || identity.sub?.trim()
    || 'user'

  const id = identity.sub?.trim() || identity.id?.trim() || login
  const name = resolveDisplayName(identity, profile, login)
  const email = profile?.email?.trim() || identity.email?.trim() || ''
  const avatar = resolveManagedProfileAvatarSrc(profile?.avatar)
  const roles = resolveRoles(accessToken)

  return {
    id,
    login,
    name,
    email,
    avatar,
    roles
  }
}
