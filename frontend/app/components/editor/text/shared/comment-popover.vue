<script setup lang="ts">
import { normalizeFullTextComment } from './full-text-comments'

const props = withDefaults(defineProps<{
  textlineId: string
  comment?: string
  readOnly?: boolean
  iconOnly?: boolean
  emptyIcon?: string
}>(), {
  comment: '',
  readOnly: false,
  iconOnly: false,
  emptyIcon: 'i-lucide-message-square'
})
const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ save: [comment: string] }>()
const normalizedComment = computed(() => normalizeFullTextComment(props.comment))
const hasComment = computed(() => normalizedComment.value.length > 0)
const draft = ref(props.comment)
const actionLabel = computed(() => props.readOnly ? 'View comment' : hasComment.value ? 'Edit comment' : 'Add comment')

watch(open, (isOpen) => {
  if (isOpen) draft.value = props.comment
})
watch(() => props.textlineId, () => {
  open.value = false
  draft.value = props.comment
})

function save(): void {
  if (props.readOnly) return
  emit('save', draft.value)
  open.value = false
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.isComposing || event.key !== 'Enter' || (!event.metaKey && !event.ctrlKey)) return
  event.preventDefault()
  event.stopPropagation()
  save()
}
</script>

<template>
  <UPopover v-model:open="open" :content="{ side: 'top', align: 'start', sideOffset: 8 }">
    <UTooltip :text="normalizedComment || actionLabel">
      <UBadge
        v-if="hasComment"
        as="button"
        type="button"
        color="info"
        variant="subtle"
        class="shrink-0 cursor-pointer focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        aria-label="Has comment"
        :title="actionLabel"
        @click.stop
      >
        <Icon name="i-lucide-message-square" class="h-3 w-3 mr-1" />
        Comment
      </UBadge>
      <UButton
        v-else
        type="button"
        color="neutral"
        variant="ghost"
        size="xs"
        :icon="props.emptyIcon"
        :aria-label="actionLabel"
        @click.stop
      >
        <template v-if="!props.iconOnly" #default>
          {{ actionLabel }}
        </template>
      </UButton>
    </UTooltip>

    <template #content>
      <div class="max-h-[min(75vh,36rem)] w-80 max-w-[calc(100vw-2rem)] space-y-3 overflow-y-auto p-3" @click.stop>
        <div class="flex min-w-0 items-center justify-between gap-2">
          <p class="text-sm font-medium text-highlighted">
            {{ actionLabel }}
          </p>
          <span class="truncate font-mono text-xs text-muted">{{ props.textlineId }}</span>
        </div>
        <p v-if="props.readOnly" class="whitespace-pre-wrap break-words text-sm text-default">
          {{ normalizedComment || 'No comment' }}
        </p>
        <UTextarea
          v-else
          v-model="draft"
          autofocus
          autoresize
          :rows="4"
          placeholder="Enter comment..."
          aria-label="Text-line comment"
          @keydown="handleKeydown"
        />
        <div class="flex items-center justify-end gap-2">
          <UButton
            type="button"
            color="neutral"
            variant="ghost"
            size="xs"
            @click.stop="open = false"
          >
            {{ props.readOnly ? 'Close' : 'Cancel' }}
          </UButton>
          <UButton
            v-if="!props.readOnly"
            type="button"
            color="primary"
            size="xs"
            @click.stop="save"
          >
            Save
          </UButton>
        </div>
        <slot name="details" />
      </div>
    </template>
  </UPopover>
</template>
