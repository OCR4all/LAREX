import { describe, expect, it } from 'vitest'
import {
  buildDatasetPackageFileName,
  buildProjectBasicExportFileName,
  buildProjectPackageFileName,
  buildToolkitPackageFileName,
  sanitizeDownloadFileName
} from '../download-file-names'

describe('download file names', () => {
  it('preserves readable names while removing unsafe path characters', () => {
    expect(sanitizeDownloadFileName('  My / Project: Edition  ', 'fallback'))
      .toBe('My Project Edition')
  })

  it('removes control characters without relying on a control-character regex', () => {
    expect(sanitizeDownloadFileName('My\u0000 Project\u001F Name\u007F', 'fallback'))
      .toBe('My Project Name')
  })

  it('builds semantic package names', () => {
    expect(buildProjectBasicExportFileName('My Project')).toBe('My Project - flat export.zip')
    expect(buildProjectPackageFileName('My Project')).toBe('My Project - LAREX package.larex-project.zip')
    expect(buildDatasetPackageFileName('Training Set')).toBe('Training Set - LAREX dataset.larex-dataset.zip')
    expect(buildToolkitPackageFileName('My Labels', 'label-set')).toBe('My Labels.larex-toolkit.json')
  })
})
