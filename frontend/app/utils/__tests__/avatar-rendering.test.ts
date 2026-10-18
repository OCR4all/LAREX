import { describe, expect, it } from 'vitest'
import { getGeneratedAvatarSeed } from '../avatar-rendering'
import {
  resolveAvailableManagedProfileAvatarSrc,
  resolveManagedProfileAvatarSrc
} from '../avatar'

describe('avatar rendering', () => {
  it('uses the stable entity ID for generated avatars', () => {
    expect(getGeneratedAvatarSeed('GRADIENT', 'user-123', 'Ada Lovelace')).toBe('user-123')
  })

  it('uses the display name for initials', () => {
    expect(getGeneratedAvatarSeed('INITIALS', 'user-123', 'Ada Lovelace')).toBe('Ada Lovelace')
  })

  it('accepts only backend-managed profile image URLs', () => {
    expect(resolveManagedProfileAvatarSrc('/api/profile/images/avatar.jpg'))
      .toBe('/api/profile/images/avatar.jpg')
    expect(resolveManagedProfileAvatarSrc('https://keycloak.example/avatar.jpg')).toBeUndefined()
  })

  it('falls back from a backend image URL after that source is invalidated', () => {
    const avatar = '/api/profile/images/8c43ca5c-320b-4245-b0a8-aa7e16f26737.jpg'

    expect(resolveAvailableManagedProfileAvatarSrc(avatar, [])).toBe(avatar)
    expect(resolveAvailableManagedProfileAvatarSrc(avatar, [avatar])).toBeUndefined()
  })
})
