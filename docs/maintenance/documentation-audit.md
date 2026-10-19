# Documentation audit and implementation plan

Audited against the working source on 18 October 2026. This is a maintainer record; the published guides live in [docs/content](../../docs/content). Source paths below are relative to the repository root. This review concerns implemented behavior, not a promise that every deployment enables every feature.

## Findings

The original documentation contains 47 Markdown pages and about 7,200 lines, but volume hides uneven coverage. The largest sections explain infrastructure and Actions; the main product journey has no tutorial. Navigation puts development, deployment, and troubleshooting before the user guide. The homepage offers overlapping setup/developer paths and a migration notice instead of a clear next step. Its Markdown content is different from the Vue component actually rendered. Search and previous/next controls exist, but there is no explicit reading path or shared conceptual foundation. [docs/README.md](../../docs/README.md) still describes the Docus starter even though the site uses plain Nuxt Content.

| Priority | Evidence in source | Documentation defect | Required change |
| --- | --- | --- | --- |
| High | [frontend/app/pages/datasets/[id].vue](../../frontend/app/pages/datasets/[id].vue), [frontend/app/components/project/slideover/add-to-dataset.vue](../../frontend/app/components/project/slideover/add-to-dataset.vue), [backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/dataset/DatasetService.java](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/dataset/DatasetService.java) | No dataset guide: links/copies, splits, copy editing, validation, releases and sharing are invisible. | Add a complete curation-to-release workflow; distinguish editable copies from immutable releases. |
| High | [frontend/app/pages/tasks/index.vue](../../frontend/app/pages/tasks/index.vue), [frontend/app/components/task/slideover/edit.vue](../../frontend/app/components/task/slideover/edit.vue), [backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/page/PageWorkflowService.java](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/page/PageWorkflowService.java) | No guide to Assignments, page Tasks, workflow state or synchronized completion. | Explain UI terminology, assignment creation, work completion, board/table views and state synchronization. |
| High | [frontend/app/components/editor/toolbar.vue](../../frontend/app/components/editor/toolbar.vue), [frontend/app/composables/editor/shortcut-registry.ts](../../frontend/app/composables/editor/shortcut-registry.ts), [frontend/app/components/editor/keyboard-shortcuts-help.vue](../../frontend/app/components/editor/keyboard-shortcuts-help.vue) | Five views documented; there are six. Customizable shortcuts omitted. | Document Full text, key 6, customization, conflict handling and reset. |
| High | [frontend/app/types/validation-ruleset.ts](../../frontend/app/types/validation-ruleset.ts), [backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/validation/ValidationRulesetService.java](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/validation/ValidationRulesetService.java) | Rulesets are described as structural/metadata validators; implementation matches regex against indexed, optionally normalized text. | Replace speculative examples with supported fields, an executable pattern and scope limitations. |
| High | [frontend/app/components/project/slideover/batch-normalization.vue](../../frontend/app/components/project/slideover/batch-normalization.vue), [frontend/app/types/normalization-profile.ts](../../frontend/app/types/normalization-profile.ts) | Normalization described as a comparison aid without explaining Apply rewrites text. | Separate profile preview, source preview, and mutation; explain variants and recovery. |
| High | [frontend/app/components/workspace/modal/project-default-propagation.vue](../../frontend/app/components/workspace/modal/project-default-propagation.vue) | Defaults said to affect only new projects. | Explain future-only, fill-missing and replace-all propagation, including locked projects. |
| Medium | [frontend/app/components/editor/version-history-slideover.vue](../../frontend/app/components/editor/version-history-slideover.vue), [frontend/app/components/editor/modal/page-version-compare.vue](../../frontend/app/components/editor/modal/page-version-compare.vue), [backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/version/PageXmlVersionService.java](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/version/PageXmlVersionService.java) | Versions listed but no comparison/recovery procedure or retention boundary. | Add save/compare/restore guide with unsaved-change behavior and backup distinction. |
| Medium | [frontend/app/pages/codecs/[id].vue](../../frontend/app/pages/codecs/[id].vue), [frontend/app/types/codec.ts](../../frontend/app/types/codec.ts) | Codec is referenced but has no guide. | Explain character inventory, generate/validate sources and scope controls. |
| Medium | [frontend/app/pages/search.vue](../../frontend/app/pages/search.vue) | No minimum query length, match modes or Hits/Clustered explanation. | Document actual search controls and index troubleshooting. |
| Medium | [frontend/app/components/user/settings/](../../frontend/app/components/user/settings/), [frontend/app/pages/workspace/settings/requests.vue](../../frontend/app/pages/workspace/settings/requests.vue) | No account, notification, invitation or transfer workflow. | Add account guide and workspace permission boundaries. |
| Medium | [frontend/app/components/project/slideover/export-target.vue](../../frontend/app/components/project/slideover/export-target.vue), [frontend/app/pages/share/[sharePublicId].vue](../../frontend/app/pages/share/[sharePublicId].vue) | Export, package, release, transfer and external share concepts are mixed. | Add a decision table and procedures; retain detailed package reference. |
| Medium | [docs/app/components/DocsLanding.vue](../../docs/app/components/DocsLanding.vue), [docs/content/index.md](../../docs/content/index.md), [docs/app/pages/[...slug].vue](../../docs/app/pages/[...slug].vue) | Duplicate homepage content and no purposeful narrative. | One rendered landing page, role-based entry points, first-project tutorial, concepts, ordered sections. |

Infrastructure and Actions audit findings and corrections are recorded separately in [reference audit](../audit-reference.md). That audit checks Compose, configuration properties, Taskfile, processor services and the sibling SDK. Retain detailed accurate references rather than replace them with generic prose.

Browser review also identified and corrected a search-index quality issue: generated syntax-highlighting `<style>` content was included in section excerpts, and H1 sections duplicated page-title records. Search now starts at H2, stops at H3, and ignores style/script nodes. Search dialog labels are explicit; small screens receive a collapsible on-page contents list. Local filename grouping by the first dot is documented from `ImageFileUtils.parseImageName`.

## Content plan

1. **Start here:** audience orientation → first project → concepts. Installation remains a separate operator route. Readers already joining an instance need no local setup.
2. **User guide:** workflow map → workspace/library → projects/import → editor/layout → transcription/review → collaboration/history → assignments → search → exports/sharing → datasets → Actions → toolkit → administration → account.
3. **Actions:** running Actions, definition/admin/SDK reference, result semantics, security, training, evaluation, integration and reports.
4. **Deployment:** installation handoff, production operation and configuration.
5. **Development:** contributor setup, commands and official services.
6. **Troubleshooting:** symptoms, non-destructive diagnosis and role-appropriate next steps.

Keep all existing published route slugs. Reorder numeric file prefixes only after content edits finish. Add explicit next-step links where a tutorial or procedure hands off to another workflow. Keep reference depth in the existing deployment/Actions/package pages.

## Implementation status

Implemented the planned reading order while preserving every original route slug. The content set grew from 47 to 58 pages with 11 new guides. Existing detailed project/package, administration and Action references remain available. Expanded layout procedures cover reading order, region relations and metadata. Project coverage now explains directory imports, filename pairing, default resources, editor overrides and output retention. Replaced the stale docs README with the actual build and editorial workflow, plus a source-linked feature coverage map.

Added build-gated content validation with the site's MDC heading normalization, sidebar/breadcrumb improvements, mobile contents, visible inline links, edit/report links and search index cleanup. Verification results are recorded in the change report; application workflows remain source-checked unless explicitly exercised.

## Acceptance and maintenance

- Every newly documented control is grounded in the frontend implementation and consequential behavior in backend policy/services.
- Every product page family has a discoverable guide or a deliberate reference link.
- First-time annotators can follow an image-to-saved-annotation-to-export path without reading deployment material.
- Task names match the UI: Assignments contain Tasks; Actions execute processors; Jobs show background progress.
- Distinguish saved/live changes, project/dataset copies/releases, profile preview/apply, permission/lock/quota failures.
- Check page titles/descriptions, route collisions, internal route links, heading anchors, local assets and Vue navigation links automatically.
- Build production docs and inspect desktop/mobile navigation, search, code blocks, tables, tutorial and reference pages in a browser.
- Record actual verification results and limitations in the final change report. Source audit is not an end-to-end application test.
