<script setup lang="ts">
const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

const versionOptions = [
  { label: 'PAGE XML 2010-03-19', value: '2010-03-19' },
  { label: 'PAGE XML 2013-07-15', value: '2013-07-15' },
  { label: 'PAGE XML 2016-07-15', value: '2016-07-15' },
  { label: 'PAGE XML 2017-07-15', value: '2017-07-15' },
  { label: 'PAGE XML 2018-07-15', value: '2018-07-15' },
  { label: 'PAGE XML 2019-07-15', value: '2019-07-15' }
]

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  initialTargetVersion?: string
  confirmLabel?: string
}>(), {
  title: 'Export PAGE XML',
  description: 'Choose the PAGE XML schema version for export.',
  initialTargetVersion: PAGE_XML_PRIMARY_VERSION,
  confirmLabel: 'Continue'
})

const emit = defineEmits<{
  close: [result: string | null]
}>()

const targetVersion = ref(props.initialTargetVersion)
const isLegacyTarget = computed(() => targetVersion.value !== PAGE_XML_PRIMARY_VERSION)
</script>

<template>
  <USlideover
    side="right"
    :title="props.title"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-4">
        <p class="text-sm text-muted">
          {{ props.description }}
        </p>

        <UFormField label="Target PAGE XML version" name="targetVersion">
          <USelect
            v-model="targetVersion"
            :items="versionOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>

        <UAlert
          v-if="isLegacyTarget"
          color="warning"
          variant="soft"
          icon="i-lucide-triangle-alert"
          title="Legacy export can lose data"
          description="Older PAGE XML schemas do not support all 2019 features."
        />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="emit('close', null)"
        >
          Cancel
        </UButton>
        <UButton
          color="primary"
          variant="solid"
          @click="emit('close', targetVersion)"
        >
          {{ props.confirmLabel }}
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
