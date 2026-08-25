import { describe, expect, it } from 'vitest'
import {
  changedProjectDefaultKeys,
  formatWorkspaceResourceDefault,
  formatWorkspaceTextIndexDefault,
  normalizeWorkspaceProjectDefaults,
  resetWorkspaceResourceDefault,
  resetTextIndexDefaults,
  resourceMatchesWorkspaceDefault,
  textIndicesMatchWorkspaceDefault,
  type WorkspaceProjectDefaultsSnapshot
} from '~/utils/workspace-project-defaults'

const base: WorkspaceProjectDefaultsSnapshot = {
  codecId: 'codec-1',
  labelSetId: 'labels-1',
  dictionaryId: null,
  tagSetId: null,
  normalizationProfileId: null,
  validationRulesetId: null,
  defaultGtIndex: 0,
  defaultRecognitionIndices: [1, 2]
}

describe('workspace project defaults', () => {
  it('detects only changed resources and text indices', () => {
    expect(changedProjectDefaultKeys(base, {
      ...base,
      labelSetId: 'labels-2',
      defaultGtIndex: 2
    })).toEqual(['LABEL_SET', 'TEXT_INDICES'])
  })

  it('treats blank resource values as cleared', () => {
    expect(changedProjectDefaultKeys(base, {
      ...base,
      labelSetId: ''
    })).toEqual(['LABEL_SET'])
  })

  it('ignores unchanged values and recognition ordering changes', () => {
    expect(changedProjectDefaultKeys(base, {
      ...base,
      defaultRecognitionIndices: [2, 1]
    })).toEqual([])
  })

  it('normalizes cleared workspace values and formats labels', () => {
    const defaults = normalizeWorkspaceProjectDefaults({
      codecId: '',
      defaultGtIndex: null,
      defaultRecognitionIndices: null
    })

    expect(defaults.codecId).toBeNull()
    expect(defaults.defaultGtIndex).toBe(0)
    expect(defaults.defaultRecognitionIndices).toEqual([1])
    expect(formatWorkspaceResourceDefault(null, [])).toBe('No workspace default')
    expect(formatWorkspaceResourceDefault('missing', [{ label: 'Codec', value: 'codec-1' }])).toBe('Unavailable resource')
    expect(resetWorkspaceResourceDefault(defaults, 'CODEC')).toBeNull()
    expect(formatWorkspaceTextIndexDefault(defaults)).toBe('GT 0; Recognition 1')
  })

  it('compares resource and text-index drafts with workspace defaults', () => {
    expect(resourceMatchesWorkspaceDefault('codec-1', 'codec-1')).toBe(true)
    expect(resourceMatchesWorkspaceDefault('', null)).toBe(true)
    expect(textIndicesMatchWorkspaceDefault({
      gtIndexInput: '0',
      recognitionIndicesInput: ['2', '1']
    }, base)).toBe(true)
    expect(textIndicesMatchWorkspaceDefault({
      gtIndexInput: '3',
      recognitionIndicesInput: ['2', '1']
    }, base)).toBe(false)
  })

  it('builds reset values for project text-index drafts', () => {
    const reset = resetTextIndexDefaults({
      ...base,
      defaultGtIndex: 3,
      defaultRecognitionIndices: [-1, 2]
    })

    expect(reset).toEqual({
      gtIndexInput: '3',
      gtIndexUndefined: false,
      recognitionIndicesInput: ['2'],
      recognitionIndicesUndefined: true
    })
  })
})
