<script setup lang="ts">
withDefaults(defineProps<{
  active?: boolean
}>(), {
  active: false
})

const emit = defineEmits<{
  clear: []
}>()
</script>

<template>
  <UButton
    v-if="active"
    type="button"
    icon="i-lucide-x"
    color="neutral"
    variant="ghost"
    class="shrink-0 text-muted hover:text-highlighted"
    @click="emit('clear')"
  >
    Clear filters
  </UButton>
</template>
