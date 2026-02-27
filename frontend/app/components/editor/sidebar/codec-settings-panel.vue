<script setup lang="ts">
const highlightUnknownCodecChars = defineModel<boolean>('highlightUnknownCodecChars', { default: false })
const includeWhitespaceInCodecHighlight = defineModel<boolean>('includeWhitespaceInCodecHighlight', { default: false })

const props = withDefaults(defineProps<{
  hasProjectCodec?: boolean
}>(), {
  hasProjectCodec: true
})
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Highlight Unknown Codec Characters</span>
        <span class="text-xs text-muted">
          {{
            props.hasProjectCodec
              ? 'Highlights characters not present in the project codec.'
              : 'Assign a codec to this project to enable unknown-character highlighting.'
          }}
        </span>
      </div>
      <USwitch v-model="highlightUnknownCodecChars" :disabled="!props.hasProjectCodec" />
    </div>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Treat Whitespace As Codec Characters</span>
        <span class="text-xs text-muted">
          When disabled, spaces/tabs/newlines are ignored in editor codec highlighting.
        </span>
      </div>
      <USwitch
        v-model="includeWhitespaceInCodecHighlight"
        :disabled="!props.hasProjectCodec || !highlightUnknownCodecChars"
      />
    </div>
  </div>
</template>
