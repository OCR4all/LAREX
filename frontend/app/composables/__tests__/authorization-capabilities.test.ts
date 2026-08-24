import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

describe('authorization composables', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('useActionVisibility allows only explicit true guards', async () => {
    const { useActionVisibility } = await import('../use-action-visibility')
    const { allow, compactGroups } = useActionVisibility()

    expect(allow(true)).toBe(true)
    expect(allow(false)).toBe(false)
    expect(allow(undefined)).toBe(false)
    expect(allow(() => true)).toBe(true)
    expect(allow(() => false)).toBe(false)

    expect(compactGroups([[1], [], [2, 3]])).toEqual([[1], [2, 3]])
  })

  it('useResourceCapabilities is conservative by default for resources', async () => {
    const { useResourceCapabilities } = await import('../use-resource-capabilities')

    const resource = ref<{ capabilities?: { canDelete?: boolean } } | null>({
      capabilities: { canDelete: true }
    })

    const caps = useResourceCapabilities(resource, 'resource')
    expect(caps.value.canEdit).toBe(false)
    expect(caps.value.canShare).toBe(false)
    expect(caps.value.canDelete).toBe(true)

    resource.value = null
    expect(caps.value.canEdit).toBe(false)
    expect(caps.value.canShare).toBe(false)
    expect(caps.value.canDelete).toBe(false)
  })

  it('useResourceCapabilities defaults task mutations to hidden when capabilities are missing', async () => {
    const { useResourceCapabilities } = await import('../use-resource-capabilities')

    const task = ref<{ capabilities?: { canUpdateStatus?: boolean } } | null>({
      capabilities: { canUpdateStatus: true }
    })

    const caps = useResourceCapabilities(task, 'task')
    expect(caps.value.canUpdateStatus).toBe(true)
    expect(caps.value.canEdit).toBe(false)
    expect(caps.value.canDelete).toBe(false)
    expect(caps.value.canAssignOthers).toBe(false)
  })

  it('useResourceCapabilities applies dataset defaults conservatively', async () => {
    const { useResourceCapabilities } = await import('../use-resource-capabilities')

    const dataset = ref<{ capabilities?: { canExportPackage?: boolean } } | null>({
      capabilities: { canExportPackage: true }
    })

    const caps = useResourceCapabilities(dataset, 'dataset')
    expect(caps.value.canExportPackage).toBe(true)
    expect(caps.value.canEdit).toBe(false)
    expect(caps.value.canManageItems).toBe(false)
    expect(caps.value.canGenerateSplit).toBe(false)
  })
})
