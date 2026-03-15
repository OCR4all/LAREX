export const useKeycloakUrls = () => {
  const { loggedIn } = useUserSession()
  const requestFetch = import.meta.server ? useRequestFetch() : $fetch

  const getAccountConsoleUrl = async () => {
    if (!loggedIn.value) {
      return null
    }

    try {
      const config = await requestFetch<{ serverUrl: string, realm: string }>('/api/auth/keycloak-config')
      return `${config.serverUrl}/realms/${config.realm}/account`
    } catch (error) {
      console.error('Failed to get Keycloak configuration:', error)
      return null
    }
  }

  const getPasswordChangeUrl = async () => {
    if (!loggedIn.value) {
      return null
    }

    try {
      const url = await requestFetch<string>('/api/auth/keycloak/password-change-url')
      return url
    } catch (error) {
      console.error('Failed to get password change URL:', error)
      return null
    }
  }

  const getDeleteAccountUrl = async () => {
    if (!loggedIn.value) {
      return null
    }

    try {
      const url = await requestFetch<string>('/api/auth/keycloak/delete-account-url')
      return url
    } catch (error) {
      console.error('Failed to get delete account URL:', error)
      return null
    }
  }

  return {
    getAccountConsoleUrl,
    getPasswordChangeUrl,
    getDeleteAccountUrl
  }
}
