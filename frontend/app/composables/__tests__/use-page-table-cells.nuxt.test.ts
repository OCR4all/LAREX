import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import type { TableSort } from '../use-table-filters'
import { usePageTableCells } from '../use-page-table-cells'

mockNuxtImport('useUserSession', () => () => ({ loggedIn: ref(true), user: ref({ roles: [] }) }))

function mountHeader(sort: TableSort, column = 'name') {
  return mountSuspended(defineComponent({
    setup() {
      const cells = usePageTableCells(ref(sort))
      const header = cells.sortableHeader('Name', column)
      return () => h(header)
    }
  }))
}

function iconClasses(wrapper: Awaited<ReturnType<typeof mountHeader>>) {
  return wrapper.find('button .iconify').classes()
}

describe('Page table sort header', () => {
  it('renders a sort button for a column that is not sorted', async () => {
    const wrapper = await mountHeader({ column: 'updated', direction: 'desc' })

    expect(wrapper.find('button').exists()).toBe(true)
    expect(iconClasses(wrapper)).toContain('i-lucide:arrow-up-down')
  })

  it('shows the direction of the active sort column', async () => {
    const asc = await mountHeader({ column: 'name', direction: 'asc' })
    const desc = await mountHeader({ column: 'name', direction: 'desc' })

    expect(iconClasses(asc)).toContain('i-lucide:arrow-up')
    expect(iconClasses(desc)).toContain('i-lucide:arrow-down')
  })

  it('sorts by the column when the button is clicked', async () => {
    const sort = ref<TableSort>({ column: 'updated', direction: 'desc' })
    const wrapper = await mountSuspended(defineComponent({
      setup() {
        const header = usePageTableCells(sort).sortableHeader('Name', 'name')
        return () => h(header)
      }
    }))

    await wrapper.find('button').trigger('click')

    expect(sort.value).toEqual({ column: 'name', direction: 'asc' })
  })
})
