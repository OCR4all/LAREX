<script setup lang="ts">
import type { TagNode } from '~/types/tag-set'
import UiColorTag from '@/components/ui/color-tag.vue'

const { activeTag, tags } = useTagSetBuilder()

const getTagPath = computed(() => {
  if (!activeTag.value) return []

  const path: TagNode[] = []

  const findPath = (tagList: TagNode[], target: string, currentPath: TagNode[]): boolean => {
    for (const tag of tagList) {
      const newPath = [...currentPath, tag]
      if (tag.id === target) {
        path.push(...newPath)
        return true
      }
      if (tag.children && findPath(tag.children, target, newPath)) {
        return true
      }
    }
    return false
  }

  findPath(tags.value, activeTag.value.id, [])
  return path
})

const childCount = computed(() => {
  if (!activeTag.value?.children) return 0
  return activeTag.value.children.length
})

const descendantCount = computed(() => {
  if (!activeTag.value) return 0

  let count = 0
  const countDescendants = (children: TagNode[] | undefined) => {
    if (!children) return
    for (const child of children) {
      count++
      countDescendants(child.children)
    }
  }

  countDescendants(activeTag.value.children)
  return count
})
</script>

<template>
  <div class="w-full lg:w-96 bg-neutral-50 dark:bg-neutral-950 border-l border-neutral-200 dark:border-neutral-800 p-6 flex flex-col relative">
    <div class="absolute top-2 left-4 text-xs font-bold text-neutral-500 tracking-widest">
      Preview
    </div>

    <div v-if="activeTag" class="flex-1 flex flex-col items-center justify-center">
      <div
        class="w-full max-w-xs p-4 rounded-sm border-2 shadow-lg"
        :style="{
          backgroundColor: activeTag.color + '15',
          borderColor: activeTag.color
        }"
      >
        <div class="flex items-center gap-3 mb-3">
          <div
            class="w-10 h-10 rounded-sm flex items-center justify-center"
            :style="{ backgroundColor: activeTag.color }"
          >
            <UIcon
              name="i-lucide-tag"
              class="w-5 h-5 text-white"
            />
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-bold text-black dark:text-white truncate">
              {{ activeTag.title || 'Untitled' }}
            </h3>
            <p v-if="activeTag.description" class="text-xs text-neutral-500 truncate">
              {{ activeTag.description }}
            </p>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-2 text-xs">
          <div class="bg-white dark:bg-neutral-800 rounded-sm px-2 py-1">
            <span class="text-neutral-500">Children:</span>
            <span class="ml-1 font-medium text-black dark:text-white">{{ childCount }}</span>
          </div>
          <div class="bg-white dark:bg-neutral-800 rounded-sm px-2 py-1">
            <span class="text-neutral-500">Descendants:</span>
            <span class="ml-1 font-medium text-black dark:text-white">{{ descendantCount }}</span>
          </div>
        </div>
      </div>

      <div v-if="getTagPath.length > 1" class="mt-6 w-full max-w-xs">
        <h4 class="text-xs font-bold text-neutral-500 mb-2">
          Hierarchy Path
        </h4>
        <div class="bg-white dark:bg-neutral-800 rounded-sm border border-neutral-200 dark:border-neutral-700 p-3">
          <div class="flex flex-wrap items-center gap-1 text-sm">
            <template v-for="(tag, idx) in getTagPath" :key="tag.id">
              <UiColorTag
                :color="tag.color"
                :variant="tag.id === activeTag.id ? 'solid' : 'subtle'"
                size="sm"
                dot
              >
                {{ tag.title }}
              </UiColorTag>
              <UIcon
                v-if="idx < getTagPath.length - 1"
                name="i-lucide-chevron-right"
                class="w-3 h-3 text-neutral-400"
              />
            </template>
          </div>
        </div>
      </div>

      <div v-if="childCount > 0" class="mt-6 w-full max-w-xs">
        <h4 class="text-xs font-bold text-neutral-500 mb-2">
          Direct Children
        </h4>
        <div class="bg-white dark:bg-neutral-800 rounded-sm border border-neutral-200 dark:border-neutral-700 p-2 space-y-1 max-h-40 overflow-y-auto">
          <div
            v-for="child in activeTag.children"
            :key="child.id"
            class="flex items-center gap-2 px-2 py-1 rounded-sm hover:bg-neutral-50 dark:hover:bg-neutral-700"
          >
            <UiColorTag
              :color="child.color"
              variant="subtle"
              size="sm"
              dot
              class="min-w-0"
            >
              {{ child.title }}
            </UiColorTag>
            <span v-if="child.children?.length" class="text-xs text-neutral-400 ml-auto">
              +{{ child.children.length }}
            </span>
          </div>
        </div>
      </div>

      <div class="mt-6 w-full max-w-xs">
        <h4 class="text-xs font-bold text-neutral-500 mb-2">
          Badge Variants
        </h4>
        <div class="bg-white dark:bg-neutral-800 rounded-sm border border-neutral-200 dark:border-neutral-700 p-3 space-y-2">
          <div class="flex items-center justify-between text-xs">
            <span class="text-neutral-500">HEX</span>
            <code class="font-mono text-black dark:text-white">{{ activeTag.color.toUpperCase() }}</code>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <UiColorTag :color="activeTag.color" variant="solid" size="sm">
              Solid
            </UiColorTag>
            <UiColorTag :color="activeTag.color" variant="subtle" size="sm">
              Subtle
            </UiColorTag>
            <UiColorTag :color="activeTag.color" variant="outline" size="sm">
              Outline
            </UiColorTag>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <UiColorTag
              :color="activeTag.color"
              variant="solid"
              size="sm"
              dot
            >
              Solid Dot
            </UiColorTag>
            <UiColorTag
              :color="activeTag.color"
              variant="subtle"
              size="sm"
              dot
            >
              Subtle Dot
            </UiColorTag>
            <UiColorTag
              :color="activeTag.color"
              variant="outline"
              size="sm"
              dot
            >
              Outline Dot
            </UiColorTag>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="flex-1 flex flex-col items-center justify-center text-neutral-500">
      <UIcon name="i-lucide-eye" class="w-12 h-12 mb-3 opacity-30" />
      <p class="text-sm">
        Select a tag to preview
      </p>
    </div>
  </div>
</template>
