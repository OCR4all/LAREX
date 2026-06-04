<script setup lang="ts">
type ActiveFilterItem = {
  key?: string | number
  label: string
  clear: () => void
}

defineProps<{
  filters: ActiveFilterItem[]
  spacing?: boolean
}>()

const emit = defineEmits<{
  clearAll: []
}>()
</script>

<template>
  <div v-if="filters.length > 0" class="flex items-center justify-between gap-3" :class="{ 'mb-4': spacing !== false }">
    <div class="flex min-w-0 flex-wrap items-center gap-2">
      <span class="text-xs text-neutral-500">Active filters:</span>
      <UBadge
        v-for="(filter, index) in filters"
        :key="filter.key ?? `${filter.label}-${index}`"
        variant="soft"
        color="neutral"
        size="sm"
        class="flex items-center gap-1"
      >
        {{ filter.label }}
        <UButton
          size="xs"
          color="neutral"
          variant="link"
          icon="i-lucide-x"
          :padded="false"
          @click="filter.clear()"
        />
      </UBadge>
    </div>
    <UButton
      icon="i-lucide-x"
      color="neutral"
      variant="ghost"
      size="sm"
      class="shrink-0"
      @click="emit('clearAll')"
    >
      Clear Filters
    </UButton>
  </div>
</template>
