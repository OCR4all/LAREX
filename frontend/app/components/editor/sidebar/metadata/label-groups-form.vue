<script setup lang="ts">
import type { LabelsFormState } from '@/utils/editor/metadata-schema'

const props = defineProps<{
  readOnly?: boolean
}>()

const groups = defineModel<LabelsFormState[]>({ required: true })
</script>

<template>
  <div class="space-y-2 rounded-md border border-default p-3">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">Labels</span>
      <UButton
        type="button"
        size="xs"
        variant="soft"
        :disabled="props.readOnly"
        @click="() => { groups.push({ externalModel: '', externalId: '', prefix: '', comments: '', labels: [] }) }"
      >
        Add Group
      </UButton>
    </div>

    <div
      v-for="(group, groupIndex) in groups"
      :key="`label-group-${groupIndex}`"
      class="space-y-2 rounded-md border border-default p-2"
    >
      <UFormField label="External Model">
        <UInput v-model="group.externalModel" placeholder="External model" :disabled="props.readOnly" />
      </UFormField>
      <UFormField label="External ID">
        <UInput v-model="group.externalId" placeholder="External ID" :disabled="props.readOnly" />
      </UFormField>
      <UFormField label="Prefix">
        <UInput v-model="group.prefix" placeholder="Prefix" :disabled="props.readOnly" />
      </UFormField>
      <UFormField label="Comments">
        <UTextarea
          v-model="group.comments"
          placeholder="Comments..."
          :rows="2"
          :disabled="props.readOnly"
        />
      </UFormField>

      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="text-xs font-medium">Labels</span>
          <UButton
            type="button"
            size="xs"
            variant="ghost"
            :disabled="props.readOnly"
            @click="() => { group.labels.push({ value: '', type: '', comments: '' }) }"
          >
            Add Label
          </UButton>
        </div>

        <div
          v-for="(label, labelIndex) in group.labels"
          :key="`label-group-${groupIndex}-label-${labelIndex}`"
          class="space-y-2 rounded-md border border-default p-2"
        >
          <UFormField label="Value">
            <UInput v-model="label.value" placeholder="Value" :disabled="props.readOnly" />
          </UFormField>
          <UFormField label="Type">
            <UInput v-model="label.type" placeholder="Type" :disabled="props.readOnly" />
          </UFormField>
          <UFormField label="Comments">
            <UInput v-model="label.comments" placeholder="Comments" :disabled="props.readOnly" />
          </UFormField>
          <UButton
            type="button"
            size="xs"
            color="error"
            variant="ghost"
            :disabled="props.readOnly"
            @click="() => { group.labels.splice(labelIndex, 1) }"
          >
            Remove Label
          </UButton>
        </div>
      </div>

      <UButton
        type="button"
        size="xs"
        color="error"
        variant="ghost"
        :disabled="props.readOnly"
        @click="() => { groups.splice(groupIndex, 1) }"
      >
        Remove Group
      </UButton>
    </div>
  </div>
</template>
