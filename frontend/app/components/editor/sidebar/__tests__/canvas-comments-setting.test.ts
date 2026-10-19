// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import settingSource from '../canvas-comments-setting.vue?raw'
import layoutSource from '../settings.vue?raw'
import textSource from '../text-settings-panel.vue?raw'

const apps: Vue.App[] = []
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.innerHTML = ''
})

// Mount the production parents without registering the shared setting globally:
// an unresolved component otherwise silently removes the toggle from the panel.
function compile(source: string, dependencies: Record<string, unknown>, useStore: () => unknown): Vue.Component {
  const script = compileScript(parse(source).descriptor, { id: 'canvas-comments-setting-test', inlineTemplate: true })
  const exportsObject: { default?: Vue.Component } = {}
  new Function('require', 'exports', 'useEditorUiStore', 'ref', 'watch', 'onBeforeUnmount', transpileModule(script.content, {
    compilerOptions: { module: ModuleKind.CommonJS }
  }).outputText)((id: string) => {
    if (id === 'vue') return Vue
    if (id === '@/stores/editor/editor.ui.store') return { useEditorUiStore: useStore }
    if (id in dependencies) return dependencies[id]
    throw new Error(`Unexpected dependency ${id}`)
  }, exportsObject, useStore, Vue.ref, Vue.watch, Vue.onBeforeUnmount)
  return exportsObject.default!
}

describe('canvas comment setting in sidebar panels', () => {
  it('renders in Layout and Text Canvas settings and shares the toggle state', async () => {
    const state = Vue.reactive({
      commentsOverlay: { visible: false },
      globalSettings: {},
      backgroundColor: '#ffffff',
      backgroundOpacity: 1,
      toggleCommentsOverlay() { this.commentsOverlay.visible = !this.commentsOverlay.visible }
    })
    const setting = compile(settingSource, {}, () => state)
    const dependencies = { './canvas-comments-setting.vue': setting }
    const layout = compile(layoutSource, dependencies, () => state)
    const text = compile(textSource, dependencies, () => state)
    const app = Vue.createApp({
      render: () => Vue.h('main', [Vue.h(layout), Vue.h(text, { canvasMode: true })])
    })
    const presentation = Vue.defineComponent({ setup: (_, { slots }) => () => Vue.h('div', slots.default?.()) })
    for (const name of ['USeparator', 'USelect', 'UPopover', 'USlider', 'UTabs', 'EditorBackgroundColorPicker']) {
      app.component(name, presentation)
    }
    app.component('USwitch', Vue.defineComponent({
      props: ['modelValue'],
      emits: ['update:modelValue'],
      setup: (props, { emit }) => () => Vue.h('button', {
        'role': 'switch',
        'aria-checked': String(Boolean(props.modelValue)),
        'onClick': () => emit('update:modelValue', !props.modelValue)
      })
    }))
    const host = document.createElement('div')
    document.body.append(host)
    apps.push(app)
    app.mount(host)
    const switches = [...host.querySelectorAll<HTMLButtonElement>('[aria-label="Show Comments on Canvas"]')]
    expect(switches).toHaveLength(2)
    switches[0]!.click()
    await Vue.nextTick()
    expect(state.commentsOverlay.visible).toBe(true)
    expect(switches.map(toggle => toggle.getAttribute('aria-checked'))).toEqual(['true', 'true'])
    switches[1]!.click()
    await Vue.nextTick()
    expect(state.commentsOverlay.visible).toBe(false)
    expect(switches.map(toggle => toggle.getAttribute('aria-checked'))).toEqual(['false', 'false'])
  })
})
