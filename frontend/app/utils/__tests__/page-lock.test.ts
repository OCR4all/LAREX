import { describe, expect, it } from 'vitest'
import { resolvePageLockReason } from '../page-lock'

describe('resolvePageLockReason', () => {
  it('keeps a persisted action lock visible after the run is no longer active', () => {
    expect(resolvePageLockReason({
      locked: true,
      lockedReason: 'LAREX Action running: Faulty OCR'
    })).toBe('LAREX Action running: Faulty OCR')
  })

  it('uses the active run while the persisted page state is catching up', () => {
    expect(resolvePageLockReason({ locked: false }, 'LAREX Action running: OCR'))
      .toBe('LAREX Action running: OCR')
  })

  it('falls back to a generic reason for a persisted lock without one', () => {
    expect(resolvePageLockReason({ locked: true, lockedReason: null }))
      .toBe('Page is locked')
  })

  it('returns null for an unlocked page without an active action run', () => {
    expect(resolvePageLockReason({ locked: false })).toBeNull()
  })
})
