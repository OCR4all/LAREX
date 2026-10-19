<script setup lang="ts">
import DocsHeroHeadline from './DocsHeroHeadline.vue'

const primaryLinks = [
  { label: 'Annotate your first page', to: '/getting-started/first-project', icon: 'i-lucide-arrow-right' },
  { label: 'Install LAREX', to: '/getting-started/installation', icon: 'i-lucide-download' }
]

const audienceCards = [
  { title: 'Annotate and review', description: 'Import a document, correct layout and transcription, and save work you can reuse.', icon: 'i-lucide-pen-tool', to: '/user-guide/overview' },
  { title: 'Coordinate a team', description: 'Set workspace conventions, distribute page Tasks, and curate datasets and releases.', icon: 'i-lucide-users', to: '/user-guide/tasks' },
  { title: 'Run an instance', description: 'Install and operate LAREX, manage accounts and storage, and configure processors.', icon: 'i-lucide-server', to: '/deployment/production-deploy' },
  { title: 'Develop and extend', description: 'Run the development stack, find commands, or build an Action processor with the SDK.', icon: 'i-lucide-code-2', to: '/development/docker-setup' }
]

const featureLinks = [
  { label: 'Run Actions', description: 'Process documents and inspect results.', icon: 'i-lucide-plug', to: '/user-guide/larex-actions' },
  { label: 'Build a dataset', description: 'Choose files, splits, and frozen releases.', icon: 'i-lucide-database', to: '/user-guide/datasets' },
  { label: 'Find a feature', description: 'Follow the user guide by workflow.', icon: 'i-lucide-book-open', to: '/user-guide/overview' },
  { label: 'Get unstuck', description: 'Resolve imports, locks, saves, and search.', icon: 'i-lucide-life-buoy', to: '/user-guide/troubleshooting' }
]

const workflow = [
  { title: 'Organize and import', to: '/user-guide/projects', description: 'Choose a workspace, create a project, and add your material.' },
  { title: 'Annotate and correct', to: '/user-guide/text-review', description: 'Review layout and text against the page image.' },
  { title: 'Review together', to: '/user-guide/tasks', description: 'Complete page Tasks and keep track of team progress.' },
  { title: 'Export and reuse', to: '/user-guide/exports-and-sharing', description: 'Deliver an export or freeze a release for downstream work.' }
]
</script>

<template>
  <div class="pb-18">
    <section class="relative mt-6 min-h-[26rem] overflow-hidden rounded-lg shadow-2xl shadow-smoke-950/20 lg:min-h-[30rem]">
      <DocsHeroDither />

      <div class="relative flex min-h-[26rem] items-center lg:min-h-[30rem]">
        <div class="mx-auto w-full max-w-7xl px-5 py-10 sm:px-8 sm:py-12 lg:px-10">
          <div class="max-w-6xl">
            <UBadge color="neutral" variant="soft" class="mb-6 bg-white/12 text-white ring-white/25 backdrop-blur">
              Documentation
            </UBadge>
            <DocsHeroHeadline />
            <p class="mt-6 max-w-4xl text-lg leading-8 text-smoke-100 sm:text-xl">
              LAREX is a self-hostable, open-source web application for scanned documents. Organize collections, annotate layouts, run OCR, review transcriptions together, and build training datasets in your browser.
            </p>

            <div class="mt-9 flex flex-wrap items-center gap-3">
              <UButton
                v-for="link in primaryLinks"
                :key="link.to"
                :to="link.to"
                :label="link.label"
                :icon="link.icon"
                size="lg"
                color="neutral"
                :variant="link.to.endsWith('first-project') ? 'solid' : 'outline'"
                :class="link.to.endsWith('first-project')
                  ? 'bg-white text-smoke-950 hover:bg-smoke-100'
                  : 'border-white/35 bg-white/10 text-white ring-white/35 hover:bg-white/18'"
              />
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="mx-auto max-w-7xl px-0 py-16 sm:py-20">
      <div class="mb-8 max-w-2xl">
        <h2 class="text-3xl font-semibold text-highlighted sm:text-4xl">
          Start with the work you need to do.
        </h2>
        <p class="mt-3 text-muted">
          Joining an existing instance? Go straight to the user guide. Installing for a team or contributing code? Follow the operator or developer path.
        </p>
      </div>

      <div class="mb-4 grid gap-4 sm:grid-cols-2">
        <UPageCard
          v-for="card in audienceCards"
          :key="card.to"
          :to="card.to"
          :title="card.title"
          :description="card.description"
          :icon="card.icon"
          variant="subtle"
        />
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <UPageCard
          v-for="link in featureLinks"
          :key="link.to"
          :to="link.to"
          :title="link.label"
          :description="link.description"
          :icon="link.icon"
          variant="subtle"
        />
      </div>
    </section>

    <section class="mx-auto max-w-7xl border-y border-default py-12" aria-labelledby="workflow-heading">
      <h2 id="workflow-heading" class="text-2xl font-semibold text-highlighted">Follow a document through LAREX</h2>
      <p class="mt-3 max-w-3xl text-muted">Each guide picks up where the previous stage leaves off. Learn the <NuxtLink to="/getting-started/concepts" class="underline underline-offset-4">core concepts</NuxtLink> if the workspace, file variants, or releases are unfamiliar.</p>
      <ol class="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <li v-for="(step, index) in workflow" :key="step.to">
          <span class="text-sm font-medium text-muted">{{ index + 1 }} / {{ workflow.length }}</span>
          <h3 class="mt-2 font-semibold text-highlighted"><NuxtLink :to="step.to" class="underline decoration-muted underline-offset-4 hover:decoration-current">{{ step.title }}</NuxtLink></h3>
          <p class="mt-2 text-sm leading-6 text-muted">{{ step.description }}</p>
        </li>
      </ol>
    </section>
  </div>
</template>
