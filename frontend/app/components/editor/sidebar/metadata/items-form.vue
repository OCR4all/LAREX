<script setup lang="ts">
import MetadataLabelGroupsForm from './label-groups-form.vue'
import type { MetadataItemFormState } from '@/utils/editor/metadata-schema'

defineProps<{
  itemTypeOptions: Array<{ label: string, value: string }>
}>()

const items = defineModel<MetadataItemFormState[]>({ required: true })
</script>

<template>
  <div class="space-y-2 rounded-md border border-default p-3">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">Metadata Items</span>
      <UButton
        type="button"
        size="xs"
        variant="soft"
        @click="items.push({ type: undefined, name: '', value: '', date: '', labels: [] })"
      >
        Add
      </UButton>
    </div>

    <div
      v-for="(item, itemIndex) in items"
      :key="`metadata-item-${itemIndex}`"
      class="space-y-2 rounded-md border border-default p-2"
    >
      <UFormField label="Type">
        <USelect v-model="item.type" :items="itemTypeOptions" placeholder="Select type" />
      </UFormField>
      <UFormField label="Name">
        <UInput v-model="item.name" placeholder="Name" />
      </UFormField>
      <UFormField label="Value">
        <UTextarea v-model="item.value" placeholder="Value" :rows="2" />
      </UFormField>
      <UFormField label="Date">
        <UInput v-model="item.date" placeholder="ISO date/time" />
      </UFormField>

      <MetadataLabelGroupsForm v-model="item.labels" />

      <UButton
        type="button"
        size="xs"
        color="error"
        variant="ghost"
        @click="items.splice(itemIndex, 1)"
      >
        Remove Item
      </UButton>
    </div>
  </div>
</template>
