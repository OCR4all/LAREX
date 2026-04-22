---
title: LAREX Documentation
description: Documentation for LAREX - Layout Analysis and Recognition application.
navigation:
  icon: i-lucide-book-open
seo:
  title: LAREX Documentation
  description: Complete documentation for LAREX web application.
---

::u-page-hero
---
class: relative isolate pt-12 sm:pt-16
ui:
  container: relative z-10
  wrapper: relative z-10
  title: text-black
  description: text-black font-medium
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
      style: background-image: url('/hero.webp');
      ---
      :::
    :::
  :::

#title
LAREX Documentation

#description
Complete documentation for developers and users of LAREX.

#links
  :::u-button
  ---
  color: neutral
  size: xl
  to: /getting-started/introduction
  trailing-icon: i-lucide-arrow-right
  ---
  Technical Documentation
  :::

  :::u-button
  ---
  color: neutral
  size: xl
  to: /user-guide/workspace-library
  variant: outline
  trailing-icon: i-lucide-arrow-right
  ---
  User Guide
  :::
::

::warning
Documentation status: this documentation set is currently generated automatically.

Content may be incomplete or inaccurate and is not guaranteed to be 100% correct.

A human-authored overhaul is planned soon.
::

::u-page-section
#title
Technical Documentation

#description
For developers, system administrators, and anyone setting up or extending LAREX.

  :::u-page-grid
    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /getting-started/introduction
    icon: i-lucide-rocket
    ---
    #title
    Getting Started

    #description
    Introduction to LAREX, installation guide, project structure, and configuration.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /development/local-setup
    icon: i-lucide-code
    ---
    #title
    Development

    #description
    Local development setup, Docker-based development, and command reference.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /deployment/production-deploy
    icon: i-lucide-server
    ---
    #title
    Deployment

    #description
    Production deployment guide, environment variables, service reference, and CI/CD.
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
    Solutions to common issues during development and production deployment.
    ::::
  :::
::

::u-page-section
#title
User Guide

#description
For end users who annotate and analyze documents in LAREX.

  :::u-page-grid
    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/workspace-library
    icon: i-lucide-folder
    ---
    #title
    Workspace and Library

    #description
    Workspaces, project library, and project management basics.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/projects
    icon: i-lucide-file-text
    ---
    #title
    Projects

    #description
    Manage project pages, uploads, IIIF imports, filters, and project actions.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/editor-interface
    icon: i-lucide-edit-3
    ---
    #title
    Editor Interface

    #description
    Editor layout, sidebars, panels, and core controls.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/workspace-search
    icon: i-lucide-search
    ---
    #title
    Workspace Search

    #description
    Search persisted transcription text across projects and pages with ranked hits and clustered results.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/annotation-tools
    icon: i-lucide-pen-tool
    ---
    #title
    Annotation Tools

    #description
    Create and edit regions, textlines, and baselines.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/tag-sets
    icon: i-lucide-tags
    ---
    #title
    Tag Sets

    #description
    Build and manage hierarchical tags for projects and pages.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/label-sets
    icon: i-lucide-code-xml
    ---
    #title
    Label Sets

    #description
    Define and manage label structures for annotation workflows.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/virtual-keyboard
    icon: i-lucide-keyboard
    ---
    #title
    Virtual Keyboard

    #description
    Use the virtual keyboard for special characters and diacritics.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/dictionaries
    icon: i-lucide-book-copy
    ---
    #title
    Dictionaries

    #description
    Create controlled dictionaries for token validation, suggestions, and editor review workflows.
    ::::

    ::::u-page-card
    ---
    class: col-span-2 md:col-span-1
    to: /user-guide/keyboard-shortcuts
    icon: i-lucide-command
    ---
    #title
    Keyboard Shortcuts

    #description
    Current shortcut reference for the editor and text workflows.
    ::::
  :::
::
