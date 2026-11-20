<script setup lang="ts">
import { useTableIssues } from '@/composables/editor/use-table-issues'
import { describeTableIssue } from '@/utils/editor/table/table-validation'

const props = withDefaults(defineProps<{
  canvasId: string
  readOnly?: boolean
}>(), {
  readOnly: false
})

const { issues, groups, selectRegion, repairTable } = useTableIssues(() => props.canvasId)

const errorCount = computed(() => issues.value.filter(issue => issue.severity === 'error').length)
const label = computed(() => `${issues.value.length} table problem${issues.value.length === 1 ? '' : 's'}`)
</script>

<template>
  <UPopover v-if="issues.length > 0" :content="{ side: 'top', align: 'end' }">
    <UButton
      :color="errorCount > 0 ? 'error' : 'warning'"
      variant="subtle"
      size="xs"
      icon="i-lucide-table"
      :label="label"
      class="shrink-0"
    />
    <template #content>
      <div class="flex max-h-96 w-96 flex-col gap-3 overflow-y-auto p-3">
        <section v-for="group in groups" :key="group.tableId ?? 'other'" class="flex flex-col gap-1.5">
          <div class="flex items-center justify-between gap-2">
            <span class="truncate text-xs font-medium text-highlighted">
              {{ group.tableId ? `Table ${group.tableId}` : 'Outside tables' }}
            </span>
            <div v-if="group.tableId" class="flex shrink-0 items-center gap-1">
              <UButton
                size="xs"
                color="neutral"
                variant="ghost"
                label="Select"
                @click="selectRegion(group.tableId)"
              />
              <UButton
                v-if="group.repairable && !readOnly"
                size="xs"
                color="primary"
                variant="soft"
                icon="i-lucide-wrench"
                label="Repair table"
                @click="repairTable(group.tableId)"
              />
            </div>
          </div>
          <ul class="flex flex-col gap-0.5">
            <li v-for="(issue, index) in group.issues" :key="`${issue.code}:${index}`">
              <button
                type="button"
                class="flex w-full items-start gap-1.5 rounded px-1 py-0.5 text-left text-xs text-default hover:bg-elevated"
                @click="selectRegion(issue.regionIds[0] ?? group.tableId ?? '')"
              >
                <UIcon
                  :name="issue.severity === 'error' ? 'i-lucide-circle-x' : 'i-lucide-triangle-alert'"
                  :class="issue.severity === 'error' ? 'text-error' : 'text-warning'"
                  class="mt-0.5 size-3.5 shrink-0"
                />
                <span>{{ describeTableIssue(issue) }}</span>
              </button>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </UPopover>
</template>
