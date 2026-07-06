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
    size="sm"
    class="shrink-0"
    @click="emit('clear')"
  >
    Clear Filters
  </UButton>
</template>
