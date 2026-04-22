<script setup lang="ts">
const props = defineProps<{ onSave?: () => void }>()
const emit = defineEmits<{ close: [] }>()

const { meta, countTags } = useTagSetBuilder()

const tagCount = computed(() => countTags())
const descriptionModel = computed({
  get: () => meta.description ?? '',
  set: (value: string) => {
    meta.description = value
  }
})

const handleSave = () => {
  props.onSave?.()
}
</script>

<template>
  <USlideover
    title="Tag Set Settings"
    :close="{ onClick: () => emit('close') }"
  >
    <template #body>
      <div class="space-y-6">
        <UFormField label="Name" required>
          <UInput v-model="meta.name" placeholder="e.g. Document Types" />
        </UFormField>

        <UFormField label="Description">
          <UTextarea
            v-model="descriptionModel"
            placeholder="Describe this tag set and its purpose..."
            :rows="3"
          />
        </UFormField>

        <UFormField label="Tags" hint="Categorize this tag set for easier discovery">
          <UInputTags v-model="meta.tags" placeholder="Add tags..." />
        </UFormField>

        <USeparator />

        <div class="bg-neutral-100 dark:bg-neutral-800 rounded-sm p-4">
          <h4 class="text-sm font-medium text-black dark:text-white mb-3">
            Statistics
          </h4>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <div class="text-2xl font-bold text-primary-600">
                {{ tagCount }}
              </div>
              <div class="text-xs text-neutral-500">
                Total Tags
              </div>
            </div>
            <div>
              <div class="text-2xl font-bold text-neutral-600 dark:text-neutral-400">
                {{ meta.tags?.length || 0 }}
              </div>
              <div class="text-xs text-neutral-500">
                Meta Tags
              </div>
            </div>
          </div>
        </div>

        <div class="bg-info/10 border border-info/30 rounded-sm p-4">
          <div class="flex items-start gap-3">
            <UIcon name="i-lucide-info" class="w-5 h-5 text-info shrink-0 mt-0.5" />
            <div class="text-sm text-info">
              <p class="font-medium mb-1">
                About Tag Sets
              </p>
              <p class="text-xs text-info/90">
                Tag sets define hierarchical tag structures for categorizing projects and pages.
                Tags are stored by ID, so you can safely rename them without breaking existing assignments.
              </p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton variant="ghost" color="neutral" @click="emit('close')">
          Close
        </UButton>
        <UButton icon="i-lucide-save" @click="handleSave">
          Save
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
