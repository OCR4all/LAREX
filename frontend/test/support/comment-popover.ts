import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent } from 'reka-ui'
import * as comments from '@/components/editor/text/shared/full-text-comments'
import source from '@/components/editor/text/shared/comment-popover.vue?raw'

const script = compileScript(parse(source).descriptor, { id: 'comment-popover-test', inlineTemplate: true })
const exportsObject: { default?: Vue.Component } = {}
new Function('require', 'exports', transpileModule(script.content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText)((id: string) => {
  if (id === 'vue') return Vue
  if (id === './full-text-comments') return comments
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject)
export const commentPopoverComponent = exportsObject.default!

// Use real popover primitives so portal rendering, dismissal, and focus work as
// in Nuxt UI. Only presentation controls are replaced with native elements.
export function registerCommentControls(app: Vue.App): void {
  app.component('UPopover', Vue.defineComponent({
    props: ['open'],
    emits: ['update:open'],
    setup: (props, { slots, emit }) => () => Vue.h(PopoverRoot, {
      'open': props.open,
      'onUpdate:open': (open: boolean) => emit('update:open', open)
    }, () => [
      Vue.h(PopoverTrigger, { asChild: true }, slots.default),
      Vue.h(PopoverPortal, {}, () => Vue.h(PopoverContent, {}, slots.content))
    ])
  }))
  app.component('UTooltip', Vue.defineComponent({
    inheritAttrs: false,
    props: ['text'],
    setup: (_, { slots, attrs }) => () => Vue.cloneVNode(slots.default!()[0]!, attrs, true)
  }))
  app.component('UBadge', Vue.defineComponent({
    props: ['as', 'color', 'variant'],
    setup: (props, { slots }) => () => Vue.h(props.as ?? 'span', {}, slots.default?.())
  }))
  app.component('UButton', Vue.defineComponent({
    props: ['color', 'variant', 'size', 'icon'],
    setup: (_, { slots }) => () => Vue.h('button', {}, slots.default?.())
  }))
  app.component('UTextarea', Vue.defineComponent({
    props: ['modelValue', 'autoresize'],
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => Vue.h('textarea', {
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
    })
  }))
  app.component('Icon', Vue.defineComponent({ setup: () => () => Vue.h('span') }))
}
