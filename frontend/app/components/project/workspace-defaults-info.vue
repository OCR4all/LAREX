<script setup lang="ts">
const props = defineProps<{
  context: 'workspace' | 'create' | 'edit'
}>()

const copy: Record<typeof props.context, { title: string, description: string }> = {
  workspace: {
    title: 'How project defaults work',
    description: 'These values are copied when a project is created. Existing projects keep their current settings unless you choose to update them when saving workspace changes.'
  },
  create: {
    title: 'How workspace defaults work',
    description: 'The current workspace defaults are copied into this project when it is created. Later workspace changes do not update it.'
  },
  edit: {
    title: 'How workspace defaults work',
    description: 'This project stores concrete values, so later workspace changes do not update it. Resetting a value only changes this draft until you save the project.'
  }
}

const content = computed(() => copy[props.context])
</script>

<template>
  <UPopover :content="{ align: 'start', side: 'bottom', sideOffset: 6 }">
    <UButton
      type="button"
      icon="i-lucide-info"
      color="neutral"
      variant="ghost"
      size="xs"
      square
      :aria-label="content.title"
      :ui="{ base: 'rounded-full' }"
    />

    <template #content>
      <div class="max-w-xs space-y-1.5 p-3">
        <p class="text-sm font-medium text-highlighted">
          {{ content.title }}
        </p>
        <p class="text-xs leading-5 text-muted">
          {{ content.description }}
        </p>
      </div>
    </template>
  </UPopover>
</template>
