# LAREX Keycloak Theme

This package contains the custom Keycloak theme used by the LAREX monorepo.
It is not a standalone starter project.

## Directory purpose

- Source for the login theme lives in `src/login/`.
- Theme build artifacts are generated in `dist/` and `dist_keycloak/`.
- `task theme:deploy` audits the archive and generates a SHA-256 checksum and local build metadata.
- The built JAR is copied to `../config/keycloak/theme.jar` and mounted by Docker Compose in local Keycloak setups.

## Development

From repository root:

```bash
task theme:install
task theme:dev
```

From this directory (`keycloak-theme/`):

```bash
pnpm install --frozen-lockfile
pnpm dev
```

## Build and deploy to local Keycloak

From repository root:

```bash
task theme:build:theme   # Build the Keycloakify JAR(s)
task theme:deploy        # Copy current JAR to config/keycloak/theme.jar
```

Then restart Keycloak:

```bash
docker compose restart keycloak
```

## Storybook

```bash
task theme:storybook
```

or inside this directory:

```bash
pnpm storybook
```

## References

- Keycloakify docs: https://docs.keycloakify.dev/
- LAREX monorepo task entrypoints: `../Taskfile.yml`

## Release distribution

The packaging and docs asset scripts are TypeScript ES modules (`.mts`), executed
with Node's built-in type stripping (Node 22.18 or newer). From `keycloak-theme/`,
run `pnpm typecheck:scripts` to check both scripts using the existing TypeScript
and Node type dependencies. CI runs this check before building the theme.

The reusable Keycloak theme workflow builds from locked dependencies and audits
the JAR to exclude Java classes, nested JARs, and provider registrations. The
release workflow uses that same artifact for the docs installer, deployment ZIP,
and standalone versioned JAR, then publishes GitHub provenance attestations.
Local builds are explicitly unattested. See
[the installation guide](../deployment/KEYCLOAK-THEME.md) for verification and
bundled/external Keycloak container installation.
