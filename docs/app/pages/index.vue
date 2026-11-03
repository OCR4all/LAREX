<script setup lang="ts">
const title = 'LAREX Documentation'
const description = 'LAREX is a self-hostable, open-source web application for organizing scanned documents, annotating layouts, running OCR, reviewing transcriptions, and curating datasets.'

useSeo({
  title,
  description,
  type: 'website'
})

// The site title already names the docs; skip the "%s - LAREX Documentation" suffix here
useSeoMeta({ titleTemplate: '%s' })

defineOgImage('Landing', {
  title,
  description
})
</script>

<template>
  <UContainer>
    <DocsLanding />
  </UContainer>
</template>
