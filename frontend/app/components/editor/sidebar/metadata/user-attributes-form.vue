<script setup lang="ts">
import type { UserAttributeFormState } from '@/utils/editor/metadata-schema'

defineProps<{
  typeOptions: Array<{ label: string, value: string }>
}>()

const attributes = defineModel<UserAttributeFormState[]>({ required: true })
</script>

<template>
  <div class="space-y-2 rounded-md border border-default p-3">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">User-Defined Attributes</span>
      <UButton
        type="button"
        size="xs"
        variant="soft"
        @click="attributes.push({ name: '', description: '', type: undefined, value: '' })"
      >
        Add
      </UButton>
    </div>

    <div
      v-for="(attribute, attributeIndex) in attributes"
      :key="`user-attribute-${attributeIndex}`"
      class="space-y-2 rounded-md border border-default p-2"
    >
      <UFormField label="Name">
        <UInput v-model="attribute.name" placeholder="Attribute name" />
      </UFormField>
      <UFormField label="Description">
        <UInput v-model="attribute.description" placeholder="Description" />
      </UFormField>
      <UFormField label="Type">
        <USelect v-model="attribute.type" :items="typeOptions" placeholder="Select type" />
      </UFormField>
      <UFormField label="Value">
        <UInput v-model="attribute.value" placeholder="Value" />
      </UFormField>
      <UButton
        type="button"
        size="xs"
        color="error"
        variant="ghost"
        @click="attributes.splice(attributeIndex, 1)"
      >
        Remove
      </UButton>
    </div>
  </div>
</template>
