<script setup lang="ts">
import { useDebounceFn, useIntersectionObserver } from '@vueuse/core'
import type { TableInfiniteScroll } from '@/composables/use-table-pagination'

const props = defineProps<{ state: TableInfiniteScroll }>()
const sentinel = ref<HTMLElement | null>(null)
const visible = ref(false)
onBeforeUnmount(() => {
  visible.value = false
})

useIntersectionObserver(sentinel, ([entry]) => {
  visible.value = entry?.isIntersecting ?? false
}, { rootMargin: '0px 0px 120px 0px' })

// Let layout and intersection observation catch up after rows are appended.
const loadIfVisible = useDebounceFn(() => {
  if (visible.value && props.state.enabled && props.state.hasMore && !props.state.loading && !props.state.error) {
    void props.state.loadMore()
  }
}, 100)

watch(
  () => [visible.value, props.state.enabled, props.state.hasMore, props.state.loading, props.state.shown, props.state.error],
  loadIfVisible,
  { flush: 'post' }
)
</script>

<template>
  <div
    v-if="state.enabled"
    ref="sentinel"
    class="flex flex-col items-center gap-2 p-3 sm:hidden"
    @click.stop
    @keydown.stop
  >
    <p role="status" class="text-xs text-muted">
      {{ state.error || `Showing ${state.shown} of ${state.total}` }}
    </p>
    <UButton
      v-if="state.hasMore"
      color="neutral"
      variant="ghost"
      size="sm"
      :loading="state.loading"
      @click="state.loadMore()"
    >
      {{ state.error ? 'Try again' : 'Load more' }}
    </UButton>
  </div>
</template>
