<script setup lang="ts">
import type { Dictionary, DictionarySummary } from '@/types/dictionary'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { toProjectToolkitSettings, useProjectToolkitPresets } from '@/composables/editor/use-project-toolkit-presets'
import { LazyEditorModalToolkitResourceEdit, LazyUiConfirmSlideover } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'

const highlightUnknownDictionaryTokens = defineModel<boolean>('highlightUnknownDictionaryTokens', { default: false })

const props = withDefaults(defineProps<{
  hasProjectDictionary?: boolean
}>(), {
  hasProjectDictionary: true
})

const toast = useToast()
const overlay = useOverlay()
const workspace = useWorkspaceStore()
const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()
const { patchProjectToolkitPresets } = useProjectToolkitPresets()
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const editModal = overlay.create(LazyEditorModalToolkitResourceEdit)

const selectedWorkspaceId = computed(() => workspace.selectedWorkspaceId as string)
const activeProjectId = computed(() => sessionStore.activeProjectId)
const canSetProjectPresets = computed(() => workspace.currentWorkspace?.capabilities?.canSetPresets ?? workspace.isCurrentUserOwner)
const canCreateDictionary = computed(() => workspace.canManageToolkit)
const canSwitchDictionary = computed(() => canSetProjectPresets.value || editorStore.projectToolkitSettings.allowDictionaryOverride)
const isSavingDefault = ref(false)
const isSwitchingDictionary = ref(false)

const { data: dictionaries, error: dictionariesError } = await useFetch<DictionarySummary[]>(
  () => `/api/workspaces/${selectedWorkspaceId.value}/dictionaries`,
  {
    key: computed(() => wsKey(selectedWorkspaceId.value, 'dictionaries', 'list')),
    default: () => []
  }
)

const dictionaryItems = computed(() => (dictionaries.value ?? []).map(dictionary => ({ label: dictionary.name, value: dictionary.id })))

const selectedDictionaryId = computed({
  get: () => editorStore.projectDictionaryId ?? '',
  set: (id: string | null | undefined) => {
    void selectDictionary(id || null)
  }
})
const selectedDictionary = computed(() => (dictionaries.value ?? []).find(dictionary => dictionary.id === selectedDictionaryId.value) ?? null)
const canEditSelectedDictionary = computed(() => Boolean(selectedDictionary.value?.capabilities?.canEdit))
const canClearDictionary = computed(() => Boolean(selectedDictionaryId.value) && canSwitchDictionary.value && !isSwitchingDictionary.value)

async function selectDictionary(dictionaryId: string | null) {
  const workspaceId = selectedWorkspaceId.value
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId || isSwitchingDictionary.value) return
  if (!canSwitchDictionary.value) {
    toast.add({ title: 'Dictionary switching is fixed for this project', color: 'warning' })
    return
  }

  isSwitchingDictionary.value = true
  try {
    if (!dictionaryId) {
      editorStore.clearProjectDictionary(projectId)
      return
    }
    const dictionary = await $fetch<Dictionary>(`/api/workspaces/${workspaceId}/dictionaries/${dictionaryId}`)
    editorStore.setProjectDictionary({
      id: dictionary.id,
      forms: [],
      caseSensitive: dictionary.caseSensitive,
      unicodeNormalization: dictionary.unicodeNormalization,
      canEdit: Boolean(dictionary.capabilities?.canEdit),
      locked: Boolean(dictionary.locked)
    }, projectId)
  } catch (error: unknown) {
    toast.add({ title: 'Could not load dictionary', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    isSwitchingDictionary.value = false
  }
}

async function saveDictionaryDefault() {
  const workspaceId = selectedWorkspaceId.value
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId || !canSetProjectPresets.value) return

  isSavingDefault.value = true
  try {
    const updated = await patchProjectToolkitPresets(workspaceId, projectId, {
      dictionaryId: editorStore.projectDictionaryId ?? null
    })
    editorStore.setProjectToolkitSettings(toProjectToolkitSettings(updated), projectId)
    toast.add({ title: 'Project dictionary default updated', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Could not save dictionary default', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    isSavingDefault.value = false
  }
}

async function confirmSaveDictionaryDefault() {
  if (!canSetProjectPresets.value || isSavingDefault.value) return
  const instance = confirmSlideover.open({
    title: 'Set Default Dictionary',
    message: 'This changes the project default dictionary and affects all users working on this project.',
    confirmLabel: 'Set Default',
    confirmIcon: 'i-lucide-save',
    confirmColor: 'warning'
  })
  const confirmed = await instance.result
  if (!confirmed) return
  await saveDictionaryDefault()
}

async function openDictionaryEditModal() {
  const dictionary = selectedDictionary.value
  const workspaceId = selectedWorkspaceId.value
  if (!dictionary || !workspaceId || !canEditSelectedDictionary.value) return

  const reloadDictionary = async () => {
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', 'list'))
    if (selectedDictionaryId.value === dictionary.id) {
      await selectDictionary(dictionary.id)
    }
  }

  const instance = editModal.open({
    title: `Edit Dictionary · ${dictionary.name}`,
    src: `/dictionaries/${dictionary.id}?embedded=toolkit-editor`,
    onSaved: reloadDictionary
  })
  await instance.result
  await reloadDictionary()
}

async function openDictionaryCreateModal() {
  const workspaceId = selectedWorkspaceId.value
  if (!workspaceId || !canCreateDictionary.value) return

  const reloadDictionaries = async () => {
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', 'list'))
  }

  const instance = editModal.open({
    title: 'Create Dictionary',
    src: '/dictionaries/new?embedded=toolkit-editor',
    onSaved: reloadDictionaries
  })
  await instance.result
  await reloadDictionaries()
}

const actionItems = computed<DropdownMenuItem[][]>(() => {
  const items: DropdownMenuItem[] = []
  if (canCreateDictionary.value) {
    items.push({
      label: 'Create',
      icon: 'i-lucide-plus',
      onSelect: openDictionaryCreateModal
    })
  }
  if (canSetProjectPresets.value) {
    items.push({
      label: 'Set as default',
      icon: 'i-lucide-save',
      disabled: isSavingDefault.value || isSwitchingDictionary.value,
      onSelect: confirmSaveDictionaryDefault
    })
  }
  if (canEditSelectedDictionary.value) {
    items.push({
      label: 'Edit',
      icon: 'i-lucide-pencil',
      onSelect: openDictionaryEditModal
    })
  }
  return items.length > 0 ? [items] : []
})
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <UFormField label="Active Dictionary" hint="Used for editor token checks and suggestions">
      <div class="flex items-center gap-2">
        <USelectMenu
          v-model="selectedDictionaryId"
          class="min-w-0 flex-1"
          :items="dictionaryItems"
          value-key="value"
          :clear="canClearDictionary"
          placeholder="Choose a dictionary"
          :search-input="{ placeholder: 'Search dictionaries...' }"
          :disabled="!!dictionariesError || dictionaryItems.length === 0 || !canSwitchDictionary || isSwitchingDictionary"
          size="sm"
        />
        <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
          <UButton
            size="sm"
            variant="ghost"
            color="neutral"
            icon="i-lucide-more-vertical"
            aria-label="Dictionary actions"
            :loading="isSavingDefault"
          />
        </UDropdownMenu>
      </div>
      <p v-if="!canSwitchDictionary" class="mt-1 text-xs text-muted">
        This project uses a fixed dictionary.
      </p>
    </UFormField>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Highlight Unknown Dictionary Tokens</span>
        <span class="text-xs text-muted">
          {{
            props.hasProjectDictionary
              ? 'Flags GT tokens that are not present in the project dictionary.'
              : 'Assign a dictionary to this project to enable token validation.'
          }}
        </span>
      </div>
      <USwitch v-model="highlightUnknownDictionaryTokens" :disabled="!props.hasProjectDictionary" />
    </div>
  </div>
</template>
