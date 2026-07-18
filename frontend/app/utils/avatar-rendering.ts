import type { AvatarSize, AvatarStyle } from '~/types/avatar'

export const AVATAR_SIZE_PIXELS: Record<AvatarSize, number> = {
  '3xs': 16,
  '2xs': 20,
  'xs': 24,
  'sm': 28,
  'md': 32,
  'lg': 36,
  'xl': 40,
  '2xl': 44,
  '3xl': 48
}

export function getGeneratedAvatarSeed(
  style: AvatarStyle,
  stableSeed: string | number,
  displayName: string
) {
  return style === 'INITIALS' ? displayName : stableSeed
}
