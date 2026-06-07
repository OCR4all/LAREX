<script setup lang="ts">
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

type DeleteSlideoverItem = {
  id?: string
  label: string
}

const props = withDefaults(defineProps<{
  name: string
  entityType: string
  title?: string
  warningMessage?: string
  warningDetails?: string[]
  items?: DeleteSlideoverItem[]
  confirmButtonLabel?: string
  loading?: boolean
}>(), {
  loading: false
})

useBlockEditorCanvasInteractions()

const emit = defineEmits<{
  close: [confirmed: boolean]
}>()

const targetCount = computed(() => {
  const match = props.name.trim().match(/^(\d+)\s+/)
  return match ? Number.parseInt(match[1]!, 10) : 1
})

function pluralizeEntityType(entityType: string) {
  const parts = entityType.split(' ')
  const last = parts.at(-1)
  if (!last) return entityType

  const pluralLast = last.endsWith('y')
    ? `${last.slice(0, -1)}ies`
    : last.endsWith('s')
      ? `${last}es`
      : `${last}s`

  return [...parts.slice(0, -1), pluralLast].join(' ')
}

const computedEntityLabel = computed(() =>
  targetCount.value === 1 ? props.entityType : pluralizeEntityType(props.entityType)
)
const computedTitle = computed(() => props.title || `Delete ${computedEntityLabel.value}`)
const computedButtonLabel = computed(() => props.confirmButtonLabel || `Delete ${computedEntityLabel.value}`)
const computedWarningMessage = computed(() =>
  props.warningMessage || `Are you sure you want to delete ${props.name}? This action cannot be undone.`
)

const itemLabelCollator = new Intl.Collator(undefined, { sensitivity: 'base' })
const targetItems = computed<DeleteSlideoverItem[]>(() => {
  const items = props.items?.length ? props.items : [{ label: props.name }]
  return [...items].sort((a, b) => itemLabelCollator.compare(a.label, b.label))
})
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #header>
      <div class="flex min-w-0 items-center gap-2 pr-8">
        <UIcon name="i-lucide-trash" class="size-5 shrink-0 text-error" />
        <h2 class="text-highlighted font-semibold">
          {{ computedTitle }}
        </h2>
      </div>
    </template>

    <template #body>
      <div class="space-y-4">
        <slot name="warning">
          <div class="flex items-start gap-3 p-4 bg-error/10 border border-error/30 rounded-sm">
            <UIcon name="i-lucide-alert-triangle" class="text-error mt-0.5 shrink-0" />
            <div>
              <p class="font-medium text-error">
                {{ computedWarningMessage }}
              </p>
              <p class="text-sm text-error/90 mt-1">
                This action is permanent and cannot be recovered.
              </p>
              <ul v-if="warningDetails?.length" class="mt-2 list-disc list-inside space-y-1 text-sm text-error/90">
                <li v-for="detail in warningDetails" :key="detail">
                  {{ detail }}
                </li>
              </ul>
            </div>
          </div>
        </slot>

        <slot name="details">
          <div class="grid grid-cols-[repeat(auto-fill,minmax(8rem,1fr))] gap-2">
            <div
              v-for="item in targetItems"
              :key="item.id ?? item.label"
              class="flex min-w-0 items-center gap-1.5 rounded-sm border border-neutral-200 bg-default px-2.5 py-1.5 text-xs dark:border-neutral-700"
            >
              <UIcon name="i-lucide-file-text" class="size-3.5 shrink-0 text-muted" />
              <span class="min-w-0 truncate">{{ item.label }}</span>
            </div>
          </div>
        </slot>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-center gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="loading"
          @click="emit('close', false)"
        >
          Cancel
        </UButton>
        <UButton
          color="error"
          variant="solid"
          icon="i-lucide-trash"
          :disabled="loading"
          :loading="loading"
          @click="emit('close', true)"
        >
          {{ computedButtonLabel }}
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
