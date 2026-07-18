export type AvatarStyle = 'GRADIENT' | 'IDENTICON' | 'FLOW_FIELD' | 'INITIALS'

export type AvatarSize = '3xs' | '2xs' | 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl'

export interface AvatarSettings {
  defaultStyle: AvatarStyle
}

export interface AdminAvatarSettings extends AvatarSettings {
  updatedAt: string | null
  updatedByUserId: string | null
}
