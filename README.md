# LAREX

LAREX (Layout Analysis and Recognition) is a full-stack web application for annotating facsimiles of early printed books, correcting OCR output, and creating ground-truth data for research and machine learning workflows.

## Stack

- Frontend: Nuxt 4
- Backend: Spring Boot (Java 21)
- Database: PostgreSQL
- Authentication: Keycloak
- Deployment: Docker Compose

## Supported Setups

| Setup | Purpose | Command |
|-------|---------|---------|
| Local dev | Source-mounted developer workflow with Traefik and `*.localhost` | `docker compose up -d` |
| Local production-like | Production images/runtime with Traefik, `*.localhost`, and Mailpit | `docker compose --env-file .env.prod.local -f compose.prod.base.yaml -f compose.prod.auth.bundled-keycloak.yaml -f compose.prod.local.yaml up -d` |
| Opinionated production | Production images/runtime with loopback-bound ports for your own reverse proxy | `docker compose --env-file .env.prod -f compose.prod.base.yaml -f compose.prod.auth.bundled-keycloak.yaml -f compose.prod.publish.localhost.yaml up -d` |

Production supports two small overrides:

- External Keycloak: replace `compose.prod.auth.bundled-keycloak.yaml` with `compose.prod.auth.external-keycloak.yaml`
- Bundled Nginx: add `-f compose.prod.nginx.yaml`

## Quick Start

### Local dev

```bash
git clone <repository-url>
cd larex
docker compose up -d
```

Local dev routes through Traefik:

- App: `http://larex.localhost`
- API: `http://api.localhost`
- Keycloak: `http://keycloak.localhost`
- Mailpit: `http://mail.localhost`

### Local production-like

```bash
cp deployment/env/.env.prod.local.example .env.prod.local
docker compose --env-file .env.prod.local \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.local.yaml \
  up -d
```

### Opinionated production

```bash
cp deployment/env/.env.prod.example .env.prod
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  up -d
```

The production default binds frontend to `127.0.0.1:3000` and bundled Keycloak to `127.0.0.1:8090`. Put your own Nginx, Caddy, Apache, or similar reverse proxy in front.

## Taskfile

Optional helper commands are available through [Task](https://taskfile.dev):

```bash
task --list
task docker:up
task docker:prod:up
task docker:prod:local:up
```

## Documentation

- Deployment docs live in `docs/`
- API docs are available locally at `http://localhost:8080/swagger-ui.html`

## License

Apache License 2.0 (see `LICENSE`).
