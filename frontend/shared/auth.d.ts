declare module '#auth-utils' {
  interface User {
    id: string
    login: string
    name: string
    email: string
    avatar?: string
    roles: string[]
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- required module augmentation hook
  interface UserSession {}

  interface SecureSessionData {
    accessToken: string
    refreshToken: string
    accessTokenExpires: number
  }
}

export {}
