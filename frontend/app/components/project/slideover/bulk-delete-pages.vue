<script setup lang="ts">
const props = defineProps<{
  projectId: string
  projectName?: string
  pages: Array<{ id: string, name: string }>
}>()

const emit = defineEmits<{
  close: [result: { deletedCount: number, requestedCount: number } | null]
}>()

const toast = useToast()
const isDeleting = ref(false)

const orderedPages = computed(() => {
  return [...props.pages].sort((a, b) => a.name.localeCompare(b.name))
})

const requestedCount = computed(() => orderedPages.value.length)

async function confirmBulkDelete() {
  const progressToast = toast.add({
    title: `Deleting Pages in ${props.projectName ?? 'Project'}`,
    description: `Deleting ${requestedCount.value} page${requestedCount.value === 1 ? '' : 's'}...`,
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  try {
    isDeleting.value = true
    const pageIds = orderedPages.value.map(p => p.id)

    const response = await $fetch<{ deletedCount: number, requestedCount: number }>(`/api/projects/${props.projectId}/pages/batch`, {
      method: 'DELETE',
      body: pageIds
    })

    toast.add({
      title: 'Pages deleted',
      description: `Successfully deleted ${response.deletedCount} of ${response.requestedCount} pages`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })

    emit('close', response)
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Delete failed',
      description: message || 'Failed to delete pages',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    toast.remove(progressToast.id)
    isDeleting.value = false
  }
}
</script>

<template>
  <USlideover
    side="right"
    title="Delete Pages"
    class="bg-linear-to-tl from-error/5 from-5% to-default"
    description="This action cannot be undone."
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-4">
        <div class="flex items-start gap-3 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-sm">
          <UIcon name="i-lucide-alert-triangle" class="text-red-500 mt-0.5 shrink-0" />
          <div>
            <p class="font-medium text-red-800 dark:text-red-200">
              Are you sure you want to delete {{ requestedCount }} page{{ requestedCount > 1 ? 's' : '' }}?
            </p>
            <p class="text-sm text-red-700 dark:text-red-300 mt-1">
              All associated images and XML files will be permanently removed.
            </p>
          </div>
        </div>

        <div class="h-full shadow-inner overflow-y-auto border border-gray-200 dark:border-gray-700 rounded-sm divide-y divide-gray-200 dark:divide-gray-700">
          <div
            v-for="p in orderedPages"
            :key="p.id"
            class="px-3 py-2 text-sm"
          >
            {{ p.name || p.id }}
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="emit('close', null)"
        >
          Cancel
        </UButton>
        <UButton
          variant="subtle"
          icon="i-lucide-trash"
          color="error"
          :loading="isDeleting"
          @click="confirmBulkDelete"
        >
          Delete {{ requestedCount }} Page{{ requestedCount > 1 ? 's' : '' }}
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
