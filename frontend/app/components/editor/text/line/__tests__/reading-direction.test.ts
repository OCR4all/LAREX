import { describe, expect, it } from 'vitest'
import {
  computeTextLineReadingDirectionMap,
  getReadingDirectionTextAttributes,
  normalizeReadingDirection
} from '../reading-direction'

describe('reading-direction', () => {
  it('normalizes known PAGE reading direction values', () => {
    expect(normalizeReadingDirection('left-to-right')).toBe('left-to-right')
    expect(normalizeReadingDirection('right-to-left')).toBe('right-to-left')
    expect(normalizeReadingDirection('top-to-bottom')).toBe('top-to-bottom')
    expect(normalizeReadingDirection('bottom-to-top')).toBe('bottom-to-top')
    expect(normalizeReadingDirection('invalid')).toBeUndefined()
    expect(normalizeReadingDirection(undefined)).toBeUndefined()
  })

  it('resolves textline direction from closest source (line > nearest region > page)', () => {
    const map = computeTextLineReadingDirectionMap({
      readingDirection: 'left-to-right',
      regions: [
        {
          readingDirection: 'right-to-left',
          textLines: [
            { id: 'line-1' },
            { id: 'line-2', readingDirection: 'top-to-bottom' }
          ],
          regions: [
            {
              textLines: [{ id: 'line-3' }]
            },
            {
              readingDirection: 'bottom-to-top',
              textLines: [{ id: 'line-4' }]
            }
          ]
        },
        {
          textLines: [{ id: 'line-5' }]
        }
      ]
    })

    expect(map['line-1']).toBe('right-to-left')
    expect(map['line-2']).toBe('top-to-bottom')
    expect(map['line-3']).toBe('right-to-left')
    expect(map['line-4']).toBe('bottom-to-top')
    expect(map['line-5']).toBe('left-to-right')
  })

  it('maps reading direction to native HTML direction and writing-mode attributes', () => {
    expect(getReadingDirectionTextAttributes(undefined)).toEqual({})
    expect(getReadingDirectionTextAttributes('left-to-right')).toEqual({})
    expect(getReadingDirectionTextAttributes('right-to-left')).toEqual({ dir: 'rtl' })
    expect(getReadingDirectionTextAttributes('top-to-bottom')).toEqual({
      style: { writingMode: 'vertical-rl' }
    })
    expect(getReadingDirectionTextAttributes('bottom-to-top')).toEqual({
      style: { writingMode: 'vertical-rl', direction: 'rtl' }
    })
  })
})
