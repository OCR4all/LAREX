export function useEmptyStateRefresh(refresh: () => unknown | Promise<unknown>) {
  const feedback = useRefreshFeedback(refresh)

  return computed(() => ({
    ...feedback.value,
    icon: 'i-lucide-refresh-cw',
    label: 'Refresh',
    color: 'neutral' as const,
    variant: 'subtle' as const
  }))
}
