<script setup lang="ts">
import { wsKey } from '@/utils/fetch-keys'
import { getStorageQuotaAlertState, getStorageQuotaProgressValue } from '@/utils/storage-quota'

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

  const state = getStorageQuotaAlertState(quota.value)
  if (state === 'exceeded') return 'red'
  if (state === 'warning') return 'orange'
  return 'primary'
})

const statusText = computed(() => {
  if (!quota.value) return 'Loading...'

  if (getStorageQuotaAlertState(quota.value) === 'exceeded') {
    return 'Exceeded'
  }

  const percentage = quota.value.usagePercentage
  if (percentage >= 90) return 'Nearly Full'
  if (percentage >= 80) return 'High Usage'

  return 'OK'
})

const statusColor = computed(() => {
  if (!quota.value) return 'neutral'

  if (getStorageQuotaAlertState(quota.value) === 'exceeded') {
    return 'red'
  }

  const percentage = quota.value.usagePercentage
  if (percentage >= 90) return 'orange'
  if (percentage >= 80) return 'yellow'

  return 'green'
})

const progressValue = computed(() => getStorageQuotaProgressValue(quota.value?.usagePercentage ?? 0))
</script>

<template>
  <div class="min-w-32">
    <div v-if="pending" class="animate-pulse">
      <div class="h-2 bg-neutral-200 rounded-sm mb-1" />
      <div class="h-3 bg-neutral-200 rounded-sm w-16" />
    </div>

    <div v-else-if="error" class="text-xs text-red-600">
      Error loading
    </div>

    <div v-else-if="quota" class="space-y-1">
      <UProgress
        :value="progressValue"
        :max="100"
        :color="progressColor"
        size="xs"
        :animation="false"
      />

      <div class="flex justify-between items-center text-xs">
        <span class="text-neutral-600">{{ quota.currentUsageFormatted }}<span v-if="quota.reservedBytes"> + {{ quota.reservedBytesFormatted }} reserved</span></span>
        <UBadge
          :color="statusColor"
          variant="soft"
          size="xs"
        >
          {{ statusText }}
        </UBadge>
      </div>
    </div>

    <div v-else class="text-xs text-neutral-400">
      No data
    </div>
  </div>
</template>
