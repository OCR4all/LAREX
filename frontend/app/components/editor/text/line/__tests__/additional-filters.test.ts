import { describe, expect, it } from 'vitest'
import { computed, ref, type ComputedRef } from 'vue'
import { parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import { matchesTextlineFilters, type PresenceFilter } from '@/utils/editor/textline-filtering'
import { compareConfidenceLowFirst, hasConfidenceFilter } from '../variant-filtering'
import source from '../list.vue?raw'

const ast = ts.createSourceFile('list.ts', parse(source).descriptor.scriptSetup!.content, ts.ScriptTarget.Latest, true)
const code = ast.statements.filter(node => (
  ts.isVariableStatement(node) && node.declarationList.declarations.some(declaration => (
    ['displayTextlines', 'activeLocalFilterCount'].includes(declaration.name.getText(ast))
  ))
) || (ts.isFunctionDeclaration(node) && node.name?.text === 'resetLineFilters'))
  .map(node => node.getText(ast)).join('\n')
const compiled = ts.transpileModule(code, {}).outputText

function setup() {
  const textlines = ref([
    { id: 'empty-match', hasGtVariant: false, hasAnyText: false, textContentVariants: [] },
    { id: 'empty-other', hasGtVariant: false, hasAnyText: false, textContentVariants: [] },
    { id: 'dictionary-match', hasGtVariant: true, hasAnyText: true, textContentVariants: [{ index: 0, text: 'typo' }] },
    { id: 'dictionary-other', hasGtVariant: true, hasAnyText: true, textContentVariants: [{ index: 0, text: 'typo' }] },
    { id: 'known-match', hasGtVariant: true, hasAnyText: true, textContentVariants: [{ index: 0, text: 'known' }] }
  ])
  const additionalFilters = ref<string[]>([])
  const gtFilterModel = ref<PresenceFilter>('all')
  const dependencies = {
    computed, textlines, additionalFilters, gtFilterModel,
    commentsFilter: ref<PresenceFilter>('all'),
    confidenceRangeModel: ref<[number, number]>([0, 1]),
    selectedIndicesModel: ref<number[]>([]),
    filterUnindexedModel: ref(false),
    showNonAssignedIndicesModel: ref(false),
    diffFilter: ref<PresenceFilter>('all'),
    gtIndexModel: ref(0), searchQuery: ref(''), sortOrder: ref('asc'),
    matchingTextLineIds: ref(new Set(['empty-match', 'dictionary-match', 'known-match'])),
    isLoadingMatchingTextLines: ref(false), matchesTextlineFilters, compareConfidenceLowFirst, hasConfidenceFilter,
    textlineHasDictionaryMismatch: (line: typeof textlines.value[number]) => line.id.startsWith('dictionary')
  }
  const filters: {
    displayTextlines: ComputedRef<typeof textlines.value>
    activeLocalFilterCount: ComputedRef<number>
    resetLineFilters: () => void
  } = new Function(...Object.keys(dependencies), `${compiled}; return { displayTextlines, activeLocalFilterCount, resetLineFilters }`)(...Object.values(dependencies))
  return { ...dependencies, ...filters, ids: () => filters.displayTextlines.value.map(line => line.id) }
}

describe('Additional textline filters', () => {
  it('combines empty transcriptions with matching textlines and allows each to be unchecked', () => {
    const filters = setup()
    filters.additionalFilters.value = ['empty', 'matchingFilter']
    expect(filters.ids()).toEqual(['empty-match'])
    expect(filters.activeLocalFilterCount.value).toBe(2)

    filters.additionalFilters.value = ['empty']
    expect(filters.ids()).toEqual(['empty-match', 'empty-other'])
    filters.additionalFilters.value = ['matchingFilter']
    expect(filters.ids()).toEqual(['empty-match', 'dictionary-match', 'known-match'])
  })

  it('combines dictionary mismatches with matching textlines and the main filters', () => {
    const filters = setup()
    filters.additionalFilters.value = ['dictionaryMismatch', 'matchingFilter']
    expect(filters.ids()).toEqual(['dictionary-match'])

    filters.gtFilterModel.value = 'without'
    expect(filters.ids()).toEqual([])
    expect(filters.activeLocalFilterCount.value).toBe(3)
  })

  it('resets all selected filters and the active count', () => {
    const filters = setup()
    filters.additionalFilters.value = ['empty', 'dictionaryMismatch', 'matchingFilter']
    filters.gtFilterModel.value = 'with'
    filters.confidenceRangeModel.value = [0.2, 0.8]
    filters.selectedIndicesModel.value = [1, 2]
    filters.filterUnindexedModel.value = true
    filters.showNonAssignedIndicesModel.value = true
    expect(filters.activeLocalFilterCount.value).toBe(7)
    filters.resetLineFilters()

    expect(filters.additionalFilters.value).toEqual([])
    expect(filters.gtFilterModel.value).toBe('all')
    expect(filters.confidenceRangeModel.value).toEqual([0, 1])
    expect(filters.selectedIndicesModel.value).toEqual([])
    expect(filters.filterUnindexedModel.value).toBe(false)
    expect(filters.showNonAssignedIndicesModel.value).toBe(false)
    expect(filters.activeLocalFilterCount.value).toBe(0)
    expect(filters.ids()).toHaveLength(5)
  })
})
