<script setup lang="ts">
import type { UploadSessionStatus } from '@/stores/upload.store'

const props = defineProps<{
  projectId: string
  projectName: string
  workspaceId: string
  files: File[]
}>()

const emit = defineEmits<{
  terminal: [status: Extract<UploadSessionStatus, 'COMPLETED' | 'FAILED' | 'CANCELLED'>]
}>()

const pages = ref<Array<{ id: string }>>([])
const pagesPending = ref(false)
const pagesError = ref<unknown>(null)

const { startProjectUpload } = useProjectUploadOrchestration({
  projectId: props.projectId,
  workspaceId: computed(() => props.workspaceId),
  projectName: computed(() => props.projectName),
  pages,
  pagesPending,
  pagesError,
  refreshPagesFetch: async () => {},
  refreshProject: async () => {
    await refreshNuxtData(wsKey(props.workspaceId, 'projects', 'list'))
  },
  refreshProjectStatus: async () => {},
  onTerminal: status => emit('terminal', status)
})

onMounted(() => startProjectUpload(props.files))
</script>

<template>
  <span class="hidden" aria-hidden="true" />
</template>
