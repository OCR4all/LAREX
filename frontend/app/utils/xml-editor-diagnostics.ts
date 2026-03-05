import type { Diagnostic } from '@codemirror/lint'

export interface XmlValidationError {
  line: number
  column: number
  severity: string
  code: string
  message: string
}

export function lineColumnToOffset(text: string, line: number, column: number): number {
  const safeLine = Math.max(1, Math.floor(line))
  const safeColumn = Math.max(1, Math.floor(column))

  let offset = 0
  let currentLine = 1

  while (currentLine < safeLine && offset < text.length) {
    const newline = text.indexOf('\n', offset)
    if (newline === -1) return text.length
    offset = newline + 1
    currentLine += 1
  }

  return Math.min(text.length, offset + safeColumn - 1)
}

function toDiagnosticSeverity(value: string): Diagnostic['severity'] {
  const normalized = value.trim().toLowerCase()
  if (normalized === 'warning') return 'warning'
  if (normalized === 'info') return 'info'
  return 'error'
}

export function toCodeMirrorDiagnostics(text: string, errors: XmlValidationError[]): Diagnostic[] {
  return errors.map((error) => {
    let from = lineColumnToOffset(text, error.line, error.column)
    let to = Math.min(text.length, from + 1)
    if (to <= from) {
      from = Math.max(0, from - 1)
      to = Math.min(text.length, from + 1)
    }

    return {
      from,
      to,
      severity: toDiagnosticSeverity(error.severity),
      message: error.message,
      source: error.code
    }
  })
}
