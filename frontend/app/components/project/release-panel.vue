<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { ProjectPackageRelease } from '@/types/project-package-release'

const props = defineProps<{
  releases: ProjectPackageRelease[]
  pending: boolean
  error?: unknown
  summary: string
  latestReleaseId?: string | null
  canShare: boolean
}>()

const emit = defineEmits<{
  create: []
  share: [release: ProjectPackageRelease]
  download: [release: ProjectPackageRelease]
}>()

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium'
  }).format(new Date(value))
}

function getReleaseCardItems(release: ProjectPackageRelease): DropdownMenuItem[] {
  const isReady = release.status === 'READY'
  const items: DropdownMenuItem[] = [
    {
      label: 'Download',
      icon: 'i-lucide-download',
      disabled: !isReady,
      onSelect: () => emit('download', release)
    }
  ]

  if (props.canShare) {
    items.unshift({
      label: 'Share',
      icon: 'i-lucide-key-round',
      disabled: !isReady,
      onSelect: () => emit('share', release)
    })
  }

  return items
}
</script>

<template>
  <div class="h-full space-y-5 p-4">
    <div class="flex items-center justify-between gap-3">
      <div class="flex items-center gap-2 text-sm font-semibold text-highlighted">
        <UIcon name="i-lucide-box" class="size-4 text-muted" />
        <span>Releases</span>
      </div>
      <UButton
        v-if="canShare"
        color="neutral"
        variant="solid"
        size="xs"
        icon="i-lucide-plus"
        @click="emit('create')"
      >
        New Release
      </UButton>
    </div>

    <p class="text-xs text-muted">
      {{ summary }}
    </p>

    <USeparator />

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-circle"
      :title="extractApiErrorMessage(error, 'Failed to load releases')"
    />

    <div v-if="pending && releases.length === 0" class="flex items-center gap-2 py-3 text-sm text-muted">
      <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />
      <span>Loading releases...</span>
    </div>

    <div v-else-if="releases.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
      No releases yet.
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="release in releases"
        :key="release.id"
        :class="[
          'px-3 py-3',
          release.id === latestReleaseId
            ? 'rounded-xl border border-default bg-default'
            : 'rounded-lg'
        ]"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0 flex items-center gap-2">
            <UIcon name="i-lucide-git-branch" class="size-4 text-muted" />
            <p class="truncate text-sm font-semibold text-highlighted">
              {{ release.versionTag }}
            </p>
          </div>
          <div class="flex items-center gap-1">
            <UBadge
              v-if="release.id === latestReleaseId"
              color="success"
              variant="soft"
              size="sm"
            >
              Latest
            </UBadge>

            <UDropdownMenu
              :items="getReleaseCardItems(release)"
              :content="{ align: 'end' }"
            >
              <UButton
                icon="i-lucide-ellipsis-vertical"
                color="neutral"
                variant="ghost"
                size="xs"
              />
            </UDropdownMenu>
          </div>
        </div>

        <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
          <span class="inline-flex items-center gap-1">
            <UIcon name="i-lucide-clock-3" class="size-3.5" />
            {{ formatDate(release.created) }}
          </span>
          <span class="inline-flex items-center gap-1">
            <UIcon name="i-lucide-file-text" class="size-3.5" />
            {{ release.pageCount }} pages
          </span>
          <span class="inline-flex items-center gap-1">
            <UIcon name="i-lucide-history" class="size-3.5" />
            {{ release.includeXmlHistory ? 'XML history included' : 'Current XML only' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
