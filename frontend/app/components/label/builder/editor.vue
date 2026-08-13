<script setup lang="ts">
import type { PageRegionType, PageTextType } from '@/types/label-set'
import { applyPageRegionTypeChange, applyPageTextTypeChange } from '@/composables/use-label-builder'

const props = defineProps<{
  isSystem?: boolean
}>()
const {
  activeLabel, PRESET_COLORS, PAGE_REGIONS, PAGE_TEXT_TYPES,
  getErrors
} = useLabelBuilder()
const currentLabel = computed(() => activeLabel.value)

const errors = computed(() => getErrors(currentLabel.value))

const fieldError = (...codes: string[]): string | undefined => {
  return errors.value.find(error => codes.includes(error.code))?.message
}

function updateRegionType(regionType: string) {
  if (!currentLabel.value || !PAGE_REGIONS.includes(regionType)) return

  applyPageRegionTypeChange(currentLabel.value.mapping.pageXml, regionType as PageRegionType)
}

function updateTextType(textType: string | null) {
  if (!currentLabel.value || (textType && !PAGE_TEXT_TYPES.includes(textType))) return
  applyPageTextTypeChange(currentLabel.value.mapping.pageXml, (textType || undefined) as PageTextType | undefined)
}
</script>

<template>
  <div data-tour="label-builder-editor" class="custom-scroll flex-1 overflow-y-auto p-6 lg:p-8">
    <div v-if="currentLabel" class="mx-auto max-w-xl space-y-6">
      <div>
        <h2 class="text-lg font-semibold text-highlighted">
          Label settings
        </h2>
        <p class="mt-1 text-sm text-muted">
          Configure how this label maps to a PAGE XML region.
        </p>
      </div>

      <div v-if="errors.length > 0" class="rounded-lg border border-error/30 bg-error/10 p-4">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-triangle-alert" class="size-5 shrink-0 text-error" />
          <div class="flex-1">
            <h3 class="text-sm font-semibold text-error">
              {{ errors.length }} {{ errors.length === 1 ? 'field needs' : 'fields need' }} attention
            </h3>
            <p class="mt-1 text-xs text-error">
              Correct the highlighted fields below before saving.
            </p>
          </div>
        </div>
      </div>

      <div class="space-y-4 rounded-lg border border-default bg-default p-5">
        <UFormField label="Name" :error="fieldError('missingName', 'nameTooLong', 'duplicateName')">
          <UInput
            v-model="currentLabel.name"
            size="lg"
            maxlength="255"
            placeholder="Give the label a name"
            :disabled="props.isSystem"
          />
        </UFormField>
        <UFormField label="Description" :error="fieldError('descriptionTooLong')">
          <UTextarea
            v-model="currentLabel.description"
            placeholder="Short label description"
            :rows="2"
            :disabled="props.isSystem"
          />
        </UFormField>
      </div>

      <div class="space-y-5 rounded-lg border border-default bg-default p-5">
        <div class="flex items-start gap-3">
          <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
            <UIcon name="i-lucide-code-xml" class="size-4" />
          </div>
          <div>
            <h3 class="text-sm font-semibold text-highlighted">
              PAGE XML mapping
            </h3>
            <p class="mt-0.5 text-xs text-muted">
              Choose the exported region type and optional subtype.
            </p>
          </div>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField
            label="Region Type"
            :error="fieldError('missingRegionType')"
          >
            <USelectMenu
              :model-value="currentLabel.mapping.pageXml.regionType"
              :items="PAGE_REGIONS"
              variant="outline"
              :disabled="props.isSystem"
              @update:model-value="updateRegionType"
            />
          </UFormField>
          <template v-if="currentLabel.mapping.pageXml.regionType === 'TextRegion'">
            <UFormField label="Subtype" :error="fieldError('duplicatePageMapping')">
              <USelectMenu
                :model-value="currentLabel.mapping.pageXml.textType"
                :items="PAGE_TEXT_TYPES"
                variant="outline"
                clear
                :disabled="props.isSystem"
                @update:model-value="updateTextType"
              />
            </UFormField>
            <div v-if="currentLabel.mapping.pageXml.textType === 'custom'" class="sm:col-span-2">
              <UFormField label="Custom Subtype" :error="fieldError('missingCustomSubType')">
                <UInput
                  v-model="currentLabel.mapping.pageXml.customSubType"
                  placeholder="article"
                  color="primary"
                  variant="outline"
                  :disabled="props.isSystem"
                />
              </UFormField>
            </div>
          </template>
          <UFormField
            v-else-if="currentLabel.mapping.pageXml.regionType"
            label="Subtype"
            description="Optional PAGE region type value."
            :error="fieldError('duplicatePageMapping')"
          >
            <UInput
              v-model="currentLabel.mapping.pageXml.customSubType"
              placeholder="Optional"
              variant="outline"
              :disabled="props.isSystem"
            />
          </UFormField>
        </div>
      </div>

      <div class="space-y-4 rounded-lg border border-default bg-default p-5">
        <div>
          <h3 class="text-sm font-semibold text-highlighted">
            Label color
          </h3>
          <p class="mt-0.5 text-xs text-muted">
            Used for regions, menus, and editor overlays.
          </p>
        </div>
        <div class="flex flex-wrap gap-2.5">
          <button
            v-for="color in PRESET_COLORS"
            :key="color"
            class="size-8 rounded-md border border-black/10 shadow-xs transition-transform hover:scale-105 disabled:cursor-not-allowed disabled:opacity-60"
            :class="{ 'ring-2 ring-primary ring-offset-2 ring-offset-default': currentLabel.color === color }"
            :style="{ backgroundColor: color }"
            :disabled="props.isSystem"
            :aria-label="`Use color ${color}`"
            @click="currentLabel.color = color"
          />
        </div>
        <UFormField label="Custom color" :error="fieldError('invalidColor')" class="border-t border-default pt-4">
          <div class="flex items-center gap-3">
            <input
              v-model="currentLabel.color"
              type="color"
              class="size-9 cursor-pointer rounded-md border-0 bg-transparent p-0"
              :disabled="props.isSystem"
            >
            <UInput
              v-model="currentLabel.color"
              class="max-w-40 font-mono uppercase"
              :disabled="props.isSystem"
            />
          </div>
        </UFormField>
      </div>

      <!-- TODO: Reintroduce PAGE-relevant semantic flags only if/when we support non-PAGE schemas again. -->
    </div>
    <div v-else class="mx-auto flex h-full max-w-2xl items-center justify-center text-sm text-muted">
      Select a label to edit.
    </div>
  </div>
</template>
