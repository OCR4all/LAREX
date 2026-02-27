<script setup lang="ts">
const props = withDefaults(defineProps<{
  name: string
  entityType: string
  title?: string
  warningMessage?: string
  warningDetails?: string[]
  confirmButtonLabel?: string
  placeholder?: string
  loading?: boolean
}>(), {
  warningMessage: 'This action cannot be undone!',
  loading: false
})

const emit = defineEmits<{
  close: [confirmed: boolean]
}>()

const confirmName = ref('')
const isMatch = computed(() => confirmName.value === props.name)

const computedTitle = computed(() => props.title || `Delete ${props.entityType}`)
const computedPlaceholder = computed(() => props.placeholder || `Type ${props.entityType.toLowerCase()} name to confirm`)
const computedButtonLabel = computed(() => props.confirmButtonLabel || `Delete ${props.entityType}`)
</script>

<template>
  <USlideover
    class="bg-linear-to-tl from-error/5 from-5% to-default"
    side="right"
    :title="computedTitle"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <div class="flex flex-col gap-4 max-w-lg mx-auto">
        <slot name="warning">
          <p class="text-muted">
            {{ warningMessage }}
          </p>
          <ul v-if="warningDetails?.length" class="text-sm text-muted list-disc list-inside space-y-1">
            <li v-for="detail in warningDetails" :key="detail">
              {{ detail }}
            </li>
          </ul>
        </slot>

        <slot name="details" />

        <p class="text-muted">
          Please type <strong class="text-default">{{ name }}</strong> to confirm.
        </p>
        <UInput
          v-model="confirmName"
          :placeholder="computedPlaceholder"
          :disabled="loading"
          autofocus
        />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-center gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="loading"
          @click="emit('close', false)"
        >
          Cancel
        </UButton>
        <UButton
          color="error"
          variant="solid"
          icon="i-lucide-trash"
          :disabled="!isMatch"
          :loading="loading"
          @click="emit('close', true)"
        >
          {{ computedButtonLabel }}
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
