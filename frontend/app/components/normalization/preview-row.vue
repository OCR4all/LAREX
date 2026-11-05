<script setup lang="ts">
import { buildNormalizationDiffSegments } from '@/utils/normalization-diff'
import type { NormalizePreview } from '@/types/normalization-profile'

const props = defineProps<{
  preview: NormalizePreview
  showApply?: boolean
  applying?: boolean
  applyDisabled?: boolean
}>()

const emit = defineEmits<{ apply: [] }>()

const originalSegments = computed(() =>
  buildNormalizationDiffSegments(props.preview.originalText || '', props.preview.normalizedText || '', 'original')
)
const normalizedSegments = computed(() =>
  buildNormalizationDiffSegments(props.preview.originalText || '', props.preview.normalizedText || '', 'normalized')
)
</script>

<template>
  <div class="rounded-lg border border-default p-3 space-y-3">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p class="font-medium">
          {{ preview.pageName }}
        </p>
        <p class="text-xs text-muted">
          {{ preview.projectName }}
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <UBadge color="neutral" variant="soft">
          {{ preview.textLineId ? 'Text Line' : 'Region' }}
        </UBadge>
        <UBadge color="neutral" variant="subtle">
          Variant {{ preview.variantIndex ?? 0 }}
        </UBadge>
        <UButton
          v-if="showApply"
          icon="i-lucide-wand-sparkles"
          color="warning"
          variant="outline"
          size="xs"
          :loading="applying"
          :disabled="applyDisabled"
          @click="emit('apply')"
        >
          Apply row
        </UButton>
      </div>
    </div>

    <div v-if="preview.matchedRules.length > 0" class="space-y-2">
      <p class="text-xs font-medium text-muted">
        Matched rules
      </p>
      <div class="flex flex-wrap gap-2">
        <UPopover
          v-for="rule in preview.matchedRules"
          :key="rule.key"
          mode="hover"
        >
          <UBadge :color="rule.manual ? 'warning' : 'primary'" variant="soft" class="cursor-help">
            {{ rule.label }}
          </UBadge>

          <template #content>
            <div class="max-w-xs space-y-1 p-3">
              <p class="text-sm font-medium">
                {{ rule.label }}
              </p>
              <p class="text-xs text-muted">
                {{ rule.description || 'No additional rule details.' }}
              </p>
            </div>
          </template>
        </UPopover>
      </div>
    </div>

    <div class="grid gap-3 lg:grid-cols-2">
      <div>
        <p class="mb-1 text-xs font-medium text-muted">
          Original
        </p>
        <div class="rounded border border-default bg-muted/30 p-2 text-xl/8 whitespace-pre-wrap break-words font-junicode">
          <template v-for="(segment, segmentIndex) in originalSegments" :key="segmentIndex">
            <mark v-if="segment.changed" class="preview-mark preview-mark--delete">{{ segment.text }}</mark>
            <template v-else>
              {{ segment.text }}
            </template>
          </template>
        </div>
      </div>
      <div>
        <p class="mb-1 text-xs font-medium text-muted">
          Normalized
        </p>
        <div class="rounded border border-default bg-muted/30 p-2 text-xl/8 whitespace-pre-wrap break-words font-junicode">
          <template v-for="(segment, segmentIndex) in normalizedSegments" :key="segmentIndex">
            <mark v-if="segment.changed" class="preview-mark preview-mark--insert">{{ segment.text }}</mark>
            <template v-else>
              {{ segment.text }}
            </template>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.preview-mark {
  border-radius: 0.25rem;
  color: var(--ui-text-highlighted);
  padding: 0 0.1rem;
}

.preview-mark--delete {
  background: color-mix(in srgb, var(--ui-error) 28%, transparent);
  box-shadow: inset 0 -2px 0 var(--ui-error);
}

.preview-mark--insert {
  background: color-mix(in srgb, var(--ui-warning) 28%, transparent);
  box-shadow: inset 0 -2px 0 var(--ui-warning);
}
</style>
