import { describe, expect, it } from 'vitest'
import { AVATAR_SIZE_PIXELS, getGeneratedAvatarSeed } from '../avatar-rendering'
import {
  resolveAvailableManagedProfileAvatarSrc,
  resolveManagedProfileAvatarSrc
} from '../avatar'

describe('avatar rendering', () => {
  it('matches every Nuxt UI avatar size', () => {
    expect(AVATAR_SIZE_PIXELS).toEqual({
      '3xs': 16,
      '2xs': 20,
      'xs': 24,
      'sm': 28,
      'md': 32,
      'lg': 36,
      'xl': 40,
      '2xl': 44,
      '3xl': 48
    })
  })

  it.each(['GRADIENT', 'IDENTICON', 'FLOW_FIELD'] as const)(
    'uses the stable entity ID for %s avatars',
    (style) => {
      expect(getGeneratedAvatarSeed(style, 'user-123', 'Ada Lovelace')).toBe('user-123')
    }
  )

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
