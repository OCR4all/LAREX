<script setup lang="ts">
const { activeTag, moveTag } = useTagSetBuilder()

const PRESET_COLORS = [
  '#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6',
  '#3b82f6', '#6366f1', '#a855f7', '#ec4899', '#f43f5e',
  '#84cc16', '#06b6d4', '#8b5cf6', '#d946ef', '#fb923c'
]

const hasErrors = computed(() => activeTag.value?.errors && activeTag.value.errors.length > 0)
</script>

<template>
  <div data-tour="tag-builder-editor" class="flex-1 p-8 overflow-y-auto custom-scroll">
    <div v-if="activeTag" class="max-w-2xl mx-auto space-y-8">
      <div v-if="hasErrors" class="bg-red-500/10 border border-red-500/50 rounded-sm p-4">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-alert-triangle" class="w-6 h-6 text-red-400 shrink-0" />
          <div class="flex-1">
            <h3 class="text-sm font-bold text-red-400 mb-1">
              Validation Errors
            </h3>
            <ul class="list-disc list-inside text-xs text-red-800/80 dark:text-red-200/80 space-y-1">
              <li v-for="err in activeTag.errors" :key="err">
                {{ err }}
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-neutral-500 mb-1">Tag Title</label>
          <input
            v-model="activeTag.title"
            type="text"
            placeholder="Enter tag title..."
            class="w-full bg-transparent border-b-2 border-neutral-300 dark:border-neutral-700 text-2xl font-bold text-black dark:text-white placeholder-neutral-400 focus:border-primary-500 focus:outline-none py-2 transition-colors"
          >
        </div>
        <div>
          <label class="block text-xs font-bold text-neutral-500 mb-1">Description</label>
          <textarea
            v-model="activeTag.description"
            rows="2"
            placeholder="Optional description..."
            class="w-full bg-neutral-100 dark:bg-neutral-800 border border-neutral-200 dark:border-neutral-700 rounded-sm px-4 py-3 text-sm text-neutral-700 dark:text-neutral-300 placeholder-neutral-400 focus:border-primary-500 focus:outline-none transition-colors resize-none"
          />
        </div>
      </div>

      <div>
        <label class="block text-xs font-bold text-neutral-500 mb-3">Color</label>
        <div class="bg-neutral-100 dark:bg-neutral-800/50 p-4 rounded-sm border border-neutral-200 dark:border-neutral-700">
          <div class="flex flex-wrap gap-3 mb-4">
            <button
              v-for="color in PRESET_COLORS"
              :key="color"
              class="w-8 h-8 rounded-sm shadow-sm transition-transform hover:scale-110"
              :class="{ 'ring-2 ring-primary-500 ring-offset-2 ring-offset-white dark:ring-offset-neutral-800': activeTag.color === color }"
              :style="{ backgroundColor: color }"
              @click="activeTag.color = color"
            />
          </div>
          <div class="flex items-center gap-3">
            <input
              v-model="activeTag.color"
              type="color"
              class="h-10 w-10 bg-transparent rounded-sm cursor-pointer border-0 p-0"
            >
            <input
              v-model="activeTag.color"
              type="text"
              class="flex-1 bg-transparent border border-neutral-300 dark:border-neutral-600 rounded-sm px-3 py-2 text-sm font-mono text-black dark:text-white focus:outline-none focus:border-primary-500"
            >
            <div
              class="w-20 h-10 rounded-sm border border-neutral-300 dark:border-neutral-600"
              :style="{ backgroundColor: activeTag.color }"
            />
          </div>
        </div>
      </div>

      <div class="bg-neutral-100 dark:bg-neutral-800/50 p-4 rounded-sm border border-neutral-200 dark:border-neutral-700">
        <label class="block text-xs font-bold uppercase text-neutral-500 mb-3">Reorder</label>
        <div class="flex items-center gap-2">
          <UButton
            label="Move Up"
            icon="i-lucide-arrow-up"
            color="neutral"
            variant="soft"
            size="sm"
            @click="moveTag(activeTag.id, 'up')"
          />
          <UButton
            label="Move Down"
            icon="i-lucide-arrow-down"
            color="neutral"
            variant="soft"
            size="sm"
            @click="moveTag(activeTag.id, 'down')"
          />
        </div>
      </div>
    </div>
  </div>
</template>
