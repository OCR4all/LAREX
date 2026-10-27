// @vitest-environment happy-dom
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'

const descriptor = parse(readFileSync(resolve('app/components/ui/image-compare.vue'), 'utf8')).descriptor
const script = compileScript(descriptor, { id: 'comparison-test' })
const template = compileTemplate({ id: 'comparison-test', filename: 'image-compare.vue', source: descriptor.template!.content, compilerOptions: { bindingMetadata: script.bindings } })
const compiled = transpileModule(`${script.content}\n${template.code}`, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText
const exportsObject: { default?: Vue.Component, render?: Vue.RenderFunction } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject)
let app: Vue.App | undefined
afterEach(() => {
  app?.unmount()
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})
it('shows a failure and retries only the failed comparison side', async () => {
  vi.stubGlobal('useElementBounding', () => ({ left: Vue.ref(0), width: Vue.ref(1000) }))
  vi.stubGlobal('useMouse', () => ({ x: Vue.ref(0) }))
  vi.stubGlobal('useEventListener', () => {})
  app = Vue.createApp({ ...exportsObject.default, render: exportsObject.render }, { leftImage: '/left?size=detail', rightImage: '/right?size=detail' })
  app.component('UIcon', { render: () => null })
  app.component('UButton', Vue.defineComponent({ setup: (_, { slots }) => () => Vue.h('button', slots.default?.()) }))
  const host = document.createElement('div')
  document.body.append(host)
  app.mount(host)
  const [right, left] = host.querySelectorAll('img')
  left!.dispatchEvent(new Event('load'))
  right!.dispatchEvent(new Event('error'))
  await Vue.nextTick()
  expect(host.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded')
  host.querySelector('button')!.click()
  await Vue.nextTick()
  expect(host.querySelectorAll('img')[1]).toBe(left)
  expect(host.querySelector('img')!.getAttribute('src')).toContain('_retry=1')
  host.querySelector('img')!.dispatchEvent(new Event('load'))
  await Vue.nextTick()
  expect(host.querySelector('[role="alert"]')).toBeNull()
  expect(host.querySelector('[role="status"]')).toBeNull()
})
