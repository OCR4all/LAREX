export function useInstance() {
  const configuredName = useRuntimeConfig().public.instanceName.trim()
  const instanceName = configuredName || 'LAREX'

  return {
    instanceName,
    storageKey: (scope: string) => `larex-${encodeURIComponent(instanceName)}-${encodeURIComponent(scope)}`
  }
}
