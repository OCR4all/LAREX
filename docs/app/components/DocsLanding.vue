<script setup lang="ts">
const primaryLinks = [
  {
    label: 'Start reading',
    to: '/getting-started/introduction',
    icon: 'i-lucide-arrow-right'
  },
  {
    label: 'Open user guide',
    to: '/user-guide/workspace-library',
    icon: 'i-lucide-book-open'
  }
]

const audienceCards = [
  {
    title: 'Set up LAREX',
    description: 'Install the stack, understand the services, and run a local development environment.',
    icon: 'i-lucide-terminal',
    to: '/getting-started/introduction'
  },
  {
    title: 'Operate deployments',
    description: 'Configure production, Docker services, environment variables, and release checks.',
    icon: 'i-lucide-server',
    to: '/deployment/production-deploy'
  },
  {
    title: 'Annotate documents',
    description: 'Work with projects, pages, editor panels, annotation tools, tags, labels, and dictionaries.',
    icon: 'i-lucide-pen-tool',
    to: '/user-guide/projects'
  }
]

const featureLinks = [
  {
    label: 'Development',
    description: 'Local setup, commands, architecture, and database workflows.',
    icon: 'i-lucide-code-2',
    to: '/development/local-setup'
  },
  {
    label: 'Troubleshooting',
    description: 'Common issues across local development and production.',
    icon: 'i-lucide-wrench',
    to: '/troubleshooting/common-issues'
  },
  {
    label: 'Keyboard shortcuts',
    description: 'Current editor shortcuts for faster layout and text work.',
    icon: 'i-lucide-command',
    to: '/user-guide/keyboard-shortcuts'
  },
  {
    label: 'Workspace search',
    description: 'Search transcription text across projects and pages.',
    icon: 'i-lucide-search',
    to: '/user-guide/workspace-search'
  }
]
</script>

<template>
  <div class="pb-18">
    <section class="relative mt-6 min-h-[26rem] overflow-hidden rounded-lg border border-white/20 shadow-2xl shadow-smoke-950/20 lg:min-h-[30rem]">
      <DocsHeroGradient />

      <div class="relative flex min-h-[26rem] items-center lg:min-h-[30rem]">
        <div class="mx-auto w-full max-w-7xl px-5 py-10 sm:px-8 sm:py-12 lg:px-10">
          <div class="max-w-6xl">
            <UBadge color="neutral" variant="soft" class="mb-6 bg-white/12 text-white ring-white/25 backdrop-blur">
              LAREX documentation
            </UBadge>
            <h1 class="max-w-5xl text-4xl font-bold text-balance text-white sm:text-5xl lg:text-6xl">
              Document annotation, data management, and processor execution in <span class="font-black">LAREX</span>
            </h1>
            <p class="mt-6 max-w-4xl text-lg leading-8 text-smoke-100 sm:text-xl">
              Practical guides for installing, developing, deploying, and using LAREX across layout analysis, transcription workflows, LAREX Actions, and processor-backed project operations.
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
                :variant="link.to.includes('getting-started') ? 'solid' : 'outline'"
                :class="link.to.includes('getting-started')
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
          Move through the docs by task.
        </h2>
        <p class="mt-3 text-muted">
          The guides are organized around setup, operation, and annotation work, with direct routes into the pages people reach for most often.
        </p>
      </div>

      <div class="mb-4 grid gap-4 lg:grid-cols-3">
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

    <section class="mx-auto max-w-7xl border-y border-default py-12">
      <div class="grid gap-6 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
        <div>
          <h2 class="text-2xl font-semibold text-highlighted">
            Documentation status
          </h2>
          <p class="mt-3 max-w-3xl text-muted">
            This documentation set is being migrated from Docus and some pages still need human review. The content structure is preserved while the shell moves to plain Nuxt Content.
          </p>
        </div>
        <UButton
          to="/troubleshooting/common-issues"
          label="Find known issues"
          icon="i-lucide-circle-help"
          color="neutral"
          variant="outline"
        />
      </div>
    </section>
  </div>
</template>
