// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest'
import { focusTextFieldInContext } from '../text-field-navigation'

function setRect(el: HTMLElement, top: number, height: number): void {
  const rect = { top, bottom: top + height, height, left: 0, right: 100, width: 100, x: 0, y: top, toJSON: () => ({}) }
  el.getBoundingClientRect = () => rect
  el.getClientRects = () => [rect] as unknown as DOMRectList
}

// Viewport 0..800; the focus area is 56..704 with its center at 380.
function setup(lines: Array<[top: number, height: number]>) {
  const container = document.createElement('div')
  container.style.overflowY = 'auto'
  setRect(container, 0, 800)
  const fields = lines.map(([top, height]) => {
    const line = document.createElement('div')
    line.setAttribute('data-textline-item', '')
    setRect(line, top, height)
    const field = document.createElement('textarea')
    line.append(field)
    container.append(line)
    return field
  })
  document.body.append(container)
  container.scrollTop = 1000
  return { container, fields }
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('focusTextFieldInContext', () => {
  it('centers the focused line', () => {
    const { container, fields } = setup([[480, 100], [600, 100], [720, 100]])
    focusTextFieldInContext(fields[1]!)

    expect(document.activeElement).toBe(fields[1])
    expect(container.scrollTop).toBe(1000 + 650 - 380)
  })

  it('shifts off center to keep a tall next line in view', () => {
    const { container, fields } = setup([[480, 100], [600, 100], [720, 400]])
    focusTextFieldInContext(fields[1]!)

    expect(container.scrollTop).toBe(1000 + 1120 - 704)
  })

  it('stays centered when the neighbours cannot fit together with the line', () => {
    const { container, fields } = setup([[480, 100], [600, 100], [720, 600]])
    focusTextFieldInContext(fields[1]!)

    expect(container.scrollTop).toBe(1000 + 650 - 380)
  })
})
