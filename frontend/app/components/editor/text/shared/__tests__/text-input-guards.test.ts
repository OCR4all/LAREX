import { describe, expect, it, vi } from 'vitest'
import {
  handleSingleLineTextareaBeforeInput,
  handleSingleLineTextareaKeydownEnter,
  normalizeSingleLineText
} from '../text-input-guards'

function inputEvent(inputType: string) {
  return {
    inputType,
    preventDefault: vi.fn()
  } as unknown as InputEvent
}

function keyboardEvent(key: string) {
  return {
    key,
    altKey: false,
    ctrlKey: false,
    metaKey: false,
    preventDefault: vi.fn()
  } as unknown as KeyboardEvent
}

describe('full text input guards', () => {
  it.each(['a', 'Backspace', ' '])('allows the %s key to edit text', (key) => {
    const event = keyboardEvent(key)

    handleSingleLineTextareaKeydownEnter(event, true)

    expect(event.preventDefault).not.toHaveBeenCalled()
  })

  it('blocks Enter from changing PAGE line structure', () => {
    const event = keyboardEvent('Enter')

    handleSingleLineTextareaKeydownEnter(event, true)

    expect(event.preventDefault).toHaveBeenCalledOnce()
  })

  it.each([
    'insertText',
    'deleteContentBackward',
    'deleteContentForward'
  ])('allows %s edits', (inputType) => {
    const event = inputEvent(inputType)

    handleSingleLineTextareaBeforeInput(event, true)

    expect(event.preventDefault).not.toHaveBeenCalled()
  })

  it.each([
    'insertLineBreak',
    'insertParagraph'
  ])('blocks %s from changing PAGE line structure', (inputType) => {
    const event = inputEvent(inputType)

    handleSingleLineTextareaBeforeInput(event, true)

    expect(event.preventDefault).toHaveBeenCalledOnce()
  })

  it('normalizes pasted line breaks to spaces', () => {
    expect(normalizeSingleLineText('one\n two\r\nthree')).toBe('one two three')
  })
})
