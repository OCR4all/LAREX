<script setup lang="ts">
import { wsKey } from '@/utils/fetch-keys'

interface Props {
  workspaceId: string
}

const props = defineProps<Props>()

const { data: quota, pending, error } = await useFetch(`/api/storage/quotas/workspace/${props.workspaceId}`, {
  key: wsKey(props.workspaceId, 'storage', 'quota', 'cell'),
  default: () => null
})

const progressColor = computed(() => {
  if (!quota.value) return 'primary'

  const percentage = quota.value.usagePercentage
  if (percentage >= 90) return 'red'
  if (percentage >= 80) return 'orange'
  if (percentage >= 70) return 'yellow'
  return 'primary'
})

const statusText = computed(() => {
  if (!quota.value) return 'Loading...'

  if (quota.value.isQuotaExceeded) {
    return 'Exceeded'
  }

  const percentage = quota.value.usagePercentage
  if (percentage >= 90) return 'Nearly Full'
  if (percentage >= 80) return 'High Usage'

  return 'OK'
})

const statusColor = computed(() => {
  if (!quota.value) return 'neutral'

  if (quota.value.isQuotaExceeded) {
    return 'red'
  }

  const percentage = quota.value.usagePercentage
  if (percentage >= 90) return 'orange'
  if (percentage >= 80) return 'yellow'

  return 'green'
})
</script>

<template>
  <div class="min-w-32">
    <div v-if="pending" class="animate-pulse">
      <div class="h-2 bg-gray-200 rounded-sm mb-1" />
      <div class="h-3 bg-gray-200 rounded-sm w-16" />
    </div>

    <div v-else-if="error" class="text-xs text-red-600">
      Error loading
    </div>

    <div v-else-if="quota" class="space-y-1">
      <UProgress
        :value="quota.usagePercentage"
        :max="100"
        :color="progressColor"
        size="xs"
        :animation="false"
      />

      <div class="flex justify-between items-center text-xs">
        <span class="text-gray-600">{{ quota.currentUsageFormatted }}</span>
        <UBadge
          :color="statusColor"
          variant="soft"
          size="xs"
        >
          {{ statusText }}
        </UBadge>
      </div>
    </div>

    <div v-else class="text-xs text-gray-400">
      No data
    </div>
  </div>
</template>
