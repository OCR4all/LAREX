<script setup lang="ts">
import { normalizeFullTextComment } from './full-text-comments'

const props = withDefaults(defineProps<{
  textlineId: string
  comment?: string
  readOnly?: boolean
  iconOnly?: boolean
  emptyIcon?: string
  indicatorLabel?: string
  emptyLabel?: string
  inputLabel?: string
}>(), {
  comment: '',
  readOnly: false,
  iconOnly: false,
  emptyIcon: 'i-lucide-message-square',
  indicatorLabel: 'Has comment',
  emptyLabel: 'Add comment',
  inputLabel: 'Text-line comment'
})
const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{ save: [comment: string] }>()
const normalizedComment = computed(() => normalizeFullTextComment(props.comment))
const hasComment = computed(() => normalizedComment.value.length > 0)
const draft = ref(props.comment)
const actionLabel = computed(() => props.readOnly ? 'View comment' : hasComment.value ? 'Edit comment' : props.emptyLabel)

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
      <UButton
        type="button"
        color="neutral"
        variant="ghost"
        size="xs"
        class="shrink-0"
        :class="hasComment ? 'text-info' : ''"
        :icon="hasComment ? 'i-lucide-message-square-text' : props.emptyIcon"
        :aria-label="hasComment ? props.indicatorLabel : actionLabel"
        :title="actionLabel"
        @click.stop
      >
        <template v-if="!props.iconOnly" #default>
          {{ hasComment ? 'Comment' : actionLabel }}
        </template>
      </UButton>
    </UTooltip>

    <template #content>
      <div class="max-h-[min(75vh,36rem)] w-80 max-w-[calc(100vw-2rem)] space-y-3 overflow-y-auto overscroll-contain p-3" @click.stop>
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
          :aria-label="props.inputLabel"
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
