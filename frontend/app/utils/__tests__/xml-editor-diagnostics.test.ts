import { describe, expect, it } from 'vitest'
import { lineColumnToOffset, toCodeMirrorDiagnostics } from '@/utils/xml-editor-diagnostics'

describe('xml-editor-diagnostics', () => {
  it('converts line/column to zero-based offsets', () => {
    const text = 'one\ntwo\nthree'

    expect(lineColumnToOffset(text, 1, 1)).toBe(0)
    expect(lineColumnToOffset(text, 2, 1)).toBe(4)
    expect(lineColumnToOffset(text, 2, 3)).toBe(6)
    expect(lineColumnToOffset(text, 3, 2)).toBe(9)
  })

  it('creates codemirror diagnostics from validation errors', () => {
    const text = '<a>\n  <b/>\n</a>'
    const diagnostics = toCodeMirrorDiagnostics(text, [
      {
        line: 2,
        column: 3,
        severity: 'error',
        code: 'XSD_VALIDATION_ERROR',
        message: 'Unexpected element'
      }
    ])

    expect(diagnostics).toHaveLength(1)
    expect(diagnostics[0]?.from).toBe(6)
    expect(diagnostics[0]?.severity).toBe('error')
    expect(diagnostics[0]?.source).toBe('XSD_VALIDATION_ERROR')
  })
})
