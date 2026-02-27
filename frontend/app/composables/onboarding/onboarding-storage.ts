import type { TourId } from './tour-registry'

export type OnboardingStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type LocalCompletionPayload = {
  version: number
  tours: Partial<Record<TourId, true>>
}

export function buildLocalCompletionKey(input: {
  schemaVersion: number
  dashboardVersion: number
  editorVersion: number
  userScopeId: string
}): string {
  return [
    'larex',
    'onboarding',
    `schema-${input.schemaVersion}`,
    `dash-${input.dashboardVersion}`,
    `editor-${input.editorVersion}`,
    input.userScopeId
  ].join(':')
}

export function createEmptyCompletionPayload(schemaVersion: number): LocalCompletionPayload {
  return {
    version: schemaVersion,
    tours: {}
  }
}

export function parseLocalCompletion(raw: string | null, schemaVersion: number): LocalCompletionPayload {
  if (!raw) return createEmptyCompletionPayload(schemaVersion)

  try {
    const parsed = JSON.parse(raw) as LocalCompletionPayload
    if (!parsed || typeof parsed !== 'object' || parsed.version !== schemaVersion) {
      return createEmptyCompletionPayload(schemaVersion)
    }
    return {
      version: schemaVersion,
      tours: parsed.tours ?? {}
    }
  } catch {
    return createEmptyCompletionPayload(schemaVersion)
  }
}

export function loadLocalCompletion(
  storage: OnboardingStorageLike | null | undefined,
  key: string,
  schemaVersion: number
): LocalCompletionPayload {
  if (!storage) return createEmptyCompletionPayload(schemaVersion)
  const raw = storage.getItem(key)
  return parseLocalCompletion(raw, schemaVersion)
}

export function saveLocalCompletion(
  storage: OnboardingStorageLike | null | undefined,
  key: string,
  payload: LocalCompletionPayload
): void {
  if (!storage) return
  storage.setItem(key, JSON.stringify(payload))
}

export function clearLocalCompletion(
  storage: OnboardingStorageLike | null | undefined,
  key: string
): void {
  if (!storage) return
  storage.removeItem(key)
}

export function setTourCompletion(
  payload: LocalCompletionPayload,
  tourId: TourId,
  completed: boolean
): LocalCompletionPayload {
  const tours = { ...payload.tours }
  if (completed) {
    tours[tourId] = true
    return {
      version: payload.version,
      tours
    }
  }

  const { [tourId]: _removed, ...remainingTours } = tours
  return {
    version: payload.version,
    tours: remainingTours
  }
}

export function isTourCompleted(payload: LocalCompletionPayload, tourId: TourId): boolean {
  return payload.tours[tourId] === true
}
