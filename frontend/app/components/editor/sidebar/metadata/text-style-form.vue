<script setup lang="ts">
import type { TextStyleFormState } from '@/utils/editor/metadata-schema'

const textStyle = defineModel<TextStyleFormState>({ required: true })
</script>

<template>
  <div class="space-y-2 rounded-md border border-default p-3">
    <span class="text-sm font-medium">Text Style</span>
    <UFormField label="Font Family">
      <UInput v-model="textStyle.fontFamily" placeholder="Font family" />
    </UFormField>
    <div class="grid grid-cols-2 gap-2">
      <UFormField label="Font Size">
        <UInput v-model.number="textStyle.fontSize" type="number" step="0.1" />
      </UFormField>
      <UFormField label="X Height">
        <UInput v-model.number="textStyle.xHeight" type="number" step="0.1" />
      </UFormField>
    </div>
    <div class="grid grid-cols-2 gap-2">
      <UFormField label="Kerning">
        <UInput v-model.number="textStyle.kerning" type="number" step="0.1" />
      </UFormField>
      <UFormField label="Underline Style">
        <UInput v-model="textStyle.underlineStyle" placeholder="Underline style" />
      </UFormField>
    </div>
    <div class="grid grid-cols-2 gap-2">
      <UFormField label="Text Colour">
        <UInput v-model="textStyle.textColour" placeholder="Named colour" />
      </UFormField>
      <UFormField label="Background Colour">
        <UInput v-model="textStyle.bgColour" placeholder="Named colour" />
      </UFormField>
    </div>
    <div class="grid grid-cols-2 gap-2">
      <UFormField label="Text Colour RGB">
        <UInput v-model.number="textStyle.textColourRgb" type="number" />
      </UFormField>
      <UFormField label="Background Colour RGB">
        <UInput v-model.number="textStyle.bgColourRgb" type="number" />
      </UFormField>
    </div>
    <div class="space-y-1">
      <UCheckbox v-model="textStyle.serif" label="Serif" />
      <UCheckbox v-model="textStyle.monospace" label="Monospace" />
      <UCheckbox v-model="textStyle.bold" label="Bold" />
      <UCheckbox v-model="textStyle.italic" label="Italic" />
      <UCheckbox v-model="textStyle.underlined" label="Underlined" />
      <UCheckbox v-model="textStyle.subscript" label="Subscript" />
      <UCheckbox v-model="textStyle.superscript" label="Superscript" />
      <UCheckbox v-model="textStyle.strikethrough" label="Strikethrough" />
      <UCheckbox v-model="textStyle.smallCaps" label="Small Caps" />
      <UCheckbox v-model="textStyle.letterSpaced" label="Letter Spaced" />
      <UCheckbox v-model="textStyle.reverseVideo" label="Reverse Video" />
    </div>
  </div>
</template>
