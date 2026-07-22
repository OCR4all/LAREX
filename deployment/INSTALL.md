# LAREX deployment bundle

This directory is the supported, repository-free installation artifact for LAREX
administrators. It contains every supported production Compose fragment so the same
download can be used with bundled or external Keycloak, an existing reverse proxy or
the bundled Nginx proxy, self-hosted documentation, and the official Kraken Action
processor.

For a bundle containing only the selected files and a preconfigured environment
template, use the deployment wizard in the LAREX documentation.

## Start the recommended deployment

Review `deployment/env/.env.prod.example`, then generate local secrets:

```bash
bash scripts/bootstrap-env-secrets.sh prod
```

Start LAREX with bundled Keycloak behind an existing reverse proxy:

```bash
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  up -d --wait --wait-timeout 300
```

The frontend listens on `127.0.0.1:3000` and Keycloak on `127.0.0.1:8090` by
default. See the production deployment documentation for the other supported
combinations and operational procedures.

## Validate before starting

Replace `up ...` in the selected command with `config --quiet`. Pin
`LAREX_IMAGE_TAG` to a released version for production.

## Secrets

The bundle never contains generated credentials. The bootstrap script creates
`.env.prod` and, when Kraken is selected, `.env.actions` locally. Keep those files
out of source control and in secret-grade storage.
