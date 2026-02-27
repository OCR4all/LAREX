import { describe, expect, it } from 'vitest'
import {
  buildLocalCompletionKey,
  clearLocalCompletion,
  createEmptyCompletionPayload,
  isTourCompleted,
  loadLocalCompletion,
  saveLocalCompletion,
  setTourCompletion
} from '../onboarding-storage'

class MemoryStorage {
  private readonly map = new Map<string, string>()

  getItem(key: string): string | null {
    return this.map.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.map.set(key, value)
  }

  removeItem(key: string): void {
    this.map.delete(key)
  }
}

describe('onboarding-storage', () => {
  it('marks and unmarks tour completion', () => {
    const schemaVersion = 1
    const payload = createEmptyCompletionPayload(schemaVersion)
    const marked = setTourCompletion(payload, 'tasks-index', true)
    expect(isTourCompleted(marked, 'tasks-index')).toBe(true)

    const unmarked = setTourCompletion(marked, 'tasks-index', false)
    expect(isTourCompleted(unmarked, 'tasks-index')).toBe(false)
  })

  it('uses versioned key segments and rolls over completion on version change', () => {
    const storage = new MemoryStorage()
    const userScopeId = 'user-1'
    const schemaVersion = 1

    const keyV2 = buildLocalCompletionKey({
      schemaVersion,
      dashboardVersion: 2,
      editorVersion: 2,
      userScopeId
    })
    const keyV3 = buildLocalCompletionKey({
      schemaVersion,
      dashboardVersion: 3,
      editorVersion: 2,
      userScopeId
    })

    const payload = setTourCompletion(createEmptyCompletionPayload(schemaVersion), 'tasks-index', true)
    saveLocalCompletion(storage, keyV2, payload)

    const loadedV2 = loadLocalCompletion(storage, keyV2, schemaVersion)
    const loadedV3 = loadLocalCompletion(storage, keyV3, schemaVersion)

    expect(isTourCompleted(loadedV2, 'tasks-index')).toBe(true)
    expect(isTourCompleted(loadedV3, 'tasks-index')).toBe(false)
  })

  it('clears local completion payload during reset', () => {
    const storage = new MemoryStorage()
    const schemaVersion = 1
    const key = buildLocalCompletionKey({
      schemaVersion,
      dashboardVersion: 2,
      editorVersion: 2,
      userScopeId: 'user-1'
    })

    const payload = setTourCompletion(createEmptyCompletionPayload(schemaVersion), 'editor-layout', true)
    saveLocalCompletion(storage, key, payload)
    expect(isTourCompleted(loadLocalCompletion(storage, key, schemaVersion), 'editor-layout')).toBe(true)

    clearLocalCompletion(storage, key)
    expect(isTourCompleted(loadLocalCompletion(storage, key, schemaVersion), 'editor-layout')).toBe(false)
  })
})
