export interface StorageQuotaLike {
  usagePercentage: number
  isQuotaExceeded?: boolean | null
}

export function getStorageQuotaProgressValue(usagePercentage: number): number {
  if (!Number.isFinite(usagePercentage)) return 0
  return Math.max(0, Math.min(usagePercentage, 100))
}

export function getStorageQuotaAlertState(quota: StorageQuotaLike | null | undefined): 'ok' | 'warning' | 'exceeded' {
  if (!quota) return 'ok'
  if (quota.isQuotaExceeded || quota.usagePercentage > 100) return 'exceeded'
  if (quota.usagePercentage >= 80) return 'warning'
  return 'ok'
}
