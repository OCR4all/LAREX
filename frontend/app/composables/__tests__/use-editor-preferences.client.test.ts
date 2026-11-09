import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

async function setup() {
  vi.resetModules()
  const windowTarget = new EventTarget()
  const documentTarget = Object.assign(new EventTarget(), { visibilityState: 'visible' })
  const debouncedRequest = vi.fn(async (_url: string, options?: { body: unknown }) => options?.body ?? {})
  const keepaliveRequest = vi.fn(async () => new Response('{}'))
  const states = new Map<string, ReturnType<typeof ref>>()

  vi.stubGlobal('window', windowTarget)
  vi.stubGlobal('document', documentTarget)
  vi.stubGlobal('$fetch', debouncedRequest)
  vi.stubGlobal('fetch', keepaliveRequest)
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })

  const { useEditorPreferences } = await import('../use-editor-preferences')
  return { preferences: useEditorPreferences(), windowTarget, documentTarget, debouncedRequest, keepaliveRequest }
}

describe('useEditorPreferences page-hide flush', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('sends a pending debounced save with keepalive when the page is hidden', async () => {
    const { preferences, windowTarget, debouncedRequest, keepaliveRequest } = await setup()

    preferences.updatePreference('toolbarCompact', false)
    preferences.updatePreference('leftWidthPx', 320)
    windowTarget.dispatchEvent(new Event('pagehide'))

    expect(keepaliveRequest).toHaveBeenCalledTimes(1)
    expect(keepaliveRequest).toHaveBeenCalledWith('/api/editor/preferences', expect.objectContaining({
      method: 'PUT',
      keepalive: true,
      body: JSON.stringify({ toolbarCompact: false, leftWidthPx: 320 })
    }))

    await vi.runAllTimersAsync()
    expect(debouncedRequest).not.toHaveBeenCalled()
  })

  it('flushes when the tab becomes hidden and sends nothing on later page hide', async () => {
    const { preferences, windowTarget, documentTarget, keepaliveRequest } = await setup()

    preferences.updatePreference('pageFocusMode', true)
    documentTarget.visibilityState = 'hidden'
    documentTarget.dispatchEvent(new Event('visibilitychange'))
    windowTarget.dispatchEvent(new Event('pagehide'))

    expect(keepaliveRequest).toHaveBeenCalledTimes(1)
  })

  it('sends nothing on page hide once the debounced save has gone out', async () => {
    const { preferences, windowTarget, debouncedRequest, keepaliveRequest } = await setup()

    preferences.updatePreference('toolbarCompact', true)
    await vi.runAllTimersAsync()
    windowTarget.dispatchEvent(new Event('pagehide'))

    expect(debouncedRequest).toHaveBeenCalledWith('/api/editor/preferences', {
      method: 'PUT',
      body: { toolbarCompact: true }
    })
    expect(keepaliveRequest).not.toHaveBeenCalled()
  })
})
