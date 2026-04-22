<script setup lang="ts">
import type { AlternativeImageFormState } from '@/utils/editor/metadata-schema'

const images = defineModel<AlternativeImageFormState[]>({ required: true })
</script>

<template>
  <div class="space-y-2 rounded-md border border-default p-3">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">Alternative Images</span>
      <UButton
        type="button"
        size="xs"
        variant="soft"
        @click="images.push({ filename: '', comments: '', confidence: undefined })"
      >
        Add
      </UButton>
    </div>

    <div
      v-for="(image, imageIndex) in images"
      :key="`alternative-image-${imageIndex}`"
      class="space-y-2 rounded-md border border-default p-2"
    >
      <UFormField label="Filename">
        <UInput v-model="image.filename" placeholder="Image filename" />
      </UFormField>
      <UFormField label="Confidence">
        <UInput
          v-model.number="image.confidence"
          type="number"
          min="0"
          max="1"
          step="0.01"
          placeholder="0.0 - 1.0"
        />
      </UFormField>
      <UFormField label="Comments">
        <UTextarea v-model="image.comments" placeholder="Comments..." :rows="2" />
      </UFormField>
      <UButton
        type="button"
        size="xs"
        color="error"
        variant="ghost"
        @click="images.splice(imageIndex, 1)"
      >
        Remove
      </UButton>
    </div>
  </div>
</template>
