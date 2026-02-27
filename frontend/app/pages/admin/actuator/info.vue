<script setup lang="ts">
interface InfoResponse {
  app?: { name?: string, version?: string, description?: string, [key: string]: any }
  build?: { version?: string, timestamp?: string, artifact?: string, group?: string, [key: string]: any }
  git?: { branch?: string, commit?: { id?: string, time?: string, [key: string]: any }, [key: string]: any }
  java?: { version?: string, vendor?: string, [key: string]: any }
  [key: string]: any
}

const { data: info, refresh: refreshInfo, pending: infoLoading, error: infoError } = await useLazyFetch<InfoResponse>('/api/actuator/info')

const formatTimestamp = (timestamp: string | undefined) => {
  if (!timestamp) return 'N/A'
  try { return new Date(timestamp).toLocaleString() } catch { return timestamp }
}

const formatObject = (obj: any): string => typeof obj === 'object' && obj !== null ? JSON.stringify(obj, null, 2) : String(obj)
</script>

<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <div>
        <h1 class="text-2xl font-bold">
          Application Information
        </h1>
        <p class="text-sm text-muted mt-1">
          General information about the application build and runtime
        </p>
      </div>
      <UButton icon="i-lucide-refresh-cw" :loading="infoLoading" @click="refreshInfo">
        Refresh
      </UButton>
    </div>

    <UAlert
      v-if="infoError"
      icon="i-lucide-alert-circle"
      color="error"
      variant="soft"
      title="Failed to load application info"
      :description="infoError.data?.message || 'Unable to fetch application information'"
      class="mb-6"
    />

    <div v-if="infoLoading" class="grid gap-6 md:grid-cols-2">
      <USkeleton class="h-40 w-full" />
      <USkeleton class="h-40 w-full" />
    </div>

    <div v-else-if="info" class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
      <UCard v-if="info.app">
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-app-window" class="w-5 h-5 text-blue-500" />
            <h2 class="text-lg font-semibold">
              Application
            </h2>
          </div>
        </template>
        <div class="space-y-3">
          <div v-if="info.app.name">
            <dt class="text-sm font-medium text-muted">
              Name
            </dt><dd class="text-sm font-mono">
              {{ info.app.name }}
            </dd>
          </div>
          <div v-if="info.app.version">
            <dt class="text-sm font-medium text-muted">
              Version
            </dt><dd class="text-sm font-mono">
              {{ info.app.version }}
            </dd>
          </div>
          <div v-if="info.app.description">
            <dt class="text-sm font-medium text-muted">
              Description
            </dt><dd class="text-sm">
              {{ info.app.description }}
            </dd>
          </div>
        </div>
      </UCard>

      <UCard v-if="info.build">
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-hammer" class="w-5 h-5 text-green-500" />
            <h2 class="text-lg font-semibold">
              Build
            </h2>
          </div>
        </template>
        <div class="space-y-3">
          <div v-if="info.build.version">
            <dt class="text-sm font-medium text-muted">
              Version
            </dt><dd class="text-sm font-mono">
              {{ info.build.version }}
            </dd>
          </div>
          <div v-if="info.build.timestamp">
            <dt class="text-sm font-medium text-muted">
              Timestamp
            </dt><dd class="text-sm">
              {{ formatTimestamp(info.build.timestamp) }}
            </dd>
          </div>
          <div v-if="info.build.artifact">
            <dt class="text-sm font-medium text-muted">
              Artifact
            </dt><dd class="text-sm font-mono">
              {{ info.build.artifact }}
            </dd>
          </div>
        </div>
      </UCard>

      <UCard v-if="info.git">
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-git-branch" class="w-5 h-5 text-orange-500" />
            <h2 class="text-lg font-semibold">
              Git
            </h2>
          </div>
        </template>
        <div class="space-y-3">
          <div v-if="info.git.branch">
            <dt class="text-sm font-medium text-muted">
              Branch
            </dt><dd class="text-sm font-mono">
              {{ info.git.branch }}
            </dd>
          </div>
          <div v-if="info.git.commit?.id">
            <dt class="text-sm font-medium text-muted">
              Commit ID
            </dt><dd class="text-sm font-mono">
              {{ info.git.commit.id.substring(0, 8) }}
            </dd>
          </div>
          <div v-if="info.git.commit?.time">
            <dt class="text-sm font-medium text-muted">
              Commit Time
            </dt><dd class="text-sm">
              {{ formatTimestamp(info.git.commit.time) }}
            </dd>
          </div>
        </div>
      </UCard>

      <UCard v-if="info.java">
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-coffee" class="w-5 h-5 text-red-500" />
            <h2 class="text-lg font-semibold">
              Java Runtime
            </h2>
          </div>
        </template>
        <div class="space-y-3">
          <div v-if="info.java.version">
            <dt class="text-sm font-medium text-muted">
              Version
            </dt><dd class="text-sm font-mono">
              {{ info.java.version }}
            </dd>
          </div>
          <div v-if="info.java.vendor">
            <dt class="text-sm font-medium text-muted">
              Vendor
            </dt><dd class="text-sm">
              {{ info.java.vendor }}
            </dd>
          </div>
        </div>
      </UCard>

      <UCard v-for="[key, value] in Object.entries(info).filter(([k]) => !['app', 'build', 'git', 'java'].includes(k))" :key="key">
        <template #header>
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-info" class="w-5 h-5 text-purple-500" />
            <h2 class="text-lg font-semibold capitalize">
              {{ key.replace(/([A-Z])/g, ' $1').trim() }}
            </h2>
          </div>
        </template>
        <pre class="text-xs text-muted whitespace-pre-wrap break-all max-h-32 overflow-y-auto bg-elevated p-2 rounded">{{ formatObject(value) }}</pre>
      </UCard>
    </div>

    <div v-else class="text-center py-12">
      <UIcon name="i-lucide-info" class="w-12 h-12 text-muted mx-auto mb-4" />
      <h3 class="text-lg font-semibold mb-2">
        No application information available
      </h3>
      <p class="text-muted">
        The info actuator endpoint returned no data
      </p>
    </div>
  </div>
</template>
