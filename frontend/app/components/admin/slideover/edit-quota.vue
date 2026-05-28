<script setup lang="ts">
interface Props {
  quota: {
    workspaceId: string
    quotaLimitBytes: number
    isCustom: boolean
  }
  defaultQuota: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ close: [], saved: [] }>()

const toast = useToast()
const { refreshAdminQuotas } = useDataRefresh()
const isSaving = ref(false)

const GB = 1024 * 1024 * 1024

const quotaInGB = ref(Math.round((props.quota.quotaLimitBytes / GB) * 100) / 100)
const isCustom = ref(props.quota.isCustom)

const presets = [1, 5, 10, 25, 50, 100]

async function save() {
  isSaving.value = true
  try {
    await $fetch(`/api/storage/quotas/admin/workspace/${props.quota.workspaceId}`, {
      method: 'PUT',
      body: { quotaLimitBytes: Math.round(quotaInGB.value * GB), isCustom: isCustom.value }
    })
    toast.add({ title: 'Quota updated', color: 'success' })
    await refreshAdminQuotas()
    emit('saved')
    emit('close')
  } catch {
    toast.add({ title: 'Failed to update quota', color: 'error' })
  } finally {
    isSaving.value = false
  }
}

function resetToDefault() {
  quotaInGB.value = Math.round((props.defaultQuota / GB) * 100) / 100
  isCustom.value = false
}
</script>

<template>
  <USlideover :close="{ onClick: () => emit('close') }">
    <template #header>
      <UiSlideoverHeader title="Edit Storage Quota" icon="i-lucide-database" />
    </template>

    <template #body>
      <div class="space-y-6">
        <div>
          <label class="text-sm font-medium">Workspace ID</label>
          <p class="text-sm text-muted font-mono mt-1">
            {{ quota.workspaceId }}
          </p>
        </div>

        <UFormField label="Quota Limit (GB)">
          <div class="space-y-3">
            <div class="flex flex-wrap gap-2">
              <UButton
                v-for="gb in presets"
                :key="gb"
                size="sm"
                :variant="quotaInGB === gb ? 'solid' : 'outline'"
                :color="quotaInGB === gb ? 'primary' : 'neutral'"
                @click="quotaInGB = gb; isCustom = true"
              >
                {{ gb }} GB
              </UButton>
            </div>
            <UInput
              v-model.number="quotaInGB"
              type="number"
              :min="0"
              :step="0.1"
              placeholder="Enter GB"
              @input="isCustom = true"
            >
              <template #trailing>
                <span class="text-muted text-sm">GB</span>
              </template>
            </UInput>
          </div>
        </UFormField>

        <UFormField label="Options">
          <div class="space-y-2">
            <UCheckbox v-model="isCustom" label="Mark as custom quota" />
            <UButton
              size="sm"
              variant="ghost"
              color="neutral"
              @click="resetToDefault"
            >
              Reset to default ({{ Math.round((defaultQuota / GB) * 100) / 100 }} GB)
            </UButton>
          </div>
        </UFormField>

        <div class="flex gap-2 pt-4">
          <UButton :loading="isSaving" @click="save">
            Save
          </UButton>
          <UButton variant="ghost" color="neutral" @click="emit('close')">
            Cancel
          </UButton>
        </div>
      </div>
    </template>
  </USlideover>
</template>
