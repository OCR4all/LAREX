<script setup lang="ts">
import type { DateTimeInput } from '@/composables/use-local-date-time'

const props = defineProps<{
  createdAt?: DateTimeInput
  updatedAt?: DateTimeInput
  value: DateTimeInput
}>()

const { absoluteLabel, isoDate, label, shouldUseRelativeLabel } = useLocalDateTime(() => props.value)
const createdLabel = computed(() => formatLocalDateTime(props.createdAt) || '—')
const updatedLabel = computed(() => formatLocalDateTime(props.updatedAt) || '—')
</script>

<template>
  <UPopover
    v-if="isoDate && shouldUseRelativeLabel"
    mode="hover"
    :open-delay="500"
    :content="{ side: 'top', align: 'center', sideOffset: 6 }"
  >
    <template #default>
      <time :datetime="isoDate" class="cursor-default">
        {{ label }}
      </time>
    </template>
    <template #content>
      <div class="w-64 p-3 space-y-2">
        <div>
          <p class="text-xs text-muted">
            Created
          </p>
          <p class="text-sm font-medium text-highlighted">
            {{ createdLabel }}
          </p>
        </div>
        <div>
          <p class="text-xs text-muted">
            Updated
          </p>
          <p class="text-sm font-medium text-highlighted">
            {{ updatedLabel }}
          </p>
        </div>
      </div>
    </template>
  </UPopover>
  <NuxtTime
    v-else-if="isoDate"
    :datetime="isoDate"
    :title="absoluteLabel"
    date-style="medium"
    time-style="short"
  />
  <span v-else>—</span>
</template>
