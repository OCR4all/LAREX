# LAREX documentation

This is the LAREX documentation site, built with [Docus](https://docus.dev) (a Nuxt layer on Nuxt Content and Nuxt UI). Published pages live in `content/`; the landing page is `app/pages/index.vue`, which renders `app/components/DocsLanding.vue`. Site title, GitHub edit links, and colors are set in `app/app.config.ts`; components in `app/components/` with the same name as a Docus component (for example `AppHeaderLogo.vue`) override it.

## Work locally

Use Node.js 22.18 or newer and the pnpm version pinned in `package.json`.

```bash
cd docs
pnpm install --frozen-lockfile
pnpm dev
```

Open the URL printed by Nuxt. `pnpm dev` stages the installation wizard's downloadable deployment assets first. Run these commands from the repository checkout: staging reads the version, Compose files, deployment templates, scripts, and packaged Keycloak theme outside this directory. A theme metadata/version mismatch must be fixed by rebuilding the theme package, not by bypassing verification.

```bash
pnpm build    # Stage installer assets, build production server
pnpm start    # Serve the production build
```

The repository's CI runs the same build. The generated site is in `.output/`. Content/database and installer assets are generated artifacts; edit sources rather than generated output.

## Organization

| Directory | Reader need |
| --- | --- |
| `content/01.getting-started/` | Orientation, first-project tutorial, concepts, and installation entry point. |
| `content/02.user-guide/` | Product workflows, toolkit, and in-app administration. |
| `content/03.actions/` | Action execution, authoring, contracts, security, and purpose-specific guides. |
| `content/04.deployment/` | Production setup, configuration, and operation. |
| `content/05.development/` | Contributor environment and commands. |
| `content/06.troubleshooting/` | Technical diagnosis for operators and developers. |

Numeric prefixes control sidebar and previous/next order. Nuxt Content removes them from published URLs. Use zero-padded prefixes so `10` sorts after `09`. Keep existing unnumbered slugs stable; changing them requires redirects and a link audit. `.navigation.yml` files provide section titles/icons. Docus renders each page's frontmatter `title` and `description` as the page header, so pages do not start with a `# Heading`. There is no `content/index.md`; the landing page lives entirely in Vue.

## Update a guide

1. Identify the reader, prerequisite, and outcome. Prefer a procedure with an observable success check over a feature list.
2. Verify control names in frontend pages/components and consequential behavior in backend policy/services. For Actions, check the definition validator and matching SDK/processor contract. For deployment, distinguish Compose interpolation from variables actually passed to containers.
3. Explain scope and persistence: saved versus live XML, selected versus all pages/variants, preview versus apply, working copy versus frozen release. State permissions and recovery where they matter.
4. Link the next useful workflow. Keep explanations in concept pages and detailed schemas/defaults in reference pages; avoid duplicating changing defaults in several guides.
5. Run `pnpm build`, then inspect the affected pages at desktop and mobile sizes. Check tables, code, navigation, and the search entry point.

The build does not validate links, commands, or application behavior. Review source evidence and execute representative workflows when behavior changes. Report what was actually tested.
