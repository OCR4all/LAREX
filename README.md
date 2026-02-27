# LAREX

LAREX (Layout Analysis and Recognition) is a full-stack web application for annotating facsimiles of early printed books, correcting OCR output, and creating ground-truth data for research and machine learning workflows.

## Stack

- Frontend: Nuxt 4
- Backend: Spring Boot (Java 21)
- Database: PostgreSQL
- Authentication: Keycloak (OIDC/OAuth2)
- Deployment/orchestration: Docker Compose
- Development helper: Taskfile (optional)

## Quick Start (Docker Compose)

### Prerequisites

- Docker Desktop

### Start the project

```bash
git clone <repository-url>
cd larex
docker compose up -d
```

### Default local services

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Keycloak: `http://localhost:8090`

## Development (Optional)

For local development workflows, the repository also provides a `Taskfile`.

### Additional prerequisites (development only)

- Node.js 20+
- Java 21+
- [Task](https://taskfile.dev) (`brew install go-task` on macOS)

### Development startup

```bash
task setup
task dev
```

## Documentation

Detailed documentation is maintained on the separate documentation website.

- Documentation source (website content): [`docs/`](docs/)
- API docs (local): `http://localhost:8080/swagger-ui.html`

## Repository Structure

- `frontend/` - Nuxt application
- `backend/` - Spring Boot API
- `keycloak-theme/` - Custom Keycloak theme
- `docs/` - Documentation website source
- `deployment/` - Deployment assets and environment templates
- `Taskfile.yml` - Monorepo task orchestration

## Common Commands (Development)

```bash
task --list        # List available tasks
task status        # Check service status
task test          # Run backend + frontend tests
task docker:up     # Start services
task docker:down   # Stop services
```

## Contributing

Please use the documentation website for development, deployment, and troubleshooting guides. Run `task test` (or the equivalent backend/frontend test commands) before opening a pull request.

## License

Apache License 2.0 (see `LICENSE`).
