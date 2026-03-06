<script setup lang="ts">
import { LazyCodecSlideoverAction, LazyVirtualKeyboardSlideoverGlyphPicker, LazyUiDeleteSlideover, LazyUiConfirmModal, LazyShareSlideover } from '#components'
import { resolveComponent } from 'vue'
import type { Codec, GenerateCodecFromSourcesResponse, ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import { DEFAULT_RESOURCE_CAPABILITIES } from '@/types/capabilities'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { wsKey } from '@/utils/fetch-keys'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { allow } = useActionVisibility()

const workspace = useWorkspaceStore()

if (!workspace.hasFetched) {
  await workspace.fetchWorkspaces()
}

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const codecListKey = computed(() => wsKey(selectedWorkspace.value, 'codecs', 'list'))

const id = route.params.id as string
const isNew = id === 'new'

const codecKey = computed(() => wsKey(selectedWorkspace.value, 'codecs', id))
const loadedCapabilities = ref<{ canEdit: boolean, canDelete: boolean } | null>(null)

const defaultCodec: Codec = {
  id: '',
  name: 'New Codec',
  description: '',
  tags: [],
  codec: [],
  characterCount: 0,
  created: '',
  updated: ''
}

let initial = defaultCodec

if (!isNew) {
  const { data, error } = await useFetch<Codec>(() => `/api/workspaces/${selectedWorkspace.value}/codecs/${id}`, {
    key: codecKey
  })
  if (data.value) {
    initial = data.value
    loadedCapabilities.value = data.value.capabilities ?? null
  } else if (error.value) {
    toast.add({ title: 'Error loading codec', color: 'error' })
    router.push('/codecs')
  }
}
const codecCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditCodec = computed(() => isNew || allow(codecCapabilities.value.canEdit))
const canDeleteCodec = computed(() => !isNew && allow(codecCapabilities.value.canDelete))

const name = ref(initial.name)
const description = ref(initial.description ?? '')
const tags = ref<string[]>([...(initial.tags ?? [])])
const codec = ref<string[]>([...(initial.codec ?? [])])

const breadcrumbItems = computed(() => [
  {
    label: 'Home',
    icon: 'i-lucide-home',
    to: '/'
  },
  {
    label: 'Codecs',
    icon: 'i-lucide-case-lower',
    to: '/codecs'
  },
  {
    label: isNew ? 'New Codec' : (name.value || 'Edit Codec')
  }
])

const getCodepoint = (char: string): string => {
  const codepoint = char.codePointAt(0)
  return codepoint ? `U+${codepoint.toString(16).toUpperCase().padStart(4, '0')}` : 'N/A'
}

const isPUA = (char: string): boolean => {
  const codepoint = char.codePointAt(0)
  if (!codepoint) return false

  return (codepoint >= 0xE000 && codepoint <= 0xF8FF)
    || (codepoint >= 0xF0000 && codepoint <= 0xFFFD)
    || (codepoint >= 0x100000 && codepoint <= 0x10FFFD)
}

const selectedCharacters = ref<Set<string>>(new Set())
const selectAll = ref(false)

const toggleSelectAll = () => {
  if (selectAll.value) {
    selectedCharacters.value = new Set(codec.value)
  } else {
    selectedCharacters.value.clear()
  }
}

const toggleCharacterSelection = (char: string) => {
  if (selectedCharacters.value.has(char)) {
    selectedCharacters.value.delete(char)
  } else {
    selectedCharacters.value.add(char)
  }
  selectAll.value = selectedCharacters.value.size === codec.value.length
}

const characterData = computed(() =>
  codec.value.map(char => ({
    character: char,
    codepoint: getCodepoint(char),
    isPUA: isPUA(char),
    selected: selectedCharacters.value.has(char)
  }))
)

type CharacterRow = {
  character: string
  codepoint: string
  isPUA: boolean
  selected: boolean
}

function getCharacterRowActions(row: CharacterRow) {
  if (!canEditCodec.value) return []
  return [{
    label: 'Remove',
    icon: 'i-lucide-trash',
    color: 'error' as const,
    onSelect: () => removeChar(row.character)
  }]
}

const columns: TableColumn<CharacterRow>[] = [
  {
    accessorKey: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: selectAll.value,
      onInput: (e: Event) => {
        selectAll.value = (e.target as HTMLInputElement).checked
        toggleSelectAll()
      },
      class: 'rounded-sm border-gray-300'
    }),
    cell: ({ row }) => h('input', {
      type: 'checkbox',
      checked: row.original.selected,
      onInput: () => toggleCharacterSelection(row.original.character),
      class: 'rounded-sm border-gray-300'
    }),
    size: 40,
    enableSorting: false
  },
  {
    accessorKey: 'character',
    header: 'Character',
    cell: ({ row }) => h('span', { class: 'font-junicode text-lg' }, row.getValue('character')),
    size: 80
  },
  {
    accessorKey: 'codepoint',
    header: 'Codepoint',
    cell: ({ row }) => h(resolveComponent('UBadge'), {
      variant: 'subtle',
      size: 'sm'
    }, { default: () => row.getValue('codepoint') })
  },
  {
    accessorKey: 'isPUA',
    header: 'Is PUA',
    cell: ({ row }) => h(resolveComponent('Icon'), {
      name: isPUA(row.original.character) ? 'i-lucide-shield-alert' : 'i-lucide-shield-check',
      class: isPUA(row.original.character) ? 'text-orange-500' : 'text-green-500'
    })
  },
  {
    accessorKey: 'actions',
    header: 'Actions',
    cell: ({ row }) => h(resolveComponent('UDropdownMenu'), {
      items: getCharacterRowActions(row.original)
    }, {
      default: () => h(resolveComponent('UButton'), {
        icon: 'i-lucide-more-horizontal',
        size: 'sm',
        variant: 'ghost'
      })
    }),
    size: 80,
    enableSorting: false
  }
]

const contextMenuCharacter = ref<CharacterRow | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuCharacter.value) return []
  return getCharacterRowActions(contextMenuCharacter.value)
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuCharacter.value = row.original as unknown as CharacterRow
}

const overlay = useOverlay()
const glyphPickerSlideover = overlay.create(LazyVirtualKeyboardSlideoverGlyphPicker)
const shareSlideover = overlay.create(LazyShareSlideover)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const confirmModal = overlay.create(LazyUiConfirmModal)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)
const importCodecInput = ref<HTMLInputElement | null>(null)

const addCharactersFromString = (value: string) => {
  if (value == null) return
  const next = new Set(codec.value)
  for (const ch of value) {
    if (ch === '') continue
    next.add(ch)
  }
  codec.value = Array.from(next)
  selectedCharacters.value.clear()
  selectAll.value = false
}

const newChar = ref('')
const addManual = () => {
  if (!canEditCodec.value) return
  if (!newChar.value) return
  addCharactersFromString(newChar.value)
  newChar.value = ''
}

const removeChar = (ch: string) => {
  if (!canEditCodec.value) return
  codec.value = codec.value.filter(c => c !== ch)
  selectedCharacters.value.delete(ch)
  selectAll.value = false
}

const removeSelectedCharacters = async () => {
  if (!canEditCodec.value) return
  if (selectedCharacters.value.size === 0) return

  const instance = confirmModal.open({
    title: 'Remove Characters',
    description: `Are you sure you want to remove ${selectedCharacters.value.size} selected characters?`,
    confirmLabel: 'Remove',
    confirmColor: 'error'
  })

  const confirmed = await instance.result
  if (!confirmed) return

  codec.value = codec.value.filter(ch => !selectedCharacters.value.has(ch))
  selectedCharacters.value.clear()
  selectAll.value = false
}

const onGlyphSelect = async () => {
  if (!canEditCodec.value) return
  const instance = glyphPickerSlideover.open({ title: 'Glyph Picker' })
  const glyph = await instance.result as { utf8?: string } | string | null
  if (typeof glyph === 'string') {
    addCharactersFromString(glyph)
    return
  }
  if (glyph?.utf8) addCharactersFromString(glyph.utf8)
}

const characterCount = computed(() => codec.value.length)

const openCodecImportDialog = () => {
  if (!canEditCodec.value) return
  importCodecInput.value?.click()
}

const handleCodecImport = async (event: Event) => {
  if (!canEditCodec.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  try {
    const content = await file.text()
    const result = await $fetch<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>(`/api/workspaces/${selectedWorkspace.value}/utilities/import`, {
      method: 'POST',
      body: { content }
    })

    const imported = result.resources?.find(r => r.type === 'CODEC')
    await refreshNuxtData(codecListKey.value)
    if (imported?.targetId) {
      await router.push(`/codecs/${imported.targetId}`)
    }

    toast.add({
      title: 'Codec imported',
      description: imported?.targetName ? `Imported as "${imported.targetName}"` : undefined,
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to import codec package'
    toast.add({ title: 'Import failed', description: message, color: 'error' })
  } finally {
    input.value = ''
  }
}

const handleCodecExport = () => {
  if (isNew) {
    const data = {
      name: name.value,
      description: description.value,
      tags: tags.value,
      codec: codec.value
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${name.value.replace(/\\s+/g, '-').toLowerCase() || 'codec'}-${Date.now()}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    return
  }

  void (async () => {
    try {
      const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/utilities/export`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          includeAll: false,
          selectors: [{ type: 'CODEC', ids: [id] }]
        })
      })

      if (!response.ok) {
        throw new Error(`Export failed (${response.status})`)
      }

      const blob = await response.blob()
      const fallbackName = `${name.value.replace(/\\s+/g, '-').toLowerCase() || 'codec'}.larex-utilities.json`
      const contentDisposition = response.headers.get('content-disposition')
      const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
      const fileName = match ? decodeURIComponent(match[1]!) : fallbackName

      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      toast.add({ title: 'Codec exported', color: 'success' })
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to export codec package'
      toast.add({ title: 'Export failed', description: message, color: 'error' })
    }
  })()
}

const handleSave = async () => {
  if (!canEditCodec.value) return
  try {
    const payload = {
      name: name.value,
      description: description.value,
      tags: tags.value,
      codec: codec.value
    }

    if (isNew) {
      const saved = await $fetch<Codec>(`/api/workspaces/${selectedWorkspace.value}/codecs`, {
        method: 'POST',
        body: payload
      })
      toast.add({ title: 'Codec created', color: 'success' })
      await refreshNuxtData(codecListKey.value)
      await router.push(`/codecs/${saved.id}`)
    } else {
      await $fetch<Codec>(`/api/workspaces/${selectedWorkspace.value}/codecs/${id}`, {
        method: 'PUT',
        body: payload
      })
      toast.add({ title: 'Codec updated', color: 'success' })
      await refreshNuxtData(codecKey.value)
      await refreshNuxtData(codecListKey.value)
    }
  } catch {
    toast.add({ title: 'Error saving codec', color: 'error' })
  }
}

const handleDelete = async () => {
  if (!canDeleteCodec.value) return
  if (isNew) return

  const instance = deleteSlideover.open({
    name: name.value,
    entityType: 'Codec',
    warningMessage: 'This action cannot be undone! All projects using this codec will lose their codec reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/codecs/${id}`, { method: 'DELETE' })
    toast.add({ title: 'Codec deleted', color: 'success' })
    await refreshNuxtData(codecListKey.value)
    await router.push('/codecs')
  } catch {
    toast.add({ title: 'Error deleting codec', color: 'error' })
  }
}

const handleGenerateFromSources = async () => {
  if (!canEditCodec.value) return
  const instance = codecActionSlideover.open({
    mode: 'generate',
    workspaceId: selectedWorkspace.value,
    sources: [],
    allowSourceEditing: true
  })
  const result = await instance.result as GenerateCodecFromSourcesResponse | null
  if (!result) return

  await refreshNuxtData(codecListKey.value)

  if (isNew || result.codec.id !== id) {
    await router.push(`/codecs/${result.codec.id}`)
    return
  }

  name.value = result.codec.name
  description.value = result.codec.description ?? ''
  tags.value = [...(result.codec.tags ?? [])]
  codec.value = [...(result.codec.codec ?? [])]
  await refreshNuxtData(codecKey.value)
}

const handleValidateAgainstSources = async () => {
  const instance = codecActionSlideover.open({
    mode: 'validate',
    workspaceId: selectedWorkspace.value,
    sources: [],
    defaultCodecId: isNew ? null : id,
    allowSourceEditing: true
  })
  await instance.result as ValidateCodecAgainstSourcesResponse | null
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = [
    {
      label: 'Export codec package',
      icon: 'i-lucide-download',
      onSelect: handleCodecExport
    },
    { type: 'separator' },
    {
      label: 'Validate codec against sources',
      icon: 'i-lucide-badge-check',
      onSelect: handleValidateAgainstSources
    }
  ]

  if (canEditCodec.value) {
    items.unshift({
      label: 'Import codec package',
      icon: 'i-lucide-upload',
      onSelect: openCodecImportDialog
    })
    items.splice(3, 0, {
      label: 'Generate codec from sources',
      icon: 'i-lucide-wand-sparkles',
      onSelect: handleGenerateFromSources
    })
  }

  if (!isNew) {
    if (canEditCodec.value) {
      items.push({
      label: 'Share codec',
      icon: 'i-lucide-share-2',
      onSelect: () => shareSlideover.open({ resourceId: id, resourceName: name.value, resourceType: 'CODEC', currentWorkspaceId: selectedWorkspace.value })
      })
    }
    if (canDeleteCodec.value) {
      items.push({
      label: 'Delete codec',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      onSelect: handleDelete
      })
    }
  }

  return items
})
</script>

<template>
  <UDashboardPanel :ui=" { body: 'p-0 sm:p-0' } ">
    <template #header>
      <UDashboardNavbar data-tour="codec-builder-header" :title="isNew ? 'Create Codec' : 'Edit Codec'">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <input
            ref="importCodecInput"
            type="file"
            class="hidden"
            accept=".json,.larex-utilities.json,application/json"
            @change="handleCodecImport"
          >
          <UFieldGroup>
            <UButton
              label="Save"
              color="neutral"
              variant="subtle"
              icon="i-lucide-save"
              :disabled="!canEditCodec"
              @click="handleSave"
            />

            <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
              <UButton
                color="neutral"
                variant="subtle"
                icon="i-lucide-chevron-down"
              />
            </UDropdownMenu>
          </UFieldGroup>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="flex h-full">
        <aside class="w-80 bg-neutral-50/30 dark:bg-neutral-800/50 border-r border-neutral-200 dark:border-neutral-700 flex flex-col shrink-0">
          <div class="flex-1 p-4 space-y-4 overflow-y-auto">
            <UFormField data-tour="codec-builder-input" label="Name">
              <UInput v-model="name" placeholder="Codec name" />
            </UFormField>

            <UFormField label="Description">
              <UTextarea v-model="description" placeholder="What is this codec used for?" />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags
                v-model="tags"
                class="w-full"
                placeholder="e.g. latin, greek, pua"
                size="md"
              />
            </UFormField>

            <div class="pt-4 border-t border-neutral-200 dark:border-neutral-700">
              <div class="text-sm text-neutral-600 dark:text-neutral-400">
                <div class="flex justify-between">
                  <span>Total Characters:</span>
                  <span class="font-medium">{{ characterCount }}</span>
                </div>
                <div class="flex justify-between mt-1">
                  <span>Selected:</span>
                  <span class="font-medium">{{ selectedCharacters.size }}</span>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <main class="flex-1 flex flex-col">
          <div class="p-4 border-b border-neutral-200 dark:border-neutral-700 bg-white dark:bg-neutral-900">
            <div class="flex items-center gap-4 mb-4">
              <h2 class="text-lg font-semibold text-neutral-900 dark:text-neutral-100">
                Characters
              </h2>
              <div class="flex items-center gap-2 ml-auto">
                <UButton
                  v-if="selectedCharacters.size > 0 && canEditCodec"
                  label="Remove Selected"
                  color="error"
                  variant="solid"
                  icon="i-lucide-trash"
                  @click="removeSelectedCharacters"
                />
                <UButton
                  label="Pick Glyph"
                  color="neutral"
                  variant="subtle"
                  icon="i-lucide-scan-search"
                  :disabled="!canEditCodec"
                  @click="onGlyphSelect"
                />
              </div>
            </div>

            <div class="flex items-center gap-2">
              <UInput
                v-model="newChar"
                placeholder="Type or paste characters…"
                class="flex-1"
                :disabled="!canEditCodec"
                @keydown.enter.prevent="addManual"
              />
              <UButton
                label="Add"
                color="primary"
                icon="i-lucide-plus"
                variant="solid"
                :disabled="!canEditCodec"
                @click="addManual"
              />
            </div>
          </div>

          <div class="flex-1 p-4 overflow-hidden">
            <div v-if="codec.length === 0" class="flex items-center justify-center h-full text-neutral-500">
              <div class="text-center">
                <UIcon name="i-lucide-type" class="w-12 h-12 mx-auto mb-4 text-neutral-400" />
                <p class="text-lg font-medium mb-2">
                  No characters yet
                </p>
                <p class="text-sm">
                  Add characters using the input above or pick glyphs
                </p>
              </div>
            </div>

            <div v-else class="h-full">
              <UContextMenu :items="contextMenuItems as any">
                <UTable
                  data-tour="codec-builder-table"
                  :data="characterData"
                  :columns="columns"
                  class="h-full"
                  :ui="{
                    base: 'table-fixed border-separate border-spacing-0',
                    thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                    tbody: '[&>tr]:last:[&>td]:border-b-0',
                    th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
                    td: 'border-b border-default py-2',
                    separator: 'h-0'
                  }"
                  @contextmenu="handleRowContextMenu"
                />
              </UContextMenu>
            </div>
          </div>
        </main>
      </div>
    </template>
  </UDashboardPanel>
</template>
