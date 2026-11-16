import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import FreeTextField from '../free-text-field.vue'

mockNuxtImport('useUserSession', () => () => ({ loggedIn: ref(true), user: ref({ roles: [] }) }))

async function mount(modelValue: string | undefined) {
  const updates: Array<string | undefined> = []
  const wrapper = await mountSuspended(FreeTextField, {
    props: { 'modelValue': modelValue, 'placeholder': 'Comments', 'onUpdate:modelValue': (value: string | undefined) => updates.push(value) }
  })
  return { wrapper, updates, input: () => wrapper.find('input').element as HTMLInputElement }
}

describe('Free-text metadata field', () => {
  it('tells an unset attribute apart from an empty one', async () => {
    const unset = await mount(undefined)
    const empty = await mount('')

    expect(unset.input().placeholder).toBe('Comments (not set)')
    expect(empty.input().placeholder).toBe('Empty value')
  })

  it('offers removal only while the attribute is set', async () => {
    const unset = await mount(undefined)
    const set = await mount('note')

    expect(unset.wrapper.find('button[aria-label="Remove attribute"]').exists()).toBe(false)
    await set.wrapper.find('button[aria-label="Remove attribute"]').trigger('click')
    expect(set.updates).toEqual([undefined])
  })

  it('keeps an empty attribute when its text is cleared', async () => {
    const { wrapper, updates } = await mount('note')

    await wrapper.find('input').setValue('')

    expect(updates).toEqual([''])
  })
})
