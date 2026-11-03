import { readFileSync } from 'node:fs'
import { ModuleKind, transpileModule } from 'typescript'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { parse } from 'vue/compiler-sfc'
import * as actionTypes from '@/types/action'
import * as definitionFilters from '@/utils/action-definition-filters'
import type { ActionDefinitionResponse } from '@/types/action'

// Exercise the page's production handlers with Nuxt services stubbed out.
const source = parse(readFileSync(new URL('../admin/actions.vue', import.meta.url), 'utf8')).descriptor.scriptSetup!.content
const compiled = transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText
interface ActionsPage {
  draftDefinition: Vue.Ref<ActionDefinitionResponse | null>
  editorContent: Vue.Ref<string>
  initialYaml: Vue.Ref<string>
  isDirty: Vue.ComputedRef<boolean>
  workflowDirty: Vue.Ref<boolean>
  workflowBusy: Vue.Ref<boolean>
  createNewDefinition: () => void
  selectDefinition: (definition: ActionDefinitionResponse) => void
  saveDefinition: () => Promise<void>
  discardDraft: () => void
}
const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor
const setupPage = new AsyncFunction('require', 'exports', `${compiled}
return { draftDefinition, editorContent, initialYaml, isDirty, workflowDirty, workflowBusy, createNewDefinition, selectDefinition, saveDefinition, discardDraft }`) as (require: (id: string) => unknown, exports: object) => Promise<ActionsPage>
const existing = { id: 'existing', processorKey: 'existing', name: 'Existing Action', yaml: 'name: Existing Action\n', tags: [] } as unknown as ActionDefinitionResponse
let scope: Vue.EffectScope
let definitions: Vue.Ref<ActionDefinitionResponse[]>
let leavePage: () => Promise<boolean>
let mounted: Array<() => void>
let unmounted: Array<() => void>
const confirmLeave = vi.fn<() => boolean>()
const openConfirmation = vi.fn((_options: Record<string, unknown>) => ({ result: Promise.resolve(confirmLeave()) }))
const addEventListener = vi.fn()
const removeEventListener = vi.fn()

async function page() {
  return setupPage((id) => {
    if (id === '@/types/action') return actionTypes
    if (id === '@/utils/action-definition-filters') return definitionFilters
    if (id === '@/utils/random-action-slug') return { generateRandomActionSlug: () => 'new-action' }
    if (id === '@codemirror/state') return { Compartment: vi.fn() }
    if (id === '@codemirror/view') return { keymap: { of: () => [] } }
    if (id === '@codemirror/search') return { searchKeymap: [] }
    return {}
  }, {})
}

beforeEach(() => {
  scope = Vue.effectScope()
  definitions = Vue.ref([{ ...existing }])
  mounted = []
  unmounted = []
  confirmLeave.mockReset().mockReturnValue(false)
  openConfirmation.mockClear()
  addEventListener.mockReset()
  removeEventListener.mockReset()
  vi.stubGlobal('window', { confirm: vi.fn(), addEventListener, removeEventListener })
  vi.stubGlobal('watch', (...args: Parameters<typeof Vue.watch>) => scope.run(() => Vue.watch(...args)))
  vi.stubGlobal('definePageMeta', vi.fn())
  vi.stubGlobal('onMounted', (callback: () => void) => mounted.push(callback))
  vi.stubGlobal('onBeforeUnmount', (callback: () => void) => unmounted.push(callback))
  vi.stubGlobal('onBeforeRouteLeave', (callback: () => Promise<boolean>) => {
    leavePage = callback
  })
  vi.stubGlobal('useRoute', () => ({ query: {} }))
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useColorMode', () => ({ value: 'light' }))
  vi.stubGlobal('useOverlay', () => ({ create: () => ({ open: openConfirmation }) }))
  vi.stubGlobal('globalKey', (...parts: string[]) => parts.join(':'))
  vi.stubGlobal('useFetch', async (url: string) => ({ data: url.endsWith('/processors') ? definitions : Vue.ref([]), pending: Vue.ref(false), refresh: vi.fn() }))
  vi.stubGlobal('$fetch', vi.fn().mockResolvedValue([]))
})
afterEach(() => {
  scope.stop()
  vi.unstubAllGlobals()
})

describe('Admin Action drafts', () => {
  it('keeps a new draft unchanged while another Action is inspected', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    const draft = state.draftDefinition.value!
    const baseline = state.initialYaml.value
    state.editorContent.value = 'name: My unfinished Action\nparameters:\n  incomplete:'
    state.selectDefinition(existing)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe(existing.yaml)
    state.selectDefinition(draft)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe('name: My unfinished Action\nparameters:\n  incomplete:')
    expect(state.initialYaml.value).toBe(baseline)
    expect(state.isDirty.value).toBe(true)
  })

  it('keeps edits to an existing Action when switching to a new draft and back', async () => {
    const state = await page()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Updated Action\n'
    state.createNewDefinition()
    await Vue.nextTick()
    state.selectDefinition(existing)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe('name: Updated Action\n')
    expect(state.isDirty.value).toBe(true)
  })

  it('resumes the unfinished draft when New Action is clicked again', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    const draft = state.draftDefinition.value
    state.editorContent.value = 'name: Still working\n'
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.createNewDefinition()
    await Vue.nextTick()
    expect(state.draftDefinition.value).toBe(draft)
    expect(state.editorContent.value).toBe('name: Still working\n')
  })

  it('keeps the unfinished draft when a different Action is saved', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    const draft = state.draftDefinition.value!
    state.editorContent.value = 'name: Unfinished\n'
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Saved changes\n'
    const saved = { ...existing, yaml: state.editorContent.value }
    vi.stubGlobal('$fetch', vi.fn(async (url: string) => {
      if (url.endsWith('/validate')) return { valid: true, diagnostics: [] }
      if (url.endsWith(`/processors/${existing.id}`)) {
        definitions.value = [saved]
        return saved
      }
      return []
    }))
    await state.saveDefinition()
    expect(state.isDirty.value).toBe(false)
    expect(state.draftDefinition.value?.id).toBe(draft.id)
    state.selectDefinition(draft)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe('name: Unfinished\n')
    state.selectDefinition(saved)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe(saved.yaml)
    expect(state.isDirty.value).toBe(false)
  })

  it('removes saved draft content so later selections use the saved YAML', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    const draft = state.draftDefinition.value!
    state.editorContent.value = 'name: Draft to save\n'
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.selectDefinition(draft)
    await Vue.nextTick()
    const saved = { ...draft, id: 'saved-draft', yaml: 'name: Saved draft\n' }
    vi.stubGlobal('$fetch', vi.fn(async (url: string) => {
      if (url.endsWith('/validate')) return { valid: true, diagnostics: [] }
      if (url.endsWith('/processors')) {
        definitions.value = [existing, saved]
        return saved
      }
      return []
    }))
    await state.saveDefinition()
    expect(state.draftDefinition.value).toBeNull()
    expect(state.isDirty.value).toBe(false)
    expect(await leavePage()).toBe(true)
    expect(confirmLeave).not.toHaveBeenCalled()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.selectDefinition(saved)
    await Vue.nextTick()
    expect(state.editorContent.value).toBe(saved.yaml)
  })

  it('clears discarded draft edits while preserving edits to another Action', async () => {
    const state = await page()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Keep these changes\n'
    state.createNewDefinition()
    await Vue.nextTick()
    const baseline = state.initialYaml.value
    state.editorContent.value = 'name: Discard these changes\n'
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.createNewDefinition()
    await Vue.nextTick()
    state.discardDraft()
    await Vue.nextTick()
    expect(state.editorContent.value).toBe('name: Keep these changes\n')
    state.createNewDefinition()
    await Vue.nextTick()
    // The deterministic test slug reuses the ID of the discarded draft.
    expect(state.editorContent.value).toBe(baseline)
    expect(state.isDirty.value).toBe(false)
  })

  it('starts fresh on a new page visit', async () => {
    const state = await page()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Changes from the previous visit\n'
    state.createNewDefinition()
    await Vue.nextTick()
    scope.stop()
    scope = Vue.effectScope()
    const nextVisit = await page()
    nextVisit.selectDefinition(existing)
    await Vue.nextTick()
    expect(nextVisit.draftDefinition.value).toBeNull()
    expect(nextVisit.editorContent.value).toBe(existing.yaml)
    expect(nextVisit.isDirty.value).toBe(false)
  })

  it('lets users cancel leaving with unsaved edits and keeps the text intact', async () => {
    const state = await page()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Keep working\n'
    expect(await leavePage()).toBe(false)
    expect(confirmLeave).toHaveBeenCalledOnce()
    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Leave Actions?',
      confirmLabel: 'Discard and leave',
      cancelLabel: 'Keep editing'
    }))
    expect(window.confirm).not.toHaveBeenCalled()
    expect(state.editorContent.value).toBe('name: Keep working\n')
    confirmLeave.mockReturnValue(true)
    expect(await leavePage()).toBe(true)
  })

  it('warns about a new draft even while another Action is selected', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    state.selectDefinition(existing)
    await Vue.nextTick()
    expect(confirmLeave).not.toHaveBeenCalled()
    expect(await leavePage()).toBe(false)
    expect(confirmLeave).toHaveBeenCalledOnce()
  })

  it('warns about retained edits to an Action that is not selected', async () => {
    const state = await page()
    const other = { ...existing, id: 'other', name: 'Other Action' }
    definitions.value.push(other)
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = 'name: Unsaved existing Action\n'
    state.selectDefinition(other)
    await Vue.nextTick()
    expect(state.isDirty.value).toBe(false)
    expect(confirmLeave).not.toHaveBeenCalled()
    expect(await leavePage()).toBe(false)
    expect(confirmLeave).toHaveBeenCalledOnce()
  })

  it('leaves without a warning when edits are reverted or a draft is discarded', async () => {
    const state = await page()
    state.selectDefinition(existing)
    await Vue.nextTick()
    expect(await leavePage()).toBe(true)
    state.editorContent.value = 'name: Temporary edit\n'
    const other = { ...existing, id: 'other' }
    definitions.value.push(other)
    state.selectDefinition(other)
    await Vue.nextTick()
    state.selectDefinition(existing)
    await Vue.nextTick()
    state.editorContent.value = existing.yaml
    expect(await leavePage()).toBe(true)
    state.createNewDefinition()
    await Vue.nextTick()
    state.discardDraft()
    await Vue.nextTick()
    expect(await leavePage()).toBe(true)
    expect(confirmLeave).not.toHaveBeenCalled()
  })

  it('keeps the workflow protection and asks only once when leaving', async () => {
    const state = await page()
    state.createNewDefinition()
    await Vue.nextTick()
    state.workflowDirty.value = true
    expect(await leavePage()).toBe(false)
    expect(confirmLeave).toHaveBeenCalledOnce()
    state.workflowBusy.value = true
    confirmLeave.mockClear()
    expect(await leavePage()).toBe(false)
    expect(confirmLeave).not.toHaveBeenCalled()
  })

  it('warns on reload or tab close and removes the listener when leaving', async () => {
    const state = await page()
    mounted.forEach(callback => callback())
    await Vue.nextTick()
    const beforeUnload = addEventListener.mock.calls.find(([name]) => name === 'beforeunload')?.[1] as ((event: BeforeUnloadEvent) => void) | undefined
    expect(beforeUnload).toBeTypeOf('function')
    const event = { preventDefault: vi.fn(), returnValue: undefined } as unknown as BeforeUnloadEvent
    beforeUnload!(event)
    expect(event.preventDefault).not.toHaveBeenCalled()
    state.createNewDefinition()
    await Vue.nextTick()
    state.selectDefinition(existing)
    await Vue.nextTick()
    beforeUnload!(event)
    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(confirmLeave).not.toHaveBeenCalled()
    unmounted.forEach(callback => callback())
    expect(removeEventListener).toHaveBeenCalledWith('beforeunload', beforeUnload)
  })
})
