// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { useLazyPopoverTrigger } from '../use-lazy-popover-trigger'

function clickOn(button: HTMLElement): Event {
  let captured!: Event
  button.addEventListener('click', event => captured = event, { once: true })
  button.click()
  return captured
}

describe('useLazyPopoverTrigger', () => {
  it('opens and anchors to the clicked button', () => {
    const trigger = useLazyPopoverTrigger()
    const button = document.createElement('button')
    let shouldOpen = false
    button.addEventListener('click', event => shouldOpen = trigger.shouldOpen(event, false), { once: true })

    trigger.onPointerDown(false)
    button.click()

    expect(shouldOpen).toBe(true)
    expect(trigger.anchor.value).toBe(button)
  })

  it('does not reopen when the click follows an outside dismiss of the open popover', () => {
    const trigger = useLazyPopoverTrigger()

    trigger.onPointerDown(true)

    expect(trigger.shouldOpen(clickOn(document.createElement('button')), false)).toBe(false)
  })

  it('closes on a keyboard click while open and opens again afterwards', () => {
    const trigger = useLazyPopoverTrigger()
    const button = document.createElement('button')

    expect(trigger.shouldOpen(null, true)).toBe(false)
    expect(trigger.shouldOpen(clickOn(button), false)).toBe(true)
  })
})
