<script setup lang="ts">
const { reference, text, open, hide } = useSharedTooltip()
</script>

<template>
  <UTooltip
    v-if="reference"
    :open="open"
    :reference="reference"
    :text="text"
    :content="{ side: 'top', align: 'center', sideOffset: 6 }"
    @update:open="(value: boolean) => { if (!value) hide() }"
  />
</template>
