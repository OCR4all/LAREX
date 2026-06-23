---
title: LAREX Documentation
description: Guides and references for LAREX users, administrators, developers, and Action processor authors.
navigation:
  icon: i-lucide-book-open
seo:
  title: LAREX Documentation
  description: Product documentation for LAREX data management, annotation, and processor execution workflows.
---

::u-page-hero
---
class: relative isolate pt-12 sm:pt-16
ui:
  container: relative z-10
  wrapper: relative z-10
  title: text-black max-w-5xl
  description: text-black font-medium max-w-3xl
---
#top
  :::div
  ---
  class: absolute inset-0 -z-10 pointer-events-none pt-4
  ---
    :::u-container
    ---
    class: h-full
    ---
      :::div
      ---
      class: h-full overflow-hidden rounded-lg bg-cover bg-center bg-no-repeat
      ---
      :::
    :::
  :::

#title
LAREX Documentation

#description
Guides for managing facsimile projects, annotating PAGE XML, configuring workspaces, and running external processors through LAREX Actions.

#links
  :::u-button
  ---
  color: neutral
  size: xl
  to: /user-guide/workspace-library
  trailing-icon: i-lucide-arrow-right
  ---
  Start Using LAREX
  :::

  :::u-button
  ---
  color: neutral
  size: xl
  to: /actions/overview
  variant: outline
  trailing-icon: i-lucide-arrow-right
  ---
  LAREX Actions
  :::
::

::u-page-section
#title
Choose Your Path

#description
The documentation is organized around the work people actually do in LAREX.

  :::u-page-grid
    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/workspace-library
    icon: i-lucide-folder
    ---
    #title
    Annotators and Curators

    #description
    Learn how to organize projects, upload pages, annotate layouts, correct text, search workspaces, and review resources.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /actions/overview
    icon: i-lucide-wand-sparkles
    ---
    #title
    LAREX Actions

    #description
    Set up processor definitions, run OCR/HTR or layout processors, and build Action processors with the Python SDK.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /getting-started/introduction
    icon: i-lucide-rocket
    ---
    #title
    Developers

    #description
    Understand the application architecture, local setup, service boundaries, and command reference.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/admin-dashboard/overview
    icon: i-lucide-server
    ---
    #title
    Administrators

    #description
    Manage users, quotas, workspaces, storage, imports, monitoring, and global Action operations.
    ::::
  :::
::

::u-page-section
#title
Core Workflows

  :::u-page-grid
    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/projects
    icon: i-lucide-file-text
    ---
    #title
    Projects and Pages

    #description
    Create projects, import IIIF manifests, upload images/XML/PDFs, resolve conflicts, and manage page metadata.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/editor-interface
    icon: i-lucide-edit-3
    ---
    #title
    Editor

    #description
    Work with the canvas, sidebars, annotation tools, text mode, metadata, relations, and saved PAGE XML.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/larex-actions
    icon: i-lucide-play
    ---
    #title
    Run Processors

    #description
    Start Actions from projects or editor selections, monitor queues, cancel runs, retry failures, and inspect history.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/workspace-search
    icon: i-lucide-search
    ---
    #title
    Search and Review

    #description
    Search transcriptions across workspaces and use dictionaries, normalization profiles, and validation rulesets.
    ::::
  :::
::

::u-page-section
#title
References

  :::u-page-grid
    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /actions/yaml-reference
    icon: i-lucide-file-code
    ---
    #title
    Actions YAML

    #description
    Complete schema, defaults, limits, validation rules, and examples for Action definitions.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /actions/processor-sdk
    icon: i-lucide-package
    ---
    #title
    Processor SDK

    #description
    Build FastAPI or framework-neutral processors with signed dispatch verification and cooperative cancellation.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /development/command-reference
    icon: i-lucide-terminal
    ---
    #title
    Commands

    #description
    Taskfile, Docker Compose, backend, frontend, docs, and Keycloak theme commands.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /troubleshooting/common-issues
    icon: i-lucide-wrench
    ---
    #title
    Troubleshooting

    #description
    Common local setup, Docker, authentication, upload, and processor execution problems.
    ::::
  :::
::
