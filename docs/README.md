# LAREX documentation

This is the LAREX documentation site, built with Nuxt Content and Nuxt UI. Published pages live in `content/`; the landing page is rendered by `app/components/DocsLanding.vue`. It is not a Docus project.

## Work locally

Use Node.js 22.18 or newer and the pnpm version pinned in `package.json`.

```bash
cd docs
pnpm install --frozen-lockfile
pnpm dev
```

Open the URL printed by Nuxt. `pnpm dev` stages the installation wizard's downloadable deployment assets first. Run these commands from the repository checkout: staging reads the version, Compose files, deployment templates, scripts, and packaged Keycloak theme outside this directory. A theme metadata/version mismatch must be fixed by rebuilding the theme package, not by bypassing verification.

```bash
pnpm check    # Metadata, routes, links, heading anchors, local assets, navigation
pnpm build    # Stage installer assets, run content checks, build production server
pnpm start    # Serve the production build
```

The repository's CI docs build runs the same prebuild checks. The generated site is in `.output/`. Content/database and installer assets are generated artifacts; edit sources rather than generated output.

## Organization

| Directory | Reader need |
| --- | --- |
| `content/01.getting-started/` | Orientation, first-project tutorial, concepts, and installation entry point. |
| `content/02.user-guide/` | Product workflows, toolkit, and in-app administration. |
| `content/03.actions/` | Action execution, authoring, contracts, security, and purpose-specific guides. |
| `content/04.deployment/` | Production setup, configuration, and operation. |
| `content/05.development/` | Contributor environment and commands. |
| `content/06.troubleshooting/` | Technical diagnosis for operators and developers. |
| `maintenance/` | Audit, coverage map, and editorial maintenance records; not published. |

Numeric prefixes control sidebar and previous/next order. Nuxt Content removes them from published URLs. Use zero-padded prefixes so `10` sorts after `09`. Keep existing unnumbered slugs stable; changing them requires redirects and a link audit. `.navigation.yml` files provide section titles/icons. The homepage's `index.md` contains metadata only; keep rendered landing content in the Vue component to avoid competing versions.

## Update a guide

1. Identify the reader, prerequisite, and outcome. Prefer a procedure with an observable success check over a feature list.
2. Verify control names in frontend pages/components and consequential behavior in backend policy/services. For Actions, check the definition validator and matching SDK/processor contract. For deployment, distinguish Compose interpolation from variables actually passed to containers.
3. Explain scope and persistence: saved versus live XML, selected versus all pages/variants, preview versus apply, working copy versus frozen release. State permissions and recovery where they matter.
4. Link the next useful workflow. Keep explanations in concept pages and detailed schemas/defaults in reference pages; avoid duplicating changing defaults in several guides.
5. Run `pnpm check` and `pnpm build`, then inspect the affected pages at desktop and mobile sizes. Check tables, code, navigation, and the search entry point.

`pnpm check` validates local documentation targets; it does not fetch external links, validate every command semantically, or exercise the application. Review source evidence and execute representative workflows when behavior changes. Report what was actually tested.

Read the [audit and plan](maintenance/documentation-audit.md), [reference audit](audit-reference.md), [feature coverage map](maintenance/feature-coverage.md), and [verification record](maintenance/verification.md) for the baseline and ownership of remaining uncertainty.
