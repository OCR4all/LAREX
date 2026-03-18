<script setup lang="ts">
const highlightUnknownDictionaryTokens = defineModel<boolean>('highlightUnknownDictionaryTokens', { default: false })

const props = withDefaults(defineProps<{
  hasProjectDictionary?: boolean
}>(), {
  hasProjectDictionary: true
})
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Highlight Unknown Dictionary Tokens</span>
        <span class="text-xs text-muted">
          {{
            props.hasProjectDictionary
              ? 'Flags GT tokens that are not present in the project dictionary.'
              : 'Assign a dictionary to this project to enable token validation.'
          }}
        </span>
      </div>
      <USwitch v-model="highlightUnknownDictionaryTokens" :disabled="!props.hasProjectDictionary" />
    </div>
  </div>
</template>
