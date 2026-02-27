# LAREX Keycloak Theme

This package contains the custom Keycloak theme used by the LAREX monorepo.
It is not a standalone starter project.

## Directory purpose

- Source for the login theme lives in `src/login/`.
- Theme build artifacts are generated in `dist/` and `dist_keycloak/`.
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
