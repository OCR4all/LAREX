# LAREX

LAREX (Layout Analysis and Recognition) is a full-stack web application for annotating facsimiles of early printed books, correcting OCR output, and creating ground-truth data for research and machine learning workflows.

## Stack

- Frontend: Nuxt 4
- Backend: Spring Boot 4 (Java 25)
- Database: PostgreSQL
- Authentication: Keycloak
- Deployment: Docker Compose

## Supported Setups

| Setup | Purpose | Command |
|-------|---------|---------|
| Local dev | Source-mounted developer workflow with Traefik and `*.localhost` | `docker compose up -d` |
| Local production-like | Production images/runtime with Traefik, `*.localhost`, and Mailpit | `docker compose --env-file .env.prod.local -f compose.prod.base.yaml -f compose.prod.auth.bundled-keycloak.yaml -f compose.prod.local.yaml up -d` |
| Opinionated production | Production images/runtime with loopback-bound ports for your own reverse proxy | `docker compose --env-file .env.prod -f compose.prod.base.yaml -f compose.prod.auth.bundled-keycloak.yaml -f compose.prod.publish.localhost.yaml up -d` |

Production supports three small overrides:

- External Keycloak: replace `compose.prod.auth.bundled-keycloak.yaml` with `compose.prod.auth.external-keycloak.yaml`
- Bundled Nginx: add `-f compose.prod.nginx.yaml`
- Bundled Kraken segmentation processor: add `-f compose.prod.kraken.yaml`

Docs self-hosting is also available as optional overrides:

- Local dev: add `-f compose.dev.docs.yaml` (served on `http://docs.localhost`)
- Local production-like: add `-f compose.prod.local.docs.yaml` (served on `http://docs.localhost`)
- Opinionated production: add `-f compose.prod.docs.yaml` (published on `127.0.0.1:3001` by default)

## Quick Start

### Local dev

```bash
git clone <repository-url>
cd larex
docker compose up -d

# with self-hosted docs
docker compose -f compose.yaml -f compose.dev.docs.yaml up -d
```

Local dev routes through Traefik:

- App: `http://larex.localhost`
- API: `http://api.localhost`
- Keycloak: `http://keycloak.localhost`
- Mailpit: `http://mail.localhost`
- Docs: `http://docs.localhost` (with `compose.dev.docs.yaml`)

### Local production-like

```bash
./scripts/bootstrap-env-secrets.sh prod-local
docker compose --env-file .env.prod.local \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.local.yaml \
  up -d

# with bundled Kraken segmentation processor
docker compose --env-file .env.prod.local \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.local.yaml \
  -f compose.prod.kraken.yaml \
  up -d

# with self-hosted docs
docker compose --env-file .env.prod.local \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.local.yaml \
  -f compose.prod.local.docs.yaml \
  up -d
```

### Opinionated production

```bash
./scripts/bootstrap-env-secrets.sh prod
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  up -d

# with bundled Kraken segmentation processor
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  -f compose.prod.kraken.yaml \
  up -d

# with self-hosted docs
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  -f compose.prod.docs.yaml \
  up -d
```

The production default binds frontend to `127.0.0.1:3000` and bundled Keycloak to `127.0.0.1:8090`. Put your own Nginx, Caddy, Apache, or similar reverse proxy in front.

## Taskfile

Optional helper commands are available through [Task](https://taskfile.dev):

```bash
task --list
task docker:up
task docker:up:docs
task docker:prod:up
task docker:prod:up:docs
task docker:prod:up:kraken
task docker:prod:init-env
task docker:prod:local:up
task docker:prod:local:up:docs
task docker:prod:local:up:kraken
task docker:prod:local:init-env
```

The bootstrap script copies the appropriate example env file if needed and seeds all required secrets, including database passwords, Keycloak client secrets, the Nuxt session secret, collaboration token secret, and notification bridge secret. Rerunning it keeps existing non-placeholder values intact.

## Documentation

- Deployment docs live in `docs/`
- Backend PAGE package map: `docs/backend/page-packages.md`
- API docs are available locally at `http://localhost:8080/swagger-ui.html`

## License

Apache License 2.0 (see `LICENSE`).
