<script setup lang="ts">
const props = withDefaults(defineProps<{
  pageCount?: number
}>(), {
  pageCount: 1
})

const emit = defineEmits<{
  close: [result: { comment: string } | null]
}>()

const isOpen = ref(true)
const comment = ref('')
const title = computed(() => props.pageCount === 1 ? 'Request changes' : `Request changes on ${props.pageCount} pages`)

function cancel() {
  emit('close', null)
  isOpen.value = false
}

function confirm() {
  emit('close', { comment: comment.value })
  isOpen.value = false
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="title"
    description="The page goes back to Open and its Tasks reopen for the editor."
    @close="cancel"
  >
    <template #body>
      <UFormField label="Comment" hint="Optional">
        <UTextarea
          v-model="comment"
          :rows="4"
          :maxlength="2000"
          autofocus
          placeholder="What needs to change?"
          class="w-full"
          @keydown.meta.enter.prevent="confirm"
          @keydown.ctrl.enter.prevent="confirm"
        />
      </UFormField>
    </template>

    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="cancel">
          Cancel
        </UButton>
        <UButton color="warning" icon="i-lucide-message-square-warning" @click="confirm">
          Request changes
        </UButton>
      </div>
    </template>
  </UModal>
</template>
