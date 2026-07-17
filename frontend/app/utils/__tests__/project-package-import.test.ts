import { describe, expect, it } from 'vitest'
import {
  projectPackageRenameNameError,
  resolveProjectPackageRenameName
} from '@/utils/project-package-import'

describe('project package import rename helpers', () => {
  it('uses the backend suggestion when present', () => {
    expect(resolveProjectPackageRenameName('Project', ' Custom copy '))
      .toBe('Custom copy')
  })

  it('creates a safe fallback when an older preview response has no suggestion', () => {
    expect(resolveProjectPackageRenameName('Project', undefined))
      .toBe('Project (imported)')
  })

  it('validates missing runtime values without throwing', () => {
    expect(projectPackageRenameNameError(undefined))
      .toBe('Enter a name for the imported project.')
  })
})
