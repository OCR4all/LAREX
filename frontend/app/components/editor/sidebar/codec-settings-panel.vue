<script setup lang="ts">
import type { CodecSummary } from '@/types/codec'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { toProjectToolkitSettings, useProjectToolkitPresets } from '@/composables/editor/use-project-toolkit-presets'
import { LazyEditorModalToolkitResourceEdit, LazyUiConfirmSlideover } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'

const highlightUnknownCodecChars = defineModel<boolean>('highlightUnknownCodecChars', { default: false })
const includeWhitespaceInCodecHighlight = defineModel<boolean>('includeWhitespaceInCodecHighlight', { default: false })

const props = withDefaults(defineProps<{
  hasProjectCodec?: boolean
}>(), {
  hasProjectCodec: true
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
const canCreateCodec = computed(() => workspace.canManageToolkit)
const canSwitchCodec = computed(() => canSetProjectPresets.value || editorStore.projectToolkitSettings.allowCodecOverride)
const isSavingDefault = ref(false)
const isSwitchingCodec = ref(false)

const { data: codecs, error: codecsError } = await useFetch<CodecSummary[]>(
  () => `/api/workspaces/${selectedWorkspaceId.value}/codecs`,
  {
    key: computed(() => wsKey(selectedWorkspaceId.value, 'codecs', 'list')),
    default: () => []
  }
)

const codecItems = computed(() => (codecs.value ?? []).map(codec => ({ label: codec.name, value: codec.id })))

const selectedCodecId = computed({
  get: () => editorStore.projectCodecId ?? '',
  set: (id: string | null | undefined) => {
    void selectCodec(id || null)
  }
})

const selectedCodec = computed(() => (codecs.value ?? []).find(codec => codec.id === selectedCodecId.value) ?? null)
const canEditSelectedCodec = computed(() => Boolean(selectedCodec.value?.capabilities?.canEdit))
const canClearCodec = computed(() => Boolean(selectedCodecId.value) && canSwitchCodec.value && !isSwitchingCodec.value)

async function selectCodec(codecId: string | null) {
  const workspaceId = selectedWorkspaceId.value
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId || isSwitchingCodec.value) return
  if (!canSwitchCodec.value) {
    toast.add({ title: 'Codec switching is fixed for this project', color: 'warning' })
    return
  }

  isSwitchingCodec.value = true
  try {
    if (!codecId) {
      editorStore.clearProjectCodec(projectId)
      return
    }
    const codec = await $fetch<{ id: string, codec: string[] }>(`/api/workspaces/${workspaceId}/codecs/${codecId}`)
    editorStore.setProjectCodec(codec.id, codec.codec ?? [], projectId)
  } catch (error: unknown) {
    toast.add({ title: 'Could not load codec', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    isSwitchingCodec.value = false
  }
}

async function saveCodecDefault() {
  const workspaceId = selectedWorkspaceId.value
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId || !canSetProjectPresets.value) return

  isSavingDefault.value = true
  try {
    const updated = await patchProjectToolkitPresets(workspaceId, projectId, {
      codecId: editorStore.projectCodecId ?? null
    })
    editorStore.setProjectToolkitSettings(toProjectToolkitSettings(updated), projectId)
    toast.add({ title: 'Project codec default updated', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Could not save codec default', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    isSavingDefault.value = false
  }
}

async function confirmSaveCodecDefault() {
  if (!canSetProjectPresets.value || isSavingDefault.value) return
  const instance = confirmSlideover.open({
    title: 'Set Default Codec',
    message: 'This changes the project default codec and affects all users working on this project.',
    confirmLabel: 'Set Default',
    confirmIcon: 'i-lucide-save',
    confirmColor: 'warning'
  })
  const confirmed = await instance.result
  if (!confirmed) return
  await saveCodecDefault()
}

async function openCodecEditModal() {
  const codec = selectedCodec.value
  const workspaceId = selectedWorkspaceId.value
  if (!codec || !workspaceId || !canEditSelectedCodec.value) return

  const reloadCodec = async () => {
    await refreshNuxtData(wsKey(workspaceId, 'codecs', 'list'))
    if (selectedCodecId.value === codec.id) {
      await selectCodec(codec.id)
    }
  }

  const instance = editModal.open({
    title: `Edit Codec · ${codec.name}`,
    src: `/codecs/${codec.id}?embedded=toolkit-editor`,
    onSaved: reloadCodec
  })
  await instance.result
  await reloadCodec()
}

async function openCodecCreateModal() {
  const workspaceId = selectedWorkspaceId.value
  if (!workspaceId || !canCreateCodec.value) return

  const reloadCodecs = async () => {
    await refreshNuxtData(wsKey(workspaceId, 'codecs', 'list'))
  }

  const instance = editModal.open({
    title: 'Create Codec',
    src: '/codecs/new?embedded=toolkit-editor',
    onSaved: reloadCodecs
  })
  await instance.result
  await reloadCodecs()
}

const actionItems = computed<DropdownMenuItem[][]>(() => {
  const items: DropdownMenuItem[] = []
  if (canCreateCodec.value) {
    items.push({
      label: 'Create',
      icon: 'i-lucide-plus',
      onSelect: openCodecCreateModal
    })
  }
  if (canSetProjectPresets.value) {
    items.push({
      label: 'Set as default',
      icon: 'i-lucide-save',
      disabled: isSavingDefault.value || isSwitchingCodec.value,
      onSelect: confirmSaveCodecDefault
    })
  }
  if (canEditSelectedCodec.value) {
    items.push({
      label: 'Edit',
      icon: 'i-lucide-pencil',
      onSelect: openCodecEditModal
    })
  }
  return items.length > 0 ? [items] : []
})
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <UFormField label="Active Codec" hint="Used for editor character checks and quick additions">
      <div class="flex items-center gap-2">
        <USelectMenu
          v-model="selectedCodecId"
          class="min-w-0 flex-1"
          :items="codecItems"
          value-key="value"
          :clear="canClearCodec"
          placeholder="Choose a codec"
          :search-input="{ placeholder: 'Search codecs...' }"
          :disabled="!!codecsError || codecItems.length === 0 || !canSwitchCodec || isSwitchingCodec"
          size="sm"
        />
        <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
          <UButton
            size="sm"
            variant="ghost"
            color="neutral"
            icon="i-lucide-more-vertical"
            aria-label="Codec actions"
            :loading="isSavingDefault"
          />
        </UDropdownMenu>
      </div>
      <p v-if="!canSwitchCodec" class="mt-1 text-xs text-muted">
        This project uses a fixed codec.
      </p>
    </UFormField>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Highlight Unknown Codec Characters</span>
        <span class="text-xs text-muted">
          {{
            props.hasProjectCodec
              ? 'Highlights characters not present in the project codec.'
              : 'Assign a codec to this project to enable unknown-character highlighting.'
          }}
        </span>
      </div>
      <USwitch v-model="highlightUnknownCodecChars" :disabled="!props.hasProjectCodec" />
    </div>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Treat Whitespace As Codec Characters</span>
        <span class="text-xs text-muted">
          When disabled, spaces/tabs/newlines are ignored in editor codec highlighting.
        </span>
      </div>
      <USwitch
        v-model="includeWhitespaceInCodecHighlight"
        :disabled="!props.hasProjectCodec || !highlightUnknownCodecChars"
      />
    </div>
  </div>
</template>
