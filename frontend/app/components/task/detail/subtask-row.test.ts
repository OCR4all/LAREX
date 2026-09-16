// @vitest-environment happy-dom
import { expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import type { Subtask } from '~/types/index'
import source from './subtask-row.vue?raw'

// Compile the real SFC without starting Nuxt or adding a component-test framework.
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'subtask-row-test', inlineTemplate: true })
const { outputText } = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } })
const exports: { default?: Vue.Component } = {}
new Function('require', 'exports', outputText)(() => Vue, exports)
const Row = exports.default!

it('keeps row actions reactive and mounts the assignee menu only on activation', async () => {
  const subtask = Vue.ref({ id: 'task-1', title: 'Transcribe', completed: false, taskDescription: 'Inherited description' } as Subtask)
  const selectionMode = Vue.ref(false)
  const selected = Vue.ref(false)
  const pending = Vue.ref(false)
  const editing = Vue.ref(false)
  const title = Vue.ref('Transcribe')
  const description = Vue.ref('')
  const offset = Vue.ref(0)
  const assign = vi.fn()
  const toggle = vi.fn()
  const save = vi.fn()
  const remove = vi.fn()
  const mounts = vi.fn()
  const options = [{ label: 'Unassigned', value: '' }, { label: 'Ada', value: 'user-1' }]
  const host = document.createElement('div')
  document.body.append(host)
  const app = Vue.createApp({
    setup: () => () => Vue.h('div', { style: { transform: `translateY(${offset.value}px)` } }, [Vue.h(Row, {
      'subtask': subtask.value,
      'assigneeOptions': options,
      'selectionMode': selectionMode.value,
      'selected': selected.value,
      'pending': pending.value,
      'editing': editing.value,
      'editingTitle': title.value,
      'editingDescription': description.value,
      'onUpdate:editingTitle': (value: string) => { title.value = value },
      'onUpdate:editingDescription': (value: string) => { description.value = value },
      'onSelect': () => { selected.value = !selected.value },
      'onToggle': toggle,
      'onEdit': () => { editing.value = true },
      'onCancel': () => { editing.value = false },
      'onSave': save,
      'onDelete': remove,
      'onAssign': assign
    })])
  })
  for (const name of ['UIcon', 'AppAvatar', 'UBadge']) app.component(name, { render: () => Vue.h('span') })
  app.component('NuxtLink', Vue.defineComponent({ props: ['to'], setup: (props, { slots }) => () => Vue.h('a', { href: props.to }, slots.default?.()) }))
  app.component('UButton', Vue.defineComponent({ setup: (_, { slots }) => () => Vue.h('button', slots.default?.()) }))
  for (const name of ['UInput', 'UTextarea']) app.component(name, Vue.defineComponent({
    props: ['modelValue'],
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => Vue.h('input', {
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value)
    })
  }))
  app.component('LazyUDropdownMenu', Vue.defineComponent({
    props: ['items', 'content'],
    emits: ['update:open'],
    setup(props, { emit }) {
      mounts()
      return () => Vue.h('div', { role: 'menu' }, props.items.map((item: { label: string, onSelect: () => void }) => Vue.h('button', {
        onClick: () => {
          item.onSelect()
          emit('update:open', false)
        }
      }, item.label)))
    }
  }))
  app.mount(host)
  const button = (label: string) => host.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!
  try {
    expect(host.textContent).toContain('Inherited description')
    expect(mounts).not.toHaveBeenCalled()
    host.querySelector<HTMLInputElement>('input')!.click()
    expect(toggle).toHaveBeenCalledOnce()
    pending.value = true
    await Vue.nextTick()
    expect(host.querySelector<HTMLInputElement>('input')!.disabled).toBe(true)
    pending.value = false
    subtask.value = { ...subtask.value, completed: true, pageId: 'page', pageName: 'Page 1', projectId: 'project' }
    await Vue.nextTick()
    expect(host.querySelector<HTMLInputElement>('input')!.checked).toBe(true)
    expect(host.querySelector('a')!.getAttribute('href')).toBe('/project/project')

    selectionMode.value = true
    await Vue.nextTick()
    host.querySelector<HTMLInputElement>('input')!.click()
    await Vue.nextTick()
    expect(selected.value).toBe(true)
    expect(host.querySelectorAll('input')[1]!.disabled).toBe(true)
    expect(host.querySelector('[aria-haspopup]')).toBeNull()
    selectionMode.value = false
    await Vue.nextTick()

    button('Assign Transcribe: Unassigned').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }))
    await Vue.nextTick()
    expect(mounts).toHaveBeenCalledOnce()
    offset.value = 48
    await Vue.nextTick()
    expect(mounts).toHaveBeenCalledOnce()
    host.querySelectorAll<HTMLButtonElement>('[role="menu"] button')[1]!.click()
    await Vue.nextTick()
    expect(assign).toHaveBeenLastCalledWith('user-1')
    expect(host.querySelector('[role="menu"]')).toBeNull()
    button('Assign Transcribe: Unassigned').click()
    await Vue.nextTick()
    host.querySelector<HTMLButtonElement>('[role="menu"] button')!.click()
    await Vue.nextTick()
    expect(assign).toHaveBeenLastCalledWith(null)

    button('Edit Transcribe').click()
    await Vue.nextTick()
    const input = host.querySelector<HTMLInputElement>('[aria-label="Task title"]')!
    input.value = 'Updated title'
    input.dispatchEvent(new Event('input'))
    await Vue.nextTick()
    expect(title.value).toBe('Updated title')
    input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }))
    expect(save).toHaveBeenCalledOnce()
    input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape' }))
    await Vue.nextTick()
    expect(editing.value).toBe(false)
    button('Delete Transcribe').click()
    expect(remove).toHaveBeenCalledOnce()
  } finally {
    app.unmount()
    host.remove()
  }
})
