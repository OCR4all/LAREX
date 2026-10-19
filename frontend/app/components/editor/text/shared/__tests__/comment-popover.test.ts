// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { commentPopoverComponent, registerCommentControls } from '../../../../../../test/support/comment-popover'
import { serializeFullTextComment } from '../full-text-comments'

const apps: Vue.App[] = []
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.innerHTML = ''
})

function mount(readOnly = false) {
  const props = Vue.reactive({ textlineId: 'line-1', comment: 'First line\nSecond line', readOnly })
  const save = vi.fn((comment: string) => {
    props.comment = serializeFullTextComment(comment) ?? ''
  })
  const app = Vue.createApp({
    render: () => Vue.h(commentPopoverComponent, { ...props, onSave: save }, {
      details: () => Vue.h('section', 'Text checks')
    })
  })
  registerCommentControls(app)
  const host = document.createElement('div')
  document.body.append(host)
  apps.push(app)
  app.mount(host)
  return { props, save }
}

function button(label: string): HTMLButtonElement {
  return [...document.querySelectorAll('button')].find(button => button.textContent?.trim() === label)!
}

async function open() {
  document.querySelector<HTMLButtonElement>('[aria-label="Has comment"], [aria-label="Add comment"]')!.click()
  await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
}

async function edit(value: string) {
  const textarea = document.querySelector('textarea')!
  textarea.value = value
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
  await Vue.nextTick()
  return textarea
}

describe('shared text-line comment popover', () => {
  it('opens from the badge, discards canceled edits, and retains proofreading details', async () => {
    const { save } = mount()
    await open()
    expect(document.querySelector('textarea')?.value).toBe('First line\nSecond line')
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('Text checks')
    await edit('Discard me')
    button('Cancel').click()
    await Vue.nextTick()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    await open()
    expect(document.querySelector('textarea')?.value).toBe('First line\nSecond line')
    expect(save).not.toHaveBeenCalled()
  })

  it('saves multiline comments with Ctrl/Cmd+Enter and clears empty comments', async () => {
    const { save } = mount()
    await open()
    const textarea = await edit('Updated\nMultiline comment')
    for (const init of [{}, { ctrlKey: true, isComposing: true }]) {
      textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, ...init }))
      expect(save).not.toHaveBeenCalled()
    }
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', metaKey: true, bubbles: true }))
    await Vue.nextTick()
    expect(save).toHaveBeenLastCalledWith('Updated\nMultiline comment')
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    await open()
    await edit(' \n ')
    button('Save').click()
    await Vue.nextTick()
    expect(document.querySelector('[aria-label="Has comment"]')).toBeNull()
    expect(document.querySelector('[aria-label="Add comment"]')).not.toBeNull()
    await open()
    expect(document.querySelector('textarea')?.value).toBe('')
  })

  it('lets read-only users open and read the complete multiline comment', async () => {
    const { save } = mount(true)
    await open()
    expect(document.querySelector('textarea')).toBeNull()
    expect(document.querySelector('.whitespace-pre-wrap')?.textContent).toBe('First line\nSecond line')
    expect(button('Save')).toBeUndefined()
    button('Close').click()
    await Vue.nextTick()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    expect(save).not.toHaveBeenCalled()
  })

  it('discards an open draft when the selected textline changes', async () => {
    const { props, save } = mount()
    await open()
    await edit('Draft for previous line')
    props.textlineId = 'line-2'
    props.comment = 'Next line comment'
    await Vue.nextTick()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    await open()
    expect(document.querySelector('textarea')?.value).toBe('Next line comment')
    expect(save).not.toHaveBeenCalled()
  })

  it('dismisses with Escape without saving', async () => {
    const { save } = mount()
    await open()
    const textarea = await edit('Unsaved draft')
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    expect(save).not.toHaveBeenCalled()
  })
})
