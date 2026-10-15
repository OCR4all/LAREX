<script setup lang="ts">
import type { PageData } from '@/stores/editor/types'
import type { Page } from '@/types/project-page'
import type { TagNode } from '@/types/tag-set'

const props = defineProps<{
  page: PageData
  workspaceId: string
  tagSetId?: string | null
  canEdit: boolean
}>()

const emit = defineEmits<{ saved: [projectId: string, page: Page] }>()
const toast = useToast()
const open = ref(false)
const draft = ref<string[]>([])
const tagNodes = ref<TagNode[]>([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const inputTags = useTemplateRef('inputTags')

const displayTags = computed(() => {
  const resolved = new Map((props.page.resolvedTags ?? []).map(tag => [tag.id, tag]))
  return (props.page.tags ?? []).map(id => ({
    id,
    label: resolved.get(id)?.label || id,
    color: resolved.get(id)?.color || null
  }))
})
const visibleTags = computed(() => displayTags.value.slice(0, 3))
const overflowTags = computed(() => displayTags.value.slice(3))

const tagItems = computed(() => {
  const items: Array<{ value: string, label: string, description?: string, color: string | null }> = []
  function flatten(nodes: TagNode[], ancestors: string[] = []) {
    for (const node of nodes) {
      items.push({
        value: node.id,
        label: node.title,
        description: ancestors.length ? [...ancestors, node.title].join(' > ') : undefined,
        color: node.color
      })
      flatten(node.children ?? [], [...ancestors, node.title])
    }
  }
  flatten(tagNodes.value)
  // Keep previously assigned tags available even if the tag set has changed.
  for (const tag of displayTags.value) {
    if (!items.some(item => item.value === tag.id)) items.push({ value: tag.id, label: tag.label, color: tag.color })
  }
  return items
})

function tagColor(color: string | null) {
  return color && isValidHex(color) ? (color.startsWith('#') ? color : `#${color}`) : undefined
}

async function loadTags() {
  loadError.value = null
  tagNodes.value = []
  if (!props.tagSetId) return

  loading.value = true
  try {
    const tagSet = await $fetch<{ tags: TagNode[] }>(`/api/workspaces/${props.workspaceId}/tag-sets/${props.tagSetId}`)
    tagNodes.value = tagSet.tags
  } catch (error) {
    loadError.value = extractApiErrorMessage(error, 'Could not load the project tag set.')
  } finally {
    loading.value = false
  }
}

watch(open, (isOpen) => {
  if (!isOpen) return
  draft.value = [...(props.page.tags ?? [])]
  saveError.value = null
  void loadTags()
})

watch(() => props.canEdit, (canEdit) => {
  if (!canEdit) open.value = false
})

async function save() {
  if (!props.canEdit || loading.value || saving.value || loadError.value) return
  saving.value = true
  saveError.value = null
  try {
    const pendingTag = inputTags.value?.inputRef?.value ?? ''
    const page = await $fetch<Page>(`/api/projects/${props.page.projectId}/pages/${props.page.id}`, {
      method: 'PUT',
      body: { tags: [...new Set([...draft.value, pendingTag].map(tag => tag.trim()).filter(Boolean))] }
    })
    emit('saved', props.page.projectId, page)
    clearNuxtData(wsKey(props.workspaceId, 'projects', props.page.projectId, 'pages'))
    open.value = false
    toast.add({ title: 'Page tags updated', color: 'success', icon: 'i-lucide-check' })
  } catch (error) {
    saveError.value = extractApiErrorMessage(error, 'Could not save the page tags.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="ml-auto flex shrink-0 items-center gap-1 min-w-0" aria-label="Page tags">
    <UTooltip
      v-for="tag in visibleTags"
      :key="tag.id"
      :text="tag.label"
      :delay-duration="300"
      :content="{ side: 'top' }"
    >
      <UBadge
        color="neutral"
        variant="subtle"
        size="xs"
        class="h-5 max-w-28 gap-1 px-1.5 py-0 text-[11px]"
      >
        <UIcon name="i-lucide-circle" class="size-1.5 shrink-0 fill-current text-dimmed" :style="{ color: tagColor(tag.color) }" />
        <span class="truncate">{{ tag.label }}</span>
      </UBadge>
    </UTooltip>

    <UTooltip
      v-if="overflowTags.length"
      :delay-duration="200"
      :content="{ side: 'top', align: 'end' }"
      :ui="{ content: 'h-auto max-w-80 p-2' }"
    >
      <UBadge
        as="button"
        type="button"
        color="neutral"
        variant="soft"
        size="xs"
        class="h-5 px-1.5 py-0 text-[11px] tabular-nums"
        :aria-label="`${overflowTags.length} more page tags`"
      >
        +{{ overflowTags.length }}
      </UBadge>
      <template #content>
        <div class="flex max-h-60 flex-wrap gap-1 overflow-y-auto">
          <UBadge
            v-for="tag in overflowTags"
            :key="tag.id"
            color="neutral"
            variant="subtle"
            size="md"
            class="gap-1.5 rounded-sm py-0.5 whitespace-normal break-words"
          >
            <UIcon name="i-lucide-circle" class="size-2 shrink-0 fill-current text-dimmed" :style="{ color: tagColor(tag.color) }" />
            {{ tag.label }}
          </UBadge>
        </div>
      </template>
    </UTooltip>

    <UPopover
      v-if="canEdit"
      v-model:open="open"
      :dismissible="!saving"
      :content="{ side: 'top', align: 'end', sideOffset: 8 }"
    >
      <UButton
        icon="i-lucide-plus"
        color="neutral"
        variant="ghost"
        size="xs"
        class="size-5 justify-center p-0"
        :ui="{ leadingIcon: 'size-3.5' }"
        aria-label="Edit page tags"
      />
      <template #content>
        <div class="w-80 max-w-[calc(100vw-2rem)] space-y-3 p-3">
          <UFormField label="Page tags" :help="tagSetId ? 'Select all tags for this page.' : 'Press Enter to add a tag.'">
            <UButton
              v-if="loading"
              label="Loading tags…"
              icon="i-lucide-tags"
              color="neutral"
              variant="ghost"
              size="sm"
              loading
              disabled
            />
            <UAlert
              v-else-if="loadError"
              color="error"
              variant="subtle"
              :description="loadError"
              :actions="[{ label: 'Retry', color: 'error', variant: 'ghost', onClick: loadTags }]"
            />
            <USelectMenu
              v-else-if="tagSetId"
              v-model="draft"
              :items="tagItems"
              value-key="value"
              multiple
              :filter-fields="['label', 'description']"
              :search-input="{ placeholder: 'Search tags…' }"
              :disabled="saving"
              placeholder="Select tags…"
              size="sm"
              class="w-full"
            >
              <template #item-leading="{ item }">
                <UIcon name="i-lucide-circle" class="size-2 shrink-0 fill-current text-dimmed" :style="{ color: tagColor(item.color) }" />
              </template>
            </USelectMenu>
            <UInputTags
              v-else
              ref="inputTags"
              v-model="draft"
              placeholder="Add tags…"
              size="sm"
              :disabled="saving"
              add-on-tab
              class="w-full"
            />
          </UFormField>

          <div v-if="tagSetId && !loading && !loadError && draft.length" class="flex max-h-40 flex-wrap gap-1 overflow-y-auto">
            <UBadge
              v-for="id in draft"
              :key="id"
              color="neutral"
              variant="subtle"
              size="md"
              class="max-w-full gap-1.5 rounded-sm py-0.5"
            >
              <span class="truncate">{{ tagItems.find(item => item.value === id)?.label || id }}</span>
              <UButton
                icon="i-lucide-x"
                color="neutral"
                variant="ghost"
                size="xs"
                class="size-3.5 shrink-0 p-0"
                :ui="{ leadingIcon: 'size-3' }"
                :aria-label="`Remove ${tagItems.find(item => item.value === id)?.label || id}`"
                :disabled="saving"
                @click="draft = draft.filter(tag => tag !== id)"
              />
            </UBadge>
          </div>

          <UAlert
            v-if="saveError"
            color="error"
            variant="subtle"
            :description="saveError"
          />
          <div class="flex justify-end gap-1.5">
            <UButton
              label="Cancel"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="saving"
              @click="open = false"
            />
            <UButton
              label="Save"
              size="xs"
              :loading="saving"
              :disabled="loading || !!loadError"
              @click="save"
            />
          </div>
        </div>
      </template>
    </UPopover>
  </div>
</template>
