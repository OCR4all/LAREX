// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import * as Virtual from '@tanstack/vue-virtual'
import { compileScript, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import type { PageMovePreview } from '@/types/page-move'
import source from './page-move-preview.vue?raw'

// Exercise the real virtualizer and SFC; simulate only layout and Nuxt UI controls.
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'page-move-preview-test', inlineTemplate: true })
const { outputText } = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } })
const exports: { default?: Vue.Component } = {}
new Function('require', 'exports', outputText)((name: string) => name === 'vue' ? Vue : Virtual, exports)
const Preview = exports.default!
type Item = PageMovePreview['items'][number]
const mounted: Array<{ app: Vue.App, host: HTMLElement }> = []

beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
    return this.getAttribute('role') === 'region' ? 320 : this.querySelector('ul') ? 144 : 80
  })
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(500)
  vi.spyOn(HTMLElement.prototype, 'scrollTo').mockImplementation(function (this: HTMLElement, options?: ScrollToOptions | number, y?: number) {
    this.scrollTop = typeof options === 'number' ? y ?? 0 : options?.top ?? 0
    this.dispatchEvent(new Event('scroll'))
  })
})

afterEach(() => {
  mounted.splice(0).forEach(({ app, host }) => {
    app.unmount()
    host.remove()
  })
  vi.restoreAllMocks()
})

async function settle() {
  await Vue.nextTick()
  await Vue.nextTick()
  await Vue.nextTick()
}

function page(index: number, overrides: Partial<Item> = {}): Item {
  const name = `Page ${String(index).padStart(5, '0')}`
  return { pageId: `page-${index}`, sourceName: name, resultingName: name, outcome: 'MOVE', overwrittenPageId: null, blockers: [], ...overrides }
}

async function mount(items: Item[]) {
  const entries = Vue.shallowRef(items)
  const host = document.createElement('div')
  document.body.append(host)
  const app = Vue.createApp({ setup: () => () => Vue.h(Preview, { items: entries.value }) })
  app.component('UIcon', { render: () => Vue.h('span') })
  app.component('UBadge', Vue.defineComponent({ setup: (_, { slots }) => () => Vue.h('span', slots.default?.()) }))
  app.component('UButton', Vue.defineComponent({
    props: ['label'],
    setup: (props, { slots }) => () => Vue.h('button', slots.default?.() ?? props.label)
  }))
  app.component('UInput', Vue.defineComponent({
    props: ['modelValue'],
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => Vue.h('input', {
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value)
    })
  }))
  app.component('USelect', Vue.defineComponent({
    props: ['modelValue', 'items'],
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => Vue.h('select', {
      value: props.modelValue,
      onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value)
    }, props.items.map((item: { value: string, label: string }) => Vue.h('option', { value: item.value }, item.label)))
  }))
  app.mount(host)
  mounted.push({ app, host })
  await settle()
  return { host, entries }
}

async function selectFilter(host: HTMLElement, value: string) {
  const select = host.querySelector('select')!
  select.value = value
  select.dispatchEvent(new Event('change'))
  await settle()
}

async function searchFor(host: HTMLElement, value: string) {
  const input = host.querySelector('input')!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await settle()
}

describe('virtual page move preview', () => {
  it('keeps the rendered list bounded for 10,000 pages and renders distant rows on scroll', async () => {
    const { host } = await mount(Array.from({ length: 10000 }, (_, index) => page(index)))
    expect(host.querySelectorAll('[role="listitem"]').length).toBeGreaterThan(0)
    expect(host.querySelectorAll('[role="listitem"]').length).toBeLessThan(40)
    expect(host.querySelector('[role="list"]')?.textContent).toContain('Page 00000')
    const scroller = host.querySelector<HTMLElement>('[role="region"]')!
    scroller.scrollTop = 5000 * 80
    scroller.dispatchEvent(new Event('scroll'))
    await settle()
    expect(host.querySelector('[role="list"]')?.textContent).toContain('Page 05000')
    expect(host.querySelector('[role="list"]')?.textContent).not.toContain('Page 00000')
    expect(host.querySelectorAll('[role="listitem"]').length).toBeLessThan(40)
    expect(host.querySelector('[role="listitem"]')?.getAttribute('aria-setsize')).toBe('10000')
  })

  it('combines outcome filtering and both-name search, resets scrolling, and leaves the batch unchanged', async () => {
    const items = Array.from({ length: 2000 }, (_, index) => page(index))
    items[1900] = page(1900, { outcome: 'RENAME', resultingName: 'prefix-renamed-page' })
    items[1901] = page(1901, { outcome: 'OVERWRITE', overwrittenPageId: 'replaced' })
    const { host, entries } = await mount(items)
    const scroller = host.querySelector<HTMLElement>('[role="region"]')!
    scroller.scrollTop = 1000 * 80
    scroller.dispatchEvent(new Event('scroll'))
    await settle()
    await selectFilter(host, 'RENAME')
    expect(scroller.scrollTop).toBe(0)
    expect(host.querySelector('[role="list"]')?.textContent).toContain('prefix-renamed-page')
    await searchFor(host, ' PREFIX-RENAMED ')
    expect(host.querySelectorAll('[role="listitem"]')).toHaveLength(1)
    await searchFor(host, 'Page 01900')
    expect(host.querySelectorAll('[role="listitem"]')).toHaveLength(1)
    await selectFilter(host, 'OVERWRITE')
    expect(host.textContent).toContain('No pages match these filters.')
    Array.from(host.querySelectorAll('button')).find(button => button.textContent === 'Clear filters')!.click()
    await settle()
    expect(host.textContent).not.toContain('No pages match these filters.')
    expect(entries.value).toHaveLength(2000)
    expect(entries.value[1900]?.resultingName).toBe('prefix-renamed-page')
  })

  it('finds offscreen blockers, measures multiline rows, and remeasures changed previews', async () => {
    const { host, entries } = await mount(Array.from({ length: 1000 }, (_, index) => page(index)))
    entries.value = entries.value.map((item, index) => index === 900
      ? page(index, { blockers: ['A destination page is locked.', 'Close its active annotation editor.'] })
      : item)
    await settle()
    Array.from(host.querySelectorAll('button')).find(button => button.textContent?.includes('1 need attention'))!.click()
    await settle()
    expect(host.querySelectorAll('[role="listitem"]')).toHaveLength(1)
    expect(host.querySelector('[role="list"]')?.textContent).toContain('Page 00900')
    expect(host.querySelector('[role="list"]')?.textContent).toContain('Close its active annotation editor.')
    expect(host.querySelector<HTMLElement>('[role="list"]')?.style.height).toBe('144px')
    entries.value = [page(900)]
    await settle()
    expect(host.textContent).toContain('No pages match these filters.')
    await selectFilter(host, 'all')
    expect(host.querySelector<HTMLElement>('[role="list"]')?.style.height).toBe('80px')
    expect(host.querySelector('[role="listitem"]')?.getAttribute('aria-posinset')).toBe('1')
  })
})
