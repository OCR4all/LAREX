// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileTemplate, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import dashboardSource from '@/pages/index.vue?raw'
import projectSource from '@/pages/project/[id].vue?raw'
import datasetSource from '@/pages/datasets/[id].vue?raw'
import evaluationSource from '@/pages/evaluation/[runId].vue?raw'
import pagePickerSource from '@/components/task/slideover/link-items.vue?raw'
import notificationsSource from '@/components/user/settings/notifications.vue?raw'
import uploadSource from '@/composables/use-project-upload-orchestration.ts?raw'

// Isolate the production loading gates: mounting entire pages would require
// unrelated editor, authentication, upload, and networking services.
function loadingCondition(source: string, name: string) {
  const template = parse(source).descriptor.template!.content
  const conditions = [...template.matchAll(/v-(?:else-)?if="([^"]+)"/g)]
  const condition = conditions.find(match => match[1]!.includes(name))?.[1]
  if (!condition) throw new Error(`Loading condition not found: ${name}`)
  return condition
}

function pageLoadingState(options: { pages: Vue.Ref<unknown>, pagesPending: Vue.Ref<boolean> }, manual: Vue.Ref<boolean>) {
  const ast = ts.createSourceFile('upload.ts', uploadSource, ts.ScriptTarget.Latest, true)
  let initializer: ts.Expression | undefined
  function visit(node: ts.Node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'showPagesLoadingSpinner') {
      initializer = node.initializer
    }
    ts.forEachChild(node, visit)
  }
  visit(ast)
  if (!initializer) throw new Error('Page loading state not found')
  const compiled = ts.transpileModule(`const loading = ${initializer.getText(ast)}`, {}).outputText
  return new Function('computed', 'options', 'isManualPagesRefresh', `${compiled}; return loading`)(
    Vue.computed, options, manual
  ) as Vue.ComputedRef<boolean>
}

const cases = [
  { name: 'project overview', source: dashboardSource, gate: 'status === \'pending\'', pending: 'status', active: 'pending', idle: 'success', data: 'data', extra: { emptyStateRefresh: { loading: false } } },
  { name: 'project details', source: projectSource, gate: 'projectPending', pending: 'projectPending', active: true, idle: false, data: 'project' },
  { name: 'project pages', source: projectSource, gate: 'showPagesLoadingSpinner', pending: 'pagesPending', active: true, idle: false, data: 'pages' },
  { name: 'dataset details', source: datasetSource, gate: 'pending', pending: 'pending', active: true, idle: false, data: 'dataset' },
  { name: 'evaluation results', source: evaluationSource, gate: 'loading', pending: 'loading', active: true, idle: false, data: 'detail' },
  { name: 'task page picker', source: pagePickerSource, gate: 'pagesStatus === \'pending\'', pending: 'pagesStatus', active: 'pending', idle: 'success', data: 'pages' },
  { name: 'notification preferences', source: notificationsSource, gate: 'isLoading', pending: 'isLoading', active: true, idle: false, data: 'types', missing: [] }
]

describe('table refresh loading gates', () => {
  it.each(cases)('keeps $name mounted and updates its rows during refresh', async (testCase) => {
    const rows = Vue.ref([{ id: 'row-1', name: 'Before update' }])
    const pending = Vue.ref(testCase.idle)
    const manual = Vue.ref(false)
    const showPagesLoadingSpinner = pageLoadingState({ pages: rows, pagesPending: pending as Vue.Ref<boolean> }, manual)
    const state = {
      ...testCase.extra,
      [testCase.pending]: pending,
      [testCase.data]: rows,
      rows,
      showPagesLoadingSpinner
    }
    const { code } = compileTemplate({
      id: 'table-refresh-test',
      filename: 'table-refresh-test.vue',
      source: `<div v-if="${loadingCondition(testCase.source, testCase.gate)}" data-loading>Loading</div><TableProbe v-else :rows="rows" />`
    })
    const compiled = ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
    const exports: { render?: Vue.RenderFunction } = {}
    new Function('require', 'exports', compiled)(() => Vue, exports)
    const mounts = vi.fn()
    const app = Vue.createApp({ setup: () => state, render: exports.render })
    app.component('TableProbe', Vue.defineComponent({
      props: ['rows'],
      setup(props) {
        mounts()
        return () => Vue.h('table', [Vue.h('tr', [Vue.h('td', props.rows[0]?.name)])])
      }
    }))
    const host = document.createElement('div')
    app.mount(host)
    try {
      const table = host.querySelector('table')
      expect(table).not.toBeNull()
      pending.value = testCase.active
      manual.value = true
      await Vue.nextTick()
      expect(host.querySelector('[data-loading]')).toBeNull()
      expect(host.querySelector('table')).toBe(table)
      expect(host.textContent).toContain('Before update')

      rows.value = [{ id: 'row-1', name: 'After update' }]
      pending.value = testCase.idle
      manual.value = false
      await Vue.nextTick()
      expect(host.textContent).toContain('After update')
      expect(host.querySelector('table')).toBe(table)
      expect(mounts).toHaveBeenCalledOnce()

      // Refreshing a loaded empty list must not replace its empty state with a loader.
      rows.value = []
      pending.value = testCase.active
      await Vue.nextTick()
      if (testCase.data !== 'types') expect(host.querySelector('[data-loading]')).toBeNull()
    } finally {
      app.unmount()
    }
  })

  it.each(cases)('shows the initial loader for $name without data', (testCase) => {
    const showPagesLoadingSpinner = pageLoadingState({ pages: Vue.ref(undefined), pagesPending: Vue.ref(true) }, Vue.ref(false))
    const state = {
      ...testCase.extra,
      [testCase.pending]: testCase.active,
      [testCase.data]: testCase.missing,
      showPagesLoadingSpinner: showPagesLoadingSpinner.value
    }
    const isLoading = new Function(...Object.keys(state), `return ${loadingCondition(testCase.source, testCase.gate)}`)(...Object.values(state))
    expect(isLoading).toBe(true)
  })
})
