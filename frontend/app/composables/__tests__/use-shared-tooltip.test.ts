// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, type App } from 'vue'
import { SHARED_TOOLTIP_DELAY_MS, useSharedTooltip } from '../use-shared-tooltip'

let app: App | null = null

function mountHost() {
  const root = document.createElement('div')
  root.innerHTML = `
    <button id="delete" data-tooltip="Delete textline"><span id="icon"></span></button>
    <button id="plain">Plain</button>
    <button id="other" data-tooltip="Add variant"></button>`
  document.body.append(root)
  let state!: ReturnType<typeof useSharedTooltip>
  app = createApp(defineComponent({
    setup() {
      state = useSharedTooltip(() => root)
      return () => h('div')
    }
  }))
  app.mount(document.createElement('div'))
  const get = (id: string) => root.querySelector<HTMLElement>(`#${id}`)!
  return { state, get }
}

function pointer(type: 'pointerover' | 'pointerout', target: HTMLElement, relatedTarget: EventTarget | null = null) {
  target.dispatchEvent(new PointerEvent(type, { bubbles: true, relatedTarget }))
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  app?.unmount()
  app = null
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('useSharedTooltip', () => {
  it('shows the hovered element text after the delay', () => {
    const { state, get } = mountHost()

    pointer('pointerover', get('icon'))
    expect(state.open.value).toBe(false)

    vi.advanceTimersByTime(SHARED_TOOLTIP_DELAY_MS)
    expect(state.open.value).toBe(true)
    expect(state.reference.value).toBe(get('delete'))
    expect(state.text.value).toBe('Delete textline')
  })

  it('ignores elements without a tooltip', () => {
    const { state, get } = mountHost()

    pointer('pointerover', get('plain'))
    vi.runAllTimers()

    expect(state.open.value).toBe(false)
  })

  it('does not show when the pointer leaves before the delay', () => {
    const { state, get } = mountHost()

    pointer('pointerover', get('delete'))
    pointer('pointerout', get('delete'), document.body)
    vi.runAllTimers()

    expect(state.open.value).toBe(false)
  })

  it('stays open while the pointer moves within the element', () => {
    const { state, get } = mountHost()
    pointer('pointerover', get('delete'))
    vi.runAllTimers()

    pointer('pointerout', get('delete'), get('icon'))

    expect(state.open.value).toBe(true)
  })

  it('moves to the next element and hides on pointer down', () => {
    const { state, get } = mountHost()
    pointer('pointerover', get('delete'))
    vi.runAllTimers()

    pointer('pointerover', get('other'))
    vi.runAllTimers()
    expect(state.text.value).toBe('Add variant')

    get('other').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
    expect(state.open.value).toBe(false)
  })

  it('shows at once for keyboard focus and hides on blur', () => {
    const { state, get } = mountHost()
    const button = get('other')
    const matches = button.matches.bind(button)
    vi.spyOn(button, 'matches').mockImplementation((selector: string) => selector === ':focus-visible' || matches(selector))

    button.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    vi.advanceTimersByTime(0)
    expect(state.open.value).toBe(true)

    button.dispatchEvent(new FocusEvent('focusout', { bubbles: true }))
    expect(state.open.value).toBe(false)
  })

  it('stops listening on unmount', () => {
    const { state, get } = mountHost()
    app!.unmount()
    app = null

    pointer('pointerover', get('delete'))
    vi.runAllTimers()

    expect(state.open.value).toBe(false)
  })
})
