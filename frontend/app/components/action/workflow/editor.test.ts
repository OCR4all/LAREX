import { readFileSync } from 'node:fs'
import { ModuleKind, transpileModule } from 'typescript'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import * as errors from '~/utils/api-error'
import * as workflows from '~/utils/action-workflow'
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowStep } from '~/types/action-workflow'

// Run the production components' state and drag handlers without a Nuxt server.
function setupComponent<T>(file: string, dependencies: Record<string, unknown> = {}) {
  const descriptor = parse(readFileSync(new URL(file, import.meta.url), 'utf8')).descriptor
  const compiled = transpileModule(compileScript(descriptor, { id: file }).content, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText
  const component: { default?: { setup: (props: unknown, context: unknown) => T } } = {}
  const modules: Record<string, unknown> = { 'vue': Vue, '~/utils/api-error': errors, '~/utils/action-workflow': workflows, ...dependencies }
  new Function('require', 'exports', compiled)((id: string) => modules[id] ?? {}, component)
  return component.default!.setup
}
interface Editor {
  name: Vue.Ref<string>
  entries: Vue.Ref<{ id: string, step: ActionWorkflowStep }[]>
  filtered: Vue.ComputedRef<ActionDefinition[]>
  query: Vue.Ref<string>
  dirty: Vue.ComputedRef<boolean>
  insert: (id: string, index?: number) => void
  reorder: (from: number, to: number) => void
  update: (index: number, step: ActionWorkflowStep) => void
  remove: (index: number) => void
  undo: () => void
  redo: () => void
  save: () => Promise<void>
}
const setupEditor = setupComponent<Editor>('./editor.vue')
const source = (id: string, name: string, parameters = {}): ActionDefinition => ({ id, name, description: 'Process page', tags: ['image'], enabled: true, targets: ['PAGE'], kind: 'PROCESSING', parameters }) as ActionDefinition
const binarize = source('binary', 'Binarization', { threshold: { type: 'integer', default: 128 } })
const region = source('region', 'Whole-page Region')
let scope: Vue.EffectScope
beforeEach(() => {
  for (const name of ['ref', 'computed', 'watch', 'toRaw', 'nextTick'] as const) vi.stubGlobal(name, Vue[name])
  vi.stubGlobal('useId', () => 'flow-test')
  vi.stubGlobal('onMounted', () => {})
  vi.stubGlobal('onBeforeUnmount', () => {})
  scope = Vue.effectScope()
})
afterEach(() => {
  scope.stop()
  vi.unstubAllGlobals()
})
function editor(workflow?: ActionWorkflow) {
  const emit = vi.fn()
  const props = Vue.reactive({ processors: [binarize, region, { ...region, id: 'disabled', enabled: false }, { ...region, id: 'workflow', workflowId: 'nested' }], workflow })
  const state = scope.run(() => setupEditor(props, { expose: () => {}, emit }))!
  return { state, props, emit }
}

describe('Workflow editor', () => {
  it('filters processors and inserts independent defaults at the requested position', () => {
    const { state } = editor()
    state.query.value = 'binar image'
    expect(state.filtered.value.map(item => item.id)).toEqual(['binary'])
    state.insert('binary')
    state.insert('region', 0)
    state.insert('binary')
    state.insert('workflow')
    expect(state.entries.value.map(entry => entry.step.processorDefinitionId)).toEqual(['region', 'binary', 'binary'])
    state.update(1, { ...state.entries.value[1]!.step, parameters: { threshold: 150 } })
    expect(state.entries.value[2]!.step.parameters.threshold).toBe(128)
    expect(binarize.parameters.threshold!.default).toBe(128)
  })

  it('preserves identity and parameters through reordering, removal, undo and redo', () => {
    const { state } = editor()
    state.insert('binary')
    state.insert('region')
    const id = state.entries.value[0]!.id
    state.update(0, { ...state.entries.value[0]!.step, parameters: { threshold: 150 }, imageTypes: ['BINARY'] })
    state.reorder(0, 1)
    expect(state.entries.value[1]!.id).toBe(id)
    state.remove(1)
    state.undo()
    expect(state.entries.value[1]!.step.parameters.threshold).toBe(150)
    expect(state.entries.value[1]!.step.imageTypes).toEqual(['BINARY'])
    state.undo()
    expect(state.entries.value[0]!.id).toBe(id)
    state.redo()
    expect(state.entries.value[1]!.id).toBe(id)
    state.undo()
    state.insert('region')
    state.redo()
    expect(state.entries.value.map(entry => entry.step.processorDefinitionId)).toEqual(['binary', 'region', 'region'])
  })

  it('snapshots parameter edits emitted from reactive node props before adding or undoing', () => {
    const { state } = editor()
    state.insert('binary')
    const step = state.entries.value[0]!.step
    // Node settings spread the reactive step, retaining its nested image-selection proxy.
    state.update(0, { ...step, imageVariantSelection: { mode: 'GLOBAL', variants: ['original'], fallbackImage: false } })
    state.update(0, { ...state.entries.value[0]!.step, parameters: { threshold: 145 } })
    expect(() => state.insert('region')).not.toThrow()
    state.undo()
    expect(state.entries.value).toHaveLength(1)
    expect(state.entries.value[0]!.step.parameters.threshold).toBe(145)
    expect(state.entries.value[0]!.step.imageVariantSelection?.variants).toEqual(['original'])
  })

  it('saves ordered defaults and version, retaining the draft on a conflict', async () => {
    const workflow = { id: 'saved', name: 'Original', description: '', version: 4, enabled: true, steps: [workflows.createWorkflowStep(binarize)] }
    const { state, props, emit } = editor(workflow)
    const fetch = vi.fn().mockRejectedValueOnce(new Error('Conflict'))
    vi.stubGlobal('$fetch', fetch)
    expect(state.dirty.value).toBe(false)
    state.insert('region')
    state.name.value = 'Updated'
    await state.save()
    expect(state.dirty.value).toBe(true)
    expect(state.entries.value).toHaveLength(2)
    expect(emit).not.toHaveBeenCalledWith('saved', expect.anything())
    const saved = { ...workflow, name: 'Updated', version: 5, steps: state.entries.value.map(entry => Vue.toRaw(entry.step)) }
    const ids = state.entries.value.map(entry => entry.id)
    fetch.mockResolvedValueOnce(saved)
    await state.save()
    expect(fetch.mock.calls[1]![0]).toBe('/api/admin/actions/workflows/saved')
    expect(fetch.mock.calls[1]![1].body).toMatchObject({ name: 'Updated', version: 4, steps: [{ processorDefinitionId: 'binary', parameters: { threshold: 128 } }, { processorDefinitionId: 'region' }] })
    expect(state.dirty.value).toBe(false)
    props.workflow = saved
    await Vue.nextTick()
    expect(state.entries.value.map(entry => entry.id)).toEqual(ids)
    expect(workflow.steps).toHaveLength(1)
  })

  it('limits to thirty steps and prevents duplicate submission or edits while saving', async () => {
    const { state } = editor()
    for (let i = 0; i < 31; i++) state.insert('binary')
    expect(state.entries.value).toHaveLength(30)
    state.name.value = 'Pipeline'
    let finish: (value: unknown) => void = () => {}
    const fetch = vi.fn(() => new Promise((resolve) => {
      finish = resolve
    }))
    vi.stubGlobal('$fetch', fetch)
    const saved = state.save()
    state.remove(0)
    state.reorder(0, 1)
    await state.save()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(state.entries.value).toHaveLength(30)
    finish({ id: 'saved', version: 0, name: 'Pipeline', description: '' })
    await saved
  })
})

interface Graph {
  edges: Vue.ComputedRef<{ source: string, target: string, sourceHandle: string, targetHandle: string }[]>
  dropIndex: Vue.Ref<number | null>
  dragOver: (event: DragEvent) => void
  drop: (event: DragEvent) => void
  nodeDragStop: (event: unknown) => Promise<void>
}
const fitWorkflow = vi.fn()
let initializeNodes: (() => void) | undefined
const setupGraph = setupComponent<Graph>('./graph.client.vue', {
  '@vue-flow/core': {
    Position: {}, MarkerType: {},
    useVueFlow: () => ({ getNodes: Vue.ref([]), onNodesInitialized: (callback: () => void) => { initializeNodes = callback }, screenToFlowCoordinate: ({ x, y }: { x: number, y: number }) => ({ x: (x - 100) / 2, y }), updateNode: vi.fn(), fitView: fitWorkflow })
  }
})
describe('Workflow canvas interactions', () => {
  beforeEach(() => {
    fitWorkflow.mockClear()
    initializeNodes = undefined
  })

  it('preserves the viewport after selection, parameter edits, additions and repeated node initialization', async () => {
    const props = Vue.reactive({ steps: [workflows.createWorkflowStep(binarize)], processors: [binarize, region], selected: 0, inlineSettings: true, nodeKeys: ['first'] })
    scope.run(() => setupGraph(props, { expose: () => {}, emit: vi.fn() }))
    initializeNodes!()
    expect(fitWorkflow).toHaveBeenCalledTimes(1)
    fitWorkflow.mockClear()
    props.steps.push(workflows.createWorkflowStep(region))
    props.nodeKeys.push('second')
    props.selected = 1
    await Vue.nextTick()
    await Vue.nextTick()
    initializeNodes!()
    props.steps[0]!.parameters.threshold = 150
    props.selected = 0
    await Vue.nextTick()
    await Vue.nextTick()
    initializeNodes!()
    expect(fitWorkflow).not.toHaveBeenCalled()
  })

  it('binds each edge to output and input ports as nodes are appended and reordered', async () => {
    const props = Vue.reactive({ steps: [workflows.createWorkflowStep(binarize)], processors: [binarize, region], selected: 0, nodeKeys: ['first'] })
    const graph = scope.run(() => setupGraph(props, { expose: () => {}, emit: vi.fn() }))!
    props.steps.push(workflows.createWorkflowStep(region))
    props.nodeKeys.push('second')
    await Vue.nextTick()
    expect(graph.edges.value).toMatchObject([{ source: 'first', sourceHandle: 'output', target: 'second', targetHandle: 'input' }])
    props.steps.push(workflows.createWorkflowStep(binarize))
    props.nodeKeys.push('third')
    await Vue.nextTick()
    expect(graph.edges.value).toMatchObject([
      { source: 'first', sourceHandle: 'output', target: 'second', targetHandle: 'input' },
      { source: 'second', sourceHandle: 'output', target: 'third', targetHandle: 'input' }
    ])
    props.steps.reverse()
    props.nodeKeys.reverse()
    await Vue.nextTick()
    expect(graph.edges.value).toMatchObject([
      { source: 'third', sourceHandle: 'output', target: 'second', targetHandle: 'input' },
      { source: 'second', sourceHandle: 'output', target: 'first', targetHandle: 'input' }
    ])
  })

  it('inserts using canvas coordinates and reorders within a single edge path', async () => {
    const emit = vi.fn()
    const props = Vue.reactive({ steps: [workflows.createWorkflowStep(binarize), workflows.createWorkflowStep(region)], processors: [binarize, region], selected: 0, editable: true, inlineSettings: true, nodeKeys: ['first', 'second'], disabled: false })
    const graph = scope.run(() => setupGraph(props, { expose: () => {}, emit }))!
    const event = { clientX: 1000, clientY: 20, preventDefault: vi.fn(), dataTransfer: { types: ['application/larex-action'], getData: () => 'binary', dropEffect: '' } } as unknown as DragEvent
    graph.dragOver(event)
    expect(graph.dropIndex.value).toBe(1)
    graph.drop(event)
    expect(emit).toHaveBeenCalledWith('insert', 'binary', 1)
    expect(graph.edges.value).toMatchObject([{ source: 'first', target: 'second' }])
    await graph.nodeDragStop({ node: { id: 'first', data: { index: 0 }, position: { x: 800, y: 60 }, dimensions: { width: 352 } } })
    expect(emit).toHaveBeenCalledWith('reorder', 0, 1)
    props.disabled = true
    graph.dragOver(event)
    expect(graph.dropIndex.value).toBeNull()
  })
})
