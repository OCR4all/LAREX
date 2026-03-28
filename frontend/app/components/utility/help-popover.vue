<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  description?: string | null
  items?: string[]
}>(), {
  description: '',
  items: () => []
})
</script>

<template>
  <UPopover
    mode="hover"
    :content="{ align: 'end', side: 'bottom', sideOffset: 8 }"
  >
    <UButton
      icon="i-lucide-circle-help"
      color="neutral"
      variant="ghost"
      size="sm"
      square
      aria-label="Utility help"
    />

    <template #content>
      <div class="max-w-sm space-y-3 p-4">
        <div class="space-y-1">
          <p class="text-sm font-semibold text-highlighted">
            {{ title }}
          </p>
          <p v-if="description" class="text-xs leading-5 text-muted">
            {{ description }}
          </p>
        </div>

        <ul v-if="items.length" class="space-y-2 text-xs leading-5 text-muted">
          <li v-for="item in items" :key="item" class="flex gap-2">
            <span class="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/70" />
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>
    </template>
  </UPopover>
</template>
