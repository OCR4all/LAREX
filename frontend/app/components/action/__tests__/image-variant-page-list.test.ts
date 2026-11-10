// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import * as Virtual from '@tanstack/vue-virtual'
import { compileScript, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import source from '../image-variant-page-list.vue?raw'

// Use the real component and virtualizer; simulate browser layout and Nuxt UI.
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'image-variant-page-list-test', inlineTemplate: true })
const { outputText } = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } })
const exports: { default?: Vue.Component } = {}
new Function('require', 'exports', outputText)((name: string) => name === 'vue' ? Vue : Virtual, exports)
const PageList = exports.default!
const mounted: Array<{ app: Vue.App, host: HTMLElement }> = []

beforeEach(() => {
  const height = function (this: HTMLElement) {
    return this.getAttribute('role') === 'region' ? 336 : 112
  }
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(height)
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(600)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockImplementation(height)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(600)
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

async function mount(count: number) {
  const rows = Vue.shallowRef(Array.from({ length: count }, (_, index) => ({
    id: `page-${index}`, name: `Page ${index}`, hasCompatibleImages: true,
    options: [{ value: 'original', label: 'original · color' }, { value: 'binary', label: 'binary · binary' }]
  })))
  const selected = Vue.reactive(Object.fromEntries(rows.value.map(page => [page.id, ['original']])))
  const disabled = Vue.ref(false)
  const multiple = Vue.ref(false)
  const select = vi.fn((pageId: string, value: unknown) => {
    selected[pageId] = typeof value === 'string' ? [value] : value as string[]
  })
  const host = document.createElement('div')
  document.body.append(host)
  const app = Vue.createApp({ setup: () => () => Vue.h(PageList, {
    rows: rows.value, selectedVariants: selected, globalVariants: ['original'],
    multiple: multiple.value, required: true, disabled: disabled.value, onSelect: select
  }) })
  for (const name of ['UBadge', 'UFormField']) {
    app.component(name, Vue.defineComponent({
      inheritAttrs: false,
      setup: (_, { slots }) => () => Vue.h('div', slots.default?.())
    }))
  }
  app.component('USelectMenu', Vue.defineComponent({
    props: ['modelValue', 'items', 'disabled', 'multiple'],
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => Vue.h('select', {
      value: props.modelValue, disabled: props.disabled, multiple: props.multiple,
      onChange: (event: Event) => {
        const select = event.target as HTMLSelectElement
        emit('update:modelValue', props.multiple ? Array.from(select.selectedOptions, option => option.value) : select.value)
      }
    }, props.items.map((item: { value: string, label: string }) => Vue.h('option', { value: item.value }, item.label)))
  }))
  app.mount(host)
  mounted.push({ app, host })
  await settle()
  return { host, rows, selected, disabled, multiple, select }
}

async function scrollToPage(host: HTMLElement, index: number) {
  const scroller = host.querySelector<HTMLElement>('[role="region"]')!
  scroller.scrollTop = index * 112
  scroller.dispatchEvent(new Event('scroll'))
  await settle()
}

describe('virtual image variant page list', () => {
  it('keeps 10,000 pages bounded to visible rows and edits distant pages without losing choices', async () => {
    const { host, selected, select } = await mount(10000)
    expect(host.querySelectorAll('[role="listitem"]').length).toBeGreaterThan(0)
    expect(host.querySelectorAll('select').length).toBeLessThan(25)
    await scrollToPage(host, 5000)
    const row = host.querySelector('[data-index="5000"]')!
    expect(row.textContent).toContain('Page 5000')
    expect(row.getAttribute('aria-posinset')).toBe('5001')
    expect(row.getAttribute('aria-setsize')).toBe('10000')
    const selector = row.querySelector('select')!
    selector.value = 'binary'
    selector.dispatchEvent(new Event('change'))
    await settle()
    expect(select).toHaveBeenCalledWith('page-5000', 'binary')
    expect(row.textContent).toContain('Differs from global selection')
    await scrollToPage(host, 0)
    expect(host.querySelector('[data-index="5000"]')).toBeNull()
    expect(selected['page-5000']).toEqual(['binary'])
    await scrollToPage(host, 5000)
    expect(host.querySelector('[data-index="5000"] select')?.getAttribute('aria-label')).toBe('Image variants for Page 5000')
    expect(host.querySelector<HTMLSelectElement>('[data-index="5000"] select')?.value).toBe('binary')
    expect(host.querySelectorAll('select').length).toBeLessThan(25)
  })

  it('keeps a focused selector mounted when scrolling and respects disabled and multiple modes', async () => {
    const { host, disabled, multiple, select } = await mount(10000)
    const first = host.querySelector<HTMLSelectElement>('[data-index="0"] select')!
    first.focus()
    await settle()
    await scrollToPage(host, 9000)
    expect(host.querySelector('[data-index="0"] select')).toBe(first)
    expect(document.activeElement).toBe(first)
    expect(host.querySelectorAll('select').length).toBeLessThan(25)
    disabled.value = true
    await settle()
    expect(first.disabled).toBe(true)
    disabled.value = false
    multiple.value = true
    await settle()
    expect(first.multiple).toBe(true)
    Array.from(first.options).forEach(option => option.selected = true)
    first.dispatchEvent(new Event('change'))
    await settle()
    expect(select).toHaveBeenCalledWith('page-0', ['original', 'binary'])
  })

  it('handles scope changes and pages without selectable image metadata', async () => {
    const { host, rows } = await mount(100)
    await scrollToPage(host, 90)
    rows.value = [
      { id: 'pending', name: 'Pending', options: [], hasCompatibleImages: true },
      { id: 'missing', name: 'Missing', options: [], hasCompatibleImages: false }
    ]
    await settle()
    expect(host.querySelector<HTMLElement>('[role="region"]')?.scrollTop).toBe(0)
    expect(host.querySelectorAll('[role="listitem"]')).toHaveLength(2)
    expect(host.textContent).toContain('Automatic selection')
    expect(host.textContent).toContain('No matching images')
    expect(host.querySelectorAll('select')).toHaveLength(0)
  })
})
