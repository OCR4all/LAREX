import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as shortcutRegistry from '@/composables/editor/shortcut-registry'

const descriptor = parse(readFileSync(new URL('../keyboard-shortcuts-help.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'keyboard-shortcuts-help-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText

interface Setup {
  isOpen: Vue.WritableComputedRef<boolean>
  searchQuery: Vue.Ref<string>
  handleCustomize: () => void
}

const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  if (id === '@/composables/editor/shortcut-registry') return shortcutRegistry
  if (id === '@/composables/editor/use-shortcut-bindings') {
    return {
      useShortcutBindings: () => ({
        resolvedShortcutDefinitions: Vue.ref(shortcutRegistry.getResolvedShortcutDefinitions(shortcutRegistry.createShortcutPreferences()))
      })
    }
  }
  if (id === '@/composables/editor/use-canvas-interaction-blocker') return { useBlockEditorCanvasInteractions: () => {} }
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject)

describe('Keyboard shortcuts help', () => {
  it('keeps the reference open when opening and closing customization', () => {
    const props = Vue.reactive({ open: true })
    const settingsOpen = Vue.ref(false)
    const setup = exportsObject.default!.setup(props, {
      expose: () => {},
      emit: (event: string, value: boolean) => {
        if (event === 'update:open') props.open = value
        if (event === 'customize') settingsOpen.value = true
      }
    })
    setup.searchQuery.value = 'zoom'

    setup.handleCustomize()
    expect(settingsOpen.value).toBe(true)
    expect(props.open).toBe(true)

    settingsOpen.value = false
    expect(setup.isOpen.value).toBe(true)
    expect(setup.searchQuery.value).toBe('zoom')

    setup.isOpen.value = false
    expect(props.open).toBe(false)
  })
})
