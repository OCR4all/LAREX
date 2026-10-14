// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as registry from '@/composables/editor/shortcut-registry'
import source from '../keyboard-shortcuts-help.vue?raw'
import { shouldIgnoreGlobalShortcutForTypingTarget } from '@/utils/editor/keyboard-shortcut-target'
import { DropdownMenuRoot, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from 'reka-ui'

const descriptor = parse(source).descriptor
const script = compileScript(descriptor, { id: 'keyboard-shortcuts-help-test' })
const compiled = transpileModule(script.content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText

type CommandId = registry.ShortcutCommandId
interface Setup {
  searchQuery: Vue.Ref<string>
  editingCommandId: Vue.Ref<CommandId | null>
  draftBindings: Vue.Ref<string[]>
  captureInput: Vue.Ref<HTMLInputElement | null>
  captureIndex: Vue.Ref<number | null>
  captureError: Vue.Ref<string>
  draftConflict: Vue.ComputedRef<string>
  resetError: Vue.Ref<{ commandId: CommandId, message: string } | null>
  hasOverrides: Vue.ComputedRef<boolean>
  openingCombos: Vue.ComputedRef<string[][]>
  visibleShortcutGroups: Vue.ComputedRef<Array<{ shortcuts: Array<{ id: string, commandId?: CommandId, combos: unknown[] }> }>>
  customize: (id: CommandId) => void
  finishEditing: (restoreFocus?: boolean) => Promise<void>
  beginCapture: (index: number) => Promise<void>
  captureKeydown: (event: KeyboardEvent) => void
  removeBinding: (index: number) => void
  saveCommand: () => void
  resetCommand: (id: CommandId) => void
  resetAll: () => void
  shortcutMenu: (id: CommandId) => Array<{ label: string, disabled?: boolean, onSelect: () => void }>
}
const scopes: Vue.EffectScope[] = []
const mountedApps: Vue.App[] = []
afterEach(() => {
  mountedApps.splice(0).forEach(app => app.unmount())
  scopes.splice(0).forEach(scope => scope.stop())
  document.body.innerHTML = ''
})

function createSetup(bindings: registry.ShortcutBindingsMap = {}) {
  const preferences = Vue.ref(registry.createShortcutPreferences(bindings))
  const save = vi.fn((value: registry.ShortcutPreferences) => {
    preferences.value = value
  })
  const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
  new Function('require', 'exports', compiled)((id: string) => {
    if (id === 'vue') return Vue
    if (id === '@/composables/editor/shortcut-registry') return registry
    if (id === '@/composables/editor/use-shortcut-bindings') return {
      useShortcutBindings: () => ({
        shortcutPreferences: preferences,
        resolvedShortcutDefinitions: Vue.computed(() => registry.getResolvedShortcutDefinitions(preferences.value)),
        saveShortcutPreferences: save
      })
    }
    if (id === '@/composables/editor/use-canvas-interaction-blocker') return { useBlockEditorCanvasInteractions: () => {} }
    throw new Error(`Unexpected dependency ${id}`)
  }, exportsObject)
  const props = Vue.reactive({ open: true })
  const scope = Vue.effectScope()
  scopes.push(scope)
  const setup = scope.run(() => exportsObject.default!.setup(props, { expose: () => {}, emit: () => {} }))!
  return { setup, props, preferences, save, component: exportsObject.default! }
}

async function record(setup: Setup, index: number, key: string, options: KeyboardEventInit = {}) {
  await setup.beginCapture(index)
  const event = new KeyboardEvent('keydown', { key, cancelable: true, ...options })
  setup.captureKeydown(event)
  return event
}

describe('Merged keyboard shortcuts dialog', () => {
  it('keeps the reference and search open, discards canceled drafts, and restores menu focus', async () => {
    const { setup, props, save } = createSetup()
    const button = document.createElement('button')
    button.id = 'shortcut-menu-zoomIn'
    document.body.append(button)
    setup.searchQuery.value = 'zoom'
    setup.shortcutMenu('zoomIn')[0]!.onSelect()
    await record(setup, 0, 'q')
    await setup.finishEditing()
    expect(document.activeElement).toBe(button)
    expect(props.open).toBe(true)
    expect(setup.searchQuery.value).toBe('zoom')
    expect(save).not.toHaveBeenCalled()
    setup.customize('zoomIn')
    expect(setup.draftBindings.value).toEqual(['meta_='])
  })

  it('saves only the selected command and preserves other overrides', async () => {
    const { setup, preferences } = createSetup({ save: ['alt_s'] })
    setup.customize('zoomIn')
    await record(setup, 0, 'q')
    setup.saveCommand()
    expect(preferences.value.bindings).toEqual({ save: ['alt_s'], zoomIn: ['q'] })
    expect(setup.editingCommandId.value).toBeNull()
  })

  it('supports alternatives, deduplicates them, and retains the final binding', async () => {
    const { setup } = createSetup()
    setup.customize('redo')
    await record(setup, 2, 'q')
    expect(setup.draftBindings.value).toEqual(['meta_shift_z', 'meta_y', 'q'])
    setup.removeBinding(0)
    setup.removeBinding(0)
    setup.removeBinding(0)
    expect(setup.draftBindings.value).toEqual(['q'])
    await record(setup, 1, 'q')
    expect(setup.draftBindings.value).toEqual(['q'])
  })

  it('keeps edited rows visible and includes unassigned commands and read-only gestures', async () => {
    const { setup } = createSetup()
    const items = () => setup.visibleShortcutGroups.value.flatMap(group => group.shortcuts)
    expect(items().find(item => item.id === 'clearSelection')?.combos).toEqual([])
    expect(items().find(item => item.id === 'showElementLabels')?.commandId).toBeUndefined()
    setup.customize('clearSelection')
    setup.searchQuery.value = 'no matching search'
    await record(setup, 0, 'q')
    expect(items().map(item => item.id)).toEqual(['clearSelection'])
    setup.saveCommand()
    expect(items()).toEqual([])
  })

  it('names clashes with filtered-out commands and blocks saving', async () => {
    const { setup, save } = createSetup()
    setup.searchQuery.value = 'zoom'
    setup.customize('zoomIn')
    await record(setup, 0, 'v')
    expect(setup.draftConflict.value).toContain('Select mode')
    setup.saveCommand()
    expect(save).not.toHaveBeenCalled()
  })

  it('allows reuse across scopes', async () => {
    const { setup, preferences } = createSetup()
    setup.customize('nextTextField')
    await record(setup, 0, 'v')
    expect(setup.draftConflict.value).toBe('')
    setup.saveCommand()
    expect(preferences.value.bindings.nextTextField).toEqual(['v'])
  })

  it('focuses capture, rejects modifiers and reserved keys, and consumes captured events', async () => {
    const { setup } = createSetup()
    const input = document.createElement('input')
    document.body.append(input)
    setup.captureInput.value = input
    setup.customize('zoomIn')
    await record(setup, 0, 'Control')
    expect(document.activeElement).toBe(input)
    expect(setup.captureError.value).toContain('complete shortcut')
    await record(setup, 0, 'r', { metaKey: true })
    expect(setup.captureError.value).toContain('reserved')
    expect(setup.draftBindings.value).toEqual(['meta_='])
    const event = await record(setup, 0, 'q')
    expect(event.defaultPrevented).toBe(true)
    expect(setup.captureIndex.value).toBeNull()
    expect(setup.captureError.value).toBe('')
  })

  it('blocks resets that would clash, then allows reset after the clash is removed', () => {
    const { setup, preferences, save } = createSetup({ selectMode: ['q'], moveMode: ['v'] })
    setup.resetCommand('selectMode')
    expect(setup.resetError.value?.message).toContain('Move mode')
    expect(save).not.toHaveBeenCalled()
    expect(preferences.value.bindings.selectMode).toEqual(['q'])
    setup.resetCommand('moveMode')
    setup.resetCommand('selectMode')
    expect(preferences.value.bindings).toEqual({})
  })

  it('resets saved overrides and discards drafts while updating the opening hint', async () => {
    const { setup, preferences } = createSetup({ showHelp: ['q'], save: ['alt_s'] })
    expect(setup.hasOverrides.value).toBe(true)
    expect(setup.openingCombos.value).toEqual([['Q']])
    setup.customize('zoomIn')
    await record(setup, 0, 'j')
    setup.resetAll()
    expect(preferences.value.bindings).toEqual({})
    expect(setup.hasOverrides.value).toBe(false)
    expect(setup.editingCommandId.value).toBeNull()
    expect(setup.openingCombos.value).toEqual([['shift', '/']])
    expect(setup.shortcutMenu('save')[1]?.disabled).toBe(true)
    expect(registry.getResolvedShortcutDefinitions(preferences.value).save.bindings).toEqual(['meta_s'])
  })

  it('discards drafts when switching commands or closing the dialog', async () => {
    const { setup, props, save } = createSetup()
    setup.customize('zoomIn')
    await record(setup, 0, 'q')
    setup.customize('zoomOut')
    expect(setup.draftBindings.value).toEqual(['meta_-'])
    props.open = false
    await Vue.nextTick()
    expect(setup.editingCommandId.value).toBeNull()
    expect(save).not.toHaveBeenCalled()
  })
})

// Mount the production template with lightweight controls and Reka's actual
// dropdown primitives to exercise keyboard navigation and native input capture.
describe('Shortcut controls', () => {
  it('opens Customize from the keyboard and captures keys without invoking editor actions', async () => {
    const { component, preferences } = createSetup()
    const renderExports: { render?: Vue.ComponentOptions['render'] } = {}
    const template = compileTemplate({
      source: descriptor.template!.content,
      filename: 'keyboard-shortcuts-help.vue',
      id: 'keyboard-shortcuts-help-test',
      compilerOptions: { bindingMetadata: script.bindings }
    })
    expect(template.errors).toEqual([])
    new Function('require', 'exports', transpileModule(template.code, {
      compilerOptions: { module: ModuleKind.CommonJS }
    }).outputText)(() => Vue, renderExports)
    const app = Vue.createApp({ ...component, render: renderExports.render } as Vue.Component, { open: true })
    const allSlots = Vue.defineComponent({
      setup: (_, { slots }) => () => Vue.h('div', Object.values(slots).flatMap(slot => slot?.({})))
    })
    app.component('UModal', allSlots)
    app.component('UCard', allSlots)
    app.component('UBadge', allSlots)
    app.component('UIcon', Vue.defineComponent({ setup: () => () => Vue.h('span') }))
    app.component('UKbd', Vue.defineComponent({
      props: ['value'], setup: props => () => Vue.h('kbd', props.value)
    }))
    app.component('UInput', Vue.defineComponent({
      setup: () => () => Vue.h('input')
    }))
    app.component('UButton', Vue.defineComponent({
      props: ['label'], setup: (props, { slots }) => () => Vue.h('button', {}, props.label ?? slots.default?.({}))
    }))
    app.component('UDropdownMenu', Vue.defineComponent({
      props: ['items'],
      setup: (props, { slots }) => () => Vue.h(DropdownMenuRoot, {}, () => [
        Vue.h(DropdownMenuTrigger, { asChild: true }, slots.default),
        Vue.h(DropdownMenuContent, {}, () => props.items.map((item: { label: string, disabled: boolean, onSelect: () => void }) =>
          Vue.h(DropdownMenuItem, { disabled: item.disabled, onSelect: item.onSelect }, () => item.label)))
      ])
    }))
    const host = document.createElement('div')
    document.body.append(host)
    mountedApps.push(app)
    app.mount(host)
    const trigger = host.querySelector<HTMLButtonElement>('#shortcut-menu-selectMode')!
    trigger.focus()
    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await vi.waitFor(() => expect(document.activeElement?.textContent).toBe('Customize'))
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await Vue.nextTick()
    const replace = host.querySelector<HTMLButtonElement>('[aria-label="Replace V"]')!
    expect(replace).not.toBeNull()
    replace.click()
    await Vue.nextTick()
    const input = host.querySelector<HTMLInputElement>('#shortcut-capture-selectMode')!
    expect(document.activeElement).toBe(input)
    const editorAction = vi.fn()
    const onGlobalKeydown = (event: KeyboardEvent) => {
      if (!shouldIgnoreGlobalShortcutForTypingTarget('selectMode', false, event)) editorAction()
    }
    window.addEventListener('keydown', onGlobalKeydown, true)
    try {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'q', bubbles: true, cancelable: true }))
      expect(editorAction).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('keydown', onGlobalKeydown, true)
    }
    await Vue.nextTick()
    expect(host.querySelector('[aria-label="Replace Q"]')).not.toBeNull()
    expect(document.activeElement?.id).toBe('shortcut-add-selectMode')
    const saveButton = Array.from(host.querySelectorAll('button')).find(button => button.textContent === 'Save')!
    saveButton.click()
    await Vue.nextTick()
    expect(preferences.value.bindings.selectMode).toEqual(['q'])
    expect(document.activeElement).toBe(trigger)
  })
})
