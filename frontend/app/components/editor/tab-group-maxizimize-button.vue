<script setup lang="ts">
import type { IDockviewHeaderActionsProps } from 'dockview-vue'

const props = defineProps<{
  params: IDockviewHeaderActionsProps
}>()

const isMaximized = ref(false)

let disposable: { dispose: () => void } | null = null
let subscribedContainerApi: IDockviewHeaderActionsProps['containerApi'] | null = null

const groupApi = shallowRef<IDockviewHeaderActionsProps['api'] | null>(null)
const containerApi = shallowRef<IDockviewHeaderActionsProps['containerApi'] | null>(null)

const maybeParams = computed(() => props.params as Partial<IDockviewHeaderActionsProps>)

const syncApis = () => {
  if (maybeParams.value.api) {
    groupApi.value = maybeParams.value.api
  }

  if (maybeParams.value.containerApi) {
    containerApi.value = maybeParams.value.containerApi
  }
}

const updateMaximizedState = () => {
  isMaximized.value = groupApi.value?.isMaximized() ?? false
}

const ensureSubscription = () => {
  if (!containerApi.value || !groupApi.value) {
    return
  }

  if (subscribedContainerApi === containerApi.value) {
    updateMaximizedState()
    return
  }

  disposable?.dispose()
  subscribedContainerApi = containerApi.value

  const onDidMaximizedGroupChange = (containerApi.value as {
    onDidMaximizedGroupChange?: ((listener: () => void) => { dispose: () => void })
  }).onDidMaximizedGroupChange

  disposable = typeof onDidMaximizedGroupChange === 'function'
    ? onDidMaximizedGroupChange(() => {
        updateMaximizedState()
      })
    : null

  updateMaximizedState()
}

const onClick = () => {
  const api = groupApi.value
  if (!api) {
    return
  }

  if (isMaximized.value) {
    api.exitMaximized()
  } else {
    api.maximize()
  }
}

watch(() => props.params, () => {
  syncApis()
  ensureSubscription()
}, { immediate: true })

onUnmounted(() => {
  disposable?.dispose()
  disposable = null
  subscribedContainerApi = null
})
</script>

<template>
  <div title="Toggle group maximization" class="maximize-button" @click="onClick">
    <Icon :name="isMaximized ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'" :size="16" />
  </div>
</template>

<style scoped>
.maximize-button {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 30px;
  height: 100%;
  cursor: pointer;
  color: var(--dv-tab-close-icon, #333);
}
.maximize-button:hover {
  background-color: var(--dv-icon-hover-background-color, rgba(0, 0, 0, 0.1));
}
</style>
