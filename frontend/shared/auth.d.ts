declare module '#auth-utils' {
  interface User {
    id: string
    login: string
    name: string
    email: string
    avatar: string
    roles: string[]
  }

  interface UserSession {
  }

  interface SecureSessionData {
    accessToken: string
    refreshToken: string
  }
}

export {}
