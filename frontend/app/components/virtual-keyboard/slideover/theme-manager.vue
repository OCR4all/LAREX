<script setup lang="ts">
import { LazyUiConfirmModal } from '#components'
import type { BoardTheme } from '~/types/virtual-keyboard'

const overlay = useOverlay()
const confirmModal = overlay.create(LazyUiConfirmModal)

const props = defineProps<{
  themes: BoardTheme[]
  activeTheme: BoardTheme
  onPreview?: (theme: BoardTheme) => void
}>()

const emit = defineEmits<{
  close: [{ themes: BoardTheme[], activeTheme: BoardTheme } | null]
}>()

const toast = useToast()
const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const themesKey = computed(() => wsKey(selectedWorkspace.value, 'board-themes', 'list'))

const createThemeApi = ({ id, _key, ...theme }: DraftTheme) => $fetch<BoardTheme>(`/api/workspaces/${selectedWorkspace.value}/board-themes`, { method: 'POST', body: theme })
const updateThemeApi = (id: string, { _key, ...theme }: DraftTheme) => $fetch<BoardTheme>(`/api/workspaces/${selectedWorkspace.value}/board-themes/${id}`, { method: 'PUT', body: theme })
const deleteThemeApi = (id: string) => $fetch(`/api/workspaces/${selectedWorkspace.value}/board-themes/${id}`, { method: 'DELETE' })

type DraftTheme = BoardTheme & { _key: string }

const toDraft = (t: BoardTheme): DraftTheme => ({ ...t, _key: t.id || `new-${Math.random().toString(36).slice(2)}` })

const draftThemes = ref<DraftTheme[]>(props.themes.map(toDraft))
const selectedKey = ref(draftThemes.value.find(t => t.id === props.activeTheme?.id)?._key || draftThemes.value[0]?._key || '')
const snapshotIds = ref<Set<string>>(new Set(props.themes.map(t => t.id).filter(Boolean) as string[]))
const hasSaved = ref(false)
const saving = ref(false)

const selectedTheme = computed(() => draftThemes.value.find(t => t._key === selectedKey.value))

const formState = reactive({
  name: '',
  bgColor: '#1e293b',
  keyBgColor: '#334155',
  keyTextColor: '#e2e8f0'
})

watch(selectedKey, () => {
  const theme = selectedTheme.value
  if (theme) {
    formState.name = theme.name
    formState.bgColor = theme.bgStyle || '#1e293b'
    formState.keyBgColor = theme.keyBgStyle || '#334155'
    formState.keyTextColor = theme.keyTextStyle || '#e2e8f0'
  }
}, { immediate: true })

watch(formState, () => {
  const theme = selectedTheme.value
  if (!theme) return
  theme.name = formState.name
  theme.bgStyle = formState.bgColor
  theme.keyBgStyle = formState.keyBgColor
  theme.keyTextStyle = formState.keyTextColor
  props.onPreview?.(theme)
}, { deep: true })

const selectTheme = (theme: DraftTheme) => {
  selectedKey.value = theme._key
  props.onPreview?.(theme)
}

const createNewTheme = () => {
  const newTheme: DraftTheme = {
    _key: `new-${Math.random().toString(36).slice(2)}`,
    id: undefined,
    name: `Theme ${Math.random().toString(36).slice(2, 7)}`,
    bgClass: '', borderClass: 'border-neutral-700', gridLineClass: 'border-white/10 text-white/50',
    keyBgClass: '', keyTextClass: '', previewClass: '',
    bgStyle: '#1e293b', keyBgStyle: '#334155', keyTextStyle: '#e2e8f0'
  }
  draftThemes.value.push(newTheme)
  selectTheme(newTheme)
}

const deleteSelectedTheme = async () => {
  const theme = selectedTheme.value
  const instance = confirmModal.open({
    title: 'Delete Theme?',
    description: `Are you sure you want to delete "${theme?.name || 'this theme'}"?`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const idx = draftThemes.value.findIndex(t => t._key === selectedKey.value)
  if (idx === -1) return
  draftThemes.value.splice(idx, 1)
  if (draftThemes.value.length > 0) {
    selectTheme(draftThemes.value[Math.max(0, idx - 1)]!)
  }
}

const saveThemes = async () => {
  if (saving.value) return
  saving.value = true
  try {
    const currentIds = new Set(draftThemes.value.map(t => t.id).filter(Boolean) as string[])
    for (const id of snapshotIds.value) {
      if (!currentIds.has(id)) await deleteThemeApi(id)
    }

    const savedThemes: DraftTheme[] = []
    for (const theme of draftThemes.value) {
      const saved = theme.id
        ? await updateThemeApi(theme.id, theme)
        : await createThemeApi(theme)
      savedThemes.push({ ...saved, _key: theme._key })
    }

    draftThemes.value = savedThemes
    snapshotIds.value = new Set(savedThemes.map(t => t.id).filter(Boolean) as string[])
    hasSaved.value = true

    toast.add({ title: 'Themes saved', color: 'success' })
    refreshNuxtData(themesKey.value)
  } catch (e: any) {
    toast.add({ title: e?.data?.message || 'Error saving themes', color: 'error' })
  } finally {
    saving.value = false
  }
}

const close = () => {
  if (hasSaved.value) {
    const active = selectedTheme.value
    const cleanThemes = draftThemes.value.map(({ _key, ...t }) => t as BoardTheme)
    const cleanActive = active ? (({ _key, ...t }) => t as BoardTheme)(active) : cleanThemes[0]
    emit('close', { themes: cleanThemes, activeTheme: cleanActive! })
  } else {
    emit('close', null)
  }
}
</script>

<template>
  <USlideover
    :modal="false"
    side="left"
    title="Board Theme Manager"
    :close="{ onClick: close }"
  >
    <template #body>
      <div class="flex flex-col gap-6">
        <div>
          <label class="text-xs font-bold text-muted mb-2 block">Board Themes</label>
          <div class="flex flex-wrap gap-2 max-h-[150px] overflow-y-auto p-2 bg-elevated rounded-sm border border-default">
            <button
              v-for="theme in draftThemes"
              :key="theme._key"
              class="w-10 h-10 rounded-sm border-2 shadow-sm flex items-center justify-center"
              :class="selectedKey === theme._key ? 'ring-2 ring-primary border-transparent' : 'border-muted'"
              :style="{ background: theme.bgStyle }"
              :title="theme.name"
              @click="selectTheme(theme)"
            >
              <div class="w-4 h-4 rounded-sm shadow-sm" :style="{ background: theme.keyBgStyle }" />
            </button>
            <button
              class="w-10 h-10 rounded-sm border-2 border-dashed border-muted hover:border-primary text-muted hover:text-primary flex items-center justify-center"
              @click="createNewTheme"
            >
              <span class="text-xl font-bold">+</span>
            </button>
          </div>
        </div>

        <div v-if="selectedTheme" class="bg-elevated p-4 rounded-sm border border-default">
          <label class="text-xs font-bold text-muted mb-2 block">Edit Theme</label>
          <div class="flex flex-col gap-3">
            <UInput v-model="formState.name" placeholder="Theme Name" />
            <UFormField label="Board BG">
              <div class="flex gap-2 items-center">
                <input v-model="formState.bgColor" type="color" class="h-8 w-10 cursor-pointer rounded">
                <span class="text-xs text-muted font-mono">{{ formState.bgColor }}</span>
              </div>
            </UFormField>
            <div class="grid grid-cols-2 gap-3">
              <UFormField label="Key BG">
                <div class="flex gap-2 items-center">
                  <input v-model="formState.keyBgColor" type="color" class="h-8 w-10 cursor-pointer rounded">
                  <span class="text-xs text-muted font-mono">{{ formState.keyBgColor }}</span>
                </div>
              </UFormField>
              <UFormField label="Key Text">
                <div class="flex gap-2 items-center">
                  <input v-model="formState.keyTextColor" type="color" class="h-8 w-10 cursor-pointer rounded">
                  <span class="text-xs text-muted font-mono">{{ formState.keyTextColor }}</span>
                </div>
              </UFormField>
            </div>
            <UButton
              color="error"
              variant="soft"
              block
              @click="deleteSelectedTheme"
            >
              Delete Theme
            </UButton>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="close">
          Cancel
        </UButton>
        <UButton
          :loading="saving"
          icon="i-lucide-save"
          variant="solid"
          @click="saveThemes"
        >
          Save
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
