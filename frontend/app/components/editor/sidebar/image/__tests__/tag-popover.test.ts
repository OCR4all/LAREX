// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import * as Vue from 'vue'
import { compileTemplate } from 'vue/compiler-sfc'
import { HoverCardContent, HoverCardPortal, HoverCardRoot, HoverCardTrigger } from 'reka-ui'
import source from '../item.vue?raw'

// Render the production tag popover with the same hover/portal primitives as Nuxt UI.
const start = source.indexOf('<UPopover')
const template = source.slice(start, source.indexOf('</UPopover>', start) + '</UPopover>'.length)
const { code } = compileTemplate({ source: template, filename: 'tag-popover.vue', id: 'tag-popover-test' })
const render = new Function('Vue', code.replace(/import \{([^}]+)\} from "vue"/, (_, names: string) =>
  `const { ${names.replace(/ as /g, ': ')} } = Vue`).replace('export function render', 'return function render'))(Vue)
let app: App | undefined

afterEach(() => {
  app?.unmount()
  document.body.innerHTML = ''
})

it('renders hovered page tags outside the clipped thumbnail and scroll container', async () => {
  const tags = Array.from({ length: 6 }, (_, index) => ({ label: `Tag ${index + 1}`, color: '#2463eb' }))
  const host = document.createElement('div')
  host.style.overflow = 'hidden'
  document.body.append(host)
  app = createApp({
    data: () => ({ displayTags: tags, visibleTagDots: tags.slice(0, 4), hiddenTagDotCount: 2, tooltipUi: { content: '' } }),
    render
  })
  app.component('UPopover', defineComponent({
    props: { portal: { type: Boolean, default: true } },
    setup: (props, { slots }) => () => h(HoverCardRoot, { openDelay: 0 }, () => [
      h(HoverCardTrigger, { asChild: true }, slots.default),
      h(HoverCardPortal, { disabled: !props.portal }, () => h(HoverCardContent, { 'data-testid': 'page-tags' }, slots.content))
    ])
  }))
  app.component('UiColorTag', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }))
  app.mount(host)
  await nextTick()
  host.firstElementChild!.dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'mouse' }))
  await vi.waitFor(() => {
    const content = document.querySelector('[data-testid="page-tags"]')
    expect(content?.textContent).toBe(tags.map(tag => tag.label).join(''))
    expect(host.contains(content)).toBe(false)
  })
})
