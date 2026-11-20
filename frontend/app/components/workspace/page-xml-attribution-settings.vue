<script setup lang="ts">
defineProps<{
  disabled: boolean
}>()

const creatorFromUser = defineModel<boolean>('creatorFromUser', { required: true })
const recordAuthors = defineModel<boolean>('recordAuthors', { required: true })
</script>

<template>
  <UiFormSectionHeader title="PAGE XML attribution" />
  <p class="text-sm text-muted">
    Choose which names saved PAGE XML files and their exports include.
  </p>
  <div class="flex flex-col gap-2">
    <div class="flex items-center justify-between gap-3">
      <div>
        <p class="text-sm font-medium">
          Name the creator
        </p>
        <p class="text-xs text-muted">
          New annotations use the creating user's name followed by "(LAREX)" as Creator. Off: just "LAREX".
        </p>
      </div>
      <USwitch v-model="creatorFromUser" :disabled="disabled" />
    </div>
    <div class="flex items-center justify-between gap-3">
      <div>
        <p class="text-sm font-medium">
          Record editors
        </p>
        <p class="text-xs text-muted">
          Each save adds the user to the file's author entries.
        </p>
      </div>
      <USwitch v-model="recordAuthors" :disabled="disabled" />
    </div>
  </div>
</template>
