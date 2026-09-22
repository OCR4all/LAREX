type PageLockState = {
  locked?: boolean
  lockedReason?: string | null
}

export function resolvePageLockReason(
  page: PageLockState | null | undefined,
  activeActionLockReason?: string | null
): string | null {
  if (activeActionLockReason) return activeActionLockReason
  if (!page?.locked) return null
  return page.lockedReason || 'Page is locked'
}

export function isActionLockedPage(page: PageLockState | null | undefined): boolean {
  return Boolean(page?.locked && page.lockedReason?.startsWith('LAREX Action running:'))
}
